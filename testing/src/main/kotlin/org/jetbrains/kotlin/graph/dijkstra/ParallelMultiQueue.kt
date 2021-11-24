package org.jetbrains.kotlin.graph.dijkstra

import org.jetbrains.kotlin.graph.util.nodes.Node
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

class ParallelMultiQueue(n: Int, comparator: Comparator<Node>) {

    private val queues: ArrayList<PriorityQueue<Node>> = arrayListOf();

    private val locks: ArrayList<ReentrantLock> = arrayListOf();

    init {
        for (i in 1..(2 * n)) {
            queues.add(PriorityQueue(n, comparator))
            locks.add(ReentrantLock(false))
        }
    }

    fun add(node: Node) {
        while (true) {
            val index = Random.nextInt(0, locks.size)
            if (locks[index].tryLock()) {
                queues[index].add(node)
                locks[index].unlock()
                return
            }
        }
    }

    fun poll(): Node? {
        val first: Node?
        val indexFirst = Random.nextInt(locks.size)
        var minNode: Node? = null

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
                        if (first.distance < second.distance) {
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