package org.jetbrains.kotlin.graph.util.edges


class Graph(
    val nodes: Int,
    val edges: List<Edge>
)

data class Edge(
    val from: Int,
    val to: Int,
    val weight: Int
)