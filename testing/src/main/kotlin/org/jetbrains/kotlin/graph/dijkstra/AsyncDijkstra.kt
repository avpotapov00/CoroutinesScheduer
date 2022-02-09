package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.graph.util.IntPhaser
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.generic.priority.Priority

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

fun asyncDijkstraGlobalScope(dispatcher: CoroutineDispatcher, start: Node)  {

    val phaser = IntPhaser()

    fun relaxNode(cur: Node) {
        for (e in cur.outgoingEdges) {

            while (cur.distance + e.weight < e.to.distance) {

                val toDist = e.to.distance
                val nextDist = cur.distance + e.weight

                if (toDist > nextDist && e.to.casDistance(toDist, nextDist)) {

                    phaser.register()

                    GlobalScope.launch(dispatcher + Priority(nextDist)) {
                        relaxNode(e.to)
                        phaser.arriveAndDeregister()
                    }
                    break
                }
            }

        }
    }

    start.distance = 0
    relaxNode(start)

    phaser.lockAndAwait()
}