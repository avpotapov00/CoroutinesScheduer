@file:Suppress("UnstableApiUsage")

package org.jetbrains.kotlin.graph.boruvka

import com.google.common.graph.MutableValueGraph
import com.google.common.graph.ValueGraphBuilder
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.graph.dijkstra.randomConnectedIntGraph
import org.jetbrains.kotlin.graph.util.edges.Edge
import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.number.scheduler.parallelBoruvkaExp
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals

class BoruvkaModelTest {

    @Test
    fun `simple model test`() {
        val graph = makeTestGraphGuava()

        val mst = BoruvkaMST(graph)

        assertEquals(40, mst.totalWeight)
    }

    @Test
    fun `async legacy test`(): Unit = runBlocking {
        val (nodesCount, edges) = makeTestGraphNodes()

        val mst = asyncBoruvkaGlobalScope(nodesCount, edges)

        assertEquals(40, mst.sumOf { it.weight })
    }

    @RepeatedTest(100)
    fun `async legacy test with random graph`(): Unit = runBlocking {
        val graph = randomConnectedIntGraph(16, 25)
        val (nodesCount, edges) = intNodesToEdges(graph)
        val guavaGraph = intNodesToGuavaGraph(graph)

        val mstAsync = asyncBoruvkaGlobalScope(nodesCount, edges).sumOf { it.weight }
        val mstGuavaWeight = BoruvkaMST(guavaGraph).totalWeight

        assertEquals(mstGuavaWeight, mstAsync)
    }

    @RepeatedTest(100)
    fun `async scheduler test with random graph`() {
        val graph = randomConnectedIntGraph(16, 25)
        val (nodesCount, edges) = intNodesToEdges(graph)
        val guavaGraph = intNodesToGuavaGraph(graph)

        val mstAsync = parallelBoruvkaExp(edges, nodesCount, 4).second.sumOf { it.weight }
        val mstGuavaWeight = BoruvkaMST(guavaGraph).totalWeight

        assertEquals(mstGuavaWeight, mstAsync)
    }

    @RepeatedTest(20)
    @Timeout(100_000)
    fun `test on big graphs`() {
        testOnRandomGraphs(10000, 100000)
    }

    @Timeout(10)
    @RepeatedTest(3000)
    fun `test on trees`() {
        testOnRandomGraphs(100, 99)
    }

    @RepeatedTest(3000)
    @Timeout(100)
    fun `test on very small graphs 2`() {
        testOnRandomGraphs(16, 25)
    }

    @RepeatedTest(3000)
    @Timeout(100)
    fun `test on small graphs`() {
        testOnRandomGraphs(100, 1000)
    }

    @RepeatedTest(3000)
    @Timeout(100)
    fun `test on big trees`() {
        testOnRandomGraphs(1000, 999)
    }

    private fun testOnRandomGraphs(nodes: Int, edgesCount: Int) {
        repeat(GRAPHS) { i ->
            val graph = randomConnectedIntGraph(nodes, edgesCount)
            val (nodesCount, edges) = intNodesToEdges(graph)
            val guavaGraph = intNodesToGuavaGraph(graph)

            val mstAsync = parallelBoruvkaExp(edges, nodesCount, 8)
            val mstAsyncSum = mstAsync.second.sumOf { it.weight }
            val mstGuavaWeight = BoruvkaMST(guavaGraph).totalWeight

            assertEquals(mstGuavaWeight, mstAsyncSum)
        }
    }

    private fun makeTestGraphGuava(): MutableValueGraph<Int, Int> {
        val graph = ValueGraphBuilder.undirected().build<Int, Int>()
        repeat(7) { graph.addNode(it) }

        graph.putEdgeValue(0, 1, 7)
        graph.putEdgeValue(1, 2, 11)
        graph.putEdgeValue(0, 3, 4)
        graph.putEdgeValue(3, 1, 9)
        graph.putEdgeValue(1, 4, 10)
        graph.putEdgeValue(2, 4, 5)
        graph.putEdgeValue(3, 4, 15)
        graph.putEdgeValue(3, 5, 6)
        graph.putEdgeValue(5, 4, 12)
        graph.putEdgeValue(5, 6, 13)
        graph.putEdgeValue(4, 6, 8)

        return graph
    }

    private fun makeTestGraphNodes(): Graph {
        return Graph(7, listOf(
            Edge(0, 1, 7),
            Edge(1, 2, 11),
            Edge(0, 3, 4),
            Edge(3, 1, 9),
            Edge(1, 4, 10),
            Edge(2, 4, 5),
            Edge(3, 4, 15),
            Edge(3, 5, 6),
            Edge(5, 4, 12),
            Edge(5, 6, 13),
            Edge(4, 6, 8),
        ))
    }

}

private const val GRAPHS = 10
private const val SEARCHES = 100