package org.jetbrains.kotlin.number.smq.heap

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import java.util.Arrays

class HeapWithStealingBufferIntQueue(
    private val stealSize: Int
) : LocalQueue(), StealingIntQueue {

    private val bit = 1 shl 20

    private val reverseBit = bit - 1

    private val state: AtomicInt = atomic(0)

    private val array = LongArray(stealSize)

    private val bufferSize = atomic(0) // TODO: do you need it?

    fun addLocal(task: Long) {
        add(task)
        if (state.value and bit != 0) fillBuffer()
    }

    fun extractTopLocal(): Long {
        if (state.value and bit != 0) fillBuffer()
        return extractTop()
    }

    override val top: Long get() {
        while (true) {
            val currentState = state.value
            if (currentState and bit != 0) return Long.MIN_VALUE

            val top = firstFromBuffer()
            if ((currentState and reverseBit) != (state.value and reverseBit)) continue

            return top
        }
    }

    override fun steal(): List<Long> {
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

    private fun firstFromBuffer(): Long {
        // TODO: Let's do not use expensive AtomicReferenceArray, use plain array instead.
        return array[0]
    }

    private fun readFromBuffer(): List<Long> {
        val result = mutableListOf<Long>()

        for (index in 0 until stealSize) {
            val task = array[index]
            if (task == Long.MIN_VALUE) {
                return result
            }
            result.add(task)
        }

        return result
    }

    private fun clearBuffer() { // stolen = true
        Arrays.fill(array, Long.MIN_VALUE)
        bufferSize.value = 0
    }

    private fun addToBuffer(task: Long) { // stolen = true
        array[bufferSize.getAndIncrement()] = task
    }

}

open class LocalQueue {

    private val q = PriorityLongQueue(4)

    private val _size = atomic(0)

    @Synchronized // we use synchronize here for the lock elision optimization
    fun extractTop(): Long {
        val value = q.poll()
        if (value != Long.MIN_VALUE) {
            _size.decrementAndGet()
        }
        return value
    }

    @Synchronized
    fun add(task: Long) {
        _size.decrementAndGet()
        q.insert(task)
    }

    val size: Int get() = _size.value
}
