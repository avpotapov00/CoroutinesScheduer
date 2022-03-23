package org.jetbrains.kotlin.benchmark

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.PriorityLongDijkstraScheduler
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit


@Warmup(iterations = 1)
@Measurement(iterations = 100)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class DijkstraSMQBenchmark {

    @Benchmark
    fun testSequence(config: Config) {
        PriorityLongDijkstraScheduler(
            config.nodes,
            startIndex = 0,
            poolSize = config.threads,
            stealSize = config.stealSize,
            pSteal = config.pSteal
        ).use { it.waitForTermination() }
    }

    @State(Scope.Thread)
    open class Config {

//        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
        var threads: Int = 128

//        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125")
        var pSteal: Double = 0.03125

//        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
        var stealSize: Int = 8

        @Param(
            "/USA-road-d.W.gr",
//            "/USA-road-d.USA.gr"
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<IntNode>


        @Setup(Level.Trial)
        fun setup() {
            nodes = GraphReader().readGraphNodesBiDirect(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(nodes)
        }

    }

}