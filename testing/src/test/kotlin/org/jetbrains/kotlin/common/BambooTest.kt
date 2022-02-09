package org.jetbrains.kotlin.common

import kotlinx.coroutines.*
import org.jetbrains.kotlin.generic.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.generic.priority.Priority
import org.jetbrains.kotlin.generic.scheduler.SMQPriorityCoroutineScheduler
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertTrue

class BambooTest {

    @RepeatedTest(10)
    fun `bamboo thread switching test smq`() = runBlocking {
        val scheduler = SMQPriorityCoroutineScheduler(4, postponeThreadsStart = true, pSteal = 0.04)
        val dispatcher = PriorityQueueCoroutineDispatcher(scheduler)

        val threadSwitches = AtomicInteger()
        val bambooHigh = 800
        scheduler.start()

        coroutineScope {
            recursiveTask(dispatcher, threadSwitches, "main", bambooHigh)
        }
        println("here")
        scheduler.close()

        val averageTasksInTread = bambooHigh.toDouble() / threadSwitches.get()

        println("Average tasks, performed per thread one by one: $averageTasksInTread")
        assertTrue(averageTasksInTread > 1)
    }

    private fun CoroutineScope.recursiveTask(
        dispatcher: CoroutineContext,
        threadSwitches: AtomicInteger,
        lastWorkerName: String,
        deep: Int
    ) {
        val name = currentWorkerName()
        if (name != lastWorkerName) {
            threadSwitches.incrementAndGet()
        }
        if (deep <= 0) {
            return
        }
        launch(dispatcher + Priority(deep)) {
            recursiveTask(dispatcher, threadSwitches, name, deep - 1)
        }
    }

}

private fun currentWorkerName() = Thread.currentThread().name.let { it.substring(0, it.indexOf(" ")) }