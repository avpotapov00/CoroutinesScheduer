package org.jetbrains.kotlin.number.heap

import kotlinx.coroutines.yield
import org.jetbrains.kotlin.number.smq.heap.PriorityLongQueue
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.secondFromLong
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

    @RepeatedTest(30)
    fun `queue test`() {
        val queue = PriorityLongQueue(arity = 4)

        (1 until 101).shuffled().forEach { queue.add(it, it) }

        var currentSecondTop = queue.getSecondTop()
        for (i in 0 until 99) {
            val top = queue.poll()
            assertEquals(i + 1, top.firstFromLong.toInt())
            assertEquals(currentSecondTop, queue.peek())
            val secondTop = queue.getSecondTop()

            if (secondTop == Long.MIN_VALUE) break
            currentSecondTop = secondTop
        }

        assertEquals(queue.poll(), currentSecondTop)
        assertEquals(Long.MIN_VALUE, queue.poll())
    }

    fun PriorityLongQueue.add(value: Int, priority: Int) {
        insert(value.zip(priority))
    }

}