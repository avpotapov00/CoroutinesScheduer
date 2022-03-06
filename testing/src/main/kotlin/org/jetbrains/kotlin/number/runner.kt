package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraScheduler

fun main() {
    val path = "/USA-road-d.W.gr"
    val nodes = GraphReader().readGraphNodesBiDirect(path)

    println("Started!")

    PriorityLongDijkstraScheduler(
        nodes,
        startIndex = 0,
        poolSize = 4,
        stealSize = 4,
        pSteal = 1.0
    ).use {
        it.waitForTermination()
    }
}