package org.jetbrains.kotlin.graph.boruvka

import com.google.common.graph.EndpointPair
import com.google.common.graph.MutableValueGraph
import com.google.common.graph.ValueGraphBuilder

@Suppress("UnstableApiUsage")
class BoruvkaMST(graph: MutableValueGraph<Int, Int>) {

    override fun toString(): String {
        return "MST: $mst | Total Weight: $totalWeight"
    }

    val mst = ValueGraphBuilder.undirected().build<Int, Int>()

    var totalWeight = 0

    init {
        val size = graph.nodes().size
        val uf = SequentialDSU(size)

        // repeat at most log N times or until we have N-1 edges
        var t = 1
        while (t < size && mst.edges().size < size - 1) {
            val closestEdgeArray: Array<EndpointPair<Int>?> = arrayOfNulls(size)

            // foreach tree in graph, find closest edge
            for (edge in graph.edges()) {
                val u = edge.nodeU()
                val v = edge.nodeV()
                val uParent: Int = uf.find(u)
                val vParent: Int = uf.find(v)

                if (uParent == vParent) {
                    continue  // same tree
                }
                val weight = graph.edgeValueOrDefault(u, v, 0)!!
                if (closestEdgeArray[uParent] == null) {
                    closestEdgeArray[uParent] = edge
                }
                if (closestEdgeArray[vParent] == null) {
                    closestEdgeArray[vParent] = edge
                }

                val uParentWeight = graph.edgeValueOrDefault(
                        closestEdgeArray[uParent]!!.nodeU(), closestEdgeArray[uParent]!!
                            .nodeV(), 0)!!

                val vParentWeight = graph.edgeValueOrDefault(
                        closestEdgeArray[vParent]!!.nodeU(), closestEdgeArray[vParent]!!
                            .nodeV(), 0)!!

                if (weight < uParentWeight) {
                    closestEdgeArray[uParent] = edge
                }
                if (weight < vParentWeight) {
                    closestEdgeArray[vParent] = edge
                }
            }

            // add newly discovered edges to MST
            for (i in 0 until size) {
                val edge = closestEdgeArray[i]
                if (edge != null) {
                    val u = edge.nodeU()
                    val v = edge.nodeV()
                    val weight = graph.edgeValueOrDefault(u, v, 0)!!
                    // don't add the same edge twice
                    if (uf.find(u) != uf.find(v)) {
                        mst.putEdgeValue(u, v, weight)
                        totalWeight += weight
                        uf.union(u, v)
                    }
                }
                graph.edges()
            }
            t += t
        }
    }
}

class SequentialDSU(
    size: Int
) {
    private val parents = (0 until size).toMutableList()

    private val rank = Array(size) { 0 }

    @Synchronized
    fun find(u: Int): Int {
        var current = u

        while (true) {
            val p = parents[current]
            val gp = parents[p]

            if (p == gp) return p

            parents[current] = gp
            current = gp
        }
    }

    @Synchronized
    fun union(u: Int, v: Int) {
        val first = find(u)
        val second = find(v)

        if (first == second) return

        if (rank[first] <= rank[second]) {
            parents[first] = second
        } else if (rank[first] > rank[second]) {
            parents[second] = first
        }

        if (rank[first] == rank[second]) {
            rank[second]++
        }
    }

    @Synchronized
    fun isSameSet(to: Int, from: Int): Boolean {
        return find(to) == find(from)
    }

}

