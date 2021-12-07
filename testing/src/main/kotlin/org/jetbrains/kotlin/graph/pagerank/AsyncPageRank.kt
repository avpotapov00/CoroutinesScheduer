package org.jetbrains.kotlin.graph.pagerank

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.priority.Priority
import kotlin.math.abs

suspend fun pagerankAsync(nodes: List<Node>, dense: Float, epsilon: Float, dispatcher: CoroutineDispatcher) =
    coroutineScope {

        val initialValue = 1f / nodes.size
        nodes.forEach { it.clear(initialValue) }

        fun processNode(node: Node) {
            val newRank = 1 - dense + dense * node.outgoingEdges.map { it.impact }.sum()

            var oldRank = node.rank

            while (abs(newRank - oldRank) > epsilon) {

                if (node.casRank(oldRank, newRank)) {
                    node.outgoingEdges.forEach {
                        launch(dispatcher + Priority((newRank * 1000).toInt())) {
                            processNode(it)
                        }
                    }
                    break
                }

                oldRank = node.rank
            }
        }

        processNode(nodes[0])
    }
