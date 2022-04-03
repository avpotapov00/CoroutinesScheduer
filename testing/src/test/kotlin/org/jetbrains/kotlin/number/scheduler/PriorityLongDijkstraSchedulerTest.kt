package org.jetbrains.kotlin.number.scheduler

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.randomConnectedIntGraph
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequentialLong
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


internal class PriorityLongDijkstraSchedulerTest {

    @Test
    fun `should store two ints in long`() {
        val random = Random
        repeat(100_000) {
            val x = random.nextInt()
            val y = random.nextInt()

            val l = x.toLong() shl 32 or (y.toLong() and 0xffffffffL)
            val xRecovered = (l shr 32).toInt()
            val yRecovered = l.toInt()

            assertEquals(x, xRecovered)
            assertEquals(y, yRecovered)
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

        Assertions.assertEquals(0, a.distance)
        Assertions.assertEquals(2, b.distance)
        Assertions.assertEquals(3, c.distance)
    }

    @RepeatedTest(500)
    fun `should find shortest paths sequential on bamboo`() {
        val nodesList = List(180) { IntNode() }

        nodesList.withIndex().windowed(2) { (a, b) ->
            a.value.addEdge(b.index, 1)
            b.value.addEdge(a.index, 1)
        }

        val dijkstraScheduler =
            PriorityLongDijkstraSchedulerKS(nodesList, 0, 1, stealSize = 1, pSteal = 1.0, retryCount = 3)
        dijkstraScheduler.waitForTermination()

        println(nodesList.map { it.distance })

        nodesList.forEach { node ->
            assertNotEquals(Int.MAX_VALUE, node.distance)
        }
    }

    @Test
    fun `should find shortest paths parallel`() {
        val nodesList = List(3) { IntNode() }
        val (a, b, c) = nodesList

        a.addEdge(1, 2)
        b.addEdge(2, 1)
        a.addEdge(2, 4)

        val dijkstraScheduler = PriorityLongDijkstraSchedulerKS(nodesList, 0, 4)

        dijkstraScheduler.waitForTermination()

        Assertions.assertEquals(0, a.distance)
        Assertions.assertEquals(2, b.distance)
        Assertions.assertEquals(3, c.distance)
    }

    @Test
    @RepeatedTest(30)
    fun `random graph should match`() {
        val nodes = randomConnectedIntGraph(16, 25)

        shortestPathSequentialLong(nodes, 0)
        val sequentialResult = nodes.map { it.distance }

        clearNodes(nodes)

        val dijkstraScheduler = PriorityLongDijkstraSchedulerKS(nodes, 0, 4)
        dijkstraScheduler.waitForTermination()

        val parallelResult = nodes.map { it.distance }

        assertEquals(sequentialResult, parallelResult)
    }

    @Timeout(100)
    @RepeatedTest(40)
    fun `test on trees`() {
        val nodes = 15

        testOnRandomGraphs(nodes, nodes - 1)
    }

    @RepeatedTest(20)
    @Timeout(100)
    fun `test on very small graphs`() {
        testOnRandomGraphs(16, 25)
    }

    @RepeatedTest(20)
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

        PriorityLongDijkstraSchedulerKS(nodesList, from, 4, pSteal = 0.25).use { scheduler ->

            scheduler.waitForTermination()

            val parRes = nodesList.map { it.distance }
            clearNodes(nodesList)

            assertEquals(seqRes, parRes)
        }
    }

    @Test
    fun `parse graph`() {
        val fileName = "E:\\Diploma\\dm-examples-master\\CoroutinesScheduer\\testing\\src\\test\\resources\\bug.txt"
        val graph = parseGraph(fileName)

        val actualLine = graph.map { it.node }.toString()
        val expectedLine = File(fileName).readText()

        assertEquals(expectedLine, actualLine)
    }

}

private fun parseGraph(file: String): List<IndexedNode> {
    val text = File(file).readText()
    val rawNodes = text.drop(1).dropLast(1).split("Node").drop(1)
    val numbersRegexp = "[0-9]+".toRegex()

    val edges = mutableListOf<FullIntEdge>()

    val nodes = rawNodes.mapIndexed { index, raw ->
        val chunked = numbersRegexp.findAll(raw).map { it.value.toInt() }.toList().chunked(2)
        check(chunked.all { it.size == 2 }) { "Illegal chunc size" }

        chunked.forEach { (to, weight) -> edges.add(FullIntEdge(index, to, weight)) }

        IndexedNode(index, IntNode())
    }

    edges.forEach { (from, to, w) ->
        nodes[from].node.addEdge(to, w)
    }

    return nodes
}


private data class IndexedNode(
    val index: Int,
    val node: IntNode,
)

private data class FullIntEdge(
    val from: Int,
    val to: Int,
    val weight: Int,
)

private const val GRAPHS = 10
private const val SEARCHES = 100