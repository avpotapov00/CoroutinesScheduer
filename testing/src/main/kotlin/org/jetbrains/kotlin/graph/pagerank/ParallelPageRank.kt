package org.jetbrains.kotlin.graph.pagerank

import org.jetbrains.kotlin.graph.dijkstra.ParallelMultiQueue
import org.jetbrains.kotlin.graph.util.IntPhaser
import kotlin.concurrent.thread

private data class IndexWithPriority(
    val node: Node,
    val priority: Int
)

fun pagerankParallelPush(nodes: List<Node>, dense: Float, epsilon: Float, threads: Int) {

    val comparator = Comparator<IndexWithPriority> { o1, o2 -> o1.priority.compareTo(o2.priority) }

    val initialValue = 1f / nodes.size
    nodes.forEach { it.clear(initialValue) }

    val phaser = IntPhaser()

    nodes.forEach { node ->
        node.rank = node.incomingEdges.map { 1f / it.outgoingEdges.size }.sum()
        node.rank = (1 - dense) * dense * node.rank
    }

    val queue = ParallelMultiQueue(threads, comparator)

    nodes.forEach { node ->
        phaser.register()
        queue.add(IndexWithPriority(node, 1))
    }

    repeat(threads) {
        thread {
            while (phaser.count != 0) {
                val node: Node = queue.poll()?.node ?: continue
                val nodeR = node.rank
                node.rank = 0f

                node.outgoingEdges.forEach { outgoingNode ->

                    var newR: Float
                    var oldR: Float

                    do {
                        oldR = outgoingNode.rank
                        newR = oldR + (nodeR * dense) / node.outgoingEdges.size
                    } while (!outgoingNode.casRank(oldR, newR))

                    if (newR >= epsilon && oldR < epsilon) {
                        val prior = -(newR * 10_000).toInt()

                        phaser.register()
                        queue.add(IndexWithPriority(outgoingNode, prior))
                    }
                }

                phaser.arriveAndDeregister()
            }
            println("Shutdown_${Thread.currentThread().name}")
        }
    }

    phaser.lockAndAwait()
}
