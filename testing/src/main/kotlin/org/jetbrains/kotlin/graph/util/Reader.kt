package org.jetbrains.kotlin.graph.util

import org.jetbrains.kotlin.graph.util.edges.Edge
import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.graph.util.nodes.Node
import java.io.File

fun readGraph(fileName: String): Graph {

    var nodeIndex = 0
    val map = mutableMapOf<String, Int>()

    val edges = mutableListOf<Edge>()

    File(fileName).bufferedReader().use { reader ->
        reader.lineSequence()
            .forEach { line ->
                val arr = line.split(" ")
                val from = arr[0]
                val to = arr[1]

                if (!map.containsKey(from)) {
                    map[from] = nodeIndex++
                }
                if (!map.containsKey(to)) {
                    map[to] = nodeIndex++
                }

                edges.add(Edge(map[from]!!, map[to]!!, 1))
            }
    }

    return Graph(map.size, edges)
}

fun readGraphNodes(fileName: String): List<Node> {
    return graphEdgesToGraphNodes(readGraph(fileName))
}