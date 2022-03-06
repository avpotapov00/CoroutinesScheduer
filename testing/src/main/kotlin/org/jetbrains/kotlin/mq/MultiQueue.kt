package org.jetbrains.kotlin.mq

import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.number.smq.heap.PriorityLongQueue
import org.jetbrains.kotlin.util.firstFromLong
import org.jetbrains.kotlin.util.secondFromLong
import org.jetbrains.kotlin.util.zip
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

class ParallelMultiQueue(n: Int) {
    private val queues: Array<PriorityLongQueue> = Array(2 * n) { PriorityLongQueue(4) }
    private val locks: Array<ReentrantLock> = Array(2 * n) { ReentrantLock(false) }

    fun add(node: Long) {
        while (true) {
            val index = Random.nextInt(0, locks.size)
            if (locks[index].tryLock()) {
                queues[index].insert(node)
                locks[index].unlock()
                return
            }
        }
    }

    fun poll(): Long {
        val first: Long
        val indexFirst = 0
        var minNode: Long = Long.MIN_VALUE
        if (locks[indexFirst].tryLock()) {
            first = queues[indexFirst].peek()
            val indexSecond = 1
            if (locks[indexSecond].tryLock()) {
                val second = queues[indexSecond].peek()
                when {
                    first == Long.MIN_VALUE -> {
                        queues[indexSecond].poll()
                        minNode = second
                    }
                    second == Long.MIN_VALUE -> {
                        queues[indexFirst].poll()
                        minNode = first
                    }
                    else -> {
                        if (first.firstFromLong < second.firstFromLong) {
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
            locks[indexFirst].unlock();
        }
        return minNode
    }


}

fun singleThreadMQDijkstra(nodes: List<IntNode>, startIndex: Int) {
    val queue = ParallelMultiQueue(1)
    val start = nodes[startIndex]

    start.distance = 0
    queue.add(0.zip(startIndex))

    while (true) {

        val cur = queue.poll()
        if (cur == Long.MIN_VALUE) return

        val node = nodes[cur.secondFromLong]

        for (e in node.outgoingEdges) {
            val to = nodes[e.to]
            val nextDist = node.distance + e.weight

            if (to.distance > nextDist) {
                to.distance = nextDist

                queue.add(nextDist.zip(e.to))
            }
        }
    }
}