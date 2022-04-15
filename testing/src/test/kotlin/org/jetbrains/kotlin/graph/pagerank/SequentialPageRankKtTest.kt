package org.jetbrains.kotlin.graph.pagerank

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class SequentialPageRankKtTest {

    @Test
    fun `page rank test`() {
        val nodes = listOf(PageRankIntNode(0f), PageRankIntNode(0f), PageRankIntNode(0f))
        nodes[0].addEdge(nodes[1])
        nodes[1].addEdge(nodes[2])
        nodes[0].addEdge(nodes[2])
        nodes[2].addEdge(nodes[0])

        pagerankSequential(nodes, 0.5f, 0.00001f)

        nodes.forEachIndexed { index, node -> println("$index: ${node.rank}") }

        assertEquals(3.0, nodes.sumOf { it.rank.toDouble() }, 0.01)
        assertEquals(1.07692308, nodes[0].rank.toDouble(), 1e-4)
        assertEquals(0.76923077, nodes[1].rank.toDouble(), 1e-4)
        assertEquals(1.153846158, nodes[2].rank.toDouble(), 1e-4)
    }


}