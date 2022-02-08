package org.jetbrains.kotlin.smq.heap

import kotlinx.atomicfu.atomic

class GlobalHeapWithStealingBufferQueue<E : Comparable<E>>(
    private val stealSize: Int
): StealingQueue<E> {

    val size: Int get() = _size.value

    private val _size = atomic(0)

    private val queue = PriorityQueue<E>(4)

    private val topTask = atomic<E?>(null)

    override fun top(): E? = topTask.value

    @Synchronized
    fun add(task: E) {
        _size.incrementAndGet()
        queue.insert(task)
        topTask.value = queue.peek()
    }

    @Synchronized
    override fun steal(): List<E> {
        // TODO: Do we need to steal all elements from the global queue?
        // TODO: I would steal 1 element instead.
        val result = mutableListOf<E>()

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

