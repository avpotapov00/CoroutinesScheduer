package org.jetbrains.kotlin.graph.util.generator

import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequentialLong
import org.jetbrains.kotlin.number.scheduler.NonBlockingLongDijkstraScheduler
import kotlin.system.measureTimeMillis

fun generateSennit(count: Int): List<IntNode> {
    require((count - 1) % 3 == 0)

    val result = ArrayList<IntNode>(count)
    var last = IntNode()
    result.add(last)

    for (i in 0 until (count - 1) / 3) {
        val upper = IntNode()
        val upperIndex = result.size

        val lower = IntNode()
        val lowerIndex = upperIndex + 1

        val end = IntNode()
        val endIndex = lowerIndex + 1

        last.addEdge(upperIndex, 1)
        last.addEdge(lowerIndex, 1)

        upper.addEdge(endIndex, 1)
        lower.addEdge(endIndex, 1)

        result.add(upper)
        result.add(lower)
        result.add(end)

        last = end
    }

    return result
}

fun separateWithBridges(
    fragment: IntNodeGraph,
    bridge: IntNodeGraph,
    bridgesCount: Int,
): List<IntNode> {
    val fragmentSize = fragment.nodes.size
    val bridgeSize = bridge.nodes.size

    val result = ArrayList<IntNode>(fragmentSize * (bridgesCount + 1) + bridgeSize * bridgesCount)
    result.addAll(fragment.nodes.copy(0))

    for (i in 0 until bridgesCount) {
        val fromSegment = (fragmentSize + bridgeSize) * i + fragment.last
        val firstInBride = (fragmentSize + bridgeSize) * i + fragmentSize + bridge.first
        val lastFromBride = (fragmentSize + bridgeSize) * i + fragmentSize + bridge.last
        val toSegment = (fragmentSize + bridgeSize) * (i + 1) + fragment.first

        result.addAll(bridge.nodes.copy(fragmentSize * (i + 1) + bridgeSize * i))
        result.addAll(fragment.nodes.copy((fragmentSize + bridgeSize) * (i + 1)))

        result[fromSegment].addEdge(firstInBride, 1)
        result[firstInBride].addEdge(fromSegment, 1)

        result[lastFromBride].addEdge(toSegment, 1)
        result[toSegment].addEdge(lastFromBride, 1)
    }

    return result
}

data class IntNodeGraph(
    val nodes: List<IntNode>,
    val first: Int,
    val last: Int
)

private fun List<IntNode>.copy(startIndex: Int): List<IntNode> {
    val result = ArrayList<IntNode>(size)

    for (node in this) {
        val newNode = IntNode()
        for (edge in node.outgoingEdges) {
            newNode.addEdge(startIndex + edge.to, edge.weight)
        }
        result.add(newNode)
    }

    return result
}

fun main() {
    val nodes = generateSennit(500_000 * 3 + 1)

    val time = measureTimeMillis {
        repeat(400) { iteration ->
            shortestPathSequentialLong(nodes, 0)
            val sequentialResult = nodes.map { it.distance }

            clearNodes(nodes)

            NonBlockingLongDijkstraScheduler(nodes, 0, 2, pStealInitialPower = 3, stealSizeInitialPower = 3).use {
                it.waitForTermination()
            }

            val parallelResult = nodes.map { it.distance }
            clearNodes(nodes)

            if (sequentialResult != parallelResult) {
                println("GG")
                throw IllegalStateException("Not equal!")
            }
            println("Done $iteration")
        }
    }

    println("All in $time ms")
}