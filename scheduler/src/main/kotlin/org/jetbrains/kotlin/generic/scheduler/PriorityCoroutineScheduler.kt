package org.jetbrains.kotlin.generic.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.generic.priority.PriorityTask
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.generic.smq.StealingMultiQueue
import java.io.Closeable
import java.util.concurrent.locks.LockSupport
import kotlin.random.Random

interface PriorityCoroutineScheduler : Closeable {
    fun execute(block: Runnable, priority: Int)
    fun start()
}

class SMQPriorityCoroutineScheduler(
    poolSize: Int,
    stealSize: Int = 3,
    pSteal: Double = 0.04,
    postponeThreadsStart: Boolean = false
) : StealingMultiQueue<PriorityTask>(stealSize, pSteal, poolSize), PriorityCoroutineScheduler {

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

    private val random = Random(0)

    init {
        if (!postponeThreadsStart) {
            start()
        }
    }

    override fun start() {
        threads.forEach { it.start() }
    }


    override fun execute(block: Runnable, priority: Int) {
        val task = PriorityTask(block, priority)
        val currentThread = Thread.currentThread()

        if (currentThread is Worker) {
            // insert into local queue
            insert(task)
            currentThread.checkWakeThread()
        } else {
            // insert into global queue
            insertGlobal(task)
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

                // trying to get from local queue
                var task = delete()

                if (task != null) {
                    attempts = 0
                    task.task.run()
                    continue
                }

                if (attempts < RETRY_COUNT) {
                    attempts++
                    continue
                }

                // if it didn't work, we try to remove it from the global queue
                task = stealAndDeleteFromGlobal()

                if (task != null) {
                    attempts = 0
                    task.task.run()
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
            // if the number of tasks in the local queue is more than the threshold, try to wake up a new thread
            if (size() > TASKS_COUNT_WAKE_THRESHOLD) {
                tryWakeThread()
            }
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