package org.jetbrains.kotlin.number.scheduler

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequentialLong
import org.junit.jupiter.api.Test
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Test of the final version
 */
class NonBlockingFullAdaptiveLongDijkstraSchedulerTest {

    @Test
    fun `test compare adaptive vs sequential`() {
        val nodes: List<IntNode> =
            GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.W.gr")

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
        val parallelResults = nodes.map { it.distance }
        println("Parallel results ready")

        clearNodes(nodes)
        shortestPathSequentialLong(nodes, 0)
        println("Sequential results ready")

        for (i in parallelResults.indices) {
            val parallelRes = parallelResults[i]
            val plainRes = nodes[i].distance
            check(parallelRes == plainRes) { "GG" }
        }
    }


    /**
     * Test graph single time
     */
    @Test
    fun `test compare plain vs adaptive`() {
        val nodes: List<IntNode> =
            GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.W.gr")

        println("Read done")
        val (parallelResults, parallelTime) = measureTimedValue {
            NonBlockingFullAdaptiveLongDijkstraScheduler(
                nodes,
                startIndex = 0,
                poolSize = 8,
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
            nodes.map { it.distance }
        }
        println("Parallel results done")
        clearNodes(nodes)

        val plainTime = measureTime {
            PlainNonBlockingLongDijkstraScheduler(
                nodes = nodes,
                startIndex = 0,
                poolSize = 8,
                stealSizeInitialPower = 3,
                pStealInitialPower = 2
            ).use {
                it.waitForTermination()
            }
        }

        println("Diff: $plainTime vs $parallelTime")
        for (i in parallelResults.indices) {
            val parallelRes = parallelResults[i]
            val plainRes = nodes[i].distance
            check(parallelRes == plainRes) { "GG" }
        }
        println("Nodes checked: ${parallelResults.size}")
    }

    /**
     * Test graph single time
     */
    @Test
    fun `test process graph multiple times with adaptive version`() {
        val nodes: List<IntNode> =
            GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.W.gr")

        PlainNonBlockingLongDijkstraScheduler(
            nodes = nodes,
            startIndex = 0,
            poolSize = 16,
            stealSizeInitialPower = 3,
            pStealInitialPower = 2
        ).use {
            it.waitForTermination()
        }
        val sequentialResults = nodes.map { it.distance }
        clearNodes(nodes)

        val parallelScheduler = NonBlockingFullAdaptiveLongDijkstraScheduler(
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
        )
        // run 1st time
        parallelScheduler.waitForTermination()
        println("Parallel results done")

        // check
        for (i in sequentialResults.indices) {
            val parallelRes = nodes[i].distance
            val seqRes = sequentialResults[i]
            check(parallelRes == seqRes) { "GG" }
        }
        println("Nodes checked: ${sequentialResults.size}")

        // run 2nd time
        clearNodes(nodes)
        parallelScheduler.restartWithNextGraph(nodes, startIndex = 0)
        // active wait for first task to be picked
        while (parallelScheduler.globalQueue.size != 0) {
        }
        parallelScheduler.waitForTermination()

        // check
        for (i in sequentialResults.indices) {
            val parallelRes = nodes[i].distance
            val plainRes = sequentialResults[i]
            check(parallelRes == plainRes) { "GG" }
        }
        println("Nodes checked: ${sequentialResults.size}")

        parallelScheduler.close()
    }

    /**
     * Test graph single time
     */
    @Test
    fun `test process graph multiple times with parametrized version`() {
        val nodes: List<IntNode> =
            GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.W.gr")

        shortestPathSequentialLong(nodes, 0)
        val sequentialResults = nodes.map { it.distance }
        clearNodes(nodes)

        val parallelScheduler = PlainNonBlockingLongDijkstraScheduler(
            nodes = nodes,
            startIndex = 0,
            poolSize = 16,
            stealSizeInitialPower = 3,
            pStealInitialPower = 2
        )
        // run 1st time
        parallelScheduler.waitForTermination()
        println("Parallel results done")

        // check
        for (i in sequentialResults.indices) {
            val parallelRes = nodes[i].distance
            val seqRes = sequentialResults[i]
            check(parallelRes == seqRes) { "GG" }
        }
        println("Nodes checked: ${sequentialResults.size}")

        // run 2nd time
        clearNodes(nodes)
        parallelScheduler.restartWithNextGraph(nodes, startIndex = 0)
        // active wait for first task to be picked
        while (parallelScheduler.globalQueue.size != 0) {
        }
        parallelScheduler.waitForTermination()

        // check
        for (i in sequentialResults.indices) {
            val parallelRes = nodes[i].distance
            val plainRes = sequentialResults[i]
            check(parallelRes == plainRes) { "GG" }
        }
        println("Nodes checked: ${sequentialResults.size}")

        parallelScheduler.close()
    }

    @Test
    fun `many graphs processing test`() {
        val nodes: List<IntNode> =
            GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.W.gr")
        val nodesList = listOf(nodes, nodes, nodes)

        val scheduler = PlainNonBlockingLongDijkstraScheduler(
            nodes = nodesList.first(),
            startIndex = 0,
            poolSize = 16,
            stealSizeInitialPower = 3,
            pStealInitialPower = 2
        )
        scheduler.waitForTermination()
        clearNodes(nodes)
        for (i in 1 until nodesList.size) {
            val graph = nodesList[i]
            scheduler.restartWithNextGraph(graph, 0)
            while (scheduler.globalQueue.size != 0) {
            }
            scheduler.waitForTermination()
            clearNodes(nodes)
        }

        scheduler.close()
    }

}