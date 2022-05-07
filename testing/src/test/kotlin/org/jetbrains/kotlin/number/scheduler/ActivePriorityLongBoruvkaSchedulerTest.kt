package org.jetbrains.kotlin.number.scheduler

import com.google.common.graph.MutableValueGraph
import com.google.common.graph.ValueGraphBuilder
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.graph.boruvka.BoruvkaMST
import org.jetbrains.kotlin.graph.boruvka.asyncBoruvkaGlobalScope
import org.jetbrains.kotlin.graph.boruvka.intNodesToEdges
import org.jetbrains.kotlin.graph.boruvka.intNodesToGuavaGraph
import org.jetbrains.kotlin.graph.dijkstra.IntEdge
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.randomConnectedIntGraph
import org.jetbrains.kotlin.graph.util.edges.EdgeIndexed
import org.jetbrains.kotlin.graph.util.edges.GraphIndexed
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Потапов Александр
 * @since 16.04.2022
 */
internal class ActivePriorityLongBoruvkaSchedulerTest {

    @Test
    fun `simple model test`() {
        val graph = makeTestGraphGuava()

        val mst = BoruvkaMST(graph)

        kotlin.test.assertEquals(40, mst.totalWeight)
    }

    @Test
    fun `async legacy test`(): Unit = runBlocking {
        val (nodesCount, edges) = makeTestGraphNodes()

        val mst = asyncBoruvkaGlobalScope(nodesCount, edges)

        kotlin.test.assertEquals(40, mst.sumOf { it.weight })
    }

    @RepeatedTest(100)
    fun `async legacy test with random graph`(): Unit = runBlocking {
        val graph = randomConnectedIntGraph(16, 25)
        val (nodesCount, edges) = intNodesToEdges(graph)
        val guavaGraph = intNodesToGuavaGraph(graph)

        val mstAsync = asyncBoruvkaGlobalScope(nodesCount, edges).sumOf { it.weight }
        val mstGuavaWeight = BoruvkaMST(guavaGraph).totalWeight

        kotlin.test.assertEquals(mstGuavaWeight, mstAsync)
    }

    @RepeatedTest(100)
    fun `async scheduler test with random graph`() {
        val graph = randomConnectedIntGraph(16, 25)
        val (nodesCount, edges) = intNodesToEdges(graph)
        val guavaGraph = intNodesToGuavaGraph(graph)

        val mstAsync = ActivePriorityLongBoruvkaScheduler(edges, nodesCount, 4).use {
            it.parallelBoruvkaExp()
        }.second.sumOf { it.weight }
        val mstGuavaWeight = BoruvkaMST(guavaGraph).totalWeight

        kotlin.test.assertEquals(mstGuavaWeight, mstAsync)
    }

    @RepeatedTest(20)
    @Timeout(100_000)
    fun `test on big graphs`() {
        testOnRandomGraphs(10000, 100000)
    }

    @Timeout(10)
    @RepeatedTest(1000)
    fun `test on trees`() {
        testOnRandomGraphs(100, 99)
    }

    @RepeatedTest(3000)
    @Timeout(100)
    fun `test on very small graphs 2`() {
        testOnRandomGraphs(16, 25)
    }

