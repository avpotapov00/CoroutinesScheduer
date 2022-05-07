package org.jetbrains.kotlin.number.adaptive

import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.number.smq.heap.PriorityLongQueue
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue

class AdaptiveGlobalHeapWithStealingBufferLongQueue(
    private val stealSize: Int
) : StealingLongQueue {

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

    @Synchronized
    override fun steal(data: MutableList<Long>) {
        data.clear()
        for (polled in (0 until stealSize)) {
            val element = queue.poll()

            if (element == Long.MIN_VALUE) {
                _size.addAndGet(-polled)
                return
            }
            data.add(element)
        }
    }

    @Synchronized
    override fun steal(data: LongArray) {
        // TODO: Do we need to steal all elements from the global queue?
        // TODO: I would steal 1 element instead.

        for (polled in (0 until stealSize)) {
            val element = queue.poll()

            if (element == Long.MIN_VALUE) {
                _size.addAndGet(-polled)
                data[polled] = Long.MIN_VALUE
                return
            }

            data[polled] = element
        }
    }

}