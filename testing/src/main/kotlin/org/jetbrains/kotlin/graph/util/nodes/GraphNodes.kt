package org.jetbrains.kotlin.graph.util.nodes

import kotlinx.atomicfu.atomic
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList

val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> o1.rank.compareTo(o2.rank) }

data class Edge(val to: Node, val weight: Int)

open class Node: RankedNode<Int> {
    private val _outgoingEdges = arrayListOf<Edge>()
    val outgoingEdges: List<Edge> = _outgoingEdges

    private val _distance = atomic(Integer.MAX_VALUE)
    var distance
        get() = _distance.value
        set(value) {
            _distance.value = value
        }

    override val rank: Int
        get() = distance

    fun casDistance(cur: Int, update: Int) = _distance.compareAndSet(cur, update)

    fun addEdge(to: Node, weight: Int) {
        _outgoingEdges.add(Edge(to, weight))
    }
}

fun clearNodes(nodes: List<Node>) {
    nodes.forEach { it.distance = Int.MAX_VALUE }
}


fun randomConnectedGraph(
    nodes: Int,
    edges: Int,
    maxWeight: Int = 100,
): List<Node> {
    require(edges >= nodes - 1)
    val r = Random()
    val nodesList = List(nodes) { Node() }
    // generate a random connected graph with `nodes-1` edges
    val s = ArrayList(nodesList)
    var cur = s.removeAt(r.nextInt(s.size))
    val visited = mutableSetOf<Node>(cur)
    while (s.isNotEmpty()) {
        val neighbor = s.removeAt(r.nextInt(s.size))
        if (visited.add(neighbor)) {
            cur.addEdge(neighbor, r.nextInt(maxWeight))
        }
        cur = neighbor
    }
    // add `edges - nodes + 1` random edges
    repeat(edges - nodes + 1) {
        while (true) {
            val first = nodesList[r.nextInt(nodes)]
            val second = nodesList[r.nextInt(nodes)]
            if (first == second) continue
            if (first.outgoingEdges.any { e -> e.to == second }) continue
            val weight = r.nextInt(maxWeight)
            first.addEdge(second, weight)
            second.addEdge(first, weight)
            break
        }
    }
    return nodesList
}
