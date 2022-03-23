package org.jetbrains.kotlin.graph.boruvka

import java.util.concurrent.atomic.AtomicReferenceArray


private data class RankParent(
    val rank: Int,
    val parent: Int
)

class ParallelDsu(
    size: Int
) {

    private val a = AtomicReferenceArray<RankParent>(size)

    init {
        (0 until size).forEach { a.set(it, RankParent(1, it)) }
    }

    fun union(first: Int, second: Int) {
        while (true) {
            val rpU = findRankParent(first)
            val rpV = findRankParent(second)

            val (ru, u) = rpU
            val (rv, v) = rpV

            if (u == v) return

            if (ru < rv) {
                if (a.compareAndSet(u, rpU, rpV)) return

            } else if (ru > rv) {
                if (a.compareAndSet(v, rpV, rpU)) return

            } else { // ru == rv

                if (u < v && a.compareAndSet(u, rpU, rpV)) {
                    a.compareAndSet(v, rpV, RankParent(rpV.rank + 1, rpV.parent))
                    return
                }

                if (u > v && a.compareAndSet(v, rpV, rpU)) {
                    a.compareAndSet(u, rpU, RankParent(rpU.rank + 1, rpU.parent))
                    return
                }
            }
        }
    }

    fun find(u: Int) = findRankParent(u).parent

    private fun findRankParent(u: Int): RankParent {
        val p = a[u]
        if (isRank(p, u)) return p

        val nextRP = findRankParent(p.parent)

        if (p.parent != nextRP.parent) {
            a.compareAndSet(u, p, nextRP)
        }

        return nextRP
    }

    fun isSameSet(u: Int, v: Int): Boolean {
        while (true) {
            val (_, parentU) = findRankParent(u)
            val (_, parentV) = findRankParent(v)

            if (parentU == parentV) return true

            if (isRank(a.get(parentU), parentU)) return false
        }
    }

    private fun isRank(rankParent: RankParent, u: Int): Boolean {
        return rankParent.parent == u
    }

}