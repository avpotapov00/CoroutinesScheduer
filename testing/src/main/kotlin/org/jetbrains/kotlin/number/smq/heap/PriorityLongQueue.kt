package org.jetbrains.kotlin.number.smq.heap

import org.jetbrains.kotlin.util.firstFromLong

class PriorityLongQueue(
    private val arity: Int,
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
) {
    private var maxIndex: Int = -1
    private var queue: LongArray = LongArray(initialCapacity)


    fun insert(element: Long) {
        grow()
        swim(element, maxIndex)
    }

    fun poll(): Long {
        if (maxIndex <= -1) {
            return Long.MIN_VALUE
        }
        val root = queue[0]
        val last = queue[maxIndex]
        queue[maxIndex] = 0
        maxIndex--
        if (maxIndex > -1) sink(last)
        return root
    }

    fun peek(): Long {
        return if (maxIndex == -1) {
            Long.MIN_VALUE
        } else {
            queue[0]
        }
    }


    private fun getParent(i: Int): Int {
        return if (i == 0) -1 else (i - 1) / arity
    }


    private fun getChild(i: Int, k: Int): Int {
        val index = arity * i + k + 1
        return if (index > maxIndex) -1 else index
    }


    private fun getMinChild(i: Int): Int {
        var minChild = getChild(i, 0)
        if (minChild <= -1) return -1

        var minChildValue = queue[minChild]

        for (k in 1 until arity) {
            val otherChild = getChild(i, k)
            if (otherChild <= -1) break
            val otherChildValue = queue[otherChild]
            if (otherChildValue.firstFromLong < minChildValue.firstFromLong) {
                minChild = otherChild
                minChildValue = otherChildValue
            }
        }

        return minChild
    }

    private fun sink(value: Long) {
        var index = 0
        var minChild = getMinChild(index)

        while (minChild > -1 && queue[minChild].firstFromLong < value.firstFromLong) {
            queue[index] = queue[minChild]
            index = minChild
            minChild = getMinChild(index)
        }

        queue[index] = value
    }

    private fun swim(value: Long, fromIndex: Int) {
        var index = fromIndex
        var parent = getParent(index)

        while (parent > -1 && queue[parent].firstFromLong > value.firstFromLong) {
            queue[index] = queue[parent]
            index = parent
            parent = getParent(index)
        }

        queue[index] = value
    }


    private fun grow() {
        maxIndex++
        if (maxIndex >= queue.size) {
            val newCapacity = (queue.size
                    + if (queue.size < 64) (queue.size + 1) else queue.size shr 1)
            queue = queue.copyOf(newCapacity)
        }
    }

    companion object {
        private const val DEFAULT_INITIAL_CAPACITY = 10
    }
}