package org.jetbrains.kotlin.graph.util

import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.graph.util.nodes.Node

fun graphEdgesToGraphNodes(graph: Graph): List<Node> {
    val nodes = (0 until graph.nodes).map { Node() }

    graph.edges.forEach {
        nodes[it.from].addEdge(nodes[it.to], it.weight)
        nodes[it.to].addEdge(nodes[it.from], it.weight)
    }

    return nodes
}