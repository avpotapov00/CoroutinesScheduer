package org.jetbrains.kotlin.graph.pagerank

import org.jetbrains.kotlin.graph.GraphReader
import java.util.*
import kotlin.math.abs


fun pagerankSequential(nodes: List<PageRankIntNode>, dense: Float, epsilon: Float) {
    val queue = PriorityQueue(NODE_RANK_COMPARATOR)

    val initialValue = 1f / nodes.size
    nodes.forEach { it.clear(initialValue) }

    queue.add(nodes[0])

    while (queue.isNotEmpty()) {
        val recent = queue.poll()

        val newRank = 1 - dense + dense * recent.incomingEdges.map { it.impact }.sum()

        if (abs(newRank - recent.rank) > epsilon) {

            recent.rank = newRank

            recent.outgoingEdges.forEach { node ->
                queue.remove(node)
                queue.add(node)
            }
        }
    }

}

fun main() {
    val graph =
        GraphReader().readGraphNodesBiDirectFromFile("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/testing/src/jmh/resources/USA-road-d.W.gr")



}

