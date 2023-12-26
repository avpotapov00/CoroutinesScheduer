package org.jetbrains.kotlin.number.scheduler

import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.adaptive.AdaptiveGlobalHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.adaptive.AdaptiveHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue
import org.jetbrains.kotlin.util.*
import java.io.Closeable
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom

/**
 * @author Потапов Александр
 * @since 12.01.2023
 */
class PlainNonBlockingLongDijkstraScheduler(
    private var nodes: List<IntNode>,
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
    private var terminated = false

    @Volatile
    private var epoch: Int = 0
    /**
     * Threads serving the scheduler
     */
    var threads: List<Worker> = (0 until poolSize).map { index -> Worker(index) }

    val finishPhaser = Phaser(poolSize + 1)

    /*
  Есть 3 потока
  ===================================================================================
  locked1 | locked2 | locked3 | finishCounter | Событие
  ===================================================================================
  false   | false   | false   | 3             | +poolSize Исходное состояние
  -----------------------------------------------------------------------------------
  false   | false   | false   | 4             | +1 Положили исходную вершину
  -----------------------------------------------------------------------------------
  false   | false   | false   | 3             | -1 Извлекли исходную вершину из глобальной очереди `doJustToStart`
  -----------------------------------------------------------------------------------
  true    | true    | true    | 0             | -poolSize Обработка закончилась
  -----------------------------------------------------------------------------------
  true    | true    | true    | 1             | +1 Положили новую исходную вершину
  -----------------------------------------------------------------------------------
  false   | true    | true    | 2             | +1 Первый поток стартанул                           // события
  -----------------------------------------------------------------------------------
  false   | true    | true    | 1             | -1 Извлекли исходную вершину из глобальной очереди  // компенсируются
  -----------------------------------------------------------------------------------
  false   | false   | false   | 3             | Все потоки в работе
  -----------------------------------------------------------------------------------
  true    | true    | true    | 0             | -poolSize Обработка закончилась
  -----------------------------------------------------------------------------------
  ...
  -----------------------------------------------------------------------------------


   */
    /*
    poolSize - все потоки должны выписаться
    +1 - для ожидания завершения фазы
     */
    val finishCounter = atomic(poolSize)

    init {
        // +1 тк вставляем изначальную вершину
        finishCounter.addAndGet(1)
        insertGlobal(0.zip(startIndex))

        nodes[startIndex].distance = 0
        threads.forEach { it.start() }
    }

    fun waitForTermination() {
        while (finishCounter.value != 0) {
            // spin-wait
        }
    }

    fun restartWithNextGraph(nextGraph: List<IntNode>, startIndex: Int) {
        nodes = nextGraph
        nodes[startIndex].distance = 0
        val currentEpoch = epoch
        // +1 вставляем изначальную вершину
        finishCounter.incrementAndGet()
        // добавляем в фазер стартовую вершину, он будет deregister во время изъятия из глобальной очереди
        insertGlobal(0.zip(startIndex))
        // переключаем эпоху
        epoch = currentEpoch + 1
    }


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

        val random: ThreadLocalRandom = ThreadLocalRandom.current()
        val stealingBuffer: MutableList<Long> = ArrayList(STEAL_SIZE_UPPER_BOUND)

        override fun run() {
            while (!terminated) {
                val currentEpoch = epoch
                doJustToStart()

                var attempts = 0

                while (currentEpoch == epoch) {
                    // идем удалять задачу из своей очереди
                    // если мы locked - то внутри обработаем этот кейс и сделаем finishCounter.incrementAndGet()
                    var task = delete()

                    if (task != Long.MIN_VALUE) {
                        attempts = 0
                        if (locked) {
                            locked = false
                        }
                        tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                        continue
                    }

                    if (attempts < retryCount) {
                        attempts++
                        continue
                    }

                    if (locked) {
                        continue
                    }

                    // if it didn't work, we try to remove it from the self queue
                    task = stealAndDeleteFromSelf(index)

                    if (task != Long.MIN_VALUE) {
                        if (locked) {
                            println("error")
                            error("Can't")
                        }
                        attempts = 0
                        tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
                        continue
                    }

                    if (!locked) {
                        // -1 тк мы больше не активны
                        finishCounter.decrementAndGet()
                        locked = true
                    }
                }
            }
        }

        private fun doJustToStart() {
            if (index != 0) {
                return
            }

            val task = stealAndDeleteFromGlobal()
            if (task == Long.MIN_VALUE) {
                println("No task in global queue!")
                error("No task in global queue!")
            }
            // +1 первый поток стартанул
            if (locked) {
                finishCounter.incrementAndGet()
                locked = false
            }
            // -1 изъяли из глобальной очереди
            finishCounter.decrementAndGet()

            tryUpdate(task.firstFromLong.toInt(), nodes[task.secondFromLong])
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

        private fun shouldStealEffective() = random.nextDouble() < pSteal

        private fun trySteal(currThread: Int): Long {
            // Choose a random queue and check whether
            // its top task has higher priority

            val otherQueueIndex = ThreadLocalRandom.current().nextInt(0, queues.size)
            val otherQueue = queues[otherQueueIndex]

            val ourTop = queues[currThread].getTopLocal()
            val otherTop = otherQueue.top

            if (otherTop == Long.MIN_VALUE) return Long.MIN_VALUE
            if (ourTop == Long.MIN_VALUE || otherTop.firstFromLong < ourTop.firstFromLong) {
                // Если мы помечались как уснувшие то только сейчас - когда знаем что там есть что воровать - помечаемся как проснувшиеся
                if (locked) finishCounter.incrementAndGet()
                // Try to steal a better task !
                otherQueue.steal(stealingBuffer)
                // Кто-то нас опередил
                if (stealingBuffer.isEmpty()) {
                    // Если у нас не получилось украсть задачу то снова помечаемся как уснувшие
                    if (locked) {
                        finishCounter.decrementAndGet()
                    }
                    return Long.MIN_VALUE
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
                        }
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
        epoch++
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

}

