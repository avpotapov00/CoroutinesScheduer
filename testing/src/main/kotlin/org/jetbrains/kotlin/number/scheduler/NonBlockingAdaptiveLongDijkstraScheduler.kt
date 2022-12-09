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
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class NonBlockingAdaptiveLongDijkstraScheduler(
    private val nodes: List<IntNode>,
    startIndex: Int,
    val poolSize: Int,
    private val stealSizeInitialPower: Int = 3,
    private val pStealInitialPower: Int = 2,
    // The number of attempts to take a task from one thread
    private val retryCount: Int = 100,
    private val metricsUpdateThreshold: Int = 512,
    private val writerThreadFrequency: Int = 5,
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

    private val changeLogMessages = arrayListOf<String>()

//    // Нужно для дебага // DEBUG
//    private val messages @Synchronized get() = changeLogMessages
//
//    @Synchronized
//    fun getMessages(): List<String> {
//        return messages
//    }
//
//    // Нужно для дебага
//    @Synchronized
//    fun addToMessageLog(message: String) {
//        changeLogMessages.add(message)
//    }

    init {
        val stealSizeInitialValue = calculateStealSize(stealSizeInitialPower)

        globalQueue = AdaptiveGlobalHeapWithStealingBufferLongQueue(stealSizeInitialValue)
        queues = Array(poolSize) { AdaptiveHeapWithStealingBufferLongQueue(stealSizeInitialValue) }
        stolenTasks = ThreadLocal.withInitial { ArrayDeque(stealSizeInitialValue) }

        val initialMetricsHolder = MetricsHolder(
            pStealPower = pStealInitialPower,
            stealSizePower = stealSizeInitialPower,
            direction = P_STEAL_INCREASE,
            metricsValue = 0.0,
            epoch = 0,
            isSyncMode = false,
            syncCount = 0,
            activeCount = poolSize
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
                startCurrentMetrics = initialMetricsHolder
            )
        }

        insertGlobal(0.zip(startIndex))

        nodes[startIndex].distance = 0
        threads.forEach { it.start() }

//        addToMessageLog("[START]: $initialMetricsHolder")
    }

    fun waitForTermination() {
        finishPhaser.arriveAndAwaitAdvance()
    }

    data class MetricsHolder(
        val isSyncMode: Boolean,
        val pStealPower: Int,
        val stealSizePower: Int,
        val direction: Int,
        val metricsValue: Double,
        val activeCount: Int,
        val syncCount: Int,
        val epoch: Int
    ) {

        init {
            if (isSyncMode && syncCount < 0) {
                println("isSyncMode && syncCount <= 0: $this")
                exitProcess(1)
            }
            if (isSyncMode && activeCount < syncCount) {
                println("activeCount < syncCount: $this")
                exitProcess(1)
            }
        }

        val isEvalMode get() = !isSyncMode

        fun deregisterFromActive(): MetricsHolder {
            if (activeCount <= 0) {
                println("Internal error: deregisterFromActive: activeCount=$activeCount")
                exitProcess(1)
            }
            return MetricsHolder(
                isSyncMode = isSyncMode,
                pStealPower = pStealPower,
                stealSizePower = stealSizePower,
                direction = direction,
                metricsValue = metricsValue,
                activeCount = activeCount - 1,
                syncCount = syncCount,
                epoch = epoch
            )
        }

        fun withNewActiveAndSync(newActiveThreadsCount: Int, newSyncThreadsCount: Int): MetricsHolder {
            return MetricsHolder(
                isSyncMode = isSyncMode,
                pStealPower = pStealPower,
                stealSizePower = stealSizePower,
                direction = direction,
                metricsValue = metricsValue,
                activeCount = newActiveThreadsCount,
                syncCount = newSyncThreadsCount,
                epoch = epoch
            )
        }

        fun deregisterFromActiveAndUnSync(): MetricsHolder {
            if (isEvalMode) {
                println("Tried to deregisterFromActiveAndUnSync from eval mode")
                exitProcess(1)
            }
            if (activeCount <= 0) {
                println("Internal error: deregisterFromActiveAndUnSync: activeCount=$activeCount")
                exitProcess(1)
            }
            if (syncCount <= 0) {
                println("Internal error: deregisterFromActiveAndUnSync: syncCount=$syncCount")
                exitProcess(1)
            }

            return MetricsHolder(
                isSyncMode = isSyncMode,
                pStealPower = pStealPower,
                stealSizePower = stealSizePower,
                direction = direction,
                metricsValue = metricsValue,
                activeCount = activeCount - 1,
                syncCount = syncCount - 1,
                epoch = epoch
            )
        }

        fun registerAsActive(): MetricsHolder {
            return MetricsHolder(
                isSyncMode = isSyncMode,
                pStealPower = pStealPower,
                stealSizePower = stealSizePower,
                direction = direction,
                metricsValue = metricsValue,
                activeCount = activeCount + 1,
                syncCount = syncCount,
                epoch = epoch
            )
        }

        fun toSynced(): MetricsHolder {
            return MetricsHolder(
                isSyncMode = isSyncMode,
                pStealPower = pStealPower,
                stealSizePower = stealSizePower,
                direction = direction,
                metricsValue = metricsValue,
                activeCount = activeCount,
                syncCount = syncCount + 1,
                epoch = epoch
            )
        }


        fun toEvalMode(): MetricsHolder {
            if (isEvalMode) {
                println("Tried to eval from eval mode")
                exitProcess(1)
            }

            return MetricsHolder(
                isSyncMode = false, // eval
                pStealPower = pStealPower,
                stealSizePower = stealSizePower,
                direction = direction,
                metricsValue = metricsValue,
                activeCount = activeCount,
                syncCount = syncCount,
                epoch = epoch + 1
            )
        }

        fun isInSyncModeAndReadyToSwitch(): Boolean {
            return isSyncMode && syncCount == activeCount
        }

    }

    inner class Worker(
        override val index: Int,
        var stealSizePowerLocal: Int,
        var pStealPowerLocal: Int,
        val isWriter: Boolean,
        startCurrentMetrics: MetricsHolder
    ) : IndexedThread() {

        private var locked = false

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        var totalTasksProcessed: Long = 0
        var uselessWork = 0L

        var parametersUpdateDirection: Int = P_STEAL_INCREASE

        var prevUsefulWorkMetricsValue = 100.0
        var prevTimestamp: Long = System.nanoTime()

        var pStealLocal: Double = calculatePSteal(pStealPowerLocal)
        var stealSizeLocal: Int = calculateStealSize(stealSizePowerLocal)
        var isSyncMode: Boolean = false

        // statistics
        var successSelfUpdatesCount = 0
        var parametersUpdateCount = 0
        var minPSteal = pStealPowerLocal
        var maxPSteal = pStealPowerLocal
        var minStealSize = stealSizeLocal
        var maxStealSize = stealSizeLocal

        private fun updatePSteal() {
            pStealLocal = calculatePSteal(pStealPowerLocal)
            minPSteal = min(minPSteal, pStealPowerLocal)
            maxPSteal = max(maxPSteal, pStealPowerLocal)
        }

        private fun updateStealSize() {
            stealSizeLocal = calculateStealSize(stealSizePowerLocal)
            minStealSize = min(stealSizeLocal, stealSizePowerLocal)
            maxStealSize = max(stealSizeLocal, stealSizePowerLocal)
        }

        private var currentMetrics: MetricsHolder = startCurrentMetrics
//            set(value) { // DEBUG
//                checkCounters(value)
//                field = value
//            }

//        private val changeLog: MutableList<String> = mutableListOf(currentMetrics.toString())

        override fun run() {
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
                        onWakeUp()
                    }
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    checkForUpdateParameters()
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
                        onWakeUp()
                    }
                    attempts = 0
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    checkForUpdateParameters()
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
                        onWakeUp()
                    }
                    attempts = 0
                    tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                    checkForUpdateParameters()
                    continue
                }

                if (!locked) {
                    finishPhaser.arriveAndDeregister()
                }
                locked = true
                // Вычитаем значение из метрики, наш голос в ней уже точно есть, поэтому меньше 0 не опустится
                if (currentMetrics.isSyncMode) {
                    unregisterAndUnSync()
                } else {
                    unregister()
                }
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

