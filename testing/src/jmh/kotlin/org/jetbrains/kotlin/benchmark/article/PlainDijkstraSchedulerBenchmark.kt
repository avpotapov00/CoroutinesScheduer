package org.jetbrains.kotlin.benchmark.article

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.PlainNonBlockingLongDijkstraScheduler
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class PlainDijkstraSchedulerBenchmark {

//    @Benchmark
//    fun testDijkstra(config: Config) {
//        PlainNonBlockingLongDijkstraScheduler(
//            config.nodes,
//            startIndex = 0,
//            poolSize = config.threads,
//            stealSizeInitialPower = config.stealSize,
//            pStealInitialPower = config.pStealInitialPower,
//        ).use { it.waitForTermination() }
//    }

    @Benchmark
    fun testDijkstra(config: Config) {
        PlainNonBlockingLongDijkstraScheduler(
            config.nodes.first(),
            startIndex = 0,
            poolSize = config.threads,
            stealSizeInitialPower = config.stealSize,
            pStealInitialPower = config.pStealInitialPower,
        ).use { scheduler ->
            scheduler.waitForTermination()
            for (i in 1 until config.nodes.size) {
                val graph = config.nodes[i]
                scheduler.restartWithNextGraph(graph, 0)
                scheduler.waitForTermination()
            }

            repeat(3) {
                config.nodes.forEach { clearNodes(it) }
                for (graph in config.nodes) {
                    scheduler.restartWithNextGraph(graph, 0)
                    scheduler.waitForTermination()
                }
            }
        }
    }


    @State(Scope.Thread)
    open class Config {

        @Param(
//            "2",
//            "4",
//            "8",
//            "16",
//            "32",
            "64"
        )
        var threads: Int = 8

        @Param(
//            "/home/ubuntu/graphs/soc-LiveJournal1.txt",
//            "/home/ubuntu/graphs/USA-road-d.W.gr",
//            "/home/ubuntu/graphs/USA-road-d.W.gr-net-soc-LiveJournal1-3.txt"
//            "/USA-road-d.CTR.gr",
//            "/USA-road-d.USA.gr",
            "/home/ubuntu/graphs/soc-LiveJournal1.txt,/home/ubuntu/graphs/USA-road-d.Full.gr"        )
        lateinit var sourcePath: String

        @Param(
            "0",  // ""1", // 0
//            "1",  // "0.5", // 1
            "2",  // "0.25", // 2
//            "3",  // "0.125", // 3
            "4",  // "0.0625", // 4
//            "5",  // "0.03125", // 5
            "6",  // "0.015625", // 6
//            "7",  // "0.0078125", // 7
//            "8",  // "0.001953125", // 8
//            "9",  // "0.000976[5625" // 9
        )
        var pStealInitialPower: Int = 5

        @Param(
            "0", // ""1",
//            "1", // //            "2",
            "2", // "4",
//            "3", // //            "8",
            "4", // "16",
//            "5", // "32",
            "6", // //            "64",
//            "7", // "128",
            "8", // //            "256",
//            "9",
            "10" // //            "1024"
        )
        var stealSize: Int = 32

        lateinit var nodes: List<List<IntNode>>
//        lateinit var nodes: List<IntNode>

        @Setup(Level.Trial)
        fun setup() {
            nodes = sourcePath.split(",").map { GraphReader().readGraphNodesBiDirectFromFile(it) }
//            nodes = GraphReader().readGraphNodesBiDirectFromFile(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            nodes.forEach { clearNodes(it) }
//            clearNodes(nodes)
        }

    }

}