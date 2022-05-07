package org.jetbrains.kotlin.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * @author Потапов Александр
 * @since 09.04.2022
 */
internal class NumberUtilsKtTest {


    @Test
    fun `binary search test 1`() {
        val values = wrapIts(1, 2, 4, 5)
        val result = indexedBinarySearch(values, 3.zip(0))
        assertEquals(2, result)
    }

    @Test
    fun `binary search test 2`() {
        val values = wrapIts(1, 2, 3, 4, 5)
        val result = indexedBinarySearch(values, 3.zip(0))
        assertEquals(2, result)
    }

    @Test
    fun `binary search test 3`() {
        val values = wrapIts(1, 2, 2, 4, 5)
        val result = indexedBinarySearch(values, 3.zip(0))
        assertEquals(3, result)
    }

    @Test
    fun `binary search test 4`() {
        val values = wrapIts(1, 2, 2, 3, 4, 5)
        val result = indexedBinarySearch(values, 3.zip(0))
        assertEquals(3, result)
    }

    @Test
    fun `binary search test 5`() {
        val values = wrapIts(1, 2, 2, 3, 3, 3, 4, 5)
        val result = indexedBinarySearch(values, 3.zip(0))
        assertEquals(3, result)
    }

    @Test
    fun `binary search test 6`() {
        val values = wrapIts(0, 1, 2, 3, 4, 5)
        val result = indexedBinarySearch(values, 0.zip(0))
        assertEquals(0, result)
    }

    @Test
    fun `triple to long test`() {
        val first = 283_791
        val second = 8392
        val third = 38922

        val triple = tripleToLong(first, second, third)

        assertEquals(first, triple.firstFromTriple())
        assertEquals(second, triple.secondFromTriple())
        assertEquals(third, triple.thirdFromTriple())
    }

    private fun wrapIts(vararg values: Int): List<Long> = values.map { it.zip(0) }

}