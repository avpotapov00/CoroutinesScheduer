package org.jetbrains.kotlin.number.smq.heap

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReferenceArray

class HeapWithStealingBufferIntQueue(
    private val stealSize: Int
) : LocalQueue(), StealingIntQueue {

    private val bit = 1 shl 20

    private val reverseBit = bit - 1

    private val state: AtomicInt = atomic(0)

    private val array = AtomicReferenceArray<Int>(stealSize) // TODO: use plain array

    private val bufferSize = atomic(0) // TODO: do you need it?

    fun addLocal(task: Int) {
        add(task)
        if (state.value and bit != 0) fillBuffer()
    }

    fun extractTopLocal(): Int? {
        if (state.value and bit != 0) fillBuffer()
        return extractTop()
    }

    override val top: Int? get() {
        while (true) {
            val currentState = state.value
            if (currentState and bit != 0) return null

            val top = firstFromBuffer()
            if ((currentState and reverseBit) != (state.value and reverseBit)) continue

            return top
        }
    }

    override fun steal(): List<Int> {
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

    private fun firstFromBuffer(): Int? {
        // TODO: Let's do not use expensive AtomicReferenceArray, use plain array instead.
        return array.get(0)
    }

    private fun readFromBuffer(): List<Int> {
        val result = mutableListOf<Int>()

        for (index in 0 until stealSize) {
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

    private fun addToBuffer(task: Int) { // stolen = true
        array.set(bufferSize.getAndIncrement(), task)
    }

}

open class LocalQueue {

    private val q = PriorityIntQueue(4)

    private val _size = atomic(0)

    @Synchronized // we use synchronize here for the lock elision optimization
    fun extractTop(): Int? {
        return q.poll()?.also {
            _size.decrementAndGet()
        }
    }

    @Synchronized
    fun add(task: Int) {
        _size.decrementAndGet()
        q.insert(task)
    }

    val size: Int get() = _size.value
}