    @RepeatedTest(10_000)
    fun myTest() {
        val graph = listOf(
            IntNode(edges=arrayListOf(IntEdge(to=11, weight=90), IntEdge(to=5, weight=21))), IntNode(edges=arrayListOf(
                IntEdge(to=5, weight=71), IntEdge(to=14, weight=39), IntEdge(to=4, weight=37), IntEdge(to=3, weight=82), IntEdge(to=11, weight=8)
            )), IntNode(edges=arrayListOf(IntEdge(to=10, weight=79), IntEdge(to=11, weight=11))), IntNode(edges=arrayListOf(
                IntEdge(to=15, weight=19), IntEdge(to=12, weight=85), IntEdge(to=14, weight=65), IntEdge(to=1, weight=82)
            )), IntNode(edges=arrayListOf(IntEdge(to=7, weight=50), IntEdge(to=8, weight=44), IntEdge(to=13, weight=96), IntEdge(to=1, weight=37))), IntNode(edges=arrayListOf(
                IntEdge(to=0, weight=21), IntEdge(to=1, weight=71), IntEdge(to=8, weight=27)
            )), IntNode(edges=arrayListOf(IntEdge(to=13, weight=65), IntEdge(to=15, weight=61))), IntNode(edges=arrayListOf(
                IntEdge(to=4, weight=50), IntEdge(to=15, weight=42)
            )), IntNode(edges=arrayListOf(IntEdge(to=4, weight=44), IntEdge(to=15, weight=67), IntEdge(to=14, weight=44), IntEdge(to=5, weight=27))), IntNode(edges=arrayListOf(
                IntEdge(to=12, weight=93), IntEdge(to=10, weight=35)
            )), IntNode(edges=arrayListOf(IntEdge(to=9, weight=35), IntEdge(to=2, weight=79), IntEdge(to=11, weight=76))), IntNode(edges=arrayListOf(
                IntEdge(to=2, weight=11), IntEdge(to=0, weight=90), IntEdge(to=10, weight=76), IntEdge(to=1, weight=8)
            )), IntNode(edges=arrayListOf(IntEdge(to=3, weight=85), IntEdge(to=9, weight=93))), IntNode(edges=arrayListOf(
                IntEdge(to=14, weight=89), IntEdge(to=6, weight=65), IntEdge(to=4, weight=96)
            )), IntNode(edges=arrayListOf(IntEdge(to=1, weight=39), IntEdge(to=13, weight=89), IntEdge(to=8, weight=44), IntEdge(to=3, weight=65))), IntNode(edges=arrayListOf(
                IntEdge(to=8, weight=67), IntEdge(to=3, weight=19), IntEdge(to=7, weight=42), IntEdge(to=6, weight=61)
            ))
        )
        val (nodesCount, edges) = intNodesToEdges(graph)

        val guavaGraph = intNodesToGuavaGraph(graph)

        val mstAsync = NonBlockingPriorityLongBoruvkaScheduler(edges, nodesCount, 4).use {
            it.parallelBoruvkaExp()
        }
        val mstAsyncSum = mstAsync.second.sumOf { it.weight }
        val mstGuavaWeight = BoruvkaMST(guavaGraph).totalWeight

        kotlin.test.assertEquals(mstGuavaWeight, mstAsyncSum)
    }

    @RepeatedTest(3000)
    @Timeout(100)
    fun `test on small graphs`() {
        testOnRandomGraphs(100, 1000)
    }

    @RepeatedTest(1000)
    @Timeout(100)
    fun `test on big trees`() {
        testOnRandomGraphs(1000, 999)
    }

    private fun testOnRandomGraphs(nodes: Int, edgesCount: Int) {
        repeat(10) { i ->
            val graph = randomConnectedIntGraph(nodes, edgesCount)
            val (nodesCount, edges) = intNodesToEdges(graph)
            val guavaGraph = intNodesToGuavaGraph(graph)

            val mstAsync = ActivePriorityLongBoruvkaScheduler(edges, nodesCount, 4).use {
                it.parallelBoruvkaExp()
            }

            val mstAsyncSum = mstAsync.second.sumOf { it.weight }
            val boruvkaMST = BoruvkaMST(guavaGraph)
            val mstGuavaWeight = boruvkaMST.totalWeight

            if (mstAsyncSum != mstGuavaWeight) {
                println("Stop")
            }

//            assertEquals(mstGuavaWeight, mstAsyncSum)
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

    private fun makeTestGraphNodes(): GraphIndexed {
        return GraphIndexed(
            7, listOf(
                EdgeIndexed(0, 0, 1, 7),
                EdgeIndexed(1, 1, 2, 11),
                EdgeIndexed(2, 0, 3, 4),
                EdgeIndexed(3, 3, 1, 9),
                EdgeIndexed(4, 1, 4, 10),
                EdgeIndexed(5, 2, 4, 5),
                EdgeIndexed(6, 3, 4, 15),
                EdgeIndexed(7, 3, 5, 6),
                EdgeIndexed(8, 5, 4, 12),
                EdgeIndexed(9, 5, 6, 13),
                EdgeIndexed(10, 4, 6, 8),
            )
        )
    }


}