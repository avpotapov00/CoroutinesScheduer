package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.generic.scheduler.SMQPriorityCoroutineScheduler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DijkstraModelIntTest {

    @Test
    fun simpleTest() = runBlocking {
        val nodesList = List(3) { IntNode() }
        val (a, b, c) = nodesList

        a.addEdge(1, 2)
        b.addEdge(2, 1)
        a.addEdge(2, 4)

        dijkstraParallelInt(nodesList, 0)

        Assertions.assertEquals(0, a.distance)
        Assertions.assertEquals(2, b.distance)
        Assertions.assertEquals(3, c.distance)
    }


}