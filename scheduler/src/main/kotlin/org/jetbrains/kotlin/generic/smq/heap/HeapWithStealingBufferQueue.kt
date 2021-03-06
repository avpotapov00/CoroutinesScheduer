package org.jetbrains.kotlin.generic.smq.heap

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray

@SuppressWarnings("UNCHECKED_CAST")
class HeapWithStealingBufferQueue<E : Comparable<E>>(
    private val stealSize: Int
) : LocalQueue<E>(), StealingQueue<E> {

    private val bit = 1 shl 20

    private val reverseBit = bit - 1

    private val state: AtomicInt = atomic(0)

    private val array = arrayOfNulls<Any>(stealSize) // TODO: use plain array

    private val bufferSize = atomic(0) // TODO: do you need it?

    fun addLocal(task: E) {
        add(task)
        if (state.value and bit != 0) fillBuffer()
    }

    fun extractTopLocal(): E? {
        if (state.value and bit != 0) fillBuffer()
        return extractTop()
    }

    override val top: E? get() {
        while (true) {
            val currentState = state.value
            if (currentState and bit != 0) return null

            val top = firstFromBuffer()
            if ((currentState and reverseBit) != (state.value and reverseBit)) continue

            return top
        }
    }

    override fun steal(): List<E> {
        while (true) {
            val currentState = state.value
            if (currentState and bit != 0) return emptyList()

            val tasks = readFromBuffer()
            //                                              without bit                flag = true
            if (!state.compareAndSet(currentState, (state.value and reverseBit) or bit)) {
                continue
            }
            return tasks
        }
    }

    private fun fillBuffer() { // stolen = true
        clearBuffer()
        for (i in 0 until stealSize) {
            val task = extractTop() ?: break
            addToBuffer(task)
        }
        state.value = ((state.value and reverseBit) + 1) and reverseBit
    }

    private fun firstFromBuffer(): E? {
        // TODO: Let's do not use expensive AtomicReferenceArray, use plain array instead.
        return array[0] as E?
    }

    private fun readFromBuffer(): List<E> {
        val result = mutableListOf<E>()

        for (index in 0 until stealSize) {
            val task = array[index] ?: return result
            result.add(task as E)
        }

        return result
    }

    private fun clearBuffer() { // stolen = true
        Arrays.fill(array, null)
        bufferSize.value = 0
    }

    private fun addToBuffer(task: E) { // stolen = true
        array[bufferSize.getAndIncrement()] = task
    }

}

open class LocalQueue<E : Comparable<E>> {

    private val q = PriorityQueue<E>(4)

    private val _size = atomic(0)

    @Synchronized // we use synchronize here for the lock elision optimization
    fun extractTop(): E? {
        return q.poll()?.also {
            _size.decrementAndGet()
        }
    }

    @Synchronized
    fun add(task: E) {
        _size.incrementAndGet()
        q.insert(task)
    }

    val size: Int get() = _size.value
}
