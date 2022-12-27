package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.adaptive.new.AdaptiveDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.NonBlockingAdaptiveByPStealLongDijkstraScheduler

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
        val scheduler = NonBlockingAdaptiveByPStealLongDijkstraScheduler(
            graph,
            pStealInitialPower = 1,
            stealSizeInitialPower = 3,
            poolSize = 32,
            startIndex = 0,
            retryCount = 10,
            metricsUpdateIterationsInit = 1000,
            k1 = 0.7,
            k2 = 0.1,
            learningRate = 0.1,
            reverseMomentum = 1.0E-3,
            initialMomentum = 0.1
        ).use {
            it.waitForTermination()
            it
        }
        println("Done: $testIndex, total=${scheduler.totalTasksProcessed()}, minPSteal=${scheduler.minPSteal()}, maxPSteal=${scheduler.maxPSteal()}")

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
