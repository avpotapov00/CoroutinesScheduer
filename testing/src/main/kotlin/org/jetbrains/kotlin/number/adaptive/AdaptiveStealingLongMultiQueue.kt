package org.jetbrains.kotlin.number.adaptive

import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.number.smq.heap.GlobalHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.HeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue
import org.jetbrains.kotlin.util.firstFromLong
import java.util.concurrent.ThreadLocalRandom

open class AdaptiveStealingLongMultiQueue(
    stealSize: Int,
    private val pSteal: Double,
    threads: Int
) {

    val globalQueue = AdaptiveGlobalHeapWithStealingBufferLongQueue(stealSize)

    val queues = Array(threads) { AdaptiveHeapWithStealingBufferLongQueue(stealSize) }

    val stolenTasks = ThreadLocal.withInitial { ArrayDeque<Long>(stealSize) }

    // your size + the size of the buffer for theft + the size of the global queue
    fun size() = queues[currThread()].size + stolenTasks.get().size + globalQueue.size

    fun insertGlobal(task: Long) {
        globalQueue.add(task)
    }

    fun stealAndDeleteFromGlobal(): Long {
        val queueToSteal = globalQueue

        return stealFromExactQueue(queueToSteal)
    }

    fun stealAndDeleteFromSelf(index: Int): Long {
        val queueToSteal = queues[index]

        return stealFromExactQueue(queueToSteal);
    }

    private fun stealFromExactQueue(queueToSteal: StealingLongQueue): Long {
        val stolen = queueToSteal.steal()
        if (stolen.isEmpty()) return Long.MIN_VALUE // failed
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



