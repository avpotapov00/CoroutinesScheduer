package org.jetbrains.kotlin.graph.pagerank

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.priority.Priority

suspend fun pagerankAsyncPush(nodes: List<Node>, dense: Float, epsilon: Float, dispatcher: CoroutineDispatcher) =
    coroutineScope {

        val initialValue = 1f / nodes.size
        nodes.forEach { it.clear(initialValue) }

        nodes.forEach { node ->
            node.rank = node.incomingEdges.map { 1f / it.outgoingEdges.size }.sum()
            node.rank = (1 - dense) * dense * node.rank
        }

        fun processNode(node: Node) {
            val nodeR = node.rank
            node.rank = 0f

            node.outgoingEdges.forEach { outgoingNode ->

                var newR: Float
                var oldR: Float

                do {
                    oldR = outgoingNode.rank
                    newR = oldR + (nodeR * dense) / node.outgoingEdges.size
                } while (!outgoingNode.casRank(oldR, newR))

                if (newR >= epsilon && oldR < epsilon) {
                    val prior = -(newR * 10_000).toInt()
                    launch(dispatcher + Priority(prior)) { processNode(outgoingNode) }
                }
            }
        }

        nodes.forEach { launch(dispatcher) { processNode(it) } }
    }

