package org.jetbrains.kotlin.number.smq.heap

import kotlinx.atomicfu.atomic

class GlobalHeapWithStealingBufferIntQueue(
    private val stealSize: Int
): StealingIntQueue {

    val size: Int get() = _size.value

    private val _size = atomic(0)

    private val queue = PriorityLongQueue(4)

    private val topTask = atomic<Long>(Long.MIN_VALUE)

    override val top: Long get() = topTask.value

    @Synchronized
    fun add(task: Long) {
        _size.incrementAndGet()
        queue.insert(task)
        topTask.value = queue.peek()
    }

    @Synchronized
    override fun steal(): List<Long> {
        // TODO: Do we need to steal all elements from the global queue?
        // TODO: I would steal 1 element instead.
        val result = mutableListOf<Long>()

        for ((polled, _) in (0 until stealSize).withIndex()) {
            val element = queue.poll()

            if (element == Long.MIN_VALUE) {
                _size.addAndGet(-polled)
                return result
            }

            result.add(element)
        }

        return result
    }
}