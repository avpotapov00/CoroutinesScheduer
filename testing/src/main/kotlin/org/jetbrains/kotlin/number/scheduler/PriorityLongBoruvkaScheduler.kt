package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.boruvka.ParallelDsu
import org.jetbrains.kotlin.graph.util.edges.Edge
import org.jetbrains.kotlin.number.smq.StealingLongMultiQueueKS
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.indexedBinarySearch
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

/**
 * В Long храним два значения: первое это приоритет, второе это индекс вершины
 */
class PriorityLongBoruvkaScheduler(
    private val edges: List<Edge>,
    private val nodes: Int,
    private val poolSize: Int,
    stealSize: Int = 3,
    pSteal: Double = 0.04,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 300,
) : StealingLongMultiQueueKS(stealSize, pSteal, poolSize), Closeable {

    val dsu = ParallelDsu(nodes)

    val edgesSet = ConcurrentHashMap.newKeySet<Int>().also { set -> set.addAll(edges.indices) }

    val mst = ConcurrentHashMap.newKeySet<Edge>()

    /**
     * End of work flag
     */
    @Volatile
    private var terminated = false

    /**
     * Threads serving the scheduler
     */
    var threads: List<Worker> = (0 until poolSize).map { index -> Worker(index) }

    /**
     * Buffer for the freshest sleeping thread
     */
    private val sleepingBox: AtomicRef<Worker?> = atomic(null)

    /**
     * Array where sleeping threads are stored
     */
    private val sleepingArray: AtomicArray<Worker?> = atomicArrayOfNulls(poolSize * 2)

    private val random = Random(0)

    val finishPhaser = Phaser(1 + poolSize)

    var shortestPaths: AtomicReferenceArray<Edge> = AtomicReferenceArray(nodes)

    @Volatile
    var isFirstPhase = 0

    @Volatile
    var isLocked = true


    private val tasks: List<ListWithLock> = (0 until poolSize).map { ListWithLock() }
    private val flags: AtomicReferenceArray<Boolean> = AtomicReferenceArray<Boolean>(poolSize).also { array ->
        (0 until poolSize).forEach { array.set(it, false) }
    }

    fun loadNextBatchFirstPhase() {
        var index = 0

        for (it in tasks) {
            it.lock.lock()
            it.list.clear()
        }
        for (edge in edgesSet) {
            if (index == poolSize) index = 0

            tasks[index++].list.add(edges[edge].weight.zip(edge))
        }

        (0 until poolSize).forEach { flags.set(it, true) }

        tasks.forEach { it.lock.unlock() }
    }

    fun loadNextBatchSecondPhase() {
        var index = 0

        tasks.forEach {
            it.lock.lock()
            it.list.clear()
        }
        (0 until nodes).forEach { node ->
            if (index == poolSize) index = 0

            tasks[index++].list.add(0.zip(node))
        }

        (0 until poolSize).forEach { flags.set(it, true) }

        tasks.forEach { it.lock.unlock() }
    }

    fun wakeAll() {
        isLocked = false

        sleepingBox.value = null
        for (i in 0 until sleepingArray.size) {
            sleepingArray[i].value = null
        }

        threads.forEach {
            LockSupport.unpark(it)
        }
    }


    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
        isLocked = true
    }

    private fun tryWakeThread() {
        var recentWorker = sleepingBox.value

        // if found a thread in sleeping box, trying to get it, or go further, if someone has taken it earlier
        while (recentWorker != null) {
            if (sleepingBox.compareAndSet(recentWorker, null)) {
                LockSupport.unpark(recentWorker)
                return
            }
            recentWorker = sleepingBox.value
        }

        // Try to get a thread from the array several times
        for (i in 0 until WAKE_RETRY_COUNT) {
            val index = random.nextInt(0, sleepingArray.size)
            recentWorker = sleepingArray[index].value

            if (recentWorker != null && sleepingArray[index].compareAndSet(recentWorker, null)) {
                LockSupport.unpark(recentWorker)
            }
        }
    }

    inner class Worker(override val index: Int) : IndexedThread() {

        private val random = Random(0)

        private var isFirstPhaseLocal = isFirstPhase

        var totalTasksProcessed: Long = 0

        var successStealing: Int = 0

        var tasksLowerThanStolen: Int = 0

        var failedStealing: Int = 0

        var stealingAttempts: Int = 0

        override fun run() {
            var attempts = 0
            while (!terminated) {
                if (isFirstPhase % 2 == 0) {
                    while (!terminated) {
                        checkInsertBatch()

                        // trying to get from local queue
                        var task = delete()

                        if (task != Long.MIN_VALUE) {
                            attempts = 0
                            tryUpdateFirstPhase(edges[task.secondFromLong], task.secondFromLong)
                            continue
                        }

                        if (attempts < retryCount) {
                            attempts++
                            continue
                        }

                        // if it didn't work, we try to remove it from the self queue
                        task = stealAndDeleteFromSelf()

                        if (task != Long.MIN_VALUE) {
                            attempts = 0
                            tryUpdateFirstPhase(edges[task.secondFromLong], task.secondFromLong)
                            continue
                        }

                        goWait()
                        attempts = 0
                        break
                    }
                } else { // 2 phase
                    while (!terminated) {
                        checkInsertBatch()

                        // trying to get from local queue
                        var task = delete()

                        if (task != Long.MIN_VALUE) {
                            attempts = 0
                            tryUpdateSecondPhase(task.secondFromLong)
                            continue
                        }

                        if (attempts < retryCount) {
                            attempts++
                            continue
                        }

                        // if it didn't work, we try to remove it from the self queue
                        task = stealAndDeleteFromSelf()

                        if (task != Long.MIN_VALUE) {
                            attempts = 0
                            tryUpdateSecondPhase(task.secondFromLong)
                            continue
                        }

                        goWait()
                        attempts = 0
                        break
                    }
                }
            }
        }

        private fun delete(): Long {
            // Do we have previously stolen tasks ?
            if (stolenTasks.get().isNotEmpty()) {
                return stolenTasks.get().removeFirst()
            }
            val currThread = index

            // Should we steal ?
            if (shouldSteal()) {
                val task = trySteal(currThread)
                if (task != Long.MIN_VALUE) {
                    return task
                }
            }
            // Try to retrieve the top task
            // from the thread - local queue
            val task = queues[currThread].extractTopLocal()
            if (task != Long.MIN_VALUE) {
                return task
            }
            // The local queue is empty , try to steal
            return tryStealWithoutCheck()
        }

        private fun trySteal(currThread: Int): Long {
            // Choose a random queue and check whether
            // its top task has higher priority

            val otherQueue = getQueueToSteal()
            val ourTop = queues[currThread].top
            val otherTop = otherQueue.top
            if (ourTop != Long.MIN_VALUE) {
                stealingAttempts++
            }

            if (ourTop == Long.MIN_VALUE || otherTop == Long.MIN_VALUE || otherTop.firstFromLong < ourTop.firstFromLong) {
                // Try to steal a better task !
                val stolen = otherQueue.steal()
                if (stolen.isEmpty()) {
                    failedStealing++
                    return Long.MIN_VALUE
                } // failed
                if (ourTop != Long.MIN_VALUE) {
                    successStealing++

                    tasksLowerThanStolen += indexedBinarySearch(stolen, ourTop)
                }
                // Return the first task and add the others
                // to the thread - local buffer of stolen ones
                val stolenTasksDeque = stolenTasks.get()

                for (i in 1 until stolen.size) {
                    stolenTasksDeque.add(stolen[i])
                }
                return stolen[0]
            }

            return Long.MIN_VALUE
        }

        fun insert(task: Long) {
            queues[index].addLocal(task)
        }

        private fun checkInsertBatch() {
            if (flags[index]) {
                tasks[index].lock.withLock {
                    tasks[index].list.forEach {
                        insert(it)
                    }
                    flags[index] = false
                }
            }
        }

        private fun goWait() {

            var oldThread: Worker?

            do {
                oldThread = sleepingBox.value
            } while (!sleepingBox.compareAndSet(oldThread, this))

            do {
                val index = random.nextInt(0, sleepingArray.size)
                val cell = sleepingArray[index].value
            } while (!sleepingArray[index].compareAndSet(cell, oldThread))

            println("Sleep $index")
            finishPhaser.arrive()
            LockSupport.park()

            println("Wake $index with $isFirstPhase")
            isFirstPhaseLocal = isFirstPhase
        }

        private fun tryUpdateFirstPhase(edge: Edge, index: Int) {
            if (dsu.isSameSet(edge.to, edge.from)) {
                edgesSet.remove(index)
                return
            }

            // UpdateIfShorter(&shortestEdges[Find(e.from)], e)
            do {
                val edgeIndex = dsu.find(edge.from)
                val prevValue = shortestPaths[edgeIndex]

                if (prevValue != null && prevValue.weight < edge.weight) {
                    break
                }
            } while (!shortestPaths.compareAndSet(edgeIndex, prevValue, edge))

            //UpdateIfShorter(&shortestEdges[Find(e.to)], e)
            do {
                val edgeIndex = dsu.find(edge.to)
                val prevValue = shortestPaths[edgeIndex]

                if (prevValue != null && prevValue.weight < edge.weight) {
                    break
                }
            } while (!shortestPaths.compareAndSet(edgeIndex, prevValue, edge))
        }

        private fun tryUpdateSecondPhase(node: Int) {
            if (node >= shortestPaths.length()) {
                println("Error")
            }
            val edge = shortestPaths[node]

            if (edge != null && !dsu.isSameSet(edge.from, edge.to)) {
                dsu.union(edge.from, edge.to)
                mst.add(edge)
            }
        }

    }


    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

}

// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5

fun parallelBoruvkaExp(
    edges: List<Edge>,
    nodes: Int,
    poolSize: Int,
    stealSize: Int = 3,
    pSteal: Double = 0.04,
    retryCount: Int = 300,
): Pair<PriorityLongBoruvkaScheduler, Set<Edge>> {
    PriorityLongBoruvkaScheduler(edges, nodes, poolSize, stealSize, pSteal, retryCount).use { scheduler ->
        scheduler.isFirstPhase = 0

        scheduler.loadNextBatchFirstPhase()
        scheduler.threads.forEach { it.start() }
        scheduler.waitForTermination()

        scheduler.isFirstPhase = 1

        scheduler.loadNextBatchSecondPhase()
        scheduler.wakeAll()
        scheduler.waitForTermination()
        println("Unlocked!")

        while (scheduler.mst.size < nodes - 1) {

            scheduler.isFirstPhase++
            println("Change to ${scheduler.isFirstPhase}")
            scheduler.shortestPaths = AtomicReferenceArray(nodes)

            scheduler.loadNextBatchFirstPhase()
            scheduler.wakeAll()
            scheduler.waitForTermination()

            scheduler.isFirstPhase++
            println("Change to ${scheduler.isFirstPhase}")

            scheduler.loadNextBatchSecondPhase()
            scheduler.wakeAll()
            scheduler.waitForTermination()
        }
        return scheduler to scheduler.mst
    }
}

class ListWithLock {
    val lock = ReentrantLock()
    val list = mutableListOf<Long>()
}

