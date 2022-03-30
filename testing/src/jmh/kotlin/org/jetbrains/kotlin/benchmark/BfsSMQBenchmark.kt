package org.jetbrains.kotlin.benchmark

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodesBfs
import org.jetbrains.kotlin.number.scheduler.PriorityLongBfsScheduler
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@Warmup(iterations = 1)
@Measurement(iterations = 5)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BfsSMQBenchmark{

    @Benchmark
    fun testSequence(config: Config) {
        PriorityLongBfsScheduler(
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

        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125",  "0.015625", "0.0078125", "0.001953125", "0.0009765625")
        var pSteal: Double = 0.2

        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
        var stealSize: Int = 1

        @Param(
            "/USA-road-d.W.gr",
            "/USA-road-d.USA.gr"
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<BfsIntNode>


        @Setup(Level.Trial)
        fun setup() {
            nodes = GraphReader().readGraphNodesBiDirectBfs(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodesBfs(nodes)
        }

    }

}