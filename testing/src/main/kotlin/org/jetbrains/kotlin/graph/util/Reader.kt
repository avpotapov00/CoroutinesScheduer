package org.jetbrains.kotlin.graph.util

import org.jetbrains.kotlin.graph.util.edges.Edge
import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.graph.util.nodes.Node
import java.io.File

fun readGraphEdges(fileName: String): Graph {
    File(fileName).bufferedReader().use { reader ->
        val edges = mutableListOf<Edge>()

        val count = reader.readLine().toInt()

        reader.lines().forEach { line ->
            val arr = line.split(" ")
            val from = arr[0].toInt()
            val to = arr[1].toInt()
            val weight = arr[2].toInt()

            edges.add(Edge(from, to, weight))
        }

        return Graph(count, edges)
    }
}

fun readGraphNodesBiDirect(fileName: String): List<Node> {
    File(fileName).bufferedReader().use { reader ->
        val count = reader.readLine().toInt()

        val nodes = Array(count) { Node() }

        reader.lines().forEach { line ->
            val arr = line.split(" ")
            val from = arr[0].toInt()
            val to = arr[1].toInt()
            val weight = arr[2].toInt()

            nodes[from].addEdge(nodes[to], weight)
            nodes[to].addEdge(nodes[from], weight)
        }

        return nodes.toList()
    }
}
