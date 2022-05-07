package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.number.adaptive.AdaptiveStealingLongMultiQueue
import org.jetbrains.kotlin.util.*
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.LockSupport

/**
 * @author Потапов Александр
 * @since 27.04.2022
 */
class AdaptiveBfsScheduler(
    private val nodes: List<BfsIntNode>,
    startIndex: Int,
    val poolSize: Int,
    val stealSize: Int = 3,
    pSteal: Double = 0.04,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    private val checkCount: Int = 1024
) : AdaptiveStealingLongMultiQueue(stealSize, pSteal, poolSize), Closeable {

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

    init {
        insertGlobal(0.zip(startIndex))

        nodes[startIndex].distance = 0
        threads.forEach { it.start() }
    }

    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
    }

    enum class Direction {
        INCREASE,
        DECREASE
    }

    inner class Worker(override val index: Int) : IndexedThread() {

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        // количество раз когда что-то украли
        var stealingTotal = 0

        // суммарное количество украденного
        var stolenCountSum = 0

        // Section 3
        var tasksFromBufferBetterThanTop = 0

        var stealSizeLocal: Int = stealSize

        var localEpoch: Int = 0

        // epoch + better + total
        var metricsHolder: Metrics = Metrics(0, 0, 0, 0)

        var previousMetrics: Double = -1.0

        var direction: Direction = Direction.INCREASE

        var stolenLastFrom: Int = -1

        override fun run() {
            var attempts = 0
            while (!terminated) {

                // trying to get from local queue
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
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
                task = stealAndDeleteFromSelf(index)

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    tryUpdate(nodes[task.secondFromLong])
                    continue
                }

                goWait()
                attempts = 0
            }
        }

        private var lastDeleteFromBuffer = false

        private fun delete(): Long {
            val currThread = index

            // Do we have previously stolen tasks ?
            val ourDeque = stolenTasks.get()
            if (ourDeque.isNotEmpty()) {
                lastDeleteFromBuffer = true
                val task = ourDeque.removeFirst()
                val localTop = queues[currThread].getTopLocal()

                if (localTop == Long.MIN_VALUE || task.firstFromLong <= localTop.firstFromLong) {
                    tasksFromBufferBetterThanTop++
                }
                return task
            }

            if (lastDeleteFromBuffer) {
                // if not from global queue
                if (stolenLastFrom != queues.size) {
                    giveFeedback()
                }
                tasksFromBufferBetterThanTop = 0
                stolenCountSum = 0
            }
            lastDeleteFromBuffer = false

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
            return trySteal(currThread)
        }

        private fun giveFeedback() {
            val otherThread = threads[stolenLastFrom]
            val otherMetrics = otherThread.metricsHolder

            val otherEpoch = otherMetrics.epoch
            val otherBetter = otherMetrics.betterSum
            val otherStolenSum = otherMetrics.stolenSum
            val otherCount = otherMetrics.count

            otherThread.metricsHolder = Metrics(
                epoch = otherEpoch,
                betterSum = otherBetter + tasksFromBufferBetterThanTop,
                stolenSum = otherStolenSum + stolenCountSum,
                count = otherCount + 1
            )
        }

        private fun trySteal(currThread: Int): Long {
            // Choose a random queue and check whether
            // its top task has higher priority

            val otherQueueIndex = ThreadLocalRandom.current().nextInt(0, queues.size + 1)
            val otherQueue = if (otherQueueIndex == queues.size) globalQueue else queues[otherQueueIndex]

            val ourTop = queues[currThread].getTopLocal()
            val otherTop = otherQueue.top

            if (otherTop == Long.MIN_VALUE) return Long.MIN_VALUE
            if (ourTop == Long.MIN_VALUE || otherTop.firstFromLong < ourTop.firstFromLong) {
                // Try to steal a better task !
                otherQueue.steal(stealingBuffer)
                stealingTotal++
                stolenCountSum = stealingBuffer.size

                if (stealingBuffer.isEmpty()) {
                    return Long.MIN_VALUE
                } // failed

                // Return the first task and add the others
                // to the thread - local buffer of stolen ones
                val stolenTasksDeque = stolenTasks.get()
                stolenLastFrom = otherQueueIndex

                if (stealingBuffer.size == 1) {
                    // if not global queue
                    if (stolenLastFrom != queues.size) {
                        giveFeedback()
                    }
                    stolenCountSum = 0
                }

                for (i in 1 until stealingBuffer.size) {
                    stolenTasksDeque.add(stealingBuffer[i])
                }

                return stealingBuffer[0]
            }

            return Long.MIN_VALUE
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

        private fun setNextStealSize(nextValue: Int) {
            queues[index].setNextStealSize(nextValue)
        }

        fun insert(task: Long) {
            checkUpdateStealSize()

            queues[index].addLocal(task)
        }

        private fun checkUpdateStealSize() {
            val currentMetrics = metricsHolder

            val epoch = currentMetrics.epoch

            // if expired
            if (localEpoch > epoch) {
                metricsHolder = Metrics(
                    epoch = localEpoch,
                    stolenSum = 0,
                    betterSum = 0,
                    count = 0
                )
                return
            }


            // if count is not great enough
            if (currentMetrics.count < checkCount) {
                return
            }
            val stolen = currentMetrics.stolenSum
            val better = currentMetrics.betterSum

            val newMetrics = better.toDouble() / stolen

            // if calculating for the first time
            if (previousMetrics < 0) {
                previousMetrics = newMetrics
                metricsHolder = Metrics(
                    epoch = currentMetrics.epoch + 1,
                    count = 0,
                    stolenSum = 0,
                    betterSum = 0
                )
                return
            }

            if (newMetrics >= previousMetrics) {
                when (direction) {
                    Direction.INCREASE -> {
                        if (stealSizeLocal == STEAL_SIZE_UPPER_BOUND) {
                            direction = Direction.DECREASE
                            stealSizeLocal /= 2
                            setNextStealSize(stealSizeLocal)
                            return
                        }
                        stealSizeLocal *= 2
                        setNextStealSize(stealSizeLocal)
                    }
                    Direction.DECREASE -> {
                        if (stealSizeLocal == STEAL_SIZE_LOWER_BOUND) {
                            direction = Direction.INCREASE
                            stealSizeLocal *= 2
                            setNextStealSize(stealSizeLocal)
                            return
                        }
                        stealSizeLocal /= 2
                        setNextStealSize(stealSizeLocal)
                    }
                }
            } else {
                when (direction) {
                    Direction.INCREASE -> {
                        direction = Direction.DECREASE
                        stealSizeLocal /= 2
                        setNextStealSize(stealSizeLocal)
                    }
                    Direction.DECREASE -> {
                        direction = Direction.INCREASE
                        stealSizeLocal *= 2
                        setNextStealSize(stealSizeLocal)
                    }
                }
            }

            previousMetrics = newMetrics
            metricsHolder = Metrics(
                epoch = currentMetrics.epoch + 1,
                count = 0,
                stolenSum = 0,
                betterSum = 0
            )
        }

    }


    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

    data class Metrics(
        val epoch: Int,
        val stolenSum: Int,
        val betterSum: Int,
        val count: Int
    )


}

// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5
