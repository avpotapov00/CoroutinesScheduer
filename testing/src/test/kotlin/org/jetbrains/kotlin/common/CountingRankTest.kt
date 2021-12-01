package org.jetbrains.kotlin.common

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.priority.Priority
import org.jetbrains.kotlin.scheduler.ExperimentalPriorityCoroutineScheduler
import org.jetbrains.kotlin.scheduler.PriorityCoroutineScheduler
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.test.assertEquals

/**
 * A test that checks the rank of a task based on the current value of the incrementing counter.
 */
class CountingRankTest {

    /**
     * Rank check using a single counter
     */
    @RepeatedTest(5)
    fun `rank test with StealingMultiQueue one barrier`() = runBlocking {
        val counter = AtomicInteger(0)

        val scheduler = ExperimentalPriorityCoroutineScheduler(4, startThreads = false, pSteal = 0.04)
        val dispatcher = PriorityQueueCoroutineDispatcher(scheduler)

        val tasksCount = 1_000_000

        val summaryDelta = AtomicInteger(0)

        tasksBlock(
            tasksCount = tasksCount,
            dispatcher = dispatcher,
            counter = counter,
            summaryDelta = summaryDelta,
            scheduler = scheduler
        )
        scheduler.close()

        assertEquals(tasksCount, counter.get())

        val approximateRank = summaryDelta.get().toDouble() / tasksCount
        println(approximateRank)
    }

    private suspend fun tasksBlock(
        tasksCount: Int,
        dispatcher: PriorityQueueCoroutineDispatcher,
        counter: AtomicInteger,
        summaryDelta: AtomicInteger,
        scheduler: PriorityCoroutineScheduler
    ) {
        coroutineScope {
            val priorities = (0 until tasksCount).shuffled()

            for (taskPriority in priorities) {

                launch(dispatcher + Priority(taskPriority)) {
                    val counterValue = counter.getAndIncrement()
                    val priority = coroutineContext[Priority]!!.priority

                    summaryDelta.addAndGet(abs(priority - counterValue))
                }
            }
            scheduler.start()
        }
    }

}