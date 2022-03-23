package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraScheduler

fun main() {
    val path = "/USA-road-d.W.gr"
    val nodes = GraphReader().readGraphNodesBiDirect(path)

    println("Started!")

    repeat(100) {
        PriorityLongDijkstraScheduler(
            nodes,
            startIndex = 0,
            poolSize = 128,
            stealSize = 8,
            pSteal = 0.03125
        ).use {
            it.waitForTermination()
        }

        clearNodes(nodes)
    }
}