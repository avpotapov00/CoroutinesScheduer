package org.jetbrains.kotlin.graph.util.nodes

import kotlinx.atomicfu.atomic

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
