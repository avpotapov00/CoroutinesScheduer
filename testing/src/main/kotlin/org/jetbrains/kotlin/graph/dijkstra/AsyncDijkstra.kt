package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.priority.Priority

suspend fun asyncDijkstra(dispatcher: CoroutineDispatcher, start: Node) = coroutineScope {

    fun relaxNode(cur: Node) {
        for (e in cur.outgoingEdges) {

            while (cur.distance + e.weight < e.to.distance) {

                val toDist = e.to.distance
                val nextDist = cur.distance + e.weight

                if (toDist > nextDist && e.to.casDistance(toDist, nextDist)) {

                    launch(dispatcher + Priority(nextDist)) {
                        relaxNode(e.to)
                    }
                    break
                }
            }

        }
    }

    start.distance = 0
    relaxNode(start)
}