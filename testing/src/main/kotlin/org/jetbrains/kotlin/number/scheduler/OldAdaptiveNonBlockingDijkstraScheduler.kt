package org.jetbrains.kotlin.number.scheduler

import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.smq.StealingLongMultiQueueKS
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.indexedBinarySearch
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom

class OldAdaptiveNonBlockingDijkstraScheduler(
    private val nodes: List<IntNode>,
    startIndex: Int,
    val poolSize: Int,
    val stealSize: Int = 3,
    pSteal: Double = 0.04,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    private val deltaStealSize: Double = 0.8,
    checkAttemptsCountInitial: Int = 100
) : StealingLongMultiQueueKS(stealSize, pSteal, poolSize), Closeable {

    val checkAttemptsCount = (checkAttemptsCountInitial / pSteal).toInt()

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

    var stealSizeGlobal: Int = stealSize

    init {
        insertGlobal(0.zip(startIndex))
        stealSizeGlobal = stealSize
        nodes[startIndex].distance = 0
    }

    fun start() {
        threads.forEach { it.start() }
    }


    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
    }

    enum class StealSizeDirection {
        DECREASE,
        INCREASE
    }

    enum class Phase {
        SKIP,
        COLLECT
    }

    inner class Worker(override val index: Int) : IndexedThread() {

        private var locked = false

        var totalTasksProcessed: Long = 0

        var successStealing: Int = 0

        var tasksLowerThanStolen: Int = 0

        var failedStealing: Int = 0

        var stealingAttempts: Int = 0

        val random: ThreadLocalRandom = ThreadLocalRandom.current()

        var previousMetric: Double = 1.0

        var lastMetrics: Double = 1.0

        var stealSizeLocal = stealSize

        var direction: StealSizeDirection = StealSizeDirection.INCREASE

        var firstTime = true

        var updates: Int = 0

        var phase = Phase.COLLECT


        init {
            require(stealSizeLocal > 0) { "Error! 0 steal size" }
        }

        override fun run() {
            var attempts = 0
            while (!terminated) {
                tryUpdateStealSize()

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

        private fun tryUpdateStealSize() {
            if (index == 0) { // controller thread
                if (successStealing > checkAttemptsCount) {
                    println("Check!")
                    if (phase == Phase.SKIP) {
                        println("Skip!")
                        successStealing = 0
                        tasksLowerThanStolen = 0
                        phase = Phase.COLLECT
                        return
                    }

                    val metrics = tasksLowerThanStolen.toDouble() / (stealSizeLocal * successStealing)

                    if (firstTime) {
                        firstTime = false
                        previousMetric = metrics
                        tasksLowerThanStolen = 0
                        successStealing = 0
                        return
                    }

                    when (direction) {
                        StealSizeDirection.INCREASE -> {
                            if (metrics > 0.5 && previousMetric - metrics < deltaStealSize && stealSizeLocal != STEAL_SIZE_UPPER_BOUND) {
                                setNewGlobalStealSize(stealSizeLocal * 2)
                            } else if (stealSizeLocal != STEAL_SIZE_LOWER_BOUND){
                                setNewGlobalStealSize(stealSizeLocal / 2)
                                direction = StealSizeDirection.DECREASE
                            }
                        }
                        StealSizeDirection.DECREASE -> {
                            if (metrics - previousMetric > deltaStealSize && stealSizeLocal != STEAL_SIZE_LOWER_BOUND) {
                                setNewGlobalStealSize(stealSizeLocal / 2)
                            } else if (stealSizeLocal != STEAL_SIZE_UPPER_BOUND) {
                                setNewGlobalStealSize(stealSizeLocal * 2)
                                direction = StealSizeDirection.INCREASE
                            }
                        }
                    }
                    lastMetrics = previousMetric
                    previousMetric = metrics

                    tasksLowerThanStolen = 0
                    successStealing = 0
                }
            } else { // other threads
                if (successStealing > checkAttemptsCount) {
                    successStealing = 0
                    tasksLowerThanStolen = 0

                    if (phase == Phase.SKIP) {
                        phase = Phase.COLLECT
                        return
                    }

                    val currentValueGlobal = stealSizeGlobal
                    if (currentValueGlobal == 0) {
                        println("ERROR!!!".repeat(10))
                        error("Error! reading 0")
                    }
                    if (currentValueGlobal != stealSizeLocal) {
                        stealSizeLocal = currentValueGlobal
                        queues[index].setNextStealSize(stealSizeLocal)
                        phase = Phase.SKIP
                    }
                }
            }
        }

        private fun setNewGlobalStealSize(nextValue: Int) {
            updates++
            check(nextValue > 0) { "Error! writing 0" }
            stealSizeGlobal = nextValue
            stealSizeLocal = nextValue
            queues[index].setNextStealSize(nextValue)
            phase = Phase.SKIP
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

        private fun tryUpdate(cur: IntNode) {
            for (e in cur.outgoingEdges) {

                val to = nodes[e.to]

                while (cur.distance + e.weight < to.distance) {
                    val currDist = cur.distance
                    val toDist = to.distance
                    val nextDist = currDist + e.weight

                    if (toDist > nextDist && to.casDistance(toDist, nextDist)) {
                        val task = nextDist.zip(e.to)

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