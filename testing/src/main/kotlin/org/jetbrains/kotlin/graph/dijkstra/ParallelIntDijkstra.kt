package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.graph.pagerank.AtomicFloat
import java.util.*

data class IntNode(
    private val edges: MutableList<IntEdge> = arrayListOf()
) {
    private val _distance = atomic(Integer.MAX_VALUE)

    var distance
        get() = _distance.value
        set(value) {
            _distance.value = value
        }

    fun casDistance(cur: Int, update: Int) = _distance.compareAndSet(cur, update)

    val outgoingEdges: List<IntEdge> get() = edges

    fun addEdge(to: Int, weight: Int) {
        edges.add(IntEdge(to, weight))
    }
}

class FloatNode(
    initial: Float
) {
    private val _residual = AtomicFloat(initial)

    var residual
        get() = _residual.value
        set(value) {
            _residual.value = value
        }

    fun exchange(nextValue: Float): Float {
        var prev: Float
        do {
            prev = _residual.value
        } while (!_residual.compareAndSet(prev, nextValue))

        return prev
    }

    fun atomicAdd(value: Float): Float {
        var current: Float
        do {
          current = _residual.value
        } while (!_residual.compareAndSet(current, current + value))

        return current
    }

    var nodesCount = 0

    fun setNodesCount() {
        nodesCount = outgoingEdges.size
    }

    val outgoingEdges: List<IntEdge> get() = edges

    private val edges: MutableList<IntEdge> = arrayListOf()

    fun addEdge(to: Int, weight: Int) {
        edges.add(IntEdge(to, weight))
    }

    override fun toString(): String {
        return "Node(${edges.joinToString(separator = ",") { "{${it.to},${it.weight}}" }})"
    }

}

class BfsIntNode {
    private val _distance = atomic(Integer.MAX_VALUE)

    var distance
        get() = _distance.value
        set(value) {
            _distance.value = value
        }

    fun casDistance(cur: Int, update: Int) = _distance.compareAndSet(cur, update)

    val outgoingEdges: List<Int> get() = edges

    private val edges: MutableList<Int> = arrayListOf()

    fun addEdge(to: Int) {
        edges.add(to)
    }

    override fun toString(): String {
        return "Node(${edges.joinToString(separator = ",") { "{${it}}" }})"
    }

}

data class IntEdge(
    val to: Int,
    val weight: Int
)


fun randomConnectedIntGraph(
    nodes: Int,
    edges: Int,
    maxWeight: Int = 100,
): List<IntNode> {
    require(edges >= nodes - 1)
    val r = Random()
    val nodesList = List(nodes) { IndexedIntNode(it, IntNode()) }
    // generate a random connected graph with `nodes-1` edges
    val s = ArrayList(nodesList)
    var cur = s.removeAt(r.nextInt(s.size))
    val visited = mutableSetOf<IndexedIntNode>(cur)

    while (s.isNotEmpty()) {
        val neighbor = s.removeAt(r.nextInt(s.size))
        if (visited.add(neighbor)) {
            val weight = r.nextInt(maxWeight)

            cur.node.addEdge(neighbor.index, weight)
            neighbor.node.addEdge(cur.index, weight)
        } else {
            error("Visited")
        }
        cur = neighbor
    }
    // add `edges - nodes + 1` random edges
    repeat(edges - nodes + 1) {
        while (true) {
            val first = nodesList[r.nextInt(nodes)]
            val second = nodesList[r.nextInt(nodes)]
            if (first == second) continue
            if (first.node.outgoingEdges.any { e -> e.to == second.index }) continue
            val weight = r.nextInt(maxWeight)
            first.node.addEdge(second.index, weight)
            second.node.addEdge(first.index, weight)
            break
        }
    }
    return nodesList.map { it.node }
}

fun clearNodes(nodes: List<IntNode>) {
    nodes.forEach { it.distance = Int.MAX_VALUE }
}

fun clearNodesBfs(nodes: List<BfsIntNode>) {
    nodes.forEach { it.distance = Int.MAX_VALUE }
}

private data class IndexedIntNode(
    val index: Int,
    val node: IntNode
)

fun shortestPathSequentialLong(nodes: List<IntNode>, start: Int) {
    val comparator = Comparator<IntNode> { o1, o2 -> o1.distance.compareTo(o2.distance) }

    nodes[start].distance = 0
    val q = PriorityQueue(comparator)
    q.add(nodes[start])

    while (q.isNotEmpty()) {

        val cur = q.poll()
        for (e in cur.outgoingEdges) {

            if (nodes[e.to].distance > cur.distance + e.weight) {
                nodes[e.to].distance = cur.distance + e.weight
                q.remove(nodes[e.to])
                q.add(nodes[e.to])
            }
        }
    }
}


fun main() {
    val graph = randomConnectedIntGraph(4, 4)

    graph.forEachIndexed { index, node ->
        println("Node: $index")
        println("Edges: ${node.outgoingEdges.joinToString(",") { "[${it.to} ${it.weight}]" }}")
        println()
    }
}