package org.jetbrains.kotlin.priority

class PriorityTaskWrapper(
    private val priority: Int,
    val block: Runnable
) : Comparable<PriorityTaskWrapper> {

    override fun compareTo(other: PriorityTaskWrapper): Int {
        return compareValues(priority, other.priority)
    }

}