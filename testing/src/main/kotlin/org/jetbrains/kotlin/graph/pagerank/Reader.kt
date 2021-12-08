package org.jetbrains.kotlin.graph.pagerank

import java.io.File

fun readGraphNodes(fileName: String): List<Node> {
    File(fileName).bufferedReader().use { reader ->
        val count = reader.readLine().toInt()

        val nodes = Array(count) { Node(0f) }

        reader.lines().forEach { line ->
            val arr = line.split(" ")
            val from = arr[0].toInt()
            val to = arr[1].toInt()

            nodes[from].addEdge(nodes[to])
            nodes[to].addEdge(nodes[from])
        }

        return nodes.toList()
    }
}
