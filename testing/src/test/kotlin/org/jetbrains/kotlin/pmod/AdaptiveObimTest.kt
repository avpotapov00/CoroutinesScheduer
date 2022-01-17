package org.jetbrains.kotlin.pmod

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals

internal class AdaptiveObimTest {

    @RepeatedTest(30)
    fun `should work as queue with same priority`() = withTestThread {
        val values = (0 until 100).shuffled()

        val pmod = AdaptiveObim<Int>(1)

        values.forEach { pmod.push(it, 1) }

        val results = generateSequence { pmod.pop() }.toSet()

        assertEquals(values.toSet(), results)
    }

    @RepeatedTest(30)
    fun `should work as queue with many priorities`() = withTestThread {
        val values = (0 until 100).shuffled()
        val random = Random(System.currentTimeMillis())

        val pmod = AdaptiveObim<Int>(1)

        values.forEach { pmod.push(it, abs(random.nextInt()) % 100) }

        val results = generateSequence { pmod.pop() }.toSet()

        assertEquals(values.toSet(), results)
    }

    @Test
    fun `should process data from different threads`() {
        val values = (0 until 100).toList()

        val pmod = AdaptiveObim<Int>(2)

        withTestThread(index = 0) {
            val random = Random(System.currentTimeMillis())
            values.forEach { pmod.push(it, abs(random.nextInt()) % 100) }
        }
        val result = ConcurrentHashMap.newKeySet<Int>()

        withTestThread(index = 1) {
            generateSequence { pmod.pop() }.forEach { result.add(it) }
        }

        assertEquals(values.toSet(), result)
    }


}