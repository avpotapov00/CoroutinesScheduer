package org.jetbrains.kotlin.number.smq.heap

import kotlinx.atomicfu.atomic

class GlobalHeapWithStealingBufferIntQueue(
    private val stealSize: Int
): StealingIntQueue {

    val size: Int get() = _size.value

    private val _size = atomic(0)

    private val queue = PriorityIntQueue(4)

    private val topTask = atomic<Int?>(null)

    override val top: Int? get() = topTask.value

    @Synchronized
    fun add(task: Int) {
        _size.incrementAndGet()
        queue.insert(task)
        topTask.value = queue.peek()
    }

    @Synchronized
    override fun steal(): List<Int> {
        // TODO: Do we need to steal all elements from the global queue?
        // TODO: I would steal 1 element instead.
        val result = mutableListOf<Int>()

        for ((polled, _) in (0 until stealSize).withIndex()) {
            val element = queue.poll()

            if (element == null) {
                _size.addAndGet(-polled)
                return result
            }

            result.add(element)
        }

        return result
    }
}