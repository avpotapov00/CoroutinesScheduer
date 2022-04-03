package org.jetbrains.kotlin.number

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.dijkstra.clearNodesBfs
import org.jetbrains.kotlin.number.scheduler.PriorityLongBfsScheduler
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraScheduler
import java.io.BufferedWriter
import java.io.File
import kotlin.system.measureTimeMillis

fun main() {
//    File("results-metrics.txt").bufferedWriter().use { writer ->
//        for (path in graphs) {
//            runExperimentsDijkstra(path, writer)
//            runExperimentsBFS(path, writer)
//        }
//    }
}

private const val measurementCount = 3
//
//private fun runExperimentsDijkstra(path: String, writer: BufferedWriter) {
//    val nodes = GraphReader().readGraphNodesBiDirect(path)
//    val configs = makeConfig()
//
//    for (config in configs) {
//        runDijkstra(nodes, config)
//        clearNodes(nodes)
//        System.gc()
//
//        val results = arrayListOf<Double>()
//        val times = arrayListOf<Long>()
//
//        for (i in 0 until measurementCount) {
//            val result: Double
//            val time = measureTimeMillis { result = runDijkstra(nodes, config) }
//
//            results.add(result)
//            times.add(time)
//            clearNodes(nodes)
//            System.gc()
//        }
//
//        writer.write("dijkstra,${config.pSteal},${config.stealSize},$path,${
//            results.average().toInt()
//        },${times.average()}")
//
//    }
//}
//
//private fun runExperimentsBFS(path: String, writer: BufferedWriter) {
//    val nodes = GraphReader().readGraphNodesBiDirectBfs(path)
//    val configs = makeConfig()
//
//    for (config in configs) {
//        runBFS(nodes, config)
//        clearNodesBfs(nodes)
//        System.gc()
//
//        val results = arrayListOf<Double>()
//        val times = arrayListOf<Long>()
//
//        for (i in 0 until measurementCount) {
//            val result: Double
//            val time = measureTimeMillis { result = runBFS(nodes, config) }
//
//            results.add(result)
//            times.add(time)
//            clearNodesBfs(nodes)
//            System.gc()
//        }
//
//        writer.write("dijkstra,${config.pSteal},${config.stealSize},$path,${
//            results.average().toInt()
//        },${times.average()}")
//    }
//}
//
//private fun runDijkstra(nodes: List<IntNode>, config: Config) {
//    PriorityLongDijkstraScheduler(
//        nodes,
//        startIndex = 0,
//        poolSize = config.threads,
//        stealSize = config.stealSize,
//        pSteal = config.pSteal
//    ).use {
//        it.waitForTermination()
//    }
//}
//
//private fun runBFS(nodes: List<BfsIntNode>, config: Config) {
//    return PriorityLongBfsScheduler(
//        nodes,
//        startIndex = 0,
//        poolSize = config.threads,
//        stealSize = config.stealSize,
//        pSteal = config.pSteal
//    ).use {
//        it.waitForTermination()
//    }
//}
//
//private data class Config(
//    val pSteal: Double,
//    val stealSize: Int,
//    val threads: Int,
//)
//
//
//private fun makeConfig(): List<Config> {
//    return pSteals.flatMap { pSteal ->
//        stealSizes.map { stealSize ->
//            Config(
//                pSteal = pSteal,
//                stealSize = stealSize,
//                threads = 128)
//        }
//    }
//}
//
//private val pSteals =
//    listOf(1.0, 0.5, 0.25, 0.125, 0.0625, 0.03125, 0.015625, 0.0078125, 0.001953125, 0.0009765625)
//
//private val stealSizes = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256)
//
//private val graphs = listOf("/USA-road-d.W.gr", "/USA-road-d.USA.gr")
