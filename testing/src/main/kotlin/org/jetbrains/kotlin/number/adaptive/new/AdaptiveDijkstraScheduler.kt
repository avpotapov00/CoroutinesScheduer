package org.jetbrains.kotlin.number.adaptive.new

import kotlinx.atomicfu.*
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.adaptive.AdaptiveGlobalHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.adaptive.AdaptiveHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.scheduler.STEAL_SIZE_UPPER_BOUND
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue
import org.jetbrains.kotlin.util.*
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.LockSupport
import kotlin.system.exitProcess

class AdaptiveDijkstraScheduler(
    private val nodes: List<IntNode>,
    startIndex: Int,
    val poolSize: Int,
    stealSizeInitialPower: Int = 3,
    pStealInitialPower: Int = 2,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    private val metricsUpdateThreshold: Int = 512,
    private val writerThreadFrequency: Int = 5
) : Closeable {

    val globalQueue: AdaptiveGlobalHeapWithStealingBufferLongQueue
    val queues: Array<AdaptiveHeapWithStealingBufferLongQueue>
    val stolenTasks: ThreadLocal<ArrayDeque<Long>>
    val globalMetrics: AtomicRef<MetricsHolder>

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

//    private val changeLogMessages = arrayListOf<String>()
//
//    private val messages @Synchronized get() = changeLogMessages
//
//    @Synchronized
//    fun addToMessageLog(message: String) {
//        changeLogMessages.add(message)
//    }


    init {
        val stealSizeInitialValue = calculateStealSize(stealSizeInitialPower)

        globalQueue = AdaptiveGlobalHeapWithStealingBufferLongQueue(stealSizeInitialValue)
        queues = Array(poolSize) { AdaptiveHeapWithStealingBufferLongQueue(stealSizeInitialValue) }
        stolenTasks = ThreadLocal.withInitial { ArrayDeque(stealSizeInitialValue) }

        val initialMetricsHolder = MetricsHolder.evaluationModeMetricsHolder(
            pStealPower = pStealInitialPower,
            stealSizePower = stealSizeInitialPower,
            direction = STEAL_SIZE_INCREASE,
            metricsValue = 0.0,
            epoch = 0
        )
        globalMetrics = atomic(
            initialMetricsHolder
        )

        threads = (0 until poolSize).map { index ->
            Worker(
                index = index,
                stealSizePowerLocal = stealSizeInitialPower,
                pStealPowerLocal = pStealInitialPower,
                isWriter = index % writerThreadFrequency == 0,
                currentMetrics = initialMetricsHolder
            )
        }

        insertGlobal(0.zip(startIndex))

        nodes[startIndex].distance = 0
        threads.forEach { it.start() }
    }

    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
    }

    data class MetricsHolder(
        val pStealPower: Int,
        val stealSizePower: Int,
        val direction: Int,
        val metricsValue: Double,
        val syncCounter: AtomicInt?,
        val epoch: Int
    ) {
        val isSyncMode get() = syncCounter != null

        fun toEvalMode() = evaluationModeMetricsHolder(
            pStealPower = pStealPower,
            stealSizePower = stealSizePower,
            direction = direction,
            metricsValue = metricsValue,
            epoch = epoch + 1
        )


        companion object {

            fun syncModeMetricsHolder(
                pStealPower: Int,
                stealSizePower: Int,
                direction: Int,
                metricsValue: Double,
                epoch: Int
            ): MetricsHolder {
                return MetricsHolder(
                    pStealPower = pStealPower,
                    stealSizePower = stealSizePower,
                    direction = direction,
                    metricsValue = metricsValue,
                    syncCounter = atomic(0),
                    epoch = epoch
                )
            }

            fun evaluationModeMetricsHolder(
                pStealPower: Int,
                stealSizePower: Int,
                direction: Int,
                metricsValue: Double,
                epoch: Int,
            ): MetricsHolder {
                return MetricsHolder(
                    pStealPower = pStealPower,
                    stealSizePower = stealSizePower,
                    direction = direction,
                    metricsValue = metricsValue,
                    syncCounter = null,
                    epoch = epoch
                )
            }

        }
    }


    inner class Worker(
        override val index: Int,
        var stealSizePowerLocal: Int,
        var pStealPowerLocal: Int,
        val isWriter: Boolean,
        var currentMetrics: MetricsHolder
    ) : IndexedThread() {

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        var totalTasksProcessed: Long = 0
        var uselessWork = 0L

        var parametersUpdateDirection: Int = STEAL_SIZE_DECREASE

        var prevUsefulWorkMetricsValue = 100.0
        var prevTimestamp: Long = System.nanoTime()

        var pStealLocal: Double = calculatePSteal(pStealPowerLocal)
        var stealSizeLocal: Int = calculateStealSize(stealSizePowerLocal)
        var isSyncMode: Boolean = false

        // statistics
        var successSelfUpdatesCount = 0
        var parametersUpdateCount = 0

        private fun updatePSteal() {
            pStealLocal = calculatePSteal(pStealPowerLocal)
        }

        private fun updateStealSize() {
            stealSizeLocal = calculateStealSize(stealSizePowerLocal)
        }

//        private val changeLog = mutableListOf(currentMetrics)

        override fun run() {
            var attempts = 0
            while (!terminated) {

                // trying to get from local queue
                var task = delete()

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    checkForUpdateParameters()
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
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    checkForUpdateParameters()
                    continue
                }

                // if it didn't work, we try to remove it from the self queue
                task = stealAndDeleteFromSelf(index)

                if (task != Long.MIN_VALUE) {
                    attempts = 0
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    checkForUpdateParameters()
                    continue
                }

                goWait()
                attempts = 0
            }
        }

        private fun checkForUpdateParameters() {
            val currTimestamp = System.nanoTime()

            if (totalTasksProcessed >= metricsUpdateThreshold) {
                if (isWriter) {
                    checkWriterUpdateParameters(currTimestamp)
                } else {
                    readGlobalParameters()
                }

                totalTasksProcessed = 0
                prevTimestamp = currTimestamp
                uselessWork = 0
            }
        }

        private fun readGlobalParameters() {
            val metrics = globalMetrics.value

//            changeLog.add(metrics)

            if (pStealPowerLocal != metrics.pStealPower || stealSizePowerLocal != metrics.stealSizePower) {
                parametersUpdateCount++
            }

            pStealPowerLocal = metrics.pStealPower
            stealSizePowerLocal = metrics.stealSizePower

            updatePSteal()
            updateStealSize()
            queues[index].setNextStealSize(stealSizeLocal)
        }

        private fun checkWriterUpdateParameters(currTimestamp: Long) {
            val globalMetricsValue = globalMetrics.value

            if (currentMetrics.isSyncMode) {
                /* если все еще синхронизация - выходим, нас уже учли,
                *  проверяем - вдруг уже можно начинать eval
                */
                if (currentMetrics === globalMetricsValue) {
                    checkSwitchToEvalMode(globalMetricsValue)
                    return
                }
                /* если наступил следующий sync - применяем его к себе и ничего не делаем
                *  если наступил eval - применяем его к себе и начинаем собирать метрики
                *  проверяем (внутри applyMetrics) - вдруг уже можно начинать eval
                */
                applyMetrics(globalMetricsValue)
            } else {
                if (currentMetrics === globalMetricsValue) {
                    /*
                     * Если последнее что мы помним, что надо считать метрики и это так и есть -
                     * - пробуем обновить глобальное состояние
                     */
                    updateParameters(currTimestamp)
                } else { // в этом блоке globalMetricsValue == SYNC
                    /*
                     не может быть, что у нас был eval, и без нашего ведома стал
                     следующий eval (тк currentMetrics !== globalMetricsValue), это ошибка
                      */
                    if (!globalMetricsValue.isSyncMode) {
                        println("internal error: PHASE ERROR: 318")
                        exitProcess(1)
                    }
                    /*
                    Последнее что мы помним - это то, что мы должны считать метрики
                    Если мы тут, то мы переключились в следующий sync - нужно применить параметры и зарегистрироваться
                    Проверяем (внутри applyMetrics) - вдруг уже можно начинать eval
                     */
                    applyMetrics(globalMetricsValue)
                }
            }
        }

        /*
            STEAL_SIZE_INCREASE_SETUP ->
            STEAL_SIZE_INCREASE ->
            STEAL_SIZE_DECREASE ->

            P_STEAL_INCREASE_SETUP ->
            P_STEAL_INCREASE ->
            P_STEAL_DECREASE ->
        */
        private fun updateParameters(currTimestamp: Long) {
            val currentMetricsValue = (totalTasksProcessed - uselessWork) / (currTimestamp - prevTimestamp).toDouble()

            when (parametersUpdateDirection) {
                STEAL_SIZE_INCREASE_SETUP -> {
                    if (stealSizePowerLocal == STEAL_SIZE_MAX_POWER) {
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal,
                                stealSizePower = stealSizePowerLocal - 1, // move left (decrease)
                                direction = STEAL_SIZE_DECREASE,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    } else {
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal,
                                stealSizePower = stealSizePowerLocal + 1, // move right (increase)
                                direction = STEAL_SIZE_INCREASE,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    }
                }

                STEAL_SIZE_INCREASE -> { // can't start from the least stealSize value
                    if (stealSizePowerLocal <= STEAL_SIZE_MIN_POWER) {
                        println("ERROR! stealSize == STEAL_SIZE_LOWER_BOUND")// \n${changeLog.joinToString("\n") { it.toString() }}")
                        exitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (stealSizePowerLocal == STEAL_SIZE_MAX_POWER) {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal,
                                    stealSizePower = stealSizePowerLocal, // don't change because it is upper bound and metrics increases
                                    direction = P_STEAL_INCREASE_SETUP,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        } else {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal,
                                    stealSizePower = stealSizePowerLocal + 1, // move right (increase)
                                    direction = STEAL_SIZE_INCREASE,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        }
                    } else { // no left bound check because it can't start from the least stealSize value
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal,
                                stealSizePower = stealSizePowerLocal - 1, // move left (decrease)
                                direction = STEAL_SIZE_DECREASE,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    }
                }

                STEAL_SIZE_DECREASE -> { // can't start from the biggest stealSize value
                    if (stealSizePowerLocal >= STEAL_SIZE_MAX_POWER) {
                        println("ERROR! stealSize == STEAL_SIZE_MAX_VALUE") //\n${changeLog.joinToString("\n") { it.toString() }}")
                        exitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (stealSizePowerLocal == STEAL_SIZE_MIN_POWER) {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal,
                                    stealSizePower = stealSizePowerLocal, // don't change because it is lower bound and metrics increases
                                    direction = P_STEAL_INCREASE_SETUP,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        } else {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal,
                                    stealSizePower = stealSizePowerLocal - 1, // move left (decrease)
                                    direction = STEAL_SIZE_DECREASE,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        }
                    } else {
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal,
                                stealSizePower = stealSizePowerLocal + 1, // move right (increase)
                                direction = P_STEAL_INCREASE_SETUP,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    }
                }

                P_STEAL_INCREASE_SETUP -> {
                    if (pStealPowerLocal == P_STEAL_MAX_POWER) {
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal - 1,  // move left (decrease)
                                stealSizePower = stealSizePowerLocal,
                                direction = P_STEAL_DECREASE,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    } else {
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal + 1, // move right (increase),
                                stealSizePower = stealSizePowerLocal,
                                direction = P_STEAL_INCREASE,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    }
                }

                P_STEAL_INCREASE -> {
                    if (pStealPowerLocal <= P_STEAL_MIN_POWER) {
                        println("ERROR! pStealPowerLocal == P_STEAL_MIN_POWER") //\n${changeLog.joinToString("\n") { it.toString() }}")
                        exitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (pStealPowerLocal == P_STEAL_MAX_POWER) {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal,  // don't change because it is upper bound and metrics increases
                                    stealSizePower = stealSizePowerLocal,
                                    direction = STEAL_SIZE_INCREASE_SETUP,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        } else {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal + 1,// move right (increase)
                                    stealSizePower = stealSizePowerLocal,
                                    direction = P_STEAL_INCREASE,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        }
                    } else { // no left bound check because it can't start from the least stealSize value
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal - 1, // move left (decrease)
                                stealSizePower = stealSizePowerLocal,
                                direction = P_STEAL_DECREASE,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    }
                }

                P_STEAL_DECREASE -> {
                    if (pStealPowerLocal >= P_STEAL_MAX_POWER) {
                        println("ERROR! pStealPowerLocal == P_STEAL_MAX_POWER") //\n${changeLog.joinToString("\n") { it.toString() }}")
                        exitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (pStealPowerLocal == P_STEAL_MIN_POWER) {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal, // don't change because it is lower bound and metrics increases
                                    stealSizePower = stealSizePowerLocal,
                                    direction = STEAL_SIZE_INCREASE_SETUP,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        } else {
                            tryUpdateGlobalMetrics(
                                MetricsHolder.syncModeMetricsHolder(
                                    pStealPower = pStealPowerLocal - 1, // move left (decrease)
                                    stealSizePower = stealSizePowerLocal,
                                    direction = P_STEAL_DECREASE,
                                    metricsValue = currentMetricsValue,
                                    epoch = currentMetrics.epoch + 1
                                )
                            )
                        }
                    } else {
                        tryUpdateGlobalMetrics(
                            MetricsHolder.syncModeMetricsHolder(
                                pStealPower = pStealPowerLocal + 1, // move right (increase)
                                stealSizePower = stealSizePowerLocal,
                                direction = STEAL_SIZE_INCREASE_SETUP,
                                metricsValue = currentMetricsValue,
                                epoch = currentMetrics.epoch + 1
                            )
                        )
                    }
                }
            }

            checkPStealAndStealSize()
        }

        private fun checkPStealAndStealSize() {
            if (stealSizePowerLocal < STEAL_SIZE_MIN_POWER || stealSizePowerLocal > STEAL_SIZE_MAX_POWER) {
                println("ERROR! stealSize < STEAL_SIZE_MIN_VALUE || stealSize > STEAL_SIZE_MAX_VALUE: $stealSizePowerLocal")
                exitProcess(1)
            }
            if (pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER) {
                println("ERROR! pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER: $pStealPowerLocal")
                exitProcess(1)
            }
        }

        private fun tryUpdateGlobalMetrics(newMetrics: MetricsHolder) {
            val casResult = globalMetrics.compareAndSet(currentMetrics, newMetrics)
            if (casResult) {
                successSelfUpdatesCount++
//                changeLog.add(newMetrics)
//                printAsCurrentThread("Successed to sync: $newMetrics")
                applyMetrics(newMetrics)
            } else {
                val metrics = globalMetrics.value
//                printAsCurrentThread("Failed to sync: $metrics")

//                changeLog.add(metrics)
                applyMetrics(metrics)
            }
        }

        private fun applyMetrics(metrics: MetricsHolder) {
//            printAsCurrentThread("Apply: $metrics")
            /*
            Если это новая метрика sync И мы еще в нее не регистрировались - регистрируемся
             */
            if (currentMetrics !== metrics && metrics.isSyncMode) {
                val newValue = metrics.syncCounter!!.incrementAndGet()
//                printAsCurrentThread("Incremented: $metrics ${newValue - 1} -> ${newValue}")
                val switchSucceed = checkSwitchToEvalMode(metrics)
                if (switchSucceed) {
                    return
                }
            }

            currentMetrics = metrics

            if (pStealPowerLocal != metrics.pStealPower || stealSizePowerLocal != metrics.stealSizePower) {
                parametersUpdateCount++
            }

            pStealPowerLocal = metrics.pStealPower
            stealSizePowerLocal = metrics.stealSizePower
            prevUsefulWorkMetricsValue = metrics.metricsValue
            parametersUpdateDirection = metrics.direction
            isSyncMode = metrics.isSyncMode

            updatePSteal()
            updateStealSize()
            queues[index].setNextStealSize(stealSizeLocal)
        }

        private var lastDeleteFromBuffer = false

        private fun delete(): Long {
            val currThread = index

            // Do we have previously stolen tasks ?
            val ourDeque = stolenTasks.get()
            if (ourDeque.isNotEmpty()) {
                lastDeleteFromBuffer = true

                return ourDeque.removeFirst()
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

        private fun shouldStealEffective() = random.nextDouble() < pStealLocal

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
//            printAsCurrentThread("Sleep")
            LockSupport.park()
//            printAsCurrentThread("Wake")
            // в этот момент могли переключить с sync на eval
            finishPhaser.register()
            onWakeUp()
        }

        private fun onWakeUp() {
            val globalValue = globalMetrics.value
            /*
            Если с момента как мы заснули ничего не изменилось - просто продолжаем работу
             */
            if (currentMetrics === globalValue) return

            /*
            Применяем себе изменившуюся метрику, если надо - регистрируемся в ней
            Если сейчас sync и мы были последние, кого ждали - пробуем переместиться в eval режим (внутри applyMetrics)
             */
            applyMetrics(globalValue)
        }

        private fun checkSwitchToEvalMode(globalValue: MetricsHolder): Boolean {
            if (globalValue.isSyncMode) {
                val globalValueSyncCounterValue = globalValue.syncCounter!!.value
//                printAsCurrentThread("Check switch to eval: globalValue=$globalValue, globalValueSyncCounterValue=$globalValueSyncCounterValue")
                if (activeThreads <= globalValueSyncCounterValue) {
                    trySwitchToEvalMode(globalValue)
                    return true
                }
            }

            return false
        }

        private fun trySwitchToEvalMode(metrics: MetricsHolder) {
            val evalMode = metrics.toEvalMode()
//            printAsCurrentThread("Try switch to eval: $evalMode")

            // хотя бы 1 попытка перевести в eval mode будет успешна
            if (globalMetrics.compareAndSet(metrics, evalMode)) {
//                printAsCurrentThread("Successed to eval: $evalMode")
                applyMetrics(evalMode)
            } else { // кто-то проснулся и успел до нас
//                printAsCurrentThread("Failed to eval: $evalMode")
                applyMetrics(globalMetrics.value)
            }
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
                        break
                    }
                }
            }

            checkWakeThread()
        }

        fun insert(task: Long) {

            queues[index].addLocal(task)
        }

