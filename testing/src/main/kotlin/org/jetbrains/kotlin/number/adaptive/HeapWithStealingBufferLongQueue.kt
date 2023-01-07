package org.jetbrains.kotlin.number.adaptive

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.number.smq.heap.PriorityLongQueue
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue
import java.util.*
import kotlin.math.min

class AdaptiveHeapWithStealingBufferLongQueue(
    var stealSize: Int
) : StealingLongQueue {

    private val q = PriorityLongQueue(4)

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
    fun extractTop(): Long {
        val value = q.poll()
        if (value != Long.MIN_VALUE) {
            _size.decrementAndGet()
        }
        return value
    }

    @Synchronized // we use synchronize here for the lock elision optimization
    fun localTop(): Long {
        return q.peek()
    }

    @Synchronized
    fun add(task: Long) {
        _size.incrementAndGet()
        q.insert(task)
    }

    private val bit = 1 shl 20

    private val reverseBit = bit - 1

    private val state: AtomicInt = atomic(0 or bit)

    private val array = LongArray(1024)

    private val bufferSize = atomic(0) // TODO: do you need it?

    fun addLocal(task: Long) {
        add(task)
        if (state.value and bit != 0) fillBuffer()
    }

    fun extractTopLocal(): Long {
        if (state.value and bit != 0) fillBuffer()
        return extractTop()
    }

    fun getTopLocal(): Long {
        if (state.value and bit != 0) fillBuffer()
        return localTop()
    }

    fun getSecondTopLocal(): Long {
        if (state.value and bit != 0) fillBuffer()

        return getSecondTopLocalFromQueue()
    }

    @Synchronized
    private fun getSecondTopLocalFromQueue(): Long {
        return q.getSecondTop()
    }

    override val top: Long
        get() {
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

    override fun steal(data: MutableList<Long>) {
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

    private fun readFromBuffer(data: MutableList<Long>) {
        for (index in 0 until stealSize) {
            val task = array[index]
            if (task == Long.MIN_VALUE) {
                return
            }
            data.add(task)
        }
    }

    override fun steal(data: LongArray) {
        while (true) {
            val currentState = state.value
            if (currentState and bit != 0) {
                data[0] = Long.MIN_VALUE
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

    private fun readFromBuffer(data: LongArray) {
        for (index in 0 until stealSize) {
            val task = array[index]
            if (task == Long.MIN_VALUE) {
                data[index] = Long.MIN_VALUE
                return
            }
            data[index] = task
        }
    }

    private fun fillBufferAggressive() { // stolen = true
        // Если у нас самих недостаточно элементов, то не заполняем буффер
        if (_size.value < stealSize * 2) return

        clearBuffer()

        for (i in 0 until stealSize) {
            val task = extractTop()
            if (task == Long.MIN_VALUE) break
            addToBuffer(task)
        }
        state.value = ((state.value and reverseBit) + 1) and reverseBit
    }

    var bufferSizeSum: Int = 0

    var bufferFilledCount: Int = 0

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
            val task = extractTop()
            if (task == Long.MIN_VALUE) break
            count++
            addToBuffer(task)
        }
        filledTimes++

        state.value = ((state.value and reverseBit) + 1) and reverseBit
        bufferSizeSum += fillingSize
        bufferFilledCount++
    }

    fun averageBufferSize(): Double = bufferSizeSum / bufferFilledCount.toDouble()

    fun resetAverageBufferSizeMetrics() {
        bufferSizeSum = 0
        bufferFilledCount = 0
    }

    private fun fillBufferBase() { // stolen = true
        if (_size.value == 0) return
        clearBuffer()

        for (i in 0 until stealSize) {
            val task = extractTop()
            if (task == Long.MIN_VALUE) break
            addToBuffer(task)
        }
        state.value = ((state.value and reverseBit) + 1) and reverseBit
    }

    private fun firstFromBuffer(): Long {
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
