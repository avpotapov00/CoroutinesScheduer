package org.jetbrains.kotlin.smq

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

class StealingBuffer<E>(
    maxSize: Int
) {

    private val array = AtomicReferenceArray<E>(maxSize)

    private val size = AtomicInteger(0)

    private val stealingSize = maxSize

    fun first(): E? {
        return array.get(0)
    }

    fun read(): List<E> {
        val result = mutableListOf<E>()

        for (index in 0 until stealingSize) {
            val task = array.get(index) ?: return result
            result.add(task)
        }

        return result
    }

    fun clear() { // stolen = true
        for (index in 0 until array.length()) {
            array.set(index, null)
        }
        size.set(0)
    }

    fun add(task: E) { // stolen = true
        array.set(size.getAndIncrement(), task)
    }


}