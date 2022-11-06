package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequentialLong
import org.jetbrains.kotlin.mq.singleThreadPriorityQueueDijkstra
import org.jetbrains.kotlin.number.adaptive.new.AdaptiveDijkstraScheduler

fun main() {
    println("Start!")

    val graph =
        GraphReader().readGraphNodesBiDirectFromFile("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/testing/src/jmh/resources/USA-road-d.W.gr")

//    println("Seq start!")
//    val from = 0
//    shortestPathSequentialLong(graph, from)
//    val seqRes = graph.map { it.distance }
//    clearNodes(graph)

    println("Real start!")

    val scheduler = AdaptiveDijkstraScheduler(
        graph, pStealInitialPower = 5,
        stealSizeInitialPower = 4,
        poolSize = 8,
        startIndex = 0,
        metricsUpdateThreshold = 200,
        writerThreadFrequency = 1
    ).use {
        it.waitForTermination()
        it
    }
    println("Done")

//    val parRes = graph.map { it.distance }
//    check(parRes == seqRes) { "GG" }

    clearNodes(graph)
}
