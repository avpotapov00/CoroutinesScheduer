@file:Suppress("UnstableApiUsage")

package org.jetbrains.kotlin.graph.boruvka

import com.google.common.graph.MutableValueGraph
import com.google.common.graph.ValueGraphBuilder
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.coroutines.*
import org.jetbrains.kotlin.generic.priority.Priority
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.util.edges.Edge
import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.graph.util.nodes.Node
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


suspend fun asyncBoruvkaGlobalScope(nodes: Int, edges: List<Edge>): Set<Edge> {

    val edgesSet = ConcurrentHashMap.newKeySet<Edge>()
    val mst = ConcurrentHashMap.newKeySet<Edge>()
    val dsu = ParallelDsu(nodes)

    edgesSet.addAll(edges)

    while (mst.size != nodes - 1) {

        val shortestPaths: AtomicReferenceArray<Edge> = AtomicReferenceArray(nodes)

        edgesSet.map { edge ->
            GlobalScope.launch {
                if (dsu.isSameSet(edge.to, edge.from)) {
                    edgesSet.remove(edge)
                    return@launch
                }

                do {
                    val index = dsu.find(edge.from)
                    val prevValue = shortestPaths[index]

                    if (prevValue != null && prevValue.weight < edge.weight) {
                        break
                    }
                } while (!shortestPaths.compareAndSet(index, prevValue, edge))

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
            GlobalScope.launch {
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

fun intNodesToGuavaGraph(nodes: List<IntNode>): MutableValueGraph<Int, Int> {
    val graph = ValueGraphBuilder.undirected().build<Int, Int>()
    val indexedNodes = nodes.withIndex()

    indexedNodes.forEach { graph.addNode(it.index) }

    indexedNodes.forEach { (indexFrom, node) ->
        node.outgoingEdges.forEach { (indexTo, w) ->
            graph.putEdgeValue(indexFrom, indexTo, w)
        }
    }

    return graph
}

fun intNodesToNodes(nodes: List<IntNode>): List<Node> {
    val indexedNodes = nodes.withIndex().toList()

    val result = indexedNodes.map { Node() }
    indexedNodes.forEach { (indexFrom, node) ->
        node.outgoingEdges.forEach { (indexTo, w) ->
            val from = result[indexFrom]
            val to = result[indexTo]

            result[indexFrom].addEdge(to, w)
            result[indexTo].addEdge(from, w)
        }
    }

    return result
}

fun intNodesToEdges(nodes: List<IntNode>): Graph {
    val visited = hashSetOf<Int>()

    val indexedNodes = nodes.withIndex().toList()
    val result = arrayListOf<Edge>()

    indexedNodes.forEach { (indexFrom, node) ->
        node.outgoingEdges.filter { (indexTo, _) -> indexTo !in visited }
            .forEach { (indexTo, w) ->
                result.add(Edge(indexFrom, indexTo, w))
        }

        visited.add(indexFrom)
    }

    return Graph(nodes.size, result)
}