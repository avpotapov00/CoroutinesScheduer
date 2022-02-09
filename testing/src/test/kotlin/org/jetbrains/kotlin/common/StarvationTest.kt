package org.jetbrains.kotlin.common

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.generic.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.generic.priority.Priority
import org.jetbrains.kotlin.generic.scheduler.SMQPriorityCoroutineScheduler
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks the uniform work of all threads
 */
class StarvationTest {

    @Test
    fun `synchronized queue starvation test`() = runBlocking {
        val threadByNameMap = ConcurrentHashMap<String, Int>()

        val scheduler = SMQPriorityCoroutineScheduler(4, postponeThreadsStart = true, pSteal = 0.05)
        val dispatcher = PriorityQueueCoroutineDispatcher(scheduler)

        val tasksCount = 1_000_000

        coroutineScope {
            val priorities = (0 until tasksCount).shuffled()

            for (taskPriority in priorities) {

                launch(dispatcher + Priority(taskPriority)) {
                    threadByNameMap.merge(currentWorkerName(), 1) { a, b -> a + b }
                }
            }
            scheduler.start()
        }
        scheduler.close()

        val minCount = threadByNameMap.minOf { it.value }
        val maxCount = threadByNameMap.maxOf { it.value }

        // check max delta
        assertTrue(maxCount - minCount < 80_000)
    }

}

private fun currentWorkerName() = Thread.currentThread().name.let { it.substring(0, it.indexOf(" ")) }