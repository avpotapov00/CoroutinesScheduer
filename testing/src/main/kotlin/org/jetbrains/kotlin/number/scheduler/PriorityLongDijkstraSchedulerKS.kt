package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.adaptive.AdaptiveStealingLongMultiQueue
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.indexedBinarySearch
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.LockSupport

class PriorityLongDijkstraSchedulerKS(
    private val nodes: List<IntNode>,
    startIndex: Int,
    val poolSize: Int,
    val stealSize: Int = 3,
    val pSteal: Double = 0.04,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
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

    inner class Worker(override val index: Int) : IndexedThread() {

        // For stolen sum calc
        var stolenTotalTimesStats = 0
        var sumStolenStats = 0

        var totalTasksProcessed: Long = 0
        var successStealing: Int = 0
        var tasksLowerThanStolen: Int = 0
        var failedStealing: Int = 0
        var stealingAttempts: Int = 0

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        // количество раз когда что-то украли
        var stealingTotal = 0

        // суммарное количество украденного
        var stolenCountSum = 0

        // суммарное количество украденного для метрик
        var stolenCountSumOnlyForMetrics = 0

        // Section 3
        var tasksFromBufferBetterThanTop = 0
        var tasksFromBufferBetterThanTopOnlyForMetrics = 0

        var stolenLastFrom: Int = -1

        // количество раз, когда буффер был заполнен полностью
        var fullBufferTimesSum = 0

        // количество задач лучше нашего топа, включая те случаи, когда у нас ничего нет
        var tasksLowerThanStolenIncludingOurEmptiness = 0

        // Section 3
        var tasksFromBufferBetterOrEqualThanTop = 0
        var tasksFromBufferBetterOrEqualThanSecondTop = 0
        var tasksFromBufferBetterThanSecondTop = 0

        var tasksFromBufferBetterThanTopWithoutEmpty = 0
        var tasksFromBufferBetterOrEqualThanTopWithoutEmpty = 0
        var tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty = 0
        var tasksFromBufferBetterThanSecondTopWithoutEmpty = 0

        // Section 4
        var isGoodSteal = false

        var insertedAfterSteal: Int = 0
        var insertedAfterGoodSteal: Int = 0
        var insertedAfterBadSteal: Int = 0

        // Section 5
        var updatesCount = 0L
        var updateAttemptsCount = 0L

        // Section 6
        var uselessWork = 0L
        var abortedUpdates = 0L

        override fun run() {
            var attempts = 0
            while (!terminated) {

                // trying to get from local queue
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    totalTasksProcessed++
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
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
                    totalTasksProcessed++
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    continue
                }

                // if it didn't work, we try to remove it from the self queue
                task = stealAndDeleteFromSelf(index)

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    totalTasksProcessed++
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
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
                if (localTop == Long.MIN_VALUE) {
                    isGoodSteal = true
                    tasksFromBufferBetterThanTop++
                    tasksFromBufferBetterThanTopOnlyForMetrics++

                    tasksFromBufferBetterOrEqualThanTop++
                    tasksFromBufferBetterThanSecondTop++
                    tasksFromBufferBetterOrEqualThanSecondTop++
                } else {
                    val topFirstFromLong = localTop.firstFromLong
                    val taskFirstFromLong = task.firstFromLong
                    if (taskFirstFromLong <= topFirstFromLong) {
                        isGoodSteal = true
                        if (taskFirstFromLong < topFirstFromLong) {
                            tasksFromBufferBetterThanTop++
                            tasksFromBufferBetterThanTopOnlyForMetrics++

                            tasksFromBufferBetterThanTopWithoutEmpty++
                        }
                        tasksFromBufferBetterOrEqualThanTop++
                        tasksFromBufferBetterOrEqualThanTopWithoutEmpty++
                    } else {
                        isGoodSteal = false
                    }

                    val secondLocalTop = queues[currThread].getSecondTopLocal()
                    if (secondLocalTop == Long.MIN_VALUE) {
                        tasksFromBufferBetterThanSecondTop++
                        tasksFromBufferBetterOrEqualThanSecondTop++
                    } else {
                        val secondTopFirstFromLong = secondLocalTop.firstFromLong
                        if (taskFirstFromLong <= secondTopFirstFromLong) {
                            if (taskFirstFromLong < secondTopFirstFromLong) {
                                tasksFromBufferBetterThanSecondTop++
                                tasksFromBufferBetterThanSecondTopWithoutEmpty++
                            }
                            tasksFromBufferBetterOrEqualThanSecondTop++
                            tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty++
                        }
                    }
                }
                return task
            }

            lastDeleteFromBuffer = false

            // Should we steal ?
            if (shouldStealEffective()) {
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

        private fun shouldStealEffective() = random.nextDouble() < pSteal

        private fun trySteal(currThread: Int): Long {
            // Choose a random queue and check whether
            // its top task has higher priority

            val otherQueueIndex = ThreadLocalRandom.current().nextInt(0, queues.size + 1)
            val otherQueue = if (otherQueueIndex == queues.size) globalQueue else queues[otherQueueIndex]

            val ourTop = queues[currThread].getTopLocal()
            val otherTop = otherQueue.top
            if (ourTop != Long.MIN_VALUE) {
                stealingAttempts++
            }


            if (otherTop == Long.MIN_VALUE) return Long.MIN_VALUE
            if (ourTop == Long.MIN_VALUE || otherTop.firstFromLong < ourTop.firstFromLong) {
                // Try to steal a better task !
                otherQueue.steal(stealingBuffer)

                if (stealingBuffer.size == stealSize) {
                    fullBufferTimesSum++
                }

                stealingTotal++
                stolenTotalTimesStats++

                stolenCountSum = stealingBuffer.size
                stolenCountSumOnlyForMetrics += stealingBuffer.size
                sumStolenStats += stealingBuffer.size

                if (stealingBuffer.isEmpty()) {
                    failedStealing++
                    return Long.MIN_VALUE
                } // failed

                if (ourTop != Long.MIN_VALUE) {
                    successStealing++

                    val tasksBetter = indexedBinarySearch(stealingBuffer, ourTop)

                    tasksLowerThanStolen += tasksBetter
                    tasksLowerThanStolenIncludingOurEmptiness += tasksBetter
                } else {
                    tasksLowerThanStolenIncludingOurEmptiness += stealingBuffer.size
                }

                // Return the first task and add the others
                // to the thread - local buffer of stolen ones
                val stolenTasksDeque = stolenTasks.get()
                stolenLastFrom = otherQueueIndex

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

        private fun tryUpdate(oldValue: Int, cur: IntNode) {
            if (cur.distance < oldValue) {
                abortedUpdates++
                return
            }

            updateAttemptsCount += cur.outgoingEdges.size

            for (e in cur.outgoingEdges) {

                val to = nodes[e.to]

                while (cur.distance + e.weight < to.distance) {
                    val currDist = cur.distance
                    val toDist = to.distance
                    val nextDist = currDist + e.weight

                    if (toDist > nextDist && to.casDistance(toDist, nextDist)) {
                        val task = nextDist.zip(e.to)

                        updatesCount++
                        insert(task)
                        if (toDist != Int.MAX_VALUE) {
                            uselessWork++
                        }
                        break
                    }
                }
            }

            checkWakeThread()
        }

        fun insert(task: Long) {
            if (lastDeleteFromBuffer) {
                insertedAfterSteal++
                if (isGoodSteal) {
                    insertedAfterGoodSteal++
                } else {
                    insertedAfterBadSteal++
                }
            }

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

    fun stealingTotal(): Long {
        return threads.sumOf { it.stealingTotal.toLong() }
    }

    fun stolenCountSum(): Long {
        return threads.sumOf { it.stolenCountSumOnlyForMetrics.toLong() }
    }

    fun fullBufferTimesSum(): Long {
        return threads.sumOf { it.fullBufferTimesSum.toLong() }
    }

    fun tasksLowerThanStolenIncludingOurEmptiness(): Long {
        return threads.sumOf { it.tasksLowerThanStolenIncludingOurEmptiness.toLong() }
    }

    fun tasksFromBufferBetterThanTop(): Long {
        return threads.sumOf { it.tasksFromBufferBetterThanTopOnlyForMetrics.toLong() }
    }

    fun tasksFromBufferBetterOrEqualThanTop(): Long {
        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanTop.toLong() }
    }

    fun tasksFromBufferBetterThanTopWithoutEmpty(): Long {
        return threads.sumOf { it.tasksFromBufferBetterThanTopWithoutEmpty.toLong() }
    }

    fun tasksFromBufferBetterOrEqualThanTopWithoutEmpty(): Long {
        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanTopWithoutEmpty.toLong() }
    }

    fun tasksFromBufferBetterThanSecondTop(): Long {
        return threads.sumOf { it.tasksFromBufferBetterThanSecondTop.toLong() }
    }

    fun tasksFromBufferBetterOrEqualThanSecondTop(): Long {
        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTop.toLong() }
    }

    fun tasksFromBufferBetterThanSecondTopWithoutEmpty(): Long {
        return threads.sumOf { it.tasksFromBufferBetterThanSecondTopWithoutEmpty.toLong() }
    }

    fun tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty(): Long {
        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty.toLong() }
    }

    fun filledTimes(): Long {
        return queues.sumOf { it.filledTimes.toLong() }
    }

    fun insertedAfterSteal(): Long {
        return threads.sumOf { it.insertedAfterSteal.toLong() }
    }

    fun insertedAfterGoodSteal(): Long {
        return threads.sumOf { it.insertedAfterGoodSteal.toLong() }
    }

    fun insertedAfterBadSteal(): Long {
        return threads.sumOf { it.insertedAfterBadSteal.toLong() }
    }

    fun updatesCount(): Long {
        return threads.sumOf { it.updatesCount }
    }

    fun updateAttemptsCount(): Long {
        return threads.sumOf { it.updateAttemptsCount }
    }

    fun uselessWork(): Long {
        return threads.sumOf { it.uselessWork }
    }

    fun abortedUpdates(): Long = threads.sumOf { it.abortedUpdates }
}


// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5
