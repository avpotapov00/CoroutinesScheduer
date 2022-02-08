package org.jetbrains.kotlin.smq

import org.jetbrains.kotlin.smq.heap.GlobalHeapWithStealingBufferQueue
import org.jetbrains.kotlin.smq.heap.HeapWithStealingBufferQueue
import org.jetbrains.kotlin.smq.heap.StealingQueue
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

open class StealingMultiQueue<E : Comparable<E>>(
    stealSize: Int,
    private val pSteal: Double,
    threads: Int
) {

    private val globalQueue = GlobalHeapWithStealingBufferQueue<E>(stealSize)

    private val queues = Array(threads) { HeapWithStealingBufferQueue<E>(stealSize) }

    private val stolenTasks = ThreadLocal.withInitial { ArrayDeque<E>(stealSize) }

    // your size + the size of the buffer for theft + the size of the global queue
    fun size() = queues[currThread()].size + stolenTasks.get().size + globalQueue.size

    fun insertGlobal(task: E) {
        globalQueue.add(task)
    }

    fun insert(task: E) {
        queues[currThread()].addLocal(task)
    }

    fun delete(): E? {
        // Do we have previously stolen tasks ?
        if (stolenTasks.get().isNotEmpty()) {
            return stolenTasks.get().removeFirst()
        }
        // Should we steal ?
        if (shouldSteal()) {
            val task = trySteal()
            if (task != null) {
                return task
            }
        }
        // Try to retrieve the top task
        // from the thread - local queue
        val task = queues[currThread()].extractTopLocal()
        if (task != null) {
            return task
        }
        // The local queue is empty , try to steal
        return trySteal()
    }

    fun stealAndDeleteFromGlobal(): E? {
        val stolen = globalQueue.steal()
        if (stolen.isEmpty()) return null // failed
        // Return the first task and add the others
        // to the thread - local buffer of stolen ones

        for (i in 1 until stolen.size) {
            stolenTasks.get().add(stolen[i])
        }
        return stolen[0]
    }

    private fun shouldSteal() = ThreadLocalRandom.current().nextDouble() < pSteal

    private fun trySteal(): E? {
        // Choose a random queue and check whether
        // its top task has higher priority

        val otherQueue = getQueueToSteal()
        val ourTop = queues[currThread()].top()
        val otherTop = otherQueue.top()

        if (ourTop == null || otherTop == null || otherTop === ourTop || otherTop < ourTop) {
            // Try to steal a better task !
            val stolen = otherQueue.steal()
            if (stolen.isEmpty()) return null // failed
            // Return the first task and add the others
            // to the thread - local buffer of stolen ones
            stolenTasks.get().addAll(stolen.subList(1, stolen.size))
            return stolen[0]
        }

        return null
    }

    private fun getQueueToSteal(): StealingQueue<E> {
        val index = ThreadLocalRandom.current().nextInt(0, queues.size + 1)
        return if (index == queues.size) globalQueue else queues[index]
    }

    private fun currThread(): Int = (Thread.currentThread() as IndexedThread).index
}

