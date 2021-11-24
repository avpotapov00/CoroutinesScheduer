package org.jetbrains.kotlin.graph.bfs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.priority.Priority

suspend fun asyncBFS(dispatcher: CoroutineDispatcher, start: Node) = coroutineScope {

    fun relaxNode(cur: Node) {
        for (e in cur.outgoingEdges) {

            while (cur.distance + 1 < e.to.distance) {

                val toDist = e.to.distance
                val nextDist = cur.distance + 1

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