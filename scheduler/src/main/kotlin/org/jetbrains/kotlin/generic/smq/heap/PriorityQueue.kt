package org.jetbrains.kotlin.generic.smq.heap

import kotlin.math.min

@Suppress("UNCHECKED_CAST")
class PriorityQueue<T : Comparable<T>>(
    private val arity: Int,
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
) {
    private var maxIndex: Int = -1
    private var queue: Array<Any?> = arrayOfNulls(initialCapacity)


    fun insert(element: T) {
        grow()
        swim(element, maxIndex)
    }

    fun poll(): T? {
        if (maxIndex <= -1) {
            return null
        }
        val root = queue[0] as T
        val last = queue[maxIndex] as T
        queue[maxIndex] = null
        maxIndex--
        if (maxIndex > -1) sink(last)
        return root
    }

    fun peek(): T? {
        return if (maxIndex == -1) {
            null
        } else {
            queue[0] as T
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

        var minChildValue = queue[minChild] as T

        for (k in 1 until arity) {
            val otherChild = getChild(i, k)
            if (otherChild <= -1) break
            val otherChildValue = queue[otherChild] as T
            if (otherChildValue < minChildValue) {
                minChild = otherChild
                minChildValue = otherChildValue
            }
        }

        return minChild
    }

    private fun sink(value: Any) {
        var index = 0
        var minChild = getMinChild(index)

        while (minChild > -1 && (queue[minChild] as T) < (value as T)) {
            queue[index] = queue[minChild]
            index = minChild
            minChild = getMinChild(index)
        }

        queue[index] = value
    }

    private fun swim(value: Any, fromIndex: Int) {
        var index = fromIndex
        var parent = getParent(index)

        while (parent > -1 && (queue[parent] as T) > (value as T)) {
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

    fun getSecondTop(): T? {
        if (maxIndex < 1) return null
        var min = queue[1] as T

        for (i in 2 .. min(maxIndex, 4)) {
            val next = queue[i] as T
            if (min > next) {
                min = next
            }
        }

        return min
    }

    companion object {
        private const val DEFAULT_INITIAL_CAPACITY = 10
    }
}