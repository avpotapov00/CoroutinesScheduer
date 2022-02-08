package org.jetbrains.kotlin.common

import kotlinx.coroutines.*
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.priority.Priority
import org.jetbrains.kotlin.scheduler.SMQPriorityCoroutineScheduler
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

/**
 * Test checks no active waiting in lack of tasks
 */
class ActiveWaitingTest {

    @Timeout(20)
    @ParameterizedTest
    @ValueSource(ints = [3, 4, 5])
    fun `no active wait check`(threadsCount: Int) = runBlocking {
        val scheduler = SMQPriorityCoroutineScheduler(threadsCount, postponeThreadsStart = false, pSteal = 0.05)
        val dispatcher = PriorityQueueCoroutineDispatcher(scheduler)

        val tasksCount = 100_000

        // execute block of actions
        val tasks = performTasksBlock(tasksCount, this, dispatcher)

        //start execution
        scheduler.start()

        // waiting for tasks to complete
        tasks.joinAll()

        // wait for workers to park
        delay(1000)

        // check every thread is waiting
        val waitingThreads = scheduler.threads.count { it.state == Thread.State.WAITING }
        assertEquals(threadsCount, waitingThreads)

        // execute next of actions
        performTasksBlock(tasksCount, this, dispatcher).joinAll()

        scheduler.close()
    }

    private suspend fun performTasksBlock(
        tasksCount: Int,
        scope: CoroutineScope,
        dispatcher: PriorityQueueCoroutineDispatcher,
    ): List<Job> {
        return (0 until tasksCount).shuffled().map { priority ->
            scope.launch(dispatcher + Priority(priority)) {
                delay(10)
            }
        }
    }


}