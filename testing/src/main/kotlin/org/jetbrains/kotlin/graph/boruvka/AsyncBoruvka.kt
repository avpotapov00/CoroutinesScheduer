package org.jetbrains.kotlin.graph.boruvka

import kotlinx.coroutines.*
import org.jetbrains.kotlin.graph.util.edges.Edge
import org.jetbrains.kotlin.priority.Priority
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReferenceArray

suspend fun asyncBoruvka(nodes: Int, edges: List<Edge>, dispatcher: CoroutineDispatcher): Set<Edge> = coroutineScope {

    val edgesSet = ConcurrentHashMap.newKeySet<Edge>()
    val mst = ConcurrentHashMap.newKeySet<Edge>()
    val dsu = ParallelDsu(nodes)

    edgesSet.addAll(edges)

    while (mst.size != nodes - 1) {

        val shortestPaths: AtomicReferenceArray<Edge> = AtomicReferenceArray(nodes)

        edgesSet.map { edge ->
            launch(dispatcher + Priority(edge.weight)) {
                if (dsu.isSameSet(edge.to, edge.from)) {
                    edgesSet.remove(edge)
                    return@launch
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
        }.joinAll()


        (0 until nodes).map { node ->
            launch(dispatcher + Priority(0)) {
                val edge = shortestPaths[node]

                if (edge != null) {

                    dsu.union(edge.from, edge.to)
                    mst.add(shortestPaths[node])
                }
            }
        }.joinAll()

    }

    return@coroutineScope mst
}



suspend fun asyncBoruvkaGlobalScope(nodes: Int, edges: List<Edge>, dispatcher: CoroutineDispatcher): Set<Edge>  {

    val edgesSet = ConcurrentHashMap.newKeySet<Edge>()
    val mst = ConcurrentHashMap.newKeySet<Edge>()
    val dsu = ParallelDsu(nodes)

    edgesSet.addAll(edges)

    while (mst.size != nodes - 1) {

        val shortestPaths: AtomicReferenceArray<Edge> = AtomicReferenceArray(nodes)

        edgesSet.map { edge ->
            GlobalScope.launch(dispatcher + Priority(edge.weight)) {
                if (dsu.isSameSet(edge.to, edge.from)) {
                    edgesSet.remove(edge)
                    return@launch
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
        }.joinAll()


        (0 until nodes).map { node ->
            GlobalScope.launch (dispatcher + Priority(0)) {
                val edge = shortestPaths[node]

                if (edge != null) {

                    dsu.union(edge.from, edge.to)
                    mst.add(shortestPaths[node])
                }
            }
        }.joinAll()

    }

    return mst
}
