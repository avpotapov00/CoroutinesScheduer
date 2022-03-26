package org.jetbrains.kotlin.number.smq

import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.number.smq.heap.GlobalHeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.HeapWithStealingBufferLongQueue
import org.jetbrains.kotlin.number.smq.heap.StealingLongQueue
import java.util.concurrent.ThreadLocalRandom

open class StealingLongMultiQueue (
    stealSize: Int,
    private val pSteal: Double,
    threads: Int
) {

    private val globalQueue = GlobalHeapWithStealingBufferLongQueue(stealSize)

    private val queues = Array(threads) { HeapWithStealingBufferLongQueue(stealSize) }

    private val stolenTasks = ThreadLocal.withInitial { ArrayDeque<Long>(stealSize) }

    val successSteals = atomic(0)

    val retrievals = atomic(0)

    // your size + the size of the buffer for theft + the size of the global queue
    fun size() = queues[currThread()].size + stolenTasks.get().size + globalQueue.size

    fun insertGlobal(task: Long) {
        globalQueue.add(task)
    }

    fun insert(task: Long) {
        queues[currThread()].addLocal(task)
    }

    fun delete(): Long {
        // Do we have previously stolen tasks ?
        if (stolenTasks.get().isNotEmpty()) {
            return stolenTasks.get().removeFirst()
        }
        // Should we steal ?
        if (shouldSteal()) {
            val task = trySteal()
            if (task != Long.MIN_VALUE) {
                return task
            }
        }
        // Try to retrieve the top task
        // from the thread - local queue
        val task = queues[currThread()].extractTopLocal()
        if (task != Long.MIN_VALUE) {
            return task
        }
        // The local queue is empty , try to steal
        return trySteal()
    }

    fun stealAndDeleteFromGlobal(): Long {
        val queueToSteal = globalQueue

        return stealFromExactQueue(queueToSteal)
    }

    fun stealAndDeleteFromSelf(): Long {
        val queueToSteal = queues[currThread()]

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

    private fun shouldSteal() = ThreadLocalRandom.current().nextDouble() < pSteal

    private fun trySteal(): Long {
        retrievals.incrementAndGet()
        // Choose a random queue and check whether
        // its top task has higher priority

        val otherQueue = getQueueToSteal()
        val ourTop = queues[currThread()].top
        val otherTop = otherQueue.top

        if (ourTop == Long.MIN_VALUE || otherTop == Long.MIN_VALUE || otherTop == ourTop || otherTop < ourTop) {
            // Try to steal a better task !
            val stolen = otherQueue.steal()
            if (stolen.isEmpty()) return Long.MIN_VALUE // failed
            if (ourTop != Long.MIN_VALUE) {
                successSteals.incrementAndGet()
            }
            // Return the first task and add the others
            // to the thread - local buffer of stolen ones
            stolenTasks.get().addAll(stolen.subList(1, stolen.size))
            return stolen[0]
        }

        return Long.MIN_VALUE
    }

    private fun getQueueToSteal(): StealingLongQueue {
        val index = ThreadLocalRandom.current().nextInt(0, queues.size + 1)

        return if (index == queues.size) globalQueue else queues[index]
    }

    private fun currThread(): Int = (Thread.currentThread() as IndexedThread).index
}

