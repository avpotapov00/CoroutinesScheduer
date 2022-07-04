package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.number.smq.StealingLongMultiQueueKS
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.LockSupport

/**
 * @author Потапов Александр
 * @since 29.03.2022
 */
class PriorityLongBfsSchedulerKS(
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

//        var totalTasksProcessed: Long = 0
//        var successStealing: Int = 0
//        var tasksLowerThanStolen: Int = 0
//        var failedStealing: Int = 0
//        var stealingAttempts: Int = 0

        val random: ThreadLocalRandom = ThreadLocalRandom.current()

        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

//        // количество раз когда что-то украли
//        var stealingTotal = 0
//
//        // суммарное количество украденного
//        var stolenCountSum = 0
//
//        // количество раз, когда буффер был заполнен полностью
//        var fullBufferTimesSum = 0
//
//        // количество задач лучше нашего топа, включая те случаи, когда у нас ничего нет
//        var tasksLowerThanStolenIncludingOurEmptiness = 0
//
//        // Section 3
//        var tasksFromBufferBetterThanTop = 0
//        var tasksFromBufferBetterOrEqualThanTop = 0
//        var tasksFromBufferBetterOrEqualThanSecondTop = 0
//        var tasksFromBufferBetterThanSecondTop = 0
//
//        var tasksFromBufferBetterThanTopWithoutEmpty = 0
//        var tasksFromBufferBetterOrEqualThanTopWithoutEmpty = 0
//        var tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty = 0
//        var tasksFromBufferBetterThanSecondTopWithoutEmpty = 0

        override fun run() {
            var attempts = 0
            while (!terminated) {

                // trying to get from local queue
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
//                    totalTasksProcessed++
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
//                    totalTasksProcessed++
                    tryUpdate(nodes[task.secondFromLong])
                    continue
                }

                goWait()
                attempts = 0
            }
        }

        private fun delete(): Long {
            val currThread = index

            // Do we have previously stolen tasks ?
            val ourDeque = stolenTasks.get()
            if (ourDeque.isNotEmpty()) {
                val task = ourDeque.removeFirst()
//                val localTop = queues[currThread].getTopLocal()
//
//                if (localTop == Long.MIN_VALUE) {
//                    tasksFromBufferBetterThanTop++
//                    tasksFromBufferBetterOrEqualThanTop++
//                    tasksFromBufferBetterThanSecondTop++
//                    tasksFromBufferBetterOrEqualThanSecondTop++
//                } else {
//                    val topFirstFromLong = localTop.firstFromLong
//                    val taskFirstFromLong = task.firstFromLong
//                    if (taskFirstFromLong <= topFirstFromLong) {
//                        if (taskFirstFromLong < topFirstFromLong) {
//                            tasksFromBufferBetterThanTop++
//                            tasksFromBufferBetterThanTopWithoutEmpty++
//                        }
//                        tasksFromBufferBetterOrEqualThanTop++
//                        tasksFromBufferBetterOrEqualThanTopWithoutEmpty++
//                    }
//
//                    val secondLocalTop = queues[currThread].getSecondTopLocal()
//                    if (secondLocalTop == Long.MIN_VALUE) {
//                        tasksFromBufferBetterThanSecondTop++
//                        tasksFromBufferBetterOrEqualThanSecondTop++
//                    } else {
//                        val secondTopFirstFromLong = secondLocalTop.firstFromLong
//                        if (taskFirstFromLong <= secondTopFirstFromLong) {
//                            if (taskFirstFromLong < secondTopFirstFromLong) {
//                                tasksFromBufferBetterThanSecondTop++
//                                tasksFromBufferBetterThanSecondTopWithoutEmpty++
//                            }
//                            tasksFromBufferBetterOrEqualThanSecondTop++
//                            tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty++
//                        }
//                    }
//                }
                return task
            }

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

        private fun trySteal(currThread: Int): Long {
            // Choose a random queue and check whether
            // its top task has higher priority

            val otherQueue = getQueueToSteal()
            val ourTop = queues[currThread].getTopLocal()
            val otherTop = otherQueue.top
//            if (ourTop != Long.MIN_VALUE) {
//                stealingAttempts++
//            }

            if (otherTop == Long.MIN_VALUE) return Long.MIN_VALUE
            if (ourTop == Long.MIN_VALUE || otherTop.firstFromLong < ourTop.firstFromLong) {
                // Try to steal a better task !
                otherQueue.steal(stealingBuffer)
//                stealingTotal++
//                stolenCountSum += stealingBuffer.size
//                if (stealingBuffer.size == stealSize) {
//                    fullBufferTimesSum++
//                }

                if (stealingBuffer.isEmpty()) {
//                    failedStealing++
                    return Long.MIN_VALUE
                } // failed

//                if (ourTop != Long.MIN_VALUE) {
//                    successStealing++
//
//                    val tasksBetter = indexedBinarySearch(stealingBuffer, ourTop)
//
//                    tasksLowerThanStolen += tasksBetter
//                    tasksLowerThanStolenIncludingOurEmptiness += tasksBetter
//                } else {
//                    tasksLowerThanStolenIncludingOurEmptiness += stealingBuffer.size
//                }
                // Return the first task and add the others
                // to the thread - local buffer of stolen ones
                val stolenTasksDeque = stolenTasks.get()

                for (i in 1 until stealingBuffer.size) {
                    stolenTasksDeque.add(stealingBuffer[i])
                }
                return stealingBuffer[0]
            }

            return Long.MIN_VALUE
        }

        private fun trySteal2(currThread: Int): Long {
            // Choose a random queue and check whether
            // its top task has higher priority

            val otherQueue = getQueueToSteal()
            val ourTop = queues[currThread].getTopLocal()
            val otherTop = otherQueue.top

            if (otherTop == Long.MIN_VALUE) return Long.MIN_VALUE
            if (ourTop == Long.MIN_VALUE || otherTop.firstFromLong < ourTop.firstFromLong) {
                // Try to steal a better task !
                otherQueue.steal(stealingBuffer)
//                stealingTotal++
//                stolenCountSum += stealingBuffer.size
//                if (stealingBuffer.size == stealSize) {
//                    fullBufferTimesSum++
//                }

                if (stealingBuffer.isEmpty()) {
                    return Long.MIN_VALUE
                } // failed

//                if (ourTop != Long.MIN_VALUE) {
//                    val tasksBetter = indexedBinarySearch(stealingBuffer, ourTop)
//
//                    tasksLowerThanStolenIncludingOurEmptiness += tasksBetter
//                } else {
//                    tasksLowerThanStolenIncludingOurEmptiness += stealingBuffer.size
//                }
                // Return the first task and add the others
                // to the thread - local buffer of stolen ones
                val stolenTasksDeque = stolenTasks.get()

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

        fun insert(task: Long) {
            queues[index].addLocal(task)
        }

    }


    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }
//
//    fun totalTasksProcessed(): Long {
//        return threads.sumOf { it.totalTasksProcessed }
//    }
//
//    fun successStealing(): Long {
//        return threads.sumOf { it.successStealing.toLong() }
//    }
//
//    fun failedStealing(): Long {
//        return threads.sumOf { it.failedStealing.toLong() }
//    }
//
//    fun stealingAttempts(): Long {
//        return threads.sumOf { it.stealingAttempts.toLong() }
//    }
//
//    fun tasksLowerThanStolen(): Long {
//        return threads.sumOf { it.tasksLowerThanStolen.toLong() }
//    }
//
//    fun stealingTotal(): Long {
//        return threads.sumOf { it.stealingTotal.toLong() }
//    }
//
//    fun stolenCountSum(): Long {
//        return threads.sumOf { it.stolenCountSum.toLong() }
//    }
//
//    fun fullBufferTimesSum(): Long {
//        return threads.sumOf { it.fullBufferTimesSum.toLong() }
//    }
//
//    fun tasksLowerThanStolenIncludingOurEmptiness(): Long {
//        return threads.sumOf { it.tasksLowerThanStolenIncludingOurEmptiness.toLong() }
//    }
//
//    fun tasksFromBufferBetterThanTop(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterThanTop.toLong() }
//    }
//
//    fun tasksFromBufferBetterOrEqualThanTop(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanTop.toLong() }
//    }
//
//    fun tasksFromBufferBetterThanTopWithoutEmpty(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterThanTopWithoutEmpty.toLong() }
//    }
//    fun tasksFromBufferBetterOrEqualThanTopWithoutEmpty(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanTopWithoutEmpty.toLong() }
//    }
//
//    fun tasksFromBufferBetterThanSecondTop(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterThanSecondTop.toLong() }
//    }
//
//    fun tasksFromBufferBetterOrEqualThanSecondTop(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTop.toLong() }
//    }
//
//    fun tasksFromBufferBetterThanSecondTopWithoutEmpty(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterThanSecondTopWithoutEmpty.toLong() }
//    }
//    fun tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty(): Long {
//        return threads.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty.toLong() }
//    }
//
//    fun filledTimes(): Long {
//        return queues.sumOf { it.filledTimes.toLong() }
//    }

}

// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5