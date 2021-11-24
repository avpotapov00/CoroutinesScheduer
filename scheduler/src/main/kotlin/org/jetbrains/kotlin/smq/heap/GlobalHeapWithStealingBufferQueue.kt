package org.jetbrains.kotlin.smq.heap

import kotlinx.atomicfu.atomic

class GlobalHeapWithStealingBufferQueue<E : Comparable<E>>(
    private val stealSize: Int
): Stealable<E> {

    val size: Int get() = counter.value

    private val counter = atomic(0)

    private val queue = PriorityQueue<E>(4)

    private val buffer = atomic<E?>(null)

    override fun top(): E? = buffer.value

    @Synchronized
    fun add(task: E) {
        counter.incrementAndGet()
        queue.insert(task)
        buffer.value = queue.peek()
    }

    @Synchronized
    override fun steal(): List<E> {
        val result = mutableListOf<E>()

        for ((polled, _) in (0 until stealSize).withIndex()) {
            val element = queue.poll()

            if (element == null) {
                counter.addAndGet(-polled)
                return result
            }

            result.add(element)
        }

        return result
    }

}

