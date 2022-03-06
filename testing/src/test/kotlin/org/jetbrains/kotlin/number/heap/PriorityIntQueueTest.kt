package org.jetbrains.kotlin.number.heap

import org.jetbrains.kotlin.number.smq.heap.PriorityLongQueue
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.zip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class PriorityIntQueueTest {

    @RepeatedTest(30)
    fun `int priority queue test`() {
        val queue = PriorityLongQueue(arity = 4)

        val random = Random(System.currentTimeMillis())

        (0 until 100).shuffled().forEach { queue.insert(it.zip(random.nextInt() % 10000)) }

        val result = generateSequence {
            val value = queue.poll()
            if (value == Long.MIN_VALUE) null else value
        }.map { it.firstFromLong }.toList()

        assertEquals((0 until 100).map { it.toLong() }, result)
    }

}