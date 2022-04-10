package org.jetbrains.kotlin.number.smq.heap

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * @author Потапов Александр
 * @since 04.04.2022
 */
internal class HeapWithStealingBufferLongQueueTest {

    @Test
    fun `should process elements`() {
        val queue = HeapWithStealingBufferLongQueue(10)

        (0L until 100L).forEachIndexed { index, i ->
            queue.addLocal(i)
            println(queue.size)
        }

        println("Before 1: ${queue.size}")
        val elements = queue.steal()
        println(elements)
        println("After 1: ${queue.size}")

        queue.addLocal(200L)

        println("Before 2: ${queue.size}")
        val nextElements = queue.steal()
        println(nextElements)
        println("After 2: ${queue.size}")
    }


}