//        private fun printAsCurrentThread(message: String) {
//            addToMessageLog("Thread[$index]: $message")
//        }

    }

    private val activeThreads: Int get() = finishPhaser.unarrivedParties

    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

    fun totalTasksProcessed(): Long {
        return threads.sumOf { it.totalTasksProcessed }
    }

    fun stealSizes(): List<Int> {
        return threads.map { it.stealSizePowerLocal }
    }

    fun pStealPower(): List<Int> {
        return threads.map { it.pStealPowerLocal }
    }

    fun successSelfUpdatesCount(): List<Int> {
        return threads.filter { it.isWriter }.map { it.successSelfUpdatesCount }
    }

    fun parametersUpdateCount(): List<Int> {
        return threads.map { it.parametersUpdateCount }
    }

}


// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5

private const val STEAL_SIZE_INCREASE_SETUP = 1
private const val STEAL_SIZE_INCREASE = 2
private const val STEAL_SIZE_DECREASE = 3

private const val P_STEAL_INCREASE_SETUP = 4
private const val P_STEAL_INCREASE = 5
private const val P_STEAL_DECREASE = 6

private const val P_STEAL_MAX_POWER = 9
private const val P_STEAL_MIN_POWER = 0

private const val STEAL_SIZE_MAX_POWER = 10
private const val STEAL_SIZE_MIN_POWER = 0
