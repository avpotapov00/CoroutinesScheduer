package org.jetbrains.kotlin.graph.bfs

import org.jetbrains.kotlin.graph.util.nodes.NODE_DISTANCE_COMPARATOR
import org.jetbrains.kotlin.graph.util.nodes.Node
import java.util.*

fun bfsSequential(start: Node) {
    start.distance = 0
    val q = PriorityQueue(NODE_DISTANCE_COMPARATOR)
    q.add(start)

    while (q.isNotEmpty()) {

        val cur = q.poll()
        for (e in cur.outgoingEdges) {

            if (e.to.distance > cur.distance + 1) {
                e.to.distance = cur.distance + 1
                q.remove(e.to)
                q.add(e.to)
            }
        }
    }
}