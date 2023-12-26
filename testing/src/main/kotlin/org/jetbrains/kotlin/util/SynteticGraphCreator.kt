package org.jetbrains.kotlin.util

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.bfsSequentialLong
import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongDijkstraScheduler
import java.io.File
import kotlin.math.max
import kotlin.math.min

fun main() {
    createConnectedGraph()
}

private fun checkGraph() {
    val nodes = GraphReader()
        .readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/USA-road-d.W.gr-net-soc-LiveJournal1-3.txt")
    adaptive(nodes)
    val unreachableNodes = nodes.asSequence()
        .withIndex()
        .filter { it.value.distance == Int.MAX_VALUE }.map { it.index + 1 }.sorted().toList()
    val overflowNodes = nodes.asSequence()
        .withIndex()
        .filter { it.value.distance < 0 }.map { it.index + 1 }.sorted().toList()
    check(unreachableNodes.isEmpty()) {
        println(unreachableNodes.first())
        println(unreachableNodes.last())
        "unreacheable: ${unreachableNodes.size}"
    }
    check(overflowNodes.isEmpty())
}

// USA-road-d.W.gr -> Index: 5720903
private fun findMostFarNodeIndex() {
    val graph =
        GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/USA-road-d.W.gr")
    bfsSequentialLong(graph, 0)
    val index = graph.asSequence().withIndex().maxBy { it.value.distance }.index
    println("Index: $index")
}

private fun createConnectedGraph() {
    val (roadGraphSize, roadGraphEdgeSet) = graphData("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/USA-road-d.W.gr")
    println("Road graph read done. Total nodes: $roadGraphSize")

    val (socialGraphSize, socialEdgeSet) = graphData("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/soc-LiveJournal1.txt")

    println("Social graph read done. Total nodes: $socialGraphSize")


    val edgeNodeIndex = 5720903

    val sentLength = 2_000_000
    val nodesCount = roadGraphSize + sentLength * 3 + socialGraphSize
    val edgesCount = roadGraphEdgeSet.size + +sentLength * 4 + 1 + socialEdgeSet.size

    println("Start creating...")

    File("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/USA-road-d.W.gr-net-soc-LiveJournal1-3.txt").bufferedWriter()
        .use { writer ->
            writer.appendLine("p sp $nodesCount $edgesCount")
            roadGraphEdgeSet.forEach { edge ->
                writer.appendLine("a ${edge.from + 1} ${edge.to + 1} ${edge.weight}")
            }
            var startNode = edgeNodeIndex
            repeat(sentLength) { index ->
                val upper = roadGraphSize + (index * 3) + 1
                val lower = roadGraphSize + (index * 3) + 2
                val right = roadGraphSize + (index * 3) + 3
                writer.appendLine("a $startNode $upper 1")
                writer.appendLine("a $startNode $lower 1")
                writer.appendLine("a $upper $right 1")
                writer.appendLine("a $lower $right 1")
                startNode = right
            }
            val startNumber = roadGraphSize + sentLength * 3

            writer.appendLine("a $startNumber ${startNumber + 1} 1")

            socialEdgeSet.forEach { edge ->
                writer.appendLine("a ${startNumber + edge.from + 1} ${startNumber + edge.to + 1} ${edge.weight}")
            }
        }
}

private fun graphData(fileName: String): Pair<Int, Set<Edge>> {
    val graph = GraphReader().readGraphNodesBiDirectFromFile(fileName)
    val socialGraphSize = graph.size
    val socialEdgeSet = createEdgeSet(graph)

    return Pair(socialGraphSize, socialEdgeSet)
}

private data class Edge(val from: Int, val to: Int, val weight: Int)

private fun createEdgeSet(graph: List<IntNode>): Set<Edge> {
    val set = hashSetOf<Edge>()
    graph.forEachIndexed { index, node ->
        node.outgoingEdges.forEach { edge ->
            val minNode = min(index, edge.to)
            val maxNode = max(index, edge.to)
            set.add(Edge(minNode, maxNode, edge.weight))
        }
    }

    return set
}

private fun adaptive(nodes: List<IntNode>) {
    NonBlockingFullAdaptiveLongDijkstraScheduler(
        nodes,
        startIndex = 0,
        poolSize = 4,
        stealSizeInitialPower = 3,
        pStealInitialPower = 2,
        learningRate = 0.1,
        initialMomentum = 100.0,
        k1 = 0.7,
        k2 = 1 - 0.7,
        pStealWindow = 1000,
        stealSizeWindow = 10,
        bufferEfficientFactor = 0.14
    ).use { it.waitForTermination() }
}