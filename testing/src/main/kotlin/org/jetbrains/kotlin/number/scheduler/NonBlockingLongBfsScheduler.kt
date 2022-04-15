package org.jetbrains.kotlin.number.scheduler

import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.smq.StealingLongMultiQueueKS
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.indexedBinarySearch
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom

/**
 * @author Потапов Александр
 * @since 01.04.2022
 */
class NonBlockingLongBfsScheduler(
    private val nodes: List<BfsIntNode>,
    startIndex: Int,
    val poolSize: Int,
    val stealSize: Int = 3,
    pSteal: Double = 0.04,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100
) : StealingLongMultiQueueKS(stealSize, pSteal, poolSize), Closeable {

    /**
     * End of work flag
     */
    @Volatile
    private var terminated = false

    /**
     * Threads serving the scheduler
     */
    var threads: List<Worker> = (0 until poolSize).map { index -> Worker(index) }

    val finishPhaser = Phaser(poolSize + 1)

    init {
        insertGlobal(0.zip(startIndex))


        nodes[startIndex].distance = 0
        threads.forEach { it.start() }
    }


    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
    }

    inner class Worker(override val index: Int) : IndexedThread() {

        private var locked = false

        var totalTasksProcessed: Long = 0

        var successStealing: Int = 0

        var tasksLowerThanStolen: Int = 0

        var failedStealing: Int = 0

        var stealingAttempts: Int = 0

        val random: ThreadLocalRandom = ThreadLocalRandom.current()

        override fun run() {
            var attempts = 0
            while (!terminated) {

                // trying to get from local queue
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    if (locked) {
                        finishPhaser.register()
                        locked = false
                    }
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
                    if (locked) {
                        finishPhaser.register()
                        locked = false
                    }
                    attempts = 0
                    tryUpdate(nodes[task.secondFromLong])
                    continue
                }

                // if it didn't work, we try to remove it from the self queue
                task = stealAndDeleteFromSelf()

                if (task != Long.MIN_VALUE) {
                    if (locked) {
                        finishPhaser.register()
                        locked = false
                    }
                    attempts = 0
                    totalTasksProcessed++
                    tryUpdate(nodes[task.secondFromLong])
                    continue
                }

                if (!locked) {
                    finishPhaser.arriveAndDeregister()
                }
                locked = true
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
        }

        fun insert(task: Long) {
            queues[index].addLocal(task)
        }

    }


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
