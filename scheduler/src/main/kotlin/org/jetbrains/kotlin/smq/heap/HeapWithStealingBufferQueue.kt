package org.jetbrains.kotlin.smq.heap

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.smq.StealingBuffer
import java.util.concurrent.atomic.AtomicReferenceArray

class HeapWithStealingBufferQueue<E : Comparable<E>>(
    private val stealSize: Int
) : LocalQueue<E>(), StealingQueue<E> {

    private val bit = 1 shl 20

    private val reverseBit = bit - 1

    private val state: AtomicInt = atomic(0)

    private val array = AtomicReferenceArray<E>(stealSize) // TODO: use plain array

    private val bufferSize = atomic(0) // TODO: do you need it?

    private val stealingSize = stealSize // TODO: do you need it?

    fun addLocal(task: E) {
        add(task)
        if (state.value and bit != 0) fillBuffer()
    }

    fun extractTopLocal(): E? {
        if (state.value and bit != 0) fillBuffer()
        return extractTop()
    }

    // TODO: should the localQueue.top() access the stealing buffer?
    override fun top(): E? {
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
        return array.get(0)
    }

    private fun readFromBuffer(): List<E> {
        val result = mutableListOf<E>()

        for (index in 0 until stealingSize) {
            val task = array.get(index) ?: return result
            result.add(task)
        }

        return result
    }

    private fun clearBuffer() { // stolen = true
        // TODO: Arrays.fill()
        for (index in 0 until array.length()) {
            array.set(index, null)
        }
        bufferSize.value = 0
    }

    private fun addToBuffer(task: E) { // stolen = true
        array.set(bufferSize.getAndIncrement(), task)
    }

}

private val Pair<Int, Boolean>.stolen: Boolean get() = second

private val Pair<Int, Boolean>.epoch: Int get() = first

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
        _size.decrementAndGet()
        q.insert(task)
    }

    val size: Int get() = _size.value
}
