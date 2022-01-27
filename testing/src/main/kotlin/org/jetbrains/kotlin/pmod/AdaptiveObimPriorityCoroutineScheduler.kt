package org.jetbrains.kotlin.pmod

import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.scheduler.PriorityCoroutineScheduler
import org.jetbrains.kotlin.smq.IndexedThread

class AdaptiveObimPriorityCoroutineScheduler(
    poolSize: Int,
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

    private val pmod = AdaptiveObim<Runnable>(poolSize + 1)

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

        if (currentThread is Worker) {
            pmod.push(block, priority)
        } else {
            pmod.pushOuter(block, priority)
        }
    }

    inner class Worker(override val index: Int) : IndexedThread() {

        override fun run() {
            try {
                while (!terminated) {
                    pmod.pop()?.run()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun close() {
        terminated = true
        threads.forEach { it.interrupt() }
        threads.forEach { it.join() }
    }

}


// The number of cells that we will look at trying to wake up the thread
private const val WAKE_RETRY_COUNT = 5