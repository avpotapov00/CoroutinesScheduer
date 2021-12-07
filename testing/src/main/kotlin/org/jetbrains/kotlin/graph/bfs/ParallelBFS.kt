package org.jetbrains.kotlin.graph.bfs

import org.jetbrains.kotlin.graph.util.nodes.NODE_DISTANCE_COMPARATOR
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.graph.dijkstra.ParallelMultiQueue
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

fun bfsParallel(
    start: Node,
    workers: Int = Runtime.getRuntime().availableProcessors()
) {
    start.distance = 0
    val q = ParallelMultiQueue(workers, NODE_DISTANCE_COMPARATOR)
    q.add(start)
    val onFinish = Phaser(workers + 1)
    val number = AtomicInteger(1)

    repeat(workers) {
        thread {
            while (number.get() > 0) {

                val cur: Node = q.poll() ?: continue
                for (e in cur.outgoingEdges) {

                    while (cur.distance + 1 < e.to.distance) {
                        val currDist = cur.distance
                        val toDist = e.to.distance
                        val nextDist = currDist + 1

                        if (toDist > nextDist && e.to.casDistance(toDist, nextDist)) {
                            number.incrementAndGet()
                            q.add(e.to)
                            break
                        }
                    }
                }
                number.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}