package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.clearNodesBfs
import org.jetbrains.kotlin.graph.dijkstra.shortestPathSequentialLong
import org.jetbrains.kotlin.number.scheduler.AdaptiveBfsScheduler
import org.jetbrains.kotlin.number.scheduler.NonBlockingLongDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraSchedulerKS
import kotlin.system.measureTimeMillis

fun main() {
    println("Start!")

    val graph =
        GraphReader().readGraphNodesBiDirectFromFileBfs("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/testing/src/jmh/resources/USA-road-d.W.gr")

    println("Real start!")

    repeat(5) {
        val time = measureTimeMillis {
            val scheduler = AdaptiveBfsScheduler(
                graph, pSteal = 1.0, stealSize = 1,
                poolSize = 8, startIndex = 0
            ).use {
                it.waitForTermination()
                it
            }

            println(scheduler.threads.map { it.stealSizeLocal })
            println("===")
        }

        println("Time: $time")
        clearNodesBfs(graph)
    }

}
