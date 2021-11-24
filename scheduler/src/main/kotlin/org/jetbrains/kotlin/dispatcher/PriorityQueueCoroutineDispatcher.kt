package org.jetbrains.kotlin.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.priority.Priority
import org.jetbrains.kotlin.scheduler.PriorityCoroutineScheduler
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

class PriorityQueueCoroutineDispatcher(
    private val scheduler: PriorityCoroutineScheduler
) : CoroutineDispatcher(), Closeable {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val priority = context[Priority]?.priority ?: DEFAULT_PRIORITY
        scheduler.execute(block, priority)
    }

    companion object {
        private const val DEFAULT_PRIORITY = 1
    }

    override fun close() {
        scheduler.close()
    }

}