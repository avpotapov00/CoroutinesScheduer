package org.jetbrains.kotlin.benchmark.article

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongDijkstraScheduler
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class FullAdaptiveDijkstraSchedulerBenchmark {

    @Benchmark
    fun testDijkstra(config: Config) {
        NonBlockingFullAdaptiveLongDijkstraScheduler(
            config.nodes,
            startIndex = 0,
            poolSize = config.threads,
            stealSizeInitialPower = config.stealSize,
            pStealInitialPower = config.pStealInitialPower,
            learningRate = config.learningRate,
            initialMomentum = config.initialMomentum,
            k1 = config.k1,
            k2 = 1 - config.k1,
            pStealWindow = config.pStealWindow,
            stealSizeWindow = config.stealSizeWindow,
            bufferEfficientFactor = config.bufferEfficientFactor
        ).use { scheduler ->
            scheduler.waitForTermination()
            scheduler
        }
    }


    @State(Scope.Thread)
    open class Config {

        @Param(
            "2",
            "4",
            "8",
            "16",
            "32",
            "64"
        )
        var threads: Int = 8

        @Param(
//            "/home/admin/graphs/soc-LiveJournal1.txt",
//            "/home/admin/graphs/USA-road-d.W.gr",
            "/home/admin/graphs/USA-road-d.W.gr-net-soc-LiveJournal1-3.txt"
//            "/USA-road-d.CTR.gr",
//            "/USA-road-d.USA.gr",
        )
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
            "8",  // "0.001953125", // 8
//            "9",  // "0.0009765625" // 9
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

        @Param(
            "0.8"//, "0.75", "0.7", "0.65", "0.5"
        )
        var k1: Double = 0.7

        @Param(
            "0.15"//, "0.1", "0.05", "0.001",
        )
        var learningRate: Double = 0.1


        @Param(
            "100.0"//, "10.0"
        )
        var initialMomentum: Double = 100.0

        @Param(
            "10",
        )
        var stealSizeWindow: Int = 10

        @Param(
            "1000",
        )
        var pStealWindow: Int = 1000

        @Param(
            "0.14",
        )
        var bufferEfficientFactor: Double = 0.14

        lateinit var nodes: List<IntNode>

        @Setup(Level.Trial)
        fun setup() {
            nodes = GraphReader().readGraphNodesBiDirectFromFile(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(nodes)
        }
    }

}