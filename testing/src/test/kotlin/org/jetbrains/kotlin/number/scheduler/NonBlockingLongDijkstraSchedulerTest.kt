package org.jetbrains.kotlin.number.scheduler

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.randomConnectedIntGraph
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequentialLong
import org.jetbrains.kotlin.graph.util.generator.generateBamboo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.random.Random

internal class NonBlockingLongDijkstraSchedulerTest {

    @Test
    fun `should store two ints in long`() {
        val random = Random
        repeat(100_000) {
            val x = random.nextInt()
            val y = random.nextInt()

            val l = x.toLong() shl 32 or (y.toLong() and 0xffffffffL)
            val xRecovered = (l shr 32).toInt()
            val yRecovered = l.toInt()

            kotlin.test.assertEquals(x, xRecovered)
            kotlin.test.assertEquals(y, yRecovered)
        }
    }

    @Test
    fun `should find shortest paths sequential`() {
        val nodesList = List(3) { IntNode() }
        val (a, b, c) = nodesList

        a.addEdge(1, 2)
        b.addEdge(2, 1)
        a.addEdge(2, 4)

        shortestPathSequentialLong(nodesList, 0)

        assertEquals(0, a.distance)
        assertEquals(2, b.distance)
        assertEquals(3, c.distance)
    }

    @RepeatedTest(500)
    fun `should find shortest paths sequential on bamboo`() {
        val nodesList = List(180) { IntNode() }

        nodesList.withIndex().windowed(2) { (a, b) ->
            a.value.addEdge(b.index, 1)
            b.value.addEdge(a.index, 1)
        }

        NonBlockingLongDijkstraScheduler(nodesList, 0, 1, stealSize = 1, pSteal = 1.0, retryCount = 3).use {
            it.waitForTermination()
        }

        println(nodesList.map { it.distance })

        nodesList.forEach { node ->
            kotlin.test.assertNotEquals(Int.MAX_VALUE, node.distance)
        }
    }

    @Test
    fun `should find shortest paths parallel`() {
        val nodesList = List(3) { IntNode() }
        val (a, b, c) = nodesList

        a.addEdge(1, 2)
        b.addEdge(2, 1)
        a.addEdge(2, 4)

        NonBlockingLongDijkstraScheduler(nodesList, 0, 4).use { it.waitForTermination() }

        assertEquals(0, a.distance)
        assertEquals(2, b.distance)
        assertEquals(3, c.distance)
    }

    @RepeatedTest(30)
    fun `random graph should match`() {
        val nodes = randomConnectedIntGraph(16, 25)

        shortestPathSequentialLong(nodes, 0)
        val sequentialResult = nodes.map { it.distance }

        clearNodes(nodes)

        NonBlockingLongDijkstraScheduler(nodes, 0, 4).use {
            it.waitForTermination()
            it
        }

        val parallelResult = nodes.map { it.distance }

        assertEquals(sequentialResult, parallelResult)
    }

    @RepeatedTest(10)
    fun `bamboo should match`() {
        val nodes = generateBamboo(1_000_000)

        shortestPathSequentialLong(nodes, 0)
        val sequentialResult = nodes.map { it.distance }

        clearNodes(nodes)

        NonBlockingLongDijkstraScheduler(nodes, 0, 8, pSteal = 0.0625, stealSize = 8).use {
            it.waitForTermination()
        }

        val parallelResult = nodes.map { it.distance }

        assertEquals(sequentialResult, parallelResult)
    }

    @Timeout(100)
    @RepeatedTest(100)
    fun `test on trees`() {
        val nodes = 25

        testOnRandomGraphs(nodes, nodes - 1)
    }

    @RepeatedTest(100)
    @Timeout(100)
    fun `test on very small graphs`() {
        testOnRandomGraphs(16, 25)
    }

    @RepeatedTest(100)
    @Timeout(100)
    fun `test on small graphs`() {
        testOnRandomGraphs(100, 1000)
    }

    @Test
    @Timeout(100_000)
    fun `test on big graphs`() {
        testOnBigRandomGraphs(10000, 100000)
    }

    private fun testOnRandomGraphs(nodes: Int, edges: Int) = runBlocking {
        val r = Random(0)

        repeat(GRAPHS) { i ->
            val nodesList = randomConnectedIntGraph(nodes, edges)
            repeat(SEARCHES) { j ->
                searchAndAssert(r, nodes, nodesList)
            }
        }
    }

    private fun testOnBigRandomGraphs(nodes: Int, edges: Int) = runBlocking {
        val r = Random(0)

        repeat(GRAPHS) { i ->
            val nodesList = randomConnectedIntGraph(nodes, edges)
            repeat(1) { j ->
                searchAndAssert(r, nodes, nodesList)
            }
            println("Done graph: $i")
        }
    }

    private fun searchAndAssert(
        r: Random,
        nodes: Int,
        nodesList: List<IntNode>,
    ) {
        val from = r.nextInt(nodes)
        shortestPathSequentialLong(nodesList, from)
        val seqRes = nodesList.map { it.distance }
        clearNodes(nodesList)

        val scheduler = NonBlockingLongDijkstraScheduler(nodesList, from, 4, pSteal = 0.25).use { scheduler ->
            scheduler.waitForTermination()
            scheduler
        }

        val parRes = nodesList.map { it.distance }
        clearNodes(nodesList)

        if (seqRes != parRes) {
            println(1)
        }
        kotlin.test.assertEquals(seqRes, parRes)
    }

}


private const val GRAPHS = 10
private const val SEARCHES = 100