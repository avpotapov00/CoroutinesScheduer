package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraSchedulerKS
import kotlin.random.Random

fun main() {
    val graph =
        GraphReader().readGraphNodesBiDirectFromFile("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/testing/src/jmh/resources/USA-road-d.W.gr")

    val r = Random(0)

    repeat(1) { i ->
        val from = r.nextInt(graph.size)

        val scheduler = PriorityLongDijkstraSchedulerKS(
            graph, from,
            poolSize = 4, stealSize = 4, pSteal = 1.0
        ).use { scheduler ->
            scheduler.finishPhaser.arrive()

            while (scheduler.finishPhaser.phase != 1) {
                Thread.sleep(100)
                println(scheduler.threads.map { it.state })
                println(scheduler.finishPhaser.registeredParties)
            }

            scheduler
        }
        clearNodes(graph)

        scheduler.apply {
            println("Total: ${totalTasksProcessed()}")
            println("Success: ${successStealing()}")
            println("Failed: ${failedStealing()}")
            println("Attempts: ${stealingAttempts()}")
        }

        println("Ok $i")
    }

}
