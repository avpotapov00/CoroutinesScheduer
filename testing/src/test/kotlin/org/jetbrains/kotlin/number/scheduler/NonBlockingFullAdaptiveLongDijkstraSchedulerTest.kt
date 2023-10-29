package org.jetbrains.kotlin.number.scheduler

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.junit.jupiter.api.Test
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Test of the final version
 */
class NonBlockingFullAdaptiveLongDijkstraSchedulerTest {


    @Test
    fun test() {
        val nodes: List<IntNode> = GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/graphs/USA-road-d.W.gr")

        val (parallelResults, parallelTime) = measureTimedValue { adaptive(nodes) }
        println("Parallel results done")
        clearNodes(nodes)

        val plainTime = measureTime {
            PlainNonBlockingLongDijkstraScheduler(
                nodes = nodes,
                startIndex = 0,
                poolSize = 16,
                stealSizeInitialPower = 3,
                pStealInitialPower = 2
            ).use {
                it.waitForTermination()
            }
        }

        println("Diff: $plainTime vs $parallelTime")
        for (i in parallelResults.indices) {
            val parallelRes = parallelResults[i]
            val seqRes = nodes[i].distance
            check(parallelRes == seqRes) { "GG" }
        }
        println("Nodes checked: ${parallelResults.size}")
    }


    private fun adaptive(nodes: List<IntNode>): List<Int> {
        NonBlockingFullAdaptiveLongDijkstraScheduler(
            nodes,
            startIndex = 0,
            poolSize = 16,
            stealSizeInitialPower = 3,
            pStealInitialPower = 2,
            learningRate = 0.1,
            initialMomentum = 100.0,
            k1 = 0.7,
            k2 = 1 - 0.7,
            pStealWindow = 1000,
            stealSizeWindow = 10,
            bufferEfficientFactor = 0.14
        ).use { it.waitForTermination() }
        return nodes.map { it.distance }
    }

}