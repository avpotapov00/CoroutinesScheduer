package org.jetbrains.kotlin.mq

import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.randomConnectedIntGraph
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequentialLong
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraScheduler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.random.Random
import kotlin.test.assertNotEquals

class MQModelTest {

    @Test
    fun `should find shortest paths sequential`() {
        val nodesList = List(3) { IntNode() }
        val (a, b, c) = nodesList

        a.addEdge(1, 2)
        b.addEdge(2, 1)
        a.addEdge(2, 4)

        singleThreadMQDijkstra(nodesList, 0)

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

        singleThreadMQDijkstra(nodesList, 0)

        println(nodesList.map { it.distance })

        nodesList.forEach { node ->
            assertNotEquals(Int.MAX_VALUE, node.distance)
        }
    }

    @Test
    @Timeout(1_000)
    fun `test on trees`() {
        testOnRandomGraphs(100, 99)
    }

    @Test
    @Timeout(1_000)
    fun `test on very small graphs`() {
        testOnRandomGraphs(16, 25)
    }

    @Test
    @Timeout(10_000)
    fun `test on small graphs`() {
        testOnRandomGraphs(100, 1000)
    }

    @Test
    @Timeout(100_000)
    fun `test on big graphs`() {
        testOnRandomGraphs(10000, 100000)
    }

    private fun testOnRandomGraphs(nodes: Int, edges: Int) {
        val r = Random(0)
        repeat(GRAPHS) {
            val nodesList = randomConnectedIntGraph(nodes, edges)
            repeat(SEARCHES) {
                val fromIndex = r.nextInt(nodes)

                shortestPathSequentialLong(nodesList, fromIndex)
                val seqRes = nodesList.map { it.distance }
                clearNodes(nodesList)

                singleThreadMQDijkstra(nodesList, fromIndex)
                val parRes = nodesList.map { it.distance }
                clearNodes(nodesList)

                assertEquals(seqRes, parRes)
            }
        }
    }
}

private const val GRAPHS = 10
private const val SEARCHES = 100