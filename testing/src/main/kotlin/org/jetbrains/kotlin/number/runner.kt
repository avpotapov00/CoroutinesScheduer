package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.adaptive.new.AdaptiveDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.NonBlockingAdaptiveByPStealLongDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongDijkstraScheduler

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

    repeat(100) { testIndex ->
        val scheduler = NonBlockingFullAdaptiveLongDijkstraScheduler(
            graph,
            pStealInitialPower = 1,
            stealSizeInitialPower = 3,
            poolSize = 32,
            startIndex = 0,
            startMetricsUpdateIterations = 100,
            restMetricsUpdateIterations = 100,
            metricsChangeStepsCount = 10,
            retryCount = 10,
            metricsChangeConsiderableDelta = 0.0,
            stealSizeTasksCheckCount = 100
        ).use {
            it.waitForTermination()
            it
        }
        println("Done: $testIndex, total=${scheduler.totalTasksProcessed()}, pStealUpdateCount=${scheduler.pStealUpdateCount()}, stealSizeUpdateCount=${scheduler.stealSizeUpdateCount()}")

        clearNodes(graph)
    }
}

private fun tryCatchBug(
    graph: List<IntNode>,
    poolSize: Int,
    retryCount: Int,
    iterations: Int
) {
    repeat(iterations) { testIndex ->
        val scheduler = AdaptiveDijkstraScheduler(
            graph, pStealInitialPower = 0,
            stealSizeInitialPower = 0,
            poolSize = poolSize,
            startIndex = 0,
            metricsUpdateThreshold = 100,
            writerThreadFrequency = 1,
            retryCount = retryCount
        ).use {
            it.waitForTermination()
            it
        }
        println("Done: $testIndex")

        clearNodes(graph)
    }
}
