package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.atomicfu.atomic
import org.jetbrains.kotlin.generic.smq.IndexedThread
import org.jetbrains.kotlin.generic.smq.IndexedThreadImpl
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.number.smq.StealingIntMultiQueue
import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class IntNode {
    private val _distance = atomic(Integer.MAX_VALUE)

    var distance
        get() = _distance.value
        set(value) {
            _distance.value = value
        }

    fun casDistance(cur: Int, update: Int) = _distance.compareAndSet(cur, update)

    val outgoingEdges: List<IntEdge> get() = edges

    private val edges: MutableList<IntEdge> = arrayListOf()

    fun addEdge(to: Int, weight: Int) {
        edges.add(IntEdge(to, weight))
    }

}

data class IntEdge(
    val to: Int,
    val weight: Int
)

fun dijkstraParallelInt(
    nodes: List<IntNode>,
    startIndex: Int,
    workers: Int = Runtime.getRuntime().availableProcessors()
) {
    val start = nodes[startIndex]
    start.distance = 0

    val q = StealingIntMultiQueue(stealSize = 4, pSteal = 0.04, threads = workers)
    q.insertGlobal(startIndex)

    val onFinish = Phaser(workers + 1)
    val number = AtomicInteger(1)

    repeat(workers) { id ->

        IndexedThreadImpl(id) {
            while (number.get() > 0) {

                val curIndex: Int = q.delete() ?: continue
                val cur = nodes[curIndex]

                for (e in cur.outgoingEdges) {

                    val to = nodes[e.to]

                    while (cur.distance + e.weight < to.distance) {
                        val currDist = cur.distance
                        val toDist = to.distance
                        val nextDist = currDist + e.weight

                        if (toDist > nextDist && to.casDistance(toDist, nextDist)) {
                            number.incrementAndGet()

                            q.insert(e.to)
                            break
                        }
                    }
                }
                number.decrementAndGet()
            }
            onFinish.arrive()
        }.start()
    }

    onFinish.arriveAndAwaitAdvance()
}

