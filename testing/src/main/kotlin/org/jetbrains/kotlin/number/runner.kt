package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.adaptive.new.AdaptiveDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.NonBlockingAdaptiveByStealSizeLongDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongDijkstraScheduler

fun main() {
    println("Start!")

    val graph =
        GraphReader().readGraphNodesBiDirectFromFile("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/graphs/USA-road-d.W.gr")

//    println("Seq start!")
//    val from = 0
//    shortestPathSequentialLong(graph, from)
//    val seqRes = graph.map { it.distance }
//    clearNodes(graph)

    println("Real start!")
    // k1=0.7,learningRate=0.3,initialMomentum=100.0,window=1000
    repeat(100) { testIndex ->
        val scheduler = NonBlockingAdaptiveByStealSizeLongDijkstraScheduler(
            graph,
            pStealInitialPower = 0,
            stealSizeInitialPower = 0,
            poolSize = 8,
            startIndex = 0,
            retryCount = 10,
            stealSizeWindow = 100,
            bufferEfficientFactor = 0.14
        ).use {
            it.waitForTermination()
            it
        }
        println("Done: $testIndex, total=${scheduler.totalTasksProcessed()}, " +
                "minStealSize=${scheduler.minStealSize()}, maxStealSize=${scheduler.maxStealSize()}, stealSizeUpdates=${scheduler.stealSizeUpdateCountAverage()}")

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
