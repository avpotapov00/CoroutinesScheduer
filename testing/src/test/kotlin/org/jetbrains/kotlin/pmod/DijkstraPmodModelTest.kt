package org.jetbrains.kotlin.pmod

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.graph.dijkstra.asyncDijkstra
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequential
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.graph.util.nodes.clearNodes
import org.jetbrains.kotlin.graph.util.nodes.randomConnectedGraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.random.Random

class DijkstraPmodModelTest {

    @RepeatedTest(30)
    fun `test on simple graph`() = runBlocking {
        val nodesList = List(3) { Node() }
        val (a, b, c) = nodesList
        a.addEdge(b, 2)
        b.addEdge(c, 1)
        a.addEdge(c, 4)

        shortestPathSequential(a)
        val seqRes = nodesList.map { it.distance }
        clearNodes(nodesList)

        println(seqRes)

        val poolSize = 2
        val scheduler = AdaptiveObimPriorityCoroutineScheduler(poolSize, startThreads = true)

        PriorityQueueCoroutineDispatcher(scheduler).use {
            asyncDijkstra(it, a)
        }

        val asyncRes = nodesList.map { it.distance }
        println(asyncRes)
        clearNodes(nodesList)

        assertEquals(seqRes, asyncRes)
    }

    @Timeout(100)
    @RepeatedTest(20)
    fun `test on trees`() {
        testOnRandomGraphs(100, 99)
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
        testOnRandomGraphs(10000, 100000)
    }

    private fun testOnRandomGraphs(nodes: Int, edges: Int) = runBlocking {
        val r = Random(0)

        repeat(GRAPHS) { i ->
            val nodesList = randomConnectedGraph(nodes, edges)
            repeat(SEARCHES) { j ->
                val scheduler = AdaptiveObimPriorityCoroutineScheduler(4, startThreads = true)

                PriorityQueueCoroutineDispatcher(scheduler).use { dispatcher ->
                    val from = nodesList[r.nextInt(nodes)]
                    shortestPathSequential(from)
                    val seqRes = nodesList.map { it.distance }
                    clearNodes(nodesList)
                    asyncDijkstra(dispatcher, from)
                    val parRes = nodesList.map { it.distance }
                    clearNodes(nodesList)
                    assertEquals(seqRes, parRes)
                }
            }
            println("Done")
        }
    }

}

private const val GRAPHS = 10
private const val SEARCHES = 100