//            printAsCurrentThread(metrics.toString())

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

            if (currentMetrics.isSyncMode) {/* если все еще синхронизация на той же фазе - выходим, нас уже учли,
                */
                if (currentMetrics.epoch == globalMetricsValue.epoch) {
                    return
                }
                // Могли переключить в следующий eval или следующий sync, но не дальше
                if (globalMetricsValue.epoch > currentMetrics.epoch + 2) {
                    println("internal error: SyncMode: globalMetricsValue.epoch > currentMetrics.epoch + 2, globalMetricsValue.epoch=$globalMetricsValue, currentMetrics=$currentMetrics")
                    myExitProcess(1)
                }/* если наступил следующий sync - применяем его к себе и ничего не делаем
                *  если наступил eval - применяем его к себе и начинаем собирать метрики
                *  проверяем (внутри applyMetrics) - вдруг уже можно начинать eval
                */
                applyMetrics(globalMetricsValue)
            } else {
                if (currentMetrics.epoch == globalMetricsValue.epoch) {/*
                     * Если последнее что мы помним, что надо считать метрики и это так и есть -
                     * - пробуем обновить глобальное состояние
                     */
                    updateParameters(currTimestamp)
                } else { // в этом блоке globalMetricsValue == SYNC
                    /*
                     не может быть, что у нас был eval, и без нашего ведома стал
                     следующий eval (тк currentMetrics !== globalMetricsValue), это ошибка
                      */
                    if (globalMetricsValue.epoch > currentMetrics.epoch + 1) {
                        printAsCurrentThread("internal error: EvalMode: globalMetricsValue.epoch > currentMetrics.epoch + 1, globalMetricsValue.epoch=$globalMetricsValue, currentMetrics=$currentMetrics")
                        myExitProcess(1)
                    }
                    if (!globalMetricsValue.isSyncMode) {
                        printAsCurrentThread("internal error: PHASE ERROR: 318, globalMetricsValue.epoch=$globalMetricsValue, currentMetrics=$currentMetrics")
                        myExitProcess(1)
                    }/*
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
//                STEAL_SIZE_INCREASE_SETUP -> {
//                    if (stealSizePowerLocal == STEAL_SIZE_MAX_POWER) {
//                        tryUpdateGlobalMetricsToSync(
//                            currentMetrics = currentMetrics,
//                            pStealPower = pStealPowerLocal,
//                            stealSizePower = stealSizePowerLocal - 1, // move left (decrease)
//                            direction = STEAL_SIZE_DECREASE,
//                            metricsValue = currentMetricsValue,
//                        )
//                    } else {
//                        tryUpdateGlobalMetricsToSync(
//                            currentMetrics = currentMetrics,
//                            pStealPower = pStealPowerLocal,
//                            stealSizePower = stealSizePowerLocal + 1, // move right (increase)
//                            direction = STEAL_SIZE_INCREASE,
//                            metricsValue = currentMetricsValue,
//                        )
//                    }
//                }
//
//                STEAL_SIZE_INCREASE -> { // can't start from the least stealSize value
//                    if (stealSizePowerLocal <= STEAL_SIZE_MIN_POWER) {
//                        println("ERROR! stealSize == STEAL_SIZE_LOWER_BOUND")// \n${changeLog.joinToString("\n") { it.toString() }}")
//                        myExitProcess(1)
//                    }
//                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
//                        if (stealSizePowerLocal == STEAL_SIZE_MAX_POWER) {
//                            tryUpdateGlobalMetricsToSync(
//                                currentMetrics = currentMetrics,
//                                pStealPower = pStealPowerLocal,
//                                stealSizePower = stealSizePowerLocal, // don't change because it is upper bound and metrics increases
//                                direction = P_STEAL_INCREASE_SETUP,
//                                metricsValue = currentMetricsValue,
//                            )
//                        } else {
//                            tryUpdateGlobalMetricsToSync(
//                                currentMetrics = currentMetrics,
//                                pStealPower = pStealPowerLocal,
//                                stealSizePower = stealSizePowerLocal + 1, // move right (increase)
//                                direction = STEAL_SIZE_INCREASE,
//                                metricsValue = currentMetricsValue,
//                            )
//                        }
//                    } else { // no left bound check because it can't start from the least stealSize value
//                        tryUpdateGlobalMetricsToSync(
//                            currentMetrics = currentMetrics,
//                            pStealPower = pStealPowerLocal,
//                            stealSizePower = stealSizePowerLocal - 1, // move left (decrease)
//                            direction = STEAL_SIZE_DECREASE,
//                            metricsValue = currentMetricsValue,
//                        )
//                    }
//                }
//
//                STEAL_SIZE_DECREASE -> { // can't start from the biggest stealSize value
//                    if (stealSizePowerLocal >= STEAL_SIZE_MAX_POWER) {
//                        println("ERROR! stealSize == STEAL_SIZE_MAX_VALUE") //\n${changeLog.joinToString("\n") { it.toString() }}")
//                        myExitProcess(1)
//                    }
//                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
//                        if (stealSizePowerLocal == STEAL_SIZE_MIN_POWER) {
//                            tryUpdateGlobalMetricsToSync(
//                                currentMetrics = currentMetrics,
//                                pStealPower = pStealPowerLocal,
//                                stealSizePower = stealSizePowerLocal, // don't change because it is lower bound and metrics increases
//                                direction = P_STEAL_INCREASE_SETUP,
//                                metricsValue = currentMetricsValue,
//                            )
//                        } else {
//                            tryUpdateGlobalMetricsToSync(
//                                currentMetrics = currentMetrics,
//                                pStealPower = pStealPowerLocal,
//                                stealSizePower = stealSizePowerLocal - 1, // move left (decrease)
//                                direction = STEAL_SIZE_DECREASE,
//                                metricsValue = currentMetricsValue,
//                            )
//                        }
//                    } else {
//                        tryUpdateGlobalMetricsToSync(
//                            currentMetrics = currentMetrics,
//                            pStealPower = pStealPowerLocal,
//                            stealSizePower = stealSizePowerLocal + 1, // move right (increase)
//                            direction = P_STEAL_INCREASE_SETUP,
//                            metricsValue = currentMetricsValue,
//                        )
//                    }
//                }

//                P_STEAL_INCREASE_SETUP -> {
//                    if (pStealPowerLocal == P_STEAL_MAX_POWER) {
//                        tryUpdateGlobalMetricsToSync(
//                            currentMetrics = currentMetrics,
//                            pStealPower = pStealPowerLocal - 1,  // move left (decrease)
//                            stealSizePower = stealSizePowerLocal,
//                            direction = P_STEAL_DECREASE,
//                            metricsValue = currentMetricsValue,
//                        )
//                    } else {
//                        tryUpdateGlobalMetricsToSync(
//                            currentMetrics = currentMetrics,
//                            pStealPower = pStealPowerLocal + 1, // move right (increase),
//                            stealSizePower = stealSizePowerLocal,
//                            direction = P_STEAL_INCREASE,
//                            metricsValue = currentMetricsValue,
//                        )
//                    }
//                }

                P_STEAL_INCREASE -> {
                    if (pStealPowerLocal <= P_STEAL_MIN_POWER) {
                        println("ERROR! pStealPowerLocal == P_STEAL_MIN_POWER") //\n${changeLog.joinToString("\n") { it.toString() }}")
                        myExitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (pStealPowerLocal == P_STEAL_MAX_POWER) {
                            tryUpdateGlobalMetricsToSync(
                                currentMetrics = currentMetrics,
                                pStealPower = pStealPowerLocal - 1,  // don't change because it is upper bound and metrics increases
                                stealSizePower = stealSizePowerLocal,
                                direction = P_STEAL_DECREASE,
                                metricsValue = currentMetricsValue,
                            )
                        } else {
                            tryUpdateGlobalMetricsToSync(
                                currentMetrics = currentMetrics,
                                pStealPower = pStealPowerLocal + 1,// move right (increase)
                                stealSizePower = stealSizePowerLocal,
                                direction = P_STEAL_INCREASE,
                                metricsValue = currentMetricsValue,
                            )
                        }
                    } else { // no left bound check because it can't start from the least stealSize value
                        tryUpdateGlobalMetricsToSync(
                            currentMetrics = currentMetrics,
                            pStealPower = pStealPowerLocal - 1, // move left (decrease)
                            stealSizePower = stealSizePowerLocal,
                            direction = P_STEAL_DECREASE,
                            metricsValue = currentMetricsValue,
                        )
                    }
                }

                P_STEAL_DECREASE -> {
                    if (pStealPowerLocal >= P_STEAL_MAX_POWER) {
                        println("ERROR! pStealPowerLocal == P_STEAL_MAX_POWER") //\n${changeLog.joinToString("\n") { it.toString() }}")
                        myExitProcess(1)
                    }
                    if (currentMetricsValue > prevUsefulWorkMetricsValue) {
                        if (pStealPowerLocal == P_STEAL_MIN_POWER) {
                            tryUpdateGlobalMetricsToSync(
                                currentMetrics = currentMetrics,
                                pStealPower = pStealPowerLocal + 1, // don't change because it is lower bound and metrics increases
                                stealSizePower = stealSizePowerLocal,
                                direction = P_STEAL_INCREASE,
                                metricsValue = currentMetricsValue,
                            )
                        } else {
                            tryUpdateGlobalMetricsToSync(
                                currentMetrics = currentMetrics,
                                pStealPower = pStealPowerLocal - 1, // move left (decrease)
                                stealSizePower = stealSizePowerLocal,
                                direction = P_STEAL_DECREASE,
                                metricsValue = currentMetricsValue,
                            )
                        }
                    } else {
                        tryUpdateGlobalMetricsToSync(
                            currentMetrics = currentMetrics,
                            pStealPower = pStealPowerLocal + 1, // move right (increase)
                            stealSizePower = stealSizePowerLocal,
                            direction = P_STEAL_INCREASE,
                            metricsValue = currentMetricsValue,
                        )
                    }
                }
            }

            checkPStealAndStealSize()
        }

        private fun checkPStealAndStealSize() {
//            if (stealSizePowerLocal < STEAL_SIZE_MIN_POWER || stealSizePowerLocal > STEAL_SIZE_MAX_POWER) {
//                println("ERROR! stealSize < STEAL_SIZE_MIN_VALUE || stealSize > STEAL_SIZE_MAX_VALUE: $stealSizePowerLocal")
//                myExitProcess(1)
//            }
            if (pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER) {
                println("ERROR! pStealPowerLocal < P_STEAL_MIN_POWER || pStealPowerLocal > P_STEAL_MAX_POWER: $pStealPowerLocal")
                myExitProcess(1)
            }
        }

        private fun tryUpdateGlobalMetricsToSync(
            currentMetrics: MetricsHolder,
            pStealPower: Int,
            stealSizePower: Int,
            direction: Int,
            metricsValue: Double,
        ) {
            val newMetrics = MetricsHolder(
                isSyncMode = true,
                pStealPower = pStealPower,
                stealSizePower = stealSizePower,
                direction = direction,
                metricsValue = metricsValue,
                activeCount = currentMetrics.activeCount,
                syncCount = 1, // только "мы"
                epoch = currentMetrics.epoch + 1
            )
            tryUpdateGlobalMetricsToSync(newMetrics)
        }

        private fun tryUpdateGlobalMetricsToSync(newMetrics: MetricsHolder) {
            var currentNewMetrics = newMetrics
            var currentExpectedMetrics = currentMetrics

            while (true) {
                if (casGlobalValue(currentExpectedMetrics, currentNewMetrics)) { //, "Succeed to sync")) {
                    successSelfUpdatesCount++
                    applyMetricsAfterSyncCheck(currentNewMetrics)
                    return
                }
                currentExpectedMetrics = globalMetrics.value

                if (currentMetrics.epoch != currentExpectedMetrics.epoch) {
                    //  значит кто-то успел до нас обновить, выходим
                    applyMetrics(currentExpectedMetrics)
                    return
                } else {
                    // кто-то выписался или записался, повторяем попытку
                    currentNewMetrics = newMetrics.withNewActiveAndSync(
                        newActiveThreadsCount = currentExpectedMetrics.activeCount,
                        newSyncThreadsCount = 1,
                    )
                }

            }
        }

        private fun applyMetrics(newMetrics: MetricsHolder) {
            // Эпоха не должна убывать
            if (newMetrics.epoch < currentMetrics.epoch) {
                println("Bad metrics to apply: $currentMetrics -> $newMetrics")
                myExitProcess(1)
            }
            // printAsCurrentThread("Apply: $metrics")
            // Если это новая метрика sync И мы еще в нее не регистрировались - регистрируемся

            // Мы тут уже регистрировались - выходим
            val updatedMetrics = if (newMetrics.epoch == currentMetrics.epoch) {
                newMetrics
            } else {
                // иначе регистрируемся и применяем себе
                registerToSync(newMetrics)
            } ?: return // если мы были последние, кого ждали на регистрации, registerToSync вызовет applyMetrics внутри
            // - registerToSync вернет null и мы выйдет мум

            applyMetricsAfterSyncCheck(updatedMetrics)
        }

        private fun applyMetricsAfterSyncCheck(newMetrics: MetricsHolder) {
            currentMetrics = newMetrics

            if (pStealPowerLocal != newMetrics.pStealPower || stealSizePowerLocal != newMetrics.stealSizePower) {
                parametersUpdateCount++
            }

            pStealPowerLocal = newMetrics.pStealPower
            stealSizePowerLocal = newMetrics.stealSizePower
            prevUsefulWorkMetricsValue = newMetrics.metricsValue
            parametersUpdateDirection = newMetrics.direction
            isSyncMode = newMetrics.isSyncMode

            updatePSteal()
            updateStealSize()
            queues[index].setNextStealSize(stealSizeLocal)
        }

        /**
         * Возвращает null если эта регистрация в sync была последней, которую ждали, и мы переключились уже в eval mode
         * и применили метрику себе
         */
        private fun registerToSync(newMetrics: MetricsHolder): MetricsHolder? {
            // Если это eval режим - просто выходим
            if (newMetrics.isEvalMode) {
                return newMetrics
            }
            // Запоминаем текущее значение
            var currentExpectedMetrics = newMetrics
            // Регистрируемся в sync
            var updatedMetrics = newMetrics.toSynced()
            // Проверяем что не переполнили счетчики
            checkCounters(updatedMetrics)
            // Помним, что если сейчас sync и в нем не регались - без нас дальше epoch переключить не смогут
            while (true) {
                if (casGlobalValue(currentExpectedMetrics, updatedMetrics)) { //, "registerToSync")) {
                    break
                }
                currentExpectedMetrics = globalMetrics.value
                // поменятья могли только эти два параметра
                if (currentExpectedMetrics.epoch != updatedMetrics.epoch) {
                    printAsCurrentThread("Unexpected sync move: ${updatedMetrics.epoch} -> ${currentExpectedMetrics.epoch}")
                    myExitProcess(1)
                }
                // кто-то выписался или записался, повторяем попытку
                updatedMetrics = updatedMetrics.withNewActiveAndSync(
                    newActiveThreadsCount = currentExpectedMetrics.activeCount,
                    newSyncThreadsCount = currentExpectedMetrics.syncCount + 1 // тк регистрируемся
                )
                checkCounters(updatedMetrics)
            }
            // Проверяем не являемся ли мы последними, кого ждут
            if (trySwitchToEvalMode(updatedMetrics)) {
                return null
            }

            return updatedMetrics
        }

        private fun checkCounters(updatedMetrics: MetricsHolder) {
            if ((updatedMetrics.isSyncMode && updatedMetrics.syncCount > updatedMetrics.activeCount) ||
                updatedMetrics.syncCount > poolSize ||
                updatedMetrics.activeCount > poolSize ||
                updatedMetrics.syncCount < 0
            ) {
                printAsCurrentThread("Bad updatedMetrics: $updatedMetrics")
                myExitProcess(1)
            }
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


        private fun unregisterAndUnSync() {
            val startCurrentMetrics = currentMetrics
            var currentExpectedMetrics = currentMetrics
            var currentDecrementedMetrics = currentExpectedMetrics.deregisterFromActiveAndUnSync()

            while (true) {
                if (casGlobalValue(currentExpectedMetrics, currentDecrementedMetrics)) {  //, "unregisterAndUnSync")) {
                    return
                }

                currentExpectedMetrics = globalMetrics.value
                // если успели переключить эпоху, значит нам нужно только выписаться из активных
                if (currentExpectedMetrics.epoch != startCurrentMetrics.epoch) {
                    unregister()
                    return
                }
                currentDecrementedMetrics = currentExpectedMetrics.deregisterFromActiveAndUnSync()
            }
        }

        private fun unregister() {
            var currentExpectedMetrics = globalMetrics.value
            var currentDecrementedMetrics = currentExpectedMetrics.deregisterFromActive()

            while (true) {
                if (casGlobalValue(currentExpectedMetrics, currentDecrementedMetrics)) { //, "unregister")) {
                    return
                }

                currentExpectedMetrics = globalMetrics.value
                currentDecrementedMetrics = currentExpectedMetrics.deregisterFromActive()
            }
        }

        private fun onWakeUp() {
            // Снова регистрируемся как активный поток
            var globalValue = register()
            //  Удаляем значения метрик
            totalTasksProcessed = 0
            uselessWork = 0
            prevTimestamp = System.nanoTime()
            if (currentMetrics.epoch == globalValue.epoch) {
                // регистрируемся в sync, если это нужно
                // функция вернет null, если мы уже применили метрику, поэтому мы тут выйдем, чтобы дважды не применять
                globalValue = registerToSync(globalValue) ?: return
            }/*
            Применяем себе изменившуюся метрику, если надо - регистрируемся в ней
            Если сейчас sync и мы были последние, кого ждали - пробуем переместиться в eval режим (внутри applyMetrics)
             */
            applyMetrics(globalValue)
        }

        /**
         * @return возвращает последнее известное значение метрики
         */
        private fun register(): MetricsHolder {
            var currentExpectedMetrics = globalMetrics.value
            var currentDecrementedMetrics = currentExpectedMetrics.registerAsActive()

            while (true) {
                if (casGlobalValue(currentExpectedMetrics, currentDecrementedMetrics)) { //, "register")) {
                    // записываем в локальное последнее значение только что обновленную метрику (оптимизация)
                    return currentDecrementedMetrics
                }

                currentExpectedMetrics = globalMetrics.value
                currentDecrementedMetrics = currentExpectedMetrics.registerAsActive()
            }
        }

        /**
         * @return применили ли мы себе какую-то переключенную метрику
         */
        private fun trySwitchToEvalMode(metrics: MetricsHolder): Boolean {
            var currentExpectedMetrics = metrics

            while (currentExpectedMetrics.isInSyncModeAndReadyToSwitch()) {
                val evalModeMetrics = currentExpectedMetrics.toEvalMode()

                if (casGlobalValue(currentExpectedMetrics, evalModeMetrics)) { //, "trySwitchToEvalMode")) {
//                    printAsCurrentThread("trySwitchToEvalMode: $evalModeMetrics, startEvalMetrics=$metrics, currentExpectedMetrics=$currentExpectedMetrics")
                    applyMetrics(evalModeMetrics)
                    return true
                }
                // кто-то проснулся и успел до нас
                currentExpectedMetrics = globalMetrics.value
                if (currentExpectedMetrics.epoch == metrics.epoch) {
                    // такое возможно только если кто-то выписался
                    if (currentExpectedMetrics.isEvalMode) {
                        printAsCurrentThread("internal error: trySwitchToEvalMode")
                        myExitProcess(1)
                    }
                } else { // если с синка успели переключить на какой-то режим дальше
                    applyMetrics(currentExpectedMetrics)
                    return true
                }
            }

            return false
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
            totalTasksProcessed++
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
                        break
                    }
                }
            }

            checkWakeThread()
        }

        fun insert(task: Long) {

            queues[index].addLocal(task)
        }

        private fun printAsCurrentThread(message: String) {
//            addToMessageLog("Thread[$index]: $message")
//            changeLog.add(message)
        }

        private fun myExitProcess(status: Int) {
//            File("bug-thread-$index-changeLog-threads-$poolSize-pStealInitialPower-$pStealInitialPower-stealSizeInitialPower-$stealSizeInitialPower.txt").bufferedWriter()
//                .use { writer ->
//                    changeLog.forEach { logEntry ->
//                        writer.write(logEntry)
//                        writer.newLine()
//                    }
//                }
//            File("bug-thread-$index-messages-threads-$poolSize-pStealInitialPower-$pStealInitialPower-stealSizeInitialPower-$stealSizeInitialPower.txt.txt").bufferedWriter()
//                .use { writer ->
//                    getMessages().forEach { messageEntry ->
//                        writer.write(messageEntry)
//                        writer.newLine()
//                    }
//                }
            exitProcess(status)
        }

        private fun casGlobalValue(expected: MetricsHolder, new: MetricsHolder): Boolean { //, message: String): Boolean  =
//            synchronized(this@AdaptiveDijkstraScheduler) {
            if (new.syncCount < 0 || new.syncCount > poolSize) {
                printAsCurrentThread("internal error: casGlobalValue: syncCount=${new.syncCount}")
                myExitProcess(1)
            }
            if (new.activeCount < 0 || new.activeCount > poolSize) {
                printAsCurrentThread("internal error: casGlobalValue: activeCount=${new.activeCount}")
                myExitProcess(1)
            }
            val result = globalMetrics.compareAndSet(expected, new)

//                if (result) {
//                    printAsCurrentThread("$message : $new")
//                }
            if (expected == new) {
                printAsCurrentThread("internal error: setting same value")
                myExitProcess(1)
            }
            if (expected.epoch > new.epoch) {
                printAsCurrentThread("internal error: expected.epoch > new.epoch, expected=$expected, new=$new")
                myExitProcess(1)
            }

            return result
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

    fun stealSizes(): List<Int> {
        return threads.map { it.stealSizePowerLocal }
    }

    fun pStealPower(): List<Int> {
        return threads.map { it.pStealPowerLocal }
    }

    fun minStealSizes(): List<Int> {
        return threads.map { it.minStealSize }
    }

    fun maxStealSizes(): List<Int> {
        return threads.map { it.maxStealSize }
    }

    fun minPSteal(): List<Int> {
        return threads.map { it.minPSteal }
    }

    fun maxPSteal(): List<Int> {
        return threads.map { it.maxPSteal }
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

//private const val STEAL_SIZE_INCREASE_SETUP = 1
//private const val STEAL_SIZE_INCREASE = 2
//private const val STEAL_SIZE_DECREASE = 3

//private const val P_STEAL_INCREASE_SETUP = 4
private const val P_STEAL_INCREASE = 5
private const val P_STEAL_DECREASE = 6

private const val P_STEAL_MAX_POWER = 9
private const val P_STEAL_MIN_POWER = 0

//private const val STEAL_SIZE_MAX_POWER = 10
//private const val STEAL_SIZE_MIN_POWER = 0
