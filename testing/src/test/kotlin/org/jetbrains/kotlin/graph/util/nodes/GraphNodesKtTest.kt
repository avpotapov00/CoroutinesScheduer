package org.jetbrains.kotlin.graph.util.nodes

import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.randomConnectedIntGraph
import org.junit.jupiter.api.RepeatedTest
import kotlin.test.assertEquals

internal class GraphNodesKtTest {

    private var counter = 0

    lateinit var nodes: List<IndexedNode>

    private fun dfs(root: IndexedNode) {
        val visited = hashSetOf<Int>()

        dfs(root, visited)
    }

    private fun dfs(root: IndexedNode, visited: MutableSet<Int>) {
        if (root.index in visited) return

        counter++
        visited.add(root.index)

        root.node.outgoingEdges.forEach { dfs(nodes[it.to], visited) }
    }

    @RepeatedTest(30)
    fun `should be connected`() {
        val nodesCount = 100
        val graph = randomConnectedIntGraph(nodesCount, nodesCount - 1)

        nodes = graph.mapIndexed { index, node -> IndexedNode(index, node) }
        counter = 0

        dfs(nodes[0])

        assertEquals(nodesCount, counter)
    }

    data class IndexedNode(
        val index: Int,
        val node: IntNode,
    )

}