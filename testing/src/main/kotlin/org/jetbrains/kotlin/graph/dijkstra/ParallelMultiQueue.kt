package org.jetbrains.kotlin.graph.dijkstra

import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

class ParallelMultiQueue<T>(n: Int, private val comparator: Comparator<in T>) {

    val queues: ArrayList<PriorityQueue<T>> = arrayListOf()

    val locks: ArrayList<ReentrantLock> = arrayListOf()

    init {
        for (i in 1..(2 * n)) {
            queues.add(PriorityQueue<T>(n, comparator))
            locks.add(ReentrantLock(false))
        }
    }

    fun add(node: T) {
        while (true) {
            val index = Random.nextInt(0, locks.size)
            if (locks[index].tryLock()) {
                queues[index].add(node)
                locks[index].unlock()
                return
            }
        }
    }

    fun poll(): T? {
        val first: T?
        val indexFirst = Random.nextInt(locks.size)
        var minNode: T? = null

        if (locks[indexFirst].tryLock()) {
            first = queues[indexFirst].peek()
            val indexSecond = Random.nextInt(locks.size)

            if (locks[indexSecond].tryLock()) {
                val second = queues[indexSecond].peek()

                when {
                    first == null -> {
                        queues[indexSecond].poll()
                        minNode = second
                    }
                    second == null -> {
                        queues[indexFirst].poll()
                        minNode = first
                    }
                    else -> {
                        if (comparator.compare(first, second) < 0) {
                            minNode = first
                            queues[indexFirst].poll()
                        } else {
                            minNode = second
                            queues[indexSecond].poll()
                        }
                    }
                }
                locks[indexSecond].unlock()

            } else {
                minNode = first
                queues[indexFirst].poll()

            }
            locks[indexFirst].unlock()

        }
        return minNode
    }


}