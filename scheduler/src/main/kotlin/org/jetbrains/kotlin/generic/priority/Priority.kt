package org.jetbrains.kotlin.generic.priority

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext


// TODO: add a special `launch`, which takes an `Int` priority and avoids
// TODO: this object construction.
// TODO: launch(priority = ...) { ... }
// TODO: the implementation will be:
// TODO: if (dispatcher is SMQCoroutineDispatcher) dispatcher.execute(block, priority) <-- OPTIMIZATION
// TODO: else launch(context + Priority(...)) { ... }
class Priority(val priority: Int) : AbstractCoroutineContextElement(Priority) {
    companion object Key : CoroutineContext.Key<Priority>
}

class PriorityTask(
    val task: Runnable,
    private val priority: Int
) : Comparable<PriorityTask> {
    override fun compareTo(other: PriorityTask) =
        compareValues(priority, other.priority)
}

