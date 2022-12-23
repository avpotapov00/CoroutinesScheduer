package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.*
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.system.exitProcess

class NonBlockingAdaptiveByPStealLongDijkstraScheduler(
    private val nodes: List<IntNode>,
    startIndex: Int,
    val poolSize: Int,
    stealSizeInitialPower: Int = 3,
    private val pStealInitialPower: Int = 2,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    private val startMetricsUpdateIterations: Int = 100,
    private val restMetricsUpdateIterations: Int = 1000,
    private val metricsChangeStepsCount: Int = 10,
    private val metricsChangeConsiderableDelta: Double = 0.0
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
                pStealPowerLocal = pStealInitialPower,
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
        var pStealPowerLocal: Int,
    ) : IndexedThread() {

        private var locked = false

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        // adaptive
        var totalTasksProcessed: Int = 0
        var uselessWork = 0L
        var parametersUpdateDirection: Direction = Direction.P_STEAL_DECREASE
        // последняя метрика перед уменьшением (сменой направления), которую мы видели
        var lastDecreasedMetrics: Double = 0.0

        var prevUsefulWorkMetricsValue = 100.0
        var prevTimestamp: Long = System.nanoTime()

        var pStealLocal: Double = calculatePSteal(pStealPowerLocal)
        var metricsUpdateIterations: Int = startMetricsUpdateIterations

        // statistics
        var parametersUpdateCount: Int = 0

        var minPSteal: Int = pStealPowerLocal
        var maxPSteal: Int = pStealPowerLocal

        var deltaLessThenThresholdCount: Int = 0
        var totalTasksProcessedSum: Long = 0
        var pStealPowerOnBias: Int = -1

        var parametersUpdateCountBeforeSwitch: Int = 0
        var parametersUpdateCountAfterSwitch: Int = 0

        var minPStealBeforeSwitch: Int = pStealPowerLocal
        var maxPStealBeforeSwitch: Int = pStealPowerLocal

        var minPStealAfterSwitch: Int = pStealPowerLocal
        var maxPStealAfterSwitch: Int = pStealPowerLocal

//            set(value) { // DEBUG
//                checkCounters(value)
//                field = value
//            }

//        private val changeLog: MutableList<String> = mutableListOf(currentMetrics.toString())

        override fun run() {
            var attempts = 0
//            var iterations = 0
            while (!terminated) {
//                iterations++
                if (totalTasksProcessed > metricsUpdateIterations) {
                    val currTimestamp = System.nanoTime()
                    updateParameters(currTimestamp)
                    resetMetricsToZero()
//                    iterations = 0
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
        private fun updateParameters(currTimestamp: Long) {
            val currentMetricsValue = (totalTasksProcessed - uselessWork) / (currTimestamp - prevTimestamp).toDouble()
            // Если изменение не существенно - пропускаем итерацию
            if (abs(currentMetricsValue - prevUsefulWorkMetricsValue) < metricsChangeConsiderableDelta) {
                prevUsefulWorkMetricsValue = currentMetricsValue
                deltaLessThenThresholdCount++
                return
            }

            when (parametersUpdateDirection) {
                Direction.P_STEAL_INCREASE -> {
                    if (pStealPowerLocal <= P_STEAL_MIN_POWER) {
                        println("ERROR! pStealPowerLocal == P_STEAL_MIN_POWER") //\n${changeLog.joinToString("\n") { it.toString() }}")
                        myExitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (pStealPowerLocal == P_STEAL_MAX_POWER) {
                            pStealPowerLocal--
                            parametersUpdateDirection = Direction.P_STEAL_DECREASE
                        } else {
                            pStealPowerLocal++
                            parametersUpdateDirection = Direction.P_STEAL_INCREASE
                        }
                    } else { // no left bound check because it can't start from the least stealSize value
                        pStealPowerLocal--
                        parametersUpdateDirection = Direction.P_STEAL_DECREASE
                    }
                }

                Direction.P_STEAL_DECREASE -> {
                    if (pStealPowerLocal >= P_STEAL_MAX_POWER) {
                        println("ERROR! pStealPowerLocal == P_STEAL_MAX_POWER") //\n${changeLog.joinToString("\n") { it.toString() }}")
                        myExitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (pStealPowerLocal == P_STEAL_MIN_POWER) {
                            pStealPowerLocal++
                            parametersUpdateDirection = Direction.P_STEAL_INCREASE
                        } else {
                            pStealPowerLocal--
                            parametersUpdateDirection = Direction.P_STEAL_DECREASE
                        }
                    } else {
                        pStealPowerLocal++
                        parametersUpdateDirection = Direction.P_STEAL_INCREASE
                    }
                }
            }
            parametersUpdateCount++
            if (parametersUpdateCount > metricsChangeStepsCount) {
                parametersUpdateCountAfterSwitch++
                minPStealAfterSwitch = min(minPStealAfterSwitch, pStealPowerLocal)
                maxPStealAfterSwitch = max(maxPStealAfterSwitch, pStealPowerLocal)
            } else {
                parametersUpdateCountBeforeSwitch++
                minPStealBeforeSwitch = min(minPStealBeforeSwitch, pStealPowerLocal)
                maxPStealBeforeSwitch = max(maxPStealBeforeSwitch, pStealPowerLocal)
            }
            if (parametersUpdateCount == metricsChangeStepsCount) {
                metricsUpdateIterations = restMetricsUpdateIterations
                pStealPowerOnBias = pStealPowerLocal
            }

            prevUsefulWorkMetricsValue = currentMetricsValue

            pStealLocal = calculatePSteal(pStealPowerLocal)
            minPSteal = min(minPSteal, pStealPowerLocal)
            maxPSteal = max(maxPSteal, pStealPowerLocal)
            checkPSteal()
        }

        private fun checkPSteal() {
            if (pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER) {
                println("ERROR! pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER: $pStealPowerLocal")
                myExitProcess(1)
            }
        }

        private fun delete(): Long {
            val currThread = index

            // Do we have previously stolen tasks ?
            val ourDeque = stolenTasks.get()
            if (ourDeque.isNotEmpty()) {
                return ourDeque.removeFirst()
            }

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


                if (stealingBuffer.isEmpty()) {
                    return Long.MIN_VALUE
                } // failed


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


        private fun resetMetricsToZero() {
            //  Удаляем значения метрик
            totalTasksProcessed = 0
            uselessWork = 0
            prevTimestamp = System.nanoTime()
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

            checkWakeThread()
        }

        fun insert(task: Long) {

            queues[index].addLocal(task)
        }

        private fun myExitProcess(status: Int) {
            exitProcess(status)
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

    fun pStealPower(): List<Int> {
        return threads.map { it.pStealPowerLocal }
    }

    fun minPSteal(): List<Int> {
        return threads.map { it.minPSteal }
    }

    fun maxPSteal(): List<Int> {
        return threads.map { it.maxPSteal }
    }

    fun minPStealBeforeSwitch(): List<Int> {
        return threads.map { it.minPStealBeforeSwitch }
    }

    fun maxPStealBeforeSwitch(): List<Int> {
        return threads.map { it.maxPStealBeforeSwitch }
    }


    fun minPStealAfterSwitch(): List<Int> {
        return threads.map { it.minPStealAfterSwitch }
    }

    fun maxPStealAfterSwitch(): List<Int> {
        return threads.map { it.maxPStealAfterSwitch }
    }

    fun parametersUpdateCount(): List<Int> {
        return threads.map { it.parametersUpdateCount }
    }

    fun parametersUpdateCountBeforeSwitch(): List<Int> {
        return threads.map { it.parametersUpdateCountBeforeSwitch }
    }

    fun parametersUpdateCountAfterSwitch(): List<Int> {
        return threads.map { it.parametersUpdateCountAfterSwitch }
    }

    fun deltaLessThenThresholdCount(): List<Int> {
        return threads.map { it.deltaLessThenThresholdCount }
    }

    fun pStealPowerOnBias(): List<Int> {
        return threads.map { it.pStealPowerOnBias }
    }

    enum class Direction {
//        P_STEAL_INCREASE_STOP,
        P_STEAL_INCREASE,
//        P_STEAL_DECREASE_STOP,
        P_STEAL_DECREASE,
    }

}


// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5

//private const val STEAL_SIZE_INCREASE_SETUP = 1
//private const val STEAL_SIZE_INCREASE = 2
//private const val STEAL_SIZE_DECREASE = 3

//private const val P_STEAL_INCREASE_SETUP = 4

private const val P_STEAL_MAX_POWER = 9
private const val P_STEAL_MIN_POWER = 0

//private const val STEAL_SIZE_MAX_POWER = 10
//private const val STEAL_SIZE_MIN_POWER = 0
