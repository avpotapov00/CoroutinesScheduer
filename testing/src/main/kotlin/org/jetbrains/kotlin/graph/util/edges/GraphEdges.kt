package org.jetbrains.kotlin.graph.util.edges


data class Graph(
    val nodes: Int,
    val edges: List<Edge>
)

data class Edge(
    val from: Int,
    val to: Int,
    val weight: Int
)