package org.jetbrains.kotlin.pmod

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AdaptiveObimTest {

    @RepeatedTest(30)
    fun `should work as queue with same priority`() {
        val values = (0 until 10_000).shuffled()
        val results = ConcurrentHashMap.newKeySet<Int>()

        withTestThread {
            val pmod = AdaptiveObim<Int>(1)

            values.forEach { pmod.push(it, 1) }

            generateSequence { pmod.pop() }.forEach { results.add(it) }
        }

        assertEquals(values.toSet(), results)
    }

    @RepeatedTest(30)
    fun `should work as queue with many priorities`() = withTestThread {
        val values = (0 until 1000).shuffled()
        val random = Random(System.currentTimeMillis())
        val results = ConcurrentHashMap.newKeySet<Int>()

        withTestThread {
            val pmod = AdaptiveObim<Int>(1)

            values.forEach { pmod.push(it, abs(random.nextInt())  % 1000) }

            generateSequence { pmod.pop() }.forEach { results.add(it) }
        }

        assertEquals(values.toSet(), results)
    }

    @RepeatedTest(30)
    fun `should process data from different threads`() {
        val values = (0 until 10000).shuffled()

        val pmod = AdaptiveObim<Int>(2)

        withTestThread(index = 0) {
            val random = Random(System.currentTimeMillis())
            values.forEach { pmod.push(it, abs(random.nextInt())  % 1000) }
        }
        val result = ConcurrentHashMap.newKeySet<Int>()

        withTestThread(index = 1) {
            generateSequence { pmod.pop() }.forEach { result.add(it) }
        }

        assertEquals(values.toSet(), result)
    }

    @RepeatedTest(30)
    fun `should process data twice`() {
        val values = (0 until 1000).toList()
        val random = Random(0)
        val results = ConcurrentHashMap.newKeySet<Int>()

        val results2 = ConcurrentHashMap.newKeySet<Int>()

        withTestThread {
            val pmod = AdaptiveObim<Int>(1, chunkSize = 64)

            values.forEach { pmod.push(it, abs(random.nextInt()) % 1000) }
            generateSequence { pmod.pop() }.forEach { results.add(it) }

            assertTrue { pmod.pop() == null }

            values.forEach { pmod.push(it, abs(random.nextInt())  % 1000) }
            generateSequence { pmod.pop() }.forEach { results2.add(it) }
        }

        assertEquals(values.toSet(), results)
        assertEquals(values.toSet(), results2)
    }


}