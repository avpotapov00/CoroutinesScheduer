package org.jetbrains.kotlin.generic.smq

import org.jetbrains.kotlin.generic.smq.heap.GlobalHeapWithStealingBufferQueue
import org.jetbrains.kotlin.generic.smq.heap.HeapWithStealingBufferQueue
import org.jetbrains.kotlin.generic.smq.heap.StealingQueue
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

open class StealingMultiQueue<E : Comparable<E>>(
    stealSize: Int,
    private val pSteal: Double,
    threads: Int
) {

    val globalQueue = GlobalHeapWithStealingBufferQueue<E>(stealSize)

    val queues = Array(threads) { HeapWithStealingBufferQueue<E>(stealSize) }

    val stolenTasks: ThreadLocal<ArrayDeque<E>> = ThreadLocal.withInitial { ArrayDeque(stealSize) }

    // your size + the size of the buffer for theft + the size of the global queue
    fun size() = queues[currThread()].size + stolenTasks.get().size + globalQueue.size

    fun insertGlobal(task: E) {
        globalQueue.add(task)
    }

    fun stealAndDeleteFromGlobal(): E? {
        val queueToSteal = globalQueue

        return stealFromExactQueue(queueToSteal)
    }

    fun stealAndDeleteFromSelf(index: Int): E? {
        val queueToSteal = queues[index]

        return stealFromExactQueue(queueToSteal)
    }

    private fun stealFromExactQueue(queueToSteal: StealingQueue<E>): E? {
        val stolen = queueToSteal.steal()
        if (stolen.isEmpty()) return null // failed
        // Return the first task and add the others
        // to the thread - local buffer of stolen ones

        for (i in 1 until stolen.size) {
            stolenTasks.get().add(stolen[i])
        }
        return stolen[0]
    }

    fun shouldSteal() = ThreadLocalRandom.current().nextDouble() < pSteal

    fun currThread(): Int = (Thread.currentThread() as IndexedThread).index
}
