package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.adaptive.AdaptiveGlobalHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.adaptive.AdaptiveHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue
import org.jetbrains.kotlin.util.*
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.LockSupport
import kotlin.system.exitProcess

/**
 * Plain scheduler version without adaptivity
 */
class NonBlockingLongDijkstraScheduler(
    private val nodes: List<IntNode>,
    startIndex: Int,
    val poolSize: Int,
    stealSizeInitialPower: Int = 3,
    pStealInitialPower: Int = 2,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
) : Closeable {

    val stealSize = calculateStealSize(stealSizeInitialPower)
    val pSteal = calculatePSteal(pStealInitialPower)

    val globalQueue = AdaptiveGlobalHeapWithStealingBufferLongQueue(stealSize)

    val queues = Array(poolSize) { AdaptiveHeapWithStealingBufferLongQueue(stealSize) }

    val stolenTasks: ThreadLocal<ArrayDeque<Long>> = ThreadLocal.withInitial { ArrayDeque<Long>(stealSize) }

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

    // your size + the size of the buffer for theft + the size of the global queue
    fun size() = queues[currThread()].size + stolenTasks.get().size + globalQueue.size

    fun insertGlobal(task: Long) {
        globalQueue.add(task)
    }

    fun stealAndDeleteFromGlobal(): Long {
        val queueToSteal = globalQueue

        return stealFromExactQueue(queueToSteal)
    }

    fun stealAndDeleteFromSelf(index: Int): Long {
        val queueToSteal = queues[index]

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

    fun shouldSteal() = ThreadLocalRandom.current().nextDouble() < pSteal

    fun currThread(): Int = (Thread.currentThread() as IndexedThread).index

    inner class Worker(override val index: Int) : IndexedThread() {

        private var locked = false

        // For stolen sum calc
        var totalTasksProcessed: Long = 0
        var successStealing: Int = 0
        var tasksLowerThanStolen: Int = 0
        var failedStealing: Int = 0
        var stealingAttempts: Int = 0

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        // количество раз когда что-то украли
        var stealingTotal = 0

        // суммарное количество украденного без учета вынужденных краж
        var stolenCountSumWithoutEmpty = 0

        // суммарное количество украденного
        var stolenCountSumOnlyForMetrics = 0

        // Section 3
        var tasksFromBufferBetterThanTopOnlyForMetrics = 0

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
            doJustToStart()

            var attempts = 0
            while (!terminated) {

                // trying to get from local queue
                if (locked) {
                    finishPhaser.register()
                }
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    if (locked) {
                        locked = false
                    }
                    totalTasksProcessed++
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    continue
                }
                if (locked) {
                    finishPhaser.arriveAndDeregister()
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
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    continue
                }

                if (locked) {
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
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    continue
                }

                if (!locked) {
                    finishPhaser.arriveAndDeregister()
                }
                locked = true
            }
        }

        private fun doJustToStart() {
            if (index != 0) return

            val task = stealAndDeleteFromGlobal()
            if (task == Long.MIN_VALUE) {
                println("Can't start: no initial task in global queue")
                exitProcess(1)
            }

            tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
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

                if (ourTop != Long.MIN_VALUE) stolenCountSumWithoutEmpty += stealingBuffer.size
                stolenCountSumOnlyForMetrics += stealingBuffer.size

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

                for (i in 1 until stealingBuffer.size) {
                    stolenTasksDeque.add(stealingBuffer[i])
                }

                return stealingBuffer[0]
            }

            return Long.MIN_VALUE
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

    fun stolenCountSumWithoutEmpty(): Long {
        return threads.sumOf { it.stolenCountSumWithoutEmpty.toLong() }
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

