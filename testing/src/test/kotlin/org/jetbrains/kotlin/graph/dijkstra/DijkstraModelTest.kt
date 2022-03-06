package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.generic.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.generic.scheduler.SMQPriorityCoroutineScheduler
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.graph.util.nodes.clearNodes
import org.jetbrains.kotlin.graph.util.nodes.randomConnectedGraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class DijkstraModelTest {

    @Test
    fun simpleTest() = runBlocking {
        val nodesList = List(3) { Node() }
        val (a, b, c) = nodesList

        a.addEdge(b, 2)
        b.addEdge(c, 1)
        a.addEdge(c, 4)

        val scheduler = SMQPriorityCoroutineScheduler(poolSize = 1, postponeThreadsStart = false, pSteal = 0.02)

        println("Start")
        PriorityQueueCoroutineDispatcher(scheduler).use { dispatcher ->
            asyncDijkstra(dispatcher, a)
        }

        assertEquals(0, a.distance)
        assertEquals(2, b.distance)
        assertEquals(3, c.distance)
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

    @Timeout(10)
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5])
    fun `test on trees with 1 thread`(poolSize: Int) {
        testOnRandomGraphs(100, 99)
    }

    @RepeatedTest(20)
    @Timeout(100)
    fun `test on very small graphs 2`() {
        testOnRandomGraphs(16, 25)
    }

    @RepeatedTest(20)
    @Timeout(100)
    fun `test on small graphs 2`() {
        testOnRandomGraphs(100, 1000)
    }

    private fun testOnRandomGraphs(nodes: Int, edges: Int) = runBlocking {
        val r = Random(0)

        repeat(GRAPHS) { i ->
            val nodesList = randomConnectedGraph(nodes, edges)
            repeat(SEARCHES) { j ->
                val scheduler = SMQPriorityCoroutineScheduler(1, pSteal = 1.0)

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
        }
    }

    private fun testOnRandomGraphs(nodes: Int, edges: Int, pSteal: Double, poolSize: Int, retryCount: Int) =
        runBlocking {
            val r = Random(0)

            repeat(GRAPHS) { i ->
                val nodesList = randomConnectedGraph(nodes, edges)
                repeat(SEARCHES) { j ->
                    val scheduler =
                        SMQPriorityCoroutineScheduler(poolSize = poolSize, pSteal = pSteal, retryCount = retryCount)

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
            }
        }

}

private const val GRAPHS = 10
private const val SEARCHES = 100