package org.jetbrains.kotlin.generic.smq.heap

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import java.util.*
import kotlin.math.min

@SuppressWarnings("UNCHECKED_CAST")
class HeapWithStealingBufferQueue<E : Comparable<E>>(
    var stealSize: Int
) : StealingQueue<E> {

    private val q = PriorityQueue<E>(4)

    private val _size = atomic(0)

    private var nextStealSize = stealSize

    var filledTimes = 0

    fun setNextStealSize(next: Int) {
        nextStealSize = next
    }

    val size: Int
        get() {
            // Если украли
            if (state.value and bit != 0) {
                return _size.value
            }
            return _size.value + stealSize
        }

    @Synchronized // we use synchronize here for the lock elision optimization
    fun extractTop(): E? {
        val value = q.poll()
        if (value != null) {
            _size.decrementAndGet()
        }
        return value
    }

    @Synchronized // we use synchronize here for the lock elision optimization
    fun localTop(): E? {
        return q.peek()
    }

    @Synchronized
    fun add(task: E) {
        _size.incrementAndGet()
        q.insert(task)
    }

    fun getSecondTopLocal(): E? {
        if (state.value and bit != 0) fillBuffer()

        return getSecondTopLocalFromQueue()
    }

    @Synchronized
    private fun getSecondTopLocalFromQueue(): E? {
        return q.getSecondTop()
    }

    private val bit = 1 shl 20

    private val reverseBit = bit - 1

    private val state: AtomicInt = atomic(0 or bit)

    private val array = arrayOfNulls<Any>(1024)

    private val bufferSize = atomic(0) // TODO: do you need it?

    fun addLocal(task: E) {
        add(task)
        if (state.value and bit != 0) fillBuffer()
    }

    fun extractTopLocal(): E? {
        if (state.value and bit != 0) fillBuffer()
        return extractTop()
    }

    fun getTopLocal(): E? {
        if (state.value and bit != 0) fillBuffer()
        return localTop()
    }

    override val top: E?
        get() {
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

    override fun steal(data: MutableList<E>) {
        while (true) {
            data.clear()

            val currentState = state.value
            if (currentState and bit != 0) {
                data.clear()
                return
            }

            readFromBuffer(data)
            //                                              without bit                flag = true
            if (!state.compareAndSet(currentState, (state.value and reverseBit) or bit)) {
                continue
            }
            return
        }
    }

    private fun readFromBuffer(data: MutableList<E>) {
        for (index in 0 until stealSize) {
            val task = array[index] ?: return
            data.add(task as E)
        }
    }

    override fun steal(data: Array<E?>) {
        while (true) {
            val currentState = state.value
            if (currentState and bit != 0) {
                data[0] = null
                return
            }

            readFromBuffer(data)
            //                                              without bit                flag = true
            if (!state.compareAndSet(currentState, (state.value and reverseBit) or bit)) {
                continue
            }
            return
        }
    }

    private fun readFromBuffer(data: Array<E?>) {
        for (index in 0 until stealSize) {
            val task = array[index]
            if (task == null) {
                data[index] = null
                return
            }
            data[index] = task as E
        }
    }

    private fun fillBufferAggressive() { // stolen = true
        // Если у нас самих недостаточно элементов, то не заполняем буффер
        if (_size.value < stealSize * 2) return

        clearBuffer()

        for (i in 0 until stealSize) {
            val task = extractTop() ?: break
            addToBuffer(task)
        }
        state.value = ((state.value and reverseBit) + 1) and reverseBit
    }

    private fun fillBuffer() { // stolen = true
        // Если у нас самих недостаточно элементов, то не заполняем буффер
        if (stealSize != nextStealSize) {
            stealSize = nextStealSize
        }

        val queueSize = _size.value
        val fillingSize = min(stealSize, queueSize / 2)
        if (fillingSize == 0) return

        clearBuffer()

        var count = 0

        for (i in 0 until fillingSize) {
            val task = extractTop() ?: break
            count++
            addToBuffer(task)
        }
        filledTimes++

        state.value = ((state.value and reverseBit) + 1) and reverseBit
    }

    private fun fillBufferBase() { // stolen = true
        if (_size.value == 0) return
        clearBuffer()

        for (i in 0 until stealSize) {
            val task = extractTop() ?: break
            addToBuffer(task)
        }
        state.value = ((state.value and reverseBit) + 1) and reverseBit
    }

    private fun firstFromBuffer(): E? {
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
