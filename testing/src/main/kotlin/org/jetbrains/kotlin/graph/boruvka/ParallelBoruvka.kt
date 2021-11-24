package org.jetbrains.kotlin.graph.boruvka

import org.jetbrains.kotlin.graph.util.edges.Edge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReferenceArray


fun parallelBoruvka(nodes: Int, edges: List<Edge>, threadsCount: Int): Set<Edge> {
    val threads = (0 until threadsCount).map { Executors.newSingleThreadExecutor() }

    val edgesSet = ConcurrentHashMap.newKeySet<Edge>()
    val mst = ConcurrentHashMap.newKeySet<Edge>()
    val dsu = ParallelDsu(nodes)

    edgesSet.addAll(edges)

    while (mst.size != nodes - 1) {
        val chunkSize = (edgesSet.size / threadsCount) + 1

        val shortestPaths: AtomicReferenceArray<Edge> = AtomicReferenceArray(nodes)

        edgesSet.chunked(chunkSize).mapIndexed { threadIndex, list ->
            threads[threadIndex].submit {
                for (edge in list) {
                    if (dsu.isSameSet(edge.to, edge.from)) {
                        edgesSet.remove(edge)
                        continue
                    }

                    // UpdateIfShorter(&shortestEdges[Find(e.from)], e)
                    do {
                        val index = dsu.find(edge.from)
                        val prevValue = shortestPaths[index]

                        if (prevValue != null && prevValue.weight < edge.weight) {
                            break
                        }
                    } while (!shortestPaths.compareAndSet(index, prevValue, edge))

                    //UpdateIfShorter(&shortestEdges[Find(e.to)], e)
                    do {
                        val index = dsu.find(edge.to)
                        val prevValue = shortestPaths[index]

                        if (prevValue != null && prevValue.weight < edge.weight) {
                            break
                        }
                    } while (!shortestPaths.compareAndSet(index, prevValue, edge))
                }
            }
        }.forEach { it.get() }


        (0 until nodes).chunked(chunkSize).mapIndexed { index, list ->
            threads[index].submit {
                list.forEach { node ->
                    val edge = shortestPaths[node]

                    if (edge != null) {

                        dsu.union(edge.from, edge.to)
                        mst.add(shortestPaths[node])
                    }
                }
            }
        }.forEach { it.get() }
    }

    return mst.also { threads.forEach { it.shutdownNow() } }
}
