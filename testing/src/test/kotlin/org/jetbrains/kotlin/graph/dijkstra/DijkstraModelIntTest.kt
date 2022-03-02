package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.generic.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.generic.scheduler.SMQPriorityCoroutineScheduler
import org.jetbrains.kotlin.graph.util.nodes.clearNodes
import org.jetbrains.kotlin.graph.util.nodes.randomConnectedGraph
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.random.Random

class DijkstraModelIntTest {

//    @Test
//    fun simpleTest() = runBlocking {
//        val nodesList = List(3) { IntNode() }
//        val (a, b, c) = nodesList
//
//        a.addEdge(1, 2)
//        b.addEdge(2, 1)
//        a.addEdge(2, 4)
//
//        dijkstraParallelInt(nodesList, 0, pSteal = 0.04, stealSize = 4)
//
//        Assertions.assertEquals(0, a.distance)
//        Assertions.assertEquals(2, b.distance)
//        Assertions.assertEquals(3, c.distance)
//    }

//    @Timeout(100)
//    @RepeatedTest(20)
//    fun `test on trees`() {
//        testOnRandomGraphs(100, 99)
//    }
//
//    @RepeatedTest(20)
//    @Timeout(100)
//    fun `test on very small graphs`() {
//        testOnRandomGraphs(16, 25)
//    }
//
//    @RepeatedTest(20)
//    @Timeout(100)
//    fun `test on small graphs`() {
//        testOnRandomGraphs(100, 1000)
//    }
//
//    @Test
//    @Timeout(100_000)
//    fun `test on big graphs`() {
//        testOnRandomGraphs(10000, 100000)
//    }
//
//    private fun testOnRandomGraphs(nodes: Int, edges: Int) = runBlocking {
//        val r = Random(0)
//
//        repeat(GRAPHS) { i ->
//            val nodesList = randomConnectedIntGraph(nodes, edges)
//            repeat(SEARCHES) { j ->
//                val scheduler = SMQPriorityCoroutineScheduler(4, pSteal = 0.02)
//
//                PriorityQueueCoroutineDispatcher(scheduler).use { dispatcher ->
//                    val from = nodesList[r.nextInt(nodes)]
//                    shortestPathSequential(from)
//                    val seqRes = nodesList.map { it.distance }
//                    clearNodes(nodesList)
//                    asyncDijkstra(dispatcher, from)
//                    val parRes = nodesList.map { it.distance }
//                    clearNodes(nodesList)
//                    Assertions.assertEquals(seqRes, parRes)
//                }
//            }
//        }
//    }

}

private const val GRAPHS = 10
private const val SEARCHES = 100