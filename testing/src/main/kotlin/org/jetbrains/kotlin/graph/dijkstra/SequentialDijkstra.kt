package org.jetbrains.kotlin.graph.dijkstra

import org.jetbrains.kotlin.graph.util.nodes.INT_NODE_DISTANCE_COMPARATOR
import org.jetbrains.kotlin.graph.util.nodes.NODE_DISTANCE_COMPARATOR
import org.jetbrains.kotlin.graph.util.nodes.Node
import java.util.*

fun shortestPathSequential(start: Node) {
    start.distance = 0
    val q = PriorityQueue(NODE_DISTANCE_COMPARATOR)
    q.add(start)

    while (q.isNotEmpty()) {

        val cur = q.poll()
        for (e in cur.outgoingEdges) {

            if (e.to.distance > cur.distance + e.weight) {
                e.to.distance = cur.distance + e.weight
                q.remove(e.to)
                q.add(e.to)
            }
        }
    }
}

fun shortestPathSequential(start: Int, nodes: List<IntNode>) {
    val startNode = nodes[start]
    startNode.distance = 0
    val q = PriorityQueue(INT_NODE_DISTANCE_COMPARATOR)
    q.add(startNode)

    while (q.isNotEmpty()) {

        val cur = q.poll()
        for (e in cur.outgoingEdges) {
            val to = nodes[e.to]

            if (to.distance > cur.distance + e.weight) {
                to.distance = cur.distance + e.weight
                q.remove(to)
                q.add(to)
            }
        }
    }
}