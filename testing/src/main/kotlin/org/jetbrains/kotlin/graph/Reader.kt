package org.jetbrains.kotlin.graph

import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.graph.dijkstra.FloatNode
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import java.io.BufferedReader
import java.io.File


class Reader {

    fun readGraphNodesBiDirect(fileName: String): List<IntNode> {
        this::class.java.getResourceAsStream(fileName)!!
            .bufferedReader().use { reader ->
                val count = reader.readLine().toInt()

                val nodes = Array(count) { IntNode() }

                reader.lines().forEach { line ->
                    val arr = line.split(" ")
                    val from = arr[0].toInt()
                    val to = arr[1].toInt()
                    val weight = arr[2].toInt()

                    nodes[from].addEdge(to, weight)
                    nodes[to].addEdge(from, weight)
                }

                return nodes.toList()
            }
    }

}

class GraphReader {

    fun readGraphNodesBiDirect(fileName: String): List<IntNode> {
        this::class.java.getResourceAsStream(fileName)!!
            .bufferedReader().use { reader ->
                return readNodes(reader)
            }
    }

    fun readGraphNodesBiDirectBfs(fileName: String): List<BfsIntNode> {
        this::class.java.getResourceAsStream(fileName)!!
            .bufferedReader().use { reader ->
                return readNodesBFS(reader)
            }
    }

    fun readGraphNodesBiDirectFromFileBfs(fileName: String): List<BfsIntNode> {
        File(fileName).bufferedReader().use { reader ->
            return readNodesBFS(reader)
        }
    }

    fun readGraphNodesBiDirectFromFile(fileName: String): List<IntNode> {
        File(fileName).bufferedReader().use { reader ->
                return readNodes(reader)
            }
    }

    fun readGraphFloatNodesBiDirect(fileName: String): List<FloatNode> {
        this::class.java.getResourceAsStream(fileName)!!
            .bufferedReader().use { reader ->
                return readFloatNodes(reader)
            }
    }

    fun readGraphFloatNodesBiDirectFromFile(fileName: String): List<FloatNode> {
        File(fileName).bufferedReader().use { reader ->
            return readFloatNodes(reader)
        }
    }

    private fun readFloatNodes(reader: BufferedReader): List<FloatNode> {
        var line = reader.readLine()
        while (line.startsWith("c")) {
            line = reader.readLine()
        }
        check(line.startsWith("p sp "))
        val count = line.split(" ")[2].toInt()

        val nodes = ArrayList<FloatNode>(count)
        repeat((0 until count).count()) { nodes.add(FloatNode(0f)) }

        do {
            line = reader.readLine()
        } while (line.startsWith("c"))

        while (line != null) {
            val (_, fromStr, toStr, w) = line.split(" ")
            val from = fromStr.toInt() - 1
            val to = toStr.toInt() - 1
            val weight = w.toInt()

            nodes[from].addEdge(to, weight)

            line = reader.readLine()
        }

        nodes.forEach { it.setNodesCount() }

        return nodes
    }

    private fun readNodes(reader: BufferedReader): List<IntNode> {
        var line = reader.readLine()
        while (line.startsWith("c")) {
            line = reader.readLine()
        }
        check(line.startsWith("p sp "))
        val count = line.split(" ")[2].toInt()

        val nodes = ArrayList<IntNode>(count)
        repeat((0 until count).count()) { nodes.add(IntNode()) }

        do {
            line = reader.readLine()
        } while (line.startsWith("c"))

        while (line != null) {
            val (_, fromStr, toStr, w) = line.split(" ")
            val from = fromStr.toInt() - 1
            val to = toStr.toInt() - 1
            val weight = w.toInt()

            nodes[from].addEdge(to, weight)
            nodes[to].addEdge(from, weight)

            line = reader.readLine()
        }

        return nodes
    }

    private fun readNodesBFS(reader: BufferedReader): List<BfsIntNode> {
        var line = reader.readLine()
        while (line.startsWith("c")) {
            line = reader.readLine()
        }
        check(line.startsWith("p sp "))
        val count = line.split(" ")[2].toInt()

        val nodes = ArrayList<BfsIntNode>(count)
        repeat((0 until count).count()) { nodes.add(BfsIntNode()) }

        do {
            line = reader.readLine()
        } while (line.startsWith("c"))

        while (line != null) {
            val (_, fromStr, toStr, _) = line.split(" ")
            val from = fromStr.toInt() - 1
            val to = toStr.toInt() - 1

            nodes[from].addEdge(to)
            nodes[to].addEdge(from)

            line = reader.readLine()
        }

        return nodes
    }

}