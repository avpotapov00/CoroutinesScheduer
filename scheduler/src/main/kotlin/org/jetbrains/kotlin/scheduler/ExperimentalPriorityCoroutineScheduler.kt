package org.jetbrains.kotlin.scheduler

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.priority.PriorityTaskWrapper
import org.jetbrains.kotlin.smq.IndexedThread
import org.jetbrains.kotlin.smq.StealingMultiQueue
import java.util.concurrent.locks.LockSupport
import kotlin.random.Random

class ExperimentalPriorityCoroutineScheduler(
    private val poolSize: Int,
    stealSize: Int = 3,
    pSteal: Double = 0.04,
    startThreads: Boolean = true
) : PriorityCoroutineScheduler {

    @Volatile
    private var terminated = false

    var threads: List<Worker> = (0 until poolSize).map { index -> Worker(index) }

    private val stealingMultiQueue = StealingMultiQueue<PriorityTaskWrapper>(stealSize, pSteal, poolSize)

    private val sleepingBox: AtomicRef<Worker?> = atomic(null)

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
        val task = PriorityTaskWrapper(priority, block)
        val currentThread = Thread.currentThread()


        if (currentThread is Worker) {
            // insert into local queue
            stealingMultiQueue.insert(task)
            currentThread.checkWakeThread()
        } else {
            // insert into global queue
            stealingMultiQueue.insertGlobal(task)
            tryWakeThread()
        }
    }

    private fun tryWakeThread() {
        var recentWorker = sleepingBox.value

        while (recentWorker != null) {
            if (sleepingBox.compareAndSet(recentWorker, null)) {
                LockSupport.unpark(recentWorker)
                return
            }
            recentWorker = sleepingBox.value
        }

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
                var task = stealingMultiQueue.delete()

                if (task != null) {
                    attempts = 0
                    task.block.run()
                    continue
                }

                if (attempts < RETRY_COUNT) {
                    attempts++
                    continue
                }

                // if it didn't work, we try to remove it from the global queue
                task = stealingMultiQueue.stealAndDeleteFromGlobal()

                if (task != null) {
                    attempts = 0
                    task.block.run()
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
            if (stealingMultiQueue.size > TASKS_COUNT_WAKE_THRESHOLD) {
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