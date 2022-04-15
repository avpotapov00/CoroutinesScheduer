package org.jetbrains.kotlin.number.scheduler

import org.jetbrains.kotlin.graph.dijkstra.FloatNode
import org.junit.jupiter.api.Test

/**
 * @author Потапов Александр
 * @since 15.04.2022
 */
internal class NonBlockingPriorityLongPageRankSchedulerTest {


    @Test
    fun `should finish calculation`() {
        val nodes = listOf(FloatNode(0f), FloatNode(0f), FloatNode(0f))
        nodes[0].addEdge(1, 1)
        nodes[1].addEdge(2, 1)
        nodes[2].addEdge(0, 1)
        nodes.forEach { it.setNodesCount() }

        NonBlockingPriorityLongPageRankScheduler(nodes, poolSize = 4).use {
            it.waitForTermination()
        }

        println("Done!")
        println(nodes.map { it.residual })
    }


}