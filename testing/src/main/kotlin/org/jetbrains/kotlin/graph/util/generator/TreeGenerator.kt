package org.jetbrains.kotlin.graph.util.generator

import org.jetbrains.kotlin.graph.dijkstra.IntNode
import kotlin.random.Random


fun generateIntNodesTree(depth: Int): List<IntNode> {
    val result = ArrayList<IntNode>()
    val random = Random(System.currentTimeMillis())

    result.add(IntNode())
    generate(random, depth, 0, 1, result)

    return result
}

private fun generate(
    random: Random,
    maxDepth: Int,
    rootId: Int,
    currentDepth: Int,
    nodes: MutableList<IntNode>
) {
    if (currentDepth >= maxDepth) return

    val root = nodes[rootId]

    nodes.add(IntNode())
    val leftId = nodes.size - 1
    root.addEdge(leftId, random.nextInt(1, 10))

    nodes.add(IntNode())
    val rightId = nodes.size - 1
    root.addEdge(rightId, random.nextInt(1, 10))

    generate(random, maxDepth, leftId, currentDepth + 1, nodes)
    generate(random, maxDepth, rightId, currentDepth + 1, nodes)
}
