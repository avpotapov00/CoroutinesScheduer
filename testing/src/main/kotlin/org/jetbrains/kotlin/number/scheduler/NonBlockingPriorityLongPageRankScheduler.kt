package org.jetbrains.kotlin.number.scheduler

import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.FloatNode
import org.jetbrains.kotlin.number.smq.StealingLongMultiQueueKS
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.indexedBinarySearch
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom

class NonBlockingPriorityLongPageRankScheduler(
    private val nodes: List<FloatNode>,
    val tolerance: Float = 1.0e-3f,
    val alpha: Float = 0.85f,
    val poolSize: Int,
    val stealSize: Int = 3,
    pSteal: Double = 0.04,
// The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    private val initResidual: Float = 1 - alpha
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
        val initPriority = toPriority(initResidual)

        nodes.forEachIndexed { index, node ->
            node.residual = initResidual
            insertGlobal(initPriority.zip(index))
        }

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
                task = stealAndDeleteFromSelf(index)

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

        private fun tryUpdate(cur: FloatNode) {
            if (cur.residual <= tolerance) {
                return
            }

            val oldResidual = cur.exchange(0.0f)
//            println(oldResidual)

            val srcNout = cur.nodesCount

            if (srcNout > 0) {
                val delta = oldResidual * alpha / srcNout

                if (delta <= 0) return

                for (edge in cur.outgoingEdges) {
                    val dst = nodes[edge.to]
                    val old = dst.atomicAdd(delta)

                    val newResidual = old + delta

                    if ((old < tolerance) || (newResidual >= tolerance)) {

                        val priority = toPriority(newResidual)
                        insert(priority.zip(edge.to))
                    }
                }
            }
        }

        fun insert(task: Long) {
            queues[index].addLocal(task)
        }

    }

    fun toPriority(residual: Float): Int {
        return residualToPriorityFactor - (residual * residualToPriorityFactor).toInt()
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

private const val residualToPriorityFactor = 100_000