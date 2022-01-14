package org.jetbrains.kotlin.pmod

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.scheduler.PriorityCoroutineScheduler
import org.jetbrains.kotlin.smq.IndexedThread
import java.util.concurrent.locks.LockSupport
import kotlin.random.Random

class AdaptiveObimPriorityCoroutineScheduler(
    poolSize: Int,
    stealSize: Int = 3,
    pSteal: Double = 0.04,
    startThreads: Boolean = true
) : PriorityCoroutineScheduler {

    /**
     * End of work flag
     */
    @Volatile
    private var terminated = false

    /**
     * Threads serving the scheduler
     */
    var threads: List<Worker> = (0 until poolSize).map { index -> Worker(index) }

    private val stealingMultiQueue = AdaptiveObim<Runnable>(poolSize)

    /**
     * Buffer for the freshest sleeping stream
     */
    private val sleepingBox: AtomicRef<Worker?> = atomic(null)

    /**
     * Array where sleeping threads are stored
     */
    private val sleepingArray: AtomicArray<Worker?> = atomicArrayOfNulls(poolSize * 2)

    private val random = Random(0)

    init {
        if (startThreads) {
            start()
        }
    }

    override fun start() {
        threads.forEach { it.start() }
    }


    override fun execute(block: Runnable, priority: Int) {
        val currentThread = Thread.currentThread()
        stealingMultiQueue.push(block, priority)

        if (currentThread is Worker) {
            // insert into local queue
            currentThread.checkWakeThread()
        } else {
            // insert into global queue
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
            val index = random.nextInt(0, sleepingArray.size)
            recentWorker = sleepingArray[index].value

            if (recentWorker != null && sleepingArray[index].compareAndSet(recentWorker, null)) {
                LockSupport.unpark(recentWorker)
            }
        }
    }

    inner class Worker(override val index: Int) : IndexedThread() {

        private val random = Random(0)

        override fun run() {
            var attempts = 0
            while (!terminated) {

                // пытаемся взять из локальной очереди
                val task = stealingMultiQueue.pop()

                if (task != null) {
                    attempts = 0
                    task.run()
                    continue
                }

                if (attempts < RETRY_COUNT) {
                    attempts++
                    continue
                }

                goWait()
                attempts = 0
            }
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

            LockSupport.park()
        }

        fun checkWakeThread() {
            tryWakeThread()
        }

    }

    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

}

// The number of attempts to take a task from one thread
private const val RETRY_COUNT = 100

// The threshold of tasks in the thread queue after which other threads must be woken up
private const val TASKS_COUNT_WAKE_THRESHOLD = 30

// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5