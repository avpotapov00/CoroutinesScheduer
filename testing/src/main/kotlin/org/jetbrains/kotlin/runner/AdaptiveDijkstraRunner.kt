package org.jetbrains.kotlin.runner

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongDijkstraScheduler

fun main() {
    val nodes =
        GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/USA-road-d.W.gr-net-soc-LiveJournal1-3.txt")
    println("Graph read done")
    val k1 = 0.8

    NonBlockingFullAdaptiveLongDijkstraScheduler(
        nodes = nodes,
        startIndex = 0,
        poolSize = 8,
        stealSizeInitialPower = 4,
        pStealInitialPower = 5,
        learningRate = 0.15,
        initialMomentum = 100.0,
        k1 = k1,
        k2 = 1 - k1,
        pStealWindow = 1000,
        stealSizeWindow = 10,
        bufferEfficientFactor = 0.14
    ).use { it.waitForTermination() }
}