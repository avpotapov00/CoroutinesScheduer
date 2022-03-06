package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraScheduler

fun main() {
    val path = "E:\\Diploma\\dm-examples-master\\CoroutinesScheduer\\testing\\src\\jmh\\resources\\USA-road-d.W.gr"
    val nodes  = GraphReader().readGraphNodesBiDirectFromFile(path)

    println("Started!")
    PriorityLongDijkstraScheduler(
        nodes,
        startIndex = 0,
        poolSize = 4,
        stealSize = 8,
        pSteal = 1.0
    ).use { it.waitForTermination() }
}