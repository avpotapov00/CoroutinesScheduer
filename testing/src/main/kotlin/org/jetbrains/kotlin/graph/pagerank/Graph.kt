package org.jetbrains.kotlin.graph.pagerank

import org.jetbrains.kotlin.graph.util.nodes.RankedNode

val NODE_RANK_COMPARATOR = Comparator<PageRankIntNode> { o1, o2 -> o1.rank.compareTo(o2.rank) }

open class PageRankIntNode(
    initialRank: Float
): RankedNode<Float> {
    val outgoingEdges = arrayListOf<PageRankIntNode>()

    val incomingEdges = arrayListOf<PageRankIntNode>()

    private val _rank = AtomicFloat(initialRank)
    override var rank
        get() = _rank.value
        set(value) {
            _rank.value = value
        }

    val impact: Float
        get() = _rank.value / outgoingEdges.size

    fun casRank(cur: Float, update: Float) = _rank.compareAndSet(cur, update)

    fun addEdge(to: PageRankIntNode) {
        to.incomingEdges.add(this)
        outgoingEdges.add(to)
    }

    fun clear(value: Float) {
        _rank.value = value
    }
}

fun clearNodes(value: Float, nodes: List<PageRankIntNode>) {
    nodes.forEach { it.clear(value) }
}
