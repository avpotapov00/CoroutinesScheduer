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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess


class NonBlockingFullAdaptiveLongDijkstraScheduler(
    private val nodes: List<IntNode>,
    startIndex: Int,
    val poolSize: Int,
    stealSizeInitialPower: Int = 3,
    private val pStealInitialPower: Int = 2,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    // АДАПТИВНОСТЬ STEAL_SIZE
    private val stealSizeWindow: Int = 1000,
    // АДАПТИВНОСТЬ P_STEAL
    private val pStealWindow: Int = 1000,
    private val learningRate: Double,
    private val initialMomentum: Double,
    private val minXBound: Double = P_STEAL_MIN_POWER,
    private val maxXBound: Double = P_STEAL_MAX_POWER,
    private val k1: Double = 0.94,
    private val k2: Double = 0.06,
    private val bufferEfficientFactor: Double = 0.14
) : Closeable {

    val globalQueue: AdaptiveGlobalHeapWithStealingBufferLongQueue
    val queues: Array<AdaptiveHeapWithStealingBufferLongQueue>
    val stolenTasks: ThreadLocal<ArrayDeque<Long>>

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

    fun currThread(): Int = (Thread.currentThread() as IndexedThread).index

    /**
     * End of work flag
     */
    @Volatile
    private var terminated = false

    /**
     * Threads serving the scheduler
     */
    var threads: List<Worker>

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
        val stealSizeInitialValue = calculateStealSize(stealSizeInitialPower)

        globalQueue = AdaptiveGlobalHeapWithStealingBufferLongQueue(stealSizeInitialValue)
        queues = Array(poolSize) { AdaptiveHeapWithStealingBufferLongQueue(stealSizeInitialValue) }
        stolenTasks = ThreadLocal.withInitial { ArrayDeque(stealSizeInitialValue) }

        threads = (0 until poolSize).map { index ->
            Worker(
                index = index,
                pStealPowerLocal = pStealInitialPower.toDouble(),
                stealSizeLocalPower = stealSizeInitialPower
            )
        }

        insertGlobal(0.zip(startIndex))

        nodes[startIndex].distance = 0
        threads.forEach { it.start() }
    }

    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
    }

    inner class Worker(
        override val index: Int,
        var pStealPowerLocal: Double,
        var stealSizeLocalPower: Int
    ) : IndexedThread() {

        private var locked = false

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        // adaptive pSteal
        var totalTasksProcessed: Int = 0
        var uselessWork = 0L
        var prevTimestamp: Long = System.nanoTime()

        var pStealLocal: Double = calculatePSteal(pStealPowerLocal)

        // adaptive stealSize
        var metricsHolder: StealSizeMetrics =
            StealSizeMetrics(0, 0, 0, 0)
        var previousStealSizeMetrics: Double = -1.0
        var stealSizeUpdateDirection: StealSizeDirection =
            if (stealSizeLocalPower > (STEAL_SIZE_MAX_POWER + STEAL_SIZE_MIN_POWER) / 2) {
                StealSizeDirection.DECREASE
            } else {
                StealSizeDirection.INCREASE
            }
        var stolenLastFrom: Int = -1
        var tasksFromBufferBetterThanTop = 0

        var localStealSizeEpoch: Int = 0
        var stolenCountSum = 0
        var prevAverageBufferSize: Double = 0.0

        // statistics
        var pStealUpdateCount: Int = 0
        var stealSizeUpdateCount: Int = 0
        var minPSteal: Double = pStealPowerLocal
        var maxPSteal: Double = pStealPowerLocal
        var minStealSize: Int = stealSizeLocalPower
        var maxStealSize: Int = stealSizeLocalPower
        var deltaLessThenThresholdCount: Int = 0
        var totalTasksProcessedSum: Long = 0
        var expiredFeedbackReceived: Int = 0

        override fun run() {
            var attempts = 0
            while (!terminated) {
                if (totalTasksProcessed > pStealWindow) {
                    val currTimestamp = System.nanoTime()
                    updatePSteal(currTimestamp)
                    resetMetricsToZero()
                }

                // trying to get from local queue
                if (locked) {
                    finishPhaser.register()
                }
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    if (locked) {
                        locked = false
                        resetMetricsToZero()
                    }
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
                    stolenLastFrom = queues.size
                    if (locked) {
                        finishPhaser.register()
                        locked = false
                        resetMetricsToZero()
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
                    stolenLastFrom = index
                    if (locked) {
                        finishPhaser.register()
                        locked = false
                        resetMetricsToZero()
                    }
                    attempts = 0
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    continue
                }

                if (!locked) {
                    finishPhaser.arriveAndDeregister()
                }
                locked = true
            }
        }

        /*
            P_STEAL_INCREASE ->
            P_STEAL_DECREASE ->
        */
        private fun updatePSteal(currTimestamp: Long) {
            val metricsValue = (totalTasksProcessed - uselessWork) / (currTimestamp - prevTimestamp).toDouble()

            pStealPowerLocal = nextX(pStealPowerLocal, metricsValue)

            pStealLocal = calculatePSteal(pStealPowerLocal)
            minPSteal = min(minPSteal, pStealPowerLocal)
            maxPSteal = max(maxPSteal, pStealPowerLocal)
            pStealUpdateCount++
            checkPSteal()
        }

        private fun checkPSteal() {
            if (pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER) {
                println("ERROR! pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER: $pStealPowerLocal")
                myExitProcess(1)
            }
        }

        private fun giveFeedback() {
            val otherThread = threads[stolenLastFrom]
            val otherMetrics = otherThread.metricsHolder

            val otherEpoch = otherMetrics.epoch
            val otherBetter = otherMetrics.betterSum
            val otherStolenSum = otherMetrics.stolenSum
            val otherCount = otherMetrics.count

            val newBetterSum = otherBetter + tasksFromBufferBetterThanTop
            val newStolenSum = otherStolenSum + stolenCountSum

            otherThread.metricsHolder = StealSizeMetrics(
                epoch = otherEpoch, betterSum = newBetterSum, stolenSum = newStolenSum, count = otherCount + 1
            )
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

        private fun shouldStealEffective(): Boolean {
            return random.nextDouble() < pStealLocal
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


        private fun resetMetricsToZero() {
            //  Удаляем значения метрик
            totalTasksProcessed = 0
            uselessWork = 0
            prevTimestamp = System.nanoTime()
        }


        private fun tryUpdate(oldValue: Int, cur: IntNode) {
            if (cur.distance < oldValue) {
                return
            }

            for (e in cur.outgoingEdges) {

                val to = nodes[e.to]

                while (cur.distance + e.weight < to.distance) {
                    val currDist = cur.distance
                    val toDist = to.distance
                    val nextDist = currDist + e.weight

                    if (toDist > nextDist && to.casDistance(toDist, nextDist)) {
                        val task = nextDist.zip(e.to)

                        insert(task)
                        if (toDist != Int.MAX_VALUE) {
                            uselessWork++
                        }
                        totalTasksProcessed++
                        totalTasksProcessedSum++
                        break
                    }
                }
            }
        }

        fun insert(task: Long) {
            checkUpdateStealSize()
            queues[index].addLocal(task)
        }

        private fun checkUpdateStealSize() {
            val currentMetrics = metricsHolder

            val epoch = currentMetrics.epoch

            // if expired
            if (localStealSizeEpoch > epoch) {
                expiredFeedbackReceived++
                metricsHolder = StealSizeMetrics(
                    epoch = localStealSizeEpoch, stolenSum = 0, betterSum = 0, count = 0
                )
                return
            }

            // if count is not great enough
            if (currentMetrics.count < stealSizeWindow) {
                return
            }
            val stolen = currentMetrics.stolenSum
            val better = currentMetrics.betterSum

            val newMetrics = better.toDouble() / stolen
            val averageBufferSize = queues[index].averageBufferSize()

            if (newMetrics >= previousStealSizeMetrics) {
                when (stealSizeUpdateDirection) {
                    StealSizeDirection.INCREASE -> {
                        if (stealSizeLocalPower == STEAL_SIZE_MAX_POWER || stealSizeIncreaseIsNotSufficient(
                                averageBufferSize
                            )
                        ) {
                            stealSizeUpdateDirection = StealSizeDirection.DECREASE
                            stealSizeLocalPower--
                        } else {
                            stealSizeLocalPower++
                        }
                    }

                    StealSizeDirection.DECREASE -> {
                        if (stealSizeLocalPower == STEAL_SIZE_MIN_POWER) {
                            stealSizeUpdateDirection = StealSizeDirection.INCREASE
                            stealSizeLocalPower++
                        } else {
                            stealSizeLocalPower--
                        }
                    }
                }
            } else {
                when (stealSizeUpdateDirection) {
                    StealSizeDirection.INCREASE -> {
                        stealSizeUpdateDirection = StealSizeDirection.DECREASE
                        stealSizeLocalPower--
                    }

                    StealSizeDirection.DECREASE -> {
                        // Если уменьшили stealSize, но это не повлияло на то, сколько в среднем кладем в буфер - продолжаем его уменьшать
                        if (stealSizeLocalPower != STEAL_SIZE_MIN_POWER && stealSizeDecreaseIsNotSufficient(
                                averageBufferSize
                            )
                        ) {
                            stealSizeLocalPower--
                        } else {
                            stealSizeUpdateDirection = StealSizeDirection.INCREASE
                            stealSizeLocalPower++
                        }
                    }
                }
            }

            setNextStealSize(stealSizeLocalPower)
            stealSizeUpdateCount++
            previousStealSizeMetrics = newMetrics
            prevAverageBufferSize = averageBufferSize
            resetEffectiveBufferSizeMetrics()

            metricsHolder = StealSizeMetrics(
                epoch = currentMetrics.epoch + 1, count = 0, stolenSum = 0, betterSum = 0
            )
        }

        private fun stealSizeIncreaseIsNotSufficient(averageBufferSize: Double): Boolean {
            return (averageBufferSize / prevAverageBufferSize) - 1 < bufferEfficientFactor
        }

        private fun stealSizeDecreaseIsNotSufficient(averageBufferSize: Double): Boolean {
            return (prevAverageBufferSize / averageBufferSize) - 1 < bufferEfficientFactor
        }

        private fun resetEffectiveBufferSizeMetrics() {
            queues[index].resetAverageBufferSizeMetrics()
        }

        private fun setNextStealSize(nextValue: Int) {
            minStealSize = min(minStealSize, nextValue)
            maxStealSize = max(maxStealSize, nextValue)
            if (nextValue > STEAL_SIZE_MAX_POWER || nextValue < STEAL_SIZE_MIN_POWER) {
                println("Illegal steal size value: $nextValue")
                exitProcess(1)
            }
            queues[index].setNextStealSize(calculateStealSize(nextValue))
        }


        private fun myExitProcess(status: Int) {
            exitProcess(status)
        }

        // АДАПТИВНОСТЬ PSTEAL

        private var justStarted: Boolean = true

        private var momentum: Double = initialMomentum

        private var prevMetricsValue: Double = Double.MAX_VALUE
        private var prevX: Double = pStealInitialPower.toDouble()

        private val minMomentum: Double = 1e-4

        private val maxReverseStep: Double = 2.0

        private fun nextX(currentX: Double, metricsValue: Double): Double {
            if (justStarted) {
                justStarted = false
                prevMetricsValue = metricsValue
                prevX = currentX

                val nextXValue = nextX(currentX)

                return max(min(maxXBound, nextXValue), minXBound)
            }
            recalculateMomentum(metricsValue, currentX)
            val nextXValue = nextX(currentX)

            if (willBeOutOfBounds(nextXValue)) {
                momentum *= -1 * k1
                justStarted = true

                return if (nextXValue > maxXBound) {
                    max(maxXBound - maxReverseStep, maxXBound - (nextXValue - maxXBound))
                } else {
                    min(minXBound + maxReverseStep, minXBound + (minXBound - nextXValue))
                }
            }

            prevX = currentX
            prevMetricsValue = metricsValue

            return nextXValue
        }

        private fun willBeOutOfBounds(nextXValue: Double): Boolean {
            return nextXValue > maxXBound || nextXValue < minXBound
        }

        private fun recalculateMomentum(metricsValue: Double, currentX: Double) {
            val diff = (metricsValue - prevMetricsValue) / (currentX - prevX)

            val newMomentum = k1 * momentum + k2 * diff

            momentum = if (newMomentum.isNaN()) {
                minMomentum
            } else if (abs(newMomentum) < minMomentum) {
                if (newMomentum > 0) {
                    minMomentum
                } else {
                    -minMomentum
                }
            } else {
                newMomentum
            }
        }

        private fun nextX(currentX: Double): Double {
            return currentX + learningRate * momentum
        }

    }


    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

    fun totalTasksProcessed(): List<Int> {
        return threads.map { it.totalTasksProcessedSum.toInt() }
    }

    fun pStealPower(): List<Double> {
        return threads.map { it.pStealPowerLocal }
    }

    fun minPSteal(): List<Double> {
        return threads.map { it.minPSteal }
    }

    fun maxPSteal(): List<Double> {
        return threads.map { it.maxPSteal }
    }

    fun minStealSize(): List<Int> {
        return threads.map { it.minStealSize }
    }

    fun maxStealSize(): List<Int> {
        return threads.map { it.maxStealSize }
    }

    fun pStealUpdateCount(): List<Int> {
        return threads.map { it.pStealUpdateCount }
    }

    fun stealSizeUpdateCount(): List<Int> {
        return threads.map { it.stealSizeUpdateCount }
    }

    fun pStealUpdateCountAvg(): Double {
        return threads.sumOf { it.pStealUpdateCount } / threads.size.toDouble()
    }

    fun stealSizeUpdateCountAvg(): Double {
        return threads.sumOf { it.stealSizeUpdateCount } / threads.size.toDouble()
    }

    fun expiredFeedbackReceived(): List<Int> {
        return threads.map { it.expiredFeedbackReceived }
    }

    fun deltaLessThenThresholdCount(): List<Int> {
        return threads.map { it.deltaLessThenThresholdCount }
    }


    enum class StealSizeDirection {
        INCREASE,
        DECREASE
    }

    data class StealSizeMetrics(
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

private const val P_STEAL_MAX_POWER = 9.0
private const val P_STEAL_MIN_POWER = 0.0

private const val STEAL_SIZE_MAX_POWER = 10
private const val STEAL_SIZE_MIN_POWER = 0
