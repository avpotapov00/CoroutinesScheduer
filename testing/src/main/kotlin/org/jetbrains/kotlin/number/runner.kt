package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.number.scheduler.NonBlockingPriorityLongPageRankScheduler
import kotlin.system.measureTimeMillis

fun main() {
    val graph =
        GraphReader().readGraphFloatNodesBiDirectFromFile("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/testing/src/jmh/resources/USA-road-d.W.gr")

    println("Start!")

    repeat(5) {
        val time = measureTimeMillis {
            val scheduler = NonBlockingPriorityLongPageRankScheduler(
                graph, poolSize = 2, alpha = 0.5f, tolerance = 0.1f,
                pSteal = 1.0, stealSize = 1
            ).use {
                it.waitForTermination()
                it
            }
        }

        println("Time: $time")
    }

}
