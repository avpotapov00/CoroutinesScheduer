package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.smq.heap.GlobalHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.HeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.indexedBinarySearch
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.LockSupport
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * @author Потапов Александр
 * @since 13.04.2022
 */
class AdaptivePriorityLongBfsScheduler(
    private val nodes: List<BfsIntNode>,
    startIndex: Int,
    val poolSize: Int,
    val stealSize: Int = 3,
    val pSteal: Double = 0.04,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    private val checkAttemptsCount: Int = 100,
) : Closeable {

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
     * Buffer for the freshest sleeping stream
     */
    private val sleepingBox: AtomicRef<Worker?> = atomic(null)

    /**
     * Array where sleeping threads are stored
     */
    private val sleepingArray: AtomicArray<Worker?> = atomicArrayOfNulls(poolSize * 2)

    val finishPhaser = Phaser(poolSize + 1)

    private val globalQueue = GlobalHeapWithStealingBufferLongQueue(stealSize)

    val queues = Array(poolSize) { HeapWithStealingBufferLongQueue(stealSize) }

    val stolenTasks = ThreadLocal.withInitial { ArrayDeque<Long>(stealSize) }

    // your size + the size of the buffer for theft + the size of the global queue
    fun size() = queues[currThread()].size + stolenTasks.get().size + globalQueue.size

    fun insertGlobal(task: Long) {
        globalQueue.add(task)
    }

    init {
        insertGlobal(0.zip(startIndex))


        nodes[startIndex].distance = 0
        threads.forEach { it.start() }
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

        val random: ThreadLocalRandom = ThreadLocalRandom.current()

        var pStealLocal = pSteal

        override fun run() {
            var attempts = 0
            while (!terminated) {

                // trying to get from local queue
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    totalTasksProcessed++
                    tryUpdate(nodes[task.secondFromLong])
                    continue
                }

                if (attempts < retryCount) {
                    attempts++
                    continue
                }

                // if it didn't work, we try to remove it from the global queue
                task = stealAndDeleteFromGlobal()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    tryUpdate(nodes[task.secondFromLong])
                    continue
                }

                // if it didn't work, we try to remove it from the self queue
                task = stealAndDeleteFromSelf()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    totalTasksProcessed++
                    tryUpdate(nodes[task.secondFromLong])
                    continue
                }

                goWait()
                attempts = 0
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
            val ourQueue = queues[currThread]
            val ourTop = ourQueue.top
            val otherTop = otherQueue.top
            if (ourTop != Long.MIN_VALUE) {
                stealingAttempts++
            }

            if (ourTop == Long.MIN_VALUE || otherTop == Long.MIN_VALUE || otherTop.firstFromLong < ourTop.firstFromLong) {
                // Try to steal a better task !
                val stolen = otherQueue.steal()
                if (stolen.isEmpty()) {
                    failedStealing++
                    checkUpdate(ourQueue)
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
                checkUpdate(ourQueue)
                return stolen[0]
            }

            checkUpdate(ourQueue)
            return Long.MIN_VALUE
        }

        private fun checkUpdate(ourQueue: HeapWithStealingBufferLongQueue) {
            if (stealingAttempts < checkAttemptsCount) return

            val successByAttempts = log10(successStealing.toDouble() / stealingAttempts)
            val tasksBetterByAttempts = log10(tasksLowerThanStolen.toDouble() / stealingAttempts)

            stealingAttempts = 0
            successStealing = 0
            tasksLowerThanStolen = 0

            val ds = 0.619194 + 0.754805 * successByAttempts + 0.0352621 * tasksBetterByAttempts
            val db = 0.250731 + 0.0352621 * successByAttempts - 0.167134 * tasksBetterByAttempts

            if (db > 0 && stealSize != stealSizeUpperBound) {
                ourQueue.setNextStealSize(stealSize * 2)
//                println("$index: db $db: - ${ourQueue.stealSize} - ${ourQueue.stealSize * 2}")
            } else if (db < 0 && stealSize != stealSizeLowerBound) {
                ourQueue.setNextStealSize(ourQueue.stealSize / 2)
//                println("$index: db $db: - ${ourQueue.stealSize} - ${ourQueue.stealSize / 2}")
            }

            if (ds > 0) {
//                println("$index: ds $ds: $pStealLocal - ${min(pStealLowerBound, pStealLocal / 2)}")
                pStealLocal = max(pStealLowerBound, pStealLocal / 2)
            } else if (ds < 0) {
//                println("$index: ds $ds: - $pStealLocal - ${max(pStealUpperBound, pStealLocal * 2)}")
                pStealLocal = min(pStealUpperBound, pStealLocal * 2)
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

            finishPhaser.arriveAndDeregister()
            LockSupport.park()
            finishPhaser.register()
        }

        private fun checkWakeThread() {
            // if the number of tasks in the local queue is more than the threshold, try to wake up a new thread
            if (size() > TASKS_COUNT_WAKE_THRESHOLD) {
                tryWakeThread()
            }
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
                val index = ThreadLocalRandom.current().nextInt(0, sleepingArray.size)
                recentWorker = sleepingArray[index].value

                if (recentWorker != null && sleepingArray[index].compareAndSet(recentWorker, null)) {
                    LockSupport.unpark(recentWorker)
                    return
                }
            }
        }

        private fun tryUpdate(cur: BfsIntNode) {
            for (e in cur.outgoingEdges) {

                val to = nodes[e]

                while (cur.distance + 1 < to.distance) {
                    val currDist = cur.distance
                    val toDist = to.distance
                    val nextDist = currDist + 1

                    if (toDist > nextDist && to.casDistance(toDist, nextDist)) {
                        val task = nextDist.zip(e)

                        insert(task)
                        break
                    }
                }
            }

            checkWakeThread()
        }

        fun insert(task: Long) {
            queues[index].addLocal(task)
        }

        fun shouldSteal() = ThreadLocalRandom.current().nextDouble() < pStealLocal

    }

    fun stealAndDeleteFromGlobal(): Long {
        val queueToSteal = globalQueue

        return stealFromExactQueue(queueToSteal)
    }

    fun stealAndDeleteFromSelf(): Long {
        val queueToSteal = queues[currThread()]

        return stealFromExactQueue(queueToSteal);
    }

    private fun stealFromExactQueue(queueToSteal: StealingLongQueue): Long {
        val stolen = queueToSteal.steal()
        if (stolen.isEmpty()) return Long.MIN_VALUE // failed
        // Return the first task and add the others
        // to the thread - local buffer of stolen ones

        for (i in 1 until stolen.size) {
            stolenTasks.get().add(stolen[i])
        }
        return stolen[0]
    }

    fun tryStealWithoutCheck(): Long {
        // Choose a random queue and check whether
        // its top task has higher priority

        val otherQueue = getQueueToSteal()
        val ourTop = queues[currThread()].top
        val otherTop = otherQueue.top

        if (ourTop == Long.MIN_VALUE || otherTop == Long.MIN_VALUE || otherTop == ourTop || otherTop.firstFromLong < ourTop.firstFromLong) {
            // Try to steal a better task !
            val stolen = otherQueue.steal()
            if (stolen.isEmpty()) return Long.MIN_VALUE // failed
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

    fun getQueueToSteal(): StealingLongQueue {
        val index = ThreadLocalRandom.current().nextInt(0, queues.size + 1)

        return if (index == queues.size) globalQueue else queues[index]
    }

    fun currThread(): Int = (Thread.currentThread() as IndexedThread).index

    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
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

}

// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5

private const val pStealLowerBound = 0.0009765625

private const val pStealUpperBound = 1.0

private const val stealSizeLowerBound = 1

private const val stealSizeUpperBound = 1024