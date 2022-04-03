package org.jetbrains.kotlin.util

import org.jetbrains.kotlin.graph.dijkstra.IntNode

fun List<IntNode>.addEdge(from: Int, to: Int, weight: Int) {
    this[from].addEdge(to, weight)
    this[to].addEdge(from, weight)
}