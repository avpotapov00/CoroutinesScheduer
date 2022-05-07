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

data class GraphIndexed(
    val nodes: Int,
    val edges: List<EdgeIndexed>
)

data class EdgeIndexed(
    val index: Int,
    val from: Int,
    val to: Int,
    val weight: Int
)