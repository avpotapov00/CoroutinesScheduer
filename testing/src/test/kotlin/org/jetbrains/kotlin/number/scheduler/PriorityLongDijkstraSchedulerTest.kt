package org.jetbrains.kotlin.number.scheduler

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.generic.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.generic.scheduler.SMQPriorityCoroutineScheduler
import org.jetbrains.kotlin.graph.dijkstra.*
import org.jetbrains.kotlin.graph.util.nodes.randomConnectedGraph
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.Phaser
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.assertEquals


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

    @Test
    fun `should find shortest paths parallel`() {
        val nodesList = List(3) { IntNode() }
        val (a, b, c) = nodesList

        a.addEdge(1, 2)
        b.addEdge(2, 1)
        a.addEdge(2, 4)

        val dijkstraScheduler = PriorityLongDijkstraScheduler(nodesList, 0, 4)

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

        val dijkstraScheduler = PriorityLongDijkstraScheduler(nodes, 0, 4)
        dijkstraScheduler.waitForTermination()

        val parallelResult = nodes.map { it.distance }

        assertEquals(sequentialResult, parallelResult)
    }

    @Test
    fun `phaser test`() {

        val phaser = Phaser(4)

        repeat(2) {
            thread(name = "t_$it") {
                Thread.sleep(1000)
                phaser.arriveAndDeregister()

                println("Mid ${Thread.currentThread().name}")

                Thread.sleep(1000)
                phaser.register()
                Thread.sleep(1000)
                phaser.arriveAndDeregister()
                println("Done ${Thread.currentThread().name}")
            }
        }

        thread {
            Thread.sleep(5_000)
            phaser.arriveAndDeregister()
            println("All done, ${phaser.unarrivedParties}")
        }

        phaser.arriveAndAwaitAdvance()

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
            val nodesList = randomConnectedIntGraph(nodes, edges)
            repeat(SEARCHES) { j ->

                val from = r.nextInt(nodes)
                shortestPathSequentialLong(nodesList, from)
                val seqRes = nodesList.map { it.distance }
                clearNodes(nodesList)

                PriorityLongDijkstraScheduler(nodesList, from, 4).use { scheduler ->

                    scheduler.waitForTermination()

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