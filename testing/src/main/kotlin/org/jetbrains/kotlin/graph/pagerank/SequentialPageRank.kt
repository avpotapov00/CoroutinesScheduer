package org.jetbrains.kotlin.graph.pagerank

import java.util.*
import kotlin.math.abs


fun pagerankSequential(nodes: List<Node>, dense: Float, epsilon: Float) {
    val queue = PriorityQueue(NODE_RANK_COMPARATOR)

    val initialValue = 1f / nodes.size
    nodes.forEach { it.clear(initialValue) }

    while (queue.isNotEmpty()) {
        val recent = queue.poll()

        val newRank = 1 - dense + dense * recent.outgoingEdges.map { it.impact }.sum()

        if (abs(newRank - recent.rank) > epsilon) {
            recent.outgoingEdges.forEach { node ->
                queue.remove(node)
                queue.add(node)
            }
        }
    }

}
