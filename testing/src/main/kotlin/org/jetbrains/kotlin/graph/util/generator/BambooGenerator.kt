package org.jetbrains.kotlin.graph.util.generator

import org.jetbrains.kotlin.graph.dijkstra.IntNode

fun generateBamboo(nodesCount: Int): List<IntNode> {
    val nodes = (0 until nodesCount).map { IntNode() }

    for (i in 0 until nodesCount - 1) {
        nodes[i].addEdge(i + 1, 1 + i % 3)
    }

    return nodes
}
