package org.jetbrains.kotlin.smq.heap

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.smq.StealingBuffer

class HeapWithStealingBufferQueue<E : Comparable<E>>(
    private val stealSize: Int
): Stealable<E> {

    private val q = LocalQueue<E>()

    private var stealingBuffer = StealingBuffer<E>(stealSize)

    private val state: AtomicRef<Pair<Int, Boolean>> = atomic(0 to true)

    private val epoch get() = state.value.first

    private val stolen get() = state.value.second

    val size: Int get() = q.size

    fun addLocal(task: E) {
        q.add(task)
        if (stolen) fillBuffer()
    }

    fun extractTopLocal(): E? {
        if (stolen) fillBuffer()
        return q.extractTop()
    }

    override fun top(): E? {
        while (true) {
            val currentState = state.value
            if (currentState.stolen) return null

            val top = stealingBuffer.first()
            if (currentState.epoch != epoch) continue

            return top
        }
    }

    override fun steal(): List<E> {
        while (true) {
            val currentState = state.value
            if (currentState.stolen) return emptyList()

            val tasks = stealingBuffer.read()

            if (!state.compareAndSet(currentState, epoch to true)) {
                continue
            }

            return tasks
        }
    }

    private fun fillBuffer() { // stolen = true
        stealingBuffer.clear()
        for (i in 0 until stealSize) {
            val task = q.extractTop() ?: break
            stealingBuffer.add(task)
        }
        state.value = (epoch + 1) to false
    }

}

private val Pair<Int, Boolean>.stolen: Boolean get() = second

private val Pair<Int, Boolean>.epoch: Int get() = first

class LocalQueue<E : Comparable<E>> {

    private val q = PriorityQueue<E>(4)

    private val _size = atomic(0)

    @Synchronized
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
