package org.jetbrains.kotlin.number.heap

import org.jetbrains.kotlin.number.smq.heap.PriorityLongQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest

internal class PriorityIntQueueTest {

    @RepeatedTest(value = 30)
    fun `int priority queue test`() {
        val queue = PriorityLongQueue(arity = 4)

        (0 until 100).shuffled().forEach { queue.insert(it.toLong()) }

        val result = generateSequence { queue.poll() }.toList()

        assertEquals((0 until 100).map { it.toLong() }, result)
    }

}