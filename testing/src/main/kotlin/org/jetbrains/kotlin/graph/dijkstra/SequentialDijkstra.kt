package org.jetbrains.kotlin.graph.dijkstra

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