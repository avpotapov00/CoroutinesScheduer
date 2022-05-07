package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.locks.withLock
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.boruvka.ParallelDsu
import org.jetbrains.kotlin.graph.util.edges.Edge
import org.jetbrains.kotlin.graph.util.edges.EdgeIndexed
import org.jetbrains.kotlin.number.smq.StealingLongMultiQueueKS
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.indexedBinarySearch
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.ReentrantLock

/**
 * В Long храним два значения: первое это приоритет, второе это индекс вершины
 */
class NonBlockingPriorityLongBoruvkaScheduler(
    private val edges: List<EdgeIndexed>,
    private val nodes: Int,
    private val poolSize: Int,
    stealSize: Int = 3,
    pSteal: Double = 0.04,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 300,
) : StealingLongMultiQueueKS(stealSize, pSteal, poolSize), Closeable {

    val dsu = ParallelDsu(nodes)

    val edgesSet = ConcurrentHashMap.newKeySet<Int>().also { set -> set.addAll(edges.indices) }

    val mst = ConcurrentHashMap.newKeySet<EdgeIndexed>()

    /**
     * End of work flag
     */
    @Volatile
    private var terminated = false

    /**
     * Threads serving the scheduler
     */
    var threads: List<Worker> = (0 until poolSize).map { index -> Worker(index) }

    val finishPhaser = Phaser(1 + poolSize)

    var shortestPaths: AtomicReferenceArray<EdgeIndexed> = AtomicReferenceArray(nodes)

    val tasks: List<MutableList<Long>> = (0 until poolSize).map { ArrayList() }
    val flags: AtomicReferenceArray<Boolean> = AtomicReferenceArray<Boolean>(poolSize).also { array ->
        (0 until poolSize).forEach { array.set(it, false) }
    }

    private fun loadNextBatchFirstPhase() {
        var index = 0

        for (edge in edgesSet) {
            if (index == poolSize) index = 0

            tasks[index++].add(edges[edge].weight.zip(edge))
        }

        (0 until poolSize).forEach { flags.set(it, true) }

    }

    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
    }

    inner class Worker(override val index: Int) : IndexedThread() {

        var totalTasksProcessed: Long = 0

        var successStealing: Int = 0

        var tasksLowerThanStolen: Int = 0

        var failedStealing: Int = 0

        var stealingAttempts: Int = 0

        override fun run() {
            var attempts = 0
            while (!terminated) {
                if (finishPhaser.phase % 2 == 0) {
                    checkInsertBatch()

                    while (true) {
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
                        task = stealAndDeleteFromSelf(index)

                        if (task != Long.MIN_VALUE) {
                            attempts = 0
                            tryUpdateFirstPhase(edges[task.secondFromLong], task.secondFromLong)
                            continue
                        }

                        finishPhaser.arriveAndAwaitAdvance()
                        break
                    }
                } else { // 2 phase

                    var i = index

                    while (i < nodes) {
                        tryUpdateSecondPhase(i)
                        i += poolSize
                    }

                    finishPhaser.arriveAndAwaitAdvance()
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
                    tasks[index].forEach {
                        insert(it)
                    }
                    flags[index] = false
            }
        }

        private fun tryUpdateFirstPhase(edge: EdgeIndexed, index: Int) {
            if (dsu.isSameSet(edge.to, edge.from)) {
                edgesSet.remove(index)
                return
            }

            // UpdateIfShorter(&shortestEdges[Find(e.from)], e)
            do {
                val nodeIndex = dsu.find(edge.from)
                val prevBestEdge = shortestPaths[nodeIndex]

                if (prevBestEdge != null && prevBestEdge.weight < edge.weight) {
                    break
                }
                if (prevBestEdge != null && prevBestEdge.weight == edge.weight && prevBestEdge.index < edge.index) {
                    break
                }
            } while (!shortestPaths.compareAndSet(nodeIndex, prevBestEdge, edge))

            // UpdateIfShorter(&shortestEdges[Find(e.to)], e)
            do {
                val nodeIndex = dsu.find(edge.to)
                val prevBestEdge = shortestPaths[nodeIndex]

                if (prevBestEdge != null && prevBestEdge.weight < edge.weight) {
                    break
                }
                if (prevBestEdge != null && prevBestEdge.weight == edge.weight && prevBestEdge.index < edge.index) {
                    break
                }
            } while (!shortestPaths.compareAndSet(nodeIndex, prevBestEdge, edge))
        }

        private fun tryUpdateSecondPhase(node: Int) {
            val edge = shortestPaths[node]

            if (edge != null) {
                if (!dsu.isSameSet(edge.from, edge.to)) {
                    dsu.union(edge.from, edge.to)
                    mst.add(edge)
                }
                shortestPaths[node] = null
            }
        }

    }

    fun totalTasksProcessed(): Long {
        return threads.sumOf { it.totalTasksProcessed }
    }

    fun successStealing(): Long {
        return threads.sumOf { it.successStealing.toLong() }
    }

    fun failedStealing(): Long {
        return threads.sumOf { it.failedStealing.toLong() }
    }

    fun stealingAttempts(): Long {
        return threads.sumOf { it.stealingAttempts.toLong() }
    }

    fun tasksLowerThanStolen(): Long {
        return threads.sumOf { it.tasksLowerThanStolen.toLong() }
    }


    fun parallelBoruvkaExp(): Pair<NonBlockingPriorityLongBoruvkaScheduler, Set<EdgeIndexed>> {
        loadNextBatchFirstPhase()
        threads.forEach { it.start() }
        waitForTermination()

        loadNextBatchFirstPhase()
        waitForTermination()

        while (mst.size < nodes - 1) {
            waitForTermination()

            loadNextBatchFirstPhase()
            waitForTermination()
        }

        terminated = true
        finishPhaser.forceTermination()

        return this to mst
    }

    override fun close() {
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

}
