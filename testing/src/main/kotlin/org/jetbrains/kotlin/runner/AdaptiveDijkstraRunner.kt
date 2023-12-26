package org.jetbrains.kotlin.runner

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.PlainNonBlockingLongDijkstraScheduler

fun main() {
    run(
        listOf(
            "/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.W.gr",
//            "/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/soc-LiveJournal1.txt",
//            "/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.W.gr-net-soc-LiveJournal1-3.txt"
        )
    )
}

private fun run(graphNames: List<String>) {
    println("Start reading graph...")
    val nodes = GraphReader().readGraphNodesBiDirectFromFile(graphNames.first())
    createPlaneRunner(nodes)
            .use { scheduler ->
                println("Start.")
                println("Waiting...")
                scheduler.waitForTermination()
//            var line = scheduler.threads.joinToString("\n\n") {
//                "Thread: ${it.index}\n" +
//                        "   maxPSteal = ${it.maxPSteal} \n" +
//                        "   minPSteal = ${it.minPSteal} \n" +
//                        "   pStealLocal = ${it.pStealLocal} \n" +
//                        "   pStealPowerLocal = ${it.pStealPowerLocal} \n" +
//                        "   pStealUpdateCount = ${it.pStealUpdateCount} \n" +
//                        "   pStealMomentum = ${it.momentum} \n" +
//                        "   stealSizeLocalPower = ${it.stealSizeLocalPower} \n" +
//                        "   localStealSizeEpoch = ${it.localStealSizeEpoch} \n" +
//                        "   stealSizeUpdateCount = ${it.stealSizeUpdateCount} \n" +
//                        "   maxStealSize = ${it.maxStealSize} \n" +
//                        "   minStealSize = ${it.minStealSize} \n" +
//                        "   metricsHolder = ${it.metricsHolder} \n" +
//                        "   expiredFeedbackReceived = ${it.expiredFeedbackReceived} \n" +
//                        "   totalTasksProcessed = ${it.totalTasksProcessed} \n" +
//                        "   pStealHistory = ${it.pStealHistory.joinToString(", ")} \n" +
//                        "   stealSizeHistory = ${it.stealSizeHistory.joinToString(", ")} \n" +
//                        "   momentumHistory = ${it.momentumHistory.joinToString(", ")} \n"
//            }
//            writer.appendLine(line)
//            writer.appendLine("\n\n============================================================\n\n")
                println("Done.")
                clearNodes(nodes)
                val total = List(1_000) { graphNames[0] }
                total.forEachIndexed { i, graphName ->
//                println("Start reading graph $graphName...")
                    println("Start.")
                    scheduler.restartWithNextGraph(nodes, 0)
                    println("Waiting...")
                    scheduler.waitForTermination()
                    println("Done ${i + 1} / ${total.size}.")

//                line = scheduler.threads.joinToString("\n\n") {
//                    "Thread: ${it.index}\n" +
//                            "   maxPSteal = ${it.maxPSteal} \n" +
//                            "   minPSteal = ${it.minPSteal} \n" +
//                            "   pStealLocal = ${it.pStealLocal} \n" +
//                            "   pStealPowerLocal = ${it.pStealPowerLocal} \n" +
//                            "   pStealUpdateCount = ${it.pStealUpdateCount} \n" +
//                            "   pStealMomentum = ${it.momentum} \n" +
//                            "   stealSizeLocalPower = ${it.stealSizeLocalPower} \n" +
//                            "   localStealSizeEpoch = ${it.localStealSizeEpoch} \n" +
//                            "   stealSizeUpdateCount = ${it.stealSizeUpdateCount} \n" +
//                            "   maxStealSize = ${it.maxStealSize} \n" +
//                            "   minStealSize = ${it.minStealSize} \n" +
//                            "   metricsHolder = ${it.metricsHolder} \n" +
//                            "   expiredFeedbackReceived = ${it.expiredFeedbackReceived} \n" +
//                            "   totalTasksProcessed = ${it.totalTasksProcessed} \n" +
//                            "   pStealHistory = ${it.pStealHistory.joinToString(", ")} \n" +
//                            "   stealSizeHistory = ${it.stealSizeHistory.joinToString(", ")} \n" +
//                            "   momentumHistory = ${it.momentumHistory.joinToString(", ")} \n"
//                }
//                writer.appendLine(line)
                    clearNodes(nodes)
                }
            }

}

fun createParallelRunner(nodes: List<IntNode>) = NonBlockingFullAdaptiveLongDijkstraScheduler(
    nodes = nodes,
    startIndex = 0,
    poolSize = 8,
    stealSizeInitialPower = 4,
    pStealInitialPower = 5,
    learningRate = 0.15,
    initialMomentum = 100.0,
    k1 = 0.8,
    k2 = 1 - 0.8,
    pStealWindow = 1_000,
    stealSizeWindow = 10,
    bufferEfficientFactor = 0.14
)

private fun createPlaneRunner(nodes: List<IntNode>) = PlainNonBlockingLongDijkstraScheduler(
    nodes,
    startIndex = 0,
    poolSize = 8,
    stealSizeInitialPower = 4,
    pStealInitialPower = 4,
)