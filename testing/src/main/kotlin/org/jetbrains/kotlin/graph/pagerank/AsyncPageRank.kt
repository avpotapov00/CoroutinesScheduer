package org.jetbrains.kotlin.graph.pagerank

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.graph.util.IntPhaser
import org.jetbrains.kotlin.generic.priority.Priority
import kotlin.math.abs

fun pagerankAsyncPush(nodes: List<PageRankIntNode>, dense: Float, epsilon: Float, dispatcher: CoroutineDispatcher) {

    val initialValue = 1f / nodes.size
    nodes.forEach { it.clear(initialValue) }

    val phaser = IntPhaser()

    nodes.forEach { node ->
        node.rank = node.incomingEdges.map { 1f / it.outgoingEdges.size }.sum()
        node.rank = (1 - dense) * dense * node.rank
    }

    fun processNode(node: PageRankIntNode) {
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

                phaser.register()
                GlobalScope.launch(dispatcher + Priority(prior)) {
                    processNode(outgoingNode)
                    phaser.arriveAndDeregister()
                }
            }
        }
    }

    nodes.forEach {
        phaser.register()
        GlobalScope.launch(dispatcher) {
            processNode(it)
            phaser.arriveAndDeregister()
        }
    }

    phaser.lockAndAwait()
}


fun pagerankAsync(nodes: List<PageRankIntNode>, dense: Float, epsilon: Float) {
    val initialValue = 1f / nodes.size
    nodes.forEach { it.clear(initialValue) }

    val phaser = IntPhaser()

    fun processNode(node: PageRankIntNode) {
        val newRank = 1 - dense + dense * node.incomingEdges.map { it.impact }.sum()

        var oldRank = node.rank

        while (abs(newRank - oldRank) > epsilon) {

            if (node.casRank(oldRank, newRank)) {
                node.outgoingEdges.forEach {

                    phaser.register()
                    GlobalScope.launch {
                        processNode(it)
                        phaser.arriveAndDeregister()
                    }

                }
                break
            }

            oldRank = node.rank
        }
    }

    processNode(nodes[0])

    phaser.lockAndAwait()
}
