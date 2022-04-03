package org.jetbrains.kotlin.benchmark

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.graph.util.generator.IntNodeGraph
import org.jetbrains.kotlin.graph.util.generator.generateSennit
import org.jetbrains.kotlin.graph.util.generator.separateWithBridges
import org.jetbrains.kotlin.number.scheduler.NonBlockingLongDijkstraScheduler
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class NonBlockingDijkstraSMQBenchmark  {

    @Benchmark
    fun dijkstraTestBlocking(config: Config) {
        NonBlockingLongDijkstraScheduler(
            config.nodes,
            startIndex = 0,
            poolSize = config.threads,
            stealSize = config.stealSize,
            pSteal = config.pSteal
        ).use {
            it.waitForTermination()
        }
    }


    @State(Scope.Thread)
    open class Config {

        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
        var threads: Int = 4

        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
        var pSteal: Double = 0.03125

        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
        var stealSize: Int = 8

        @Param(
            "/USA-road-d.W.gr",
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<IntNode>

        @Setup(Level.Trial)
        fun setup() {
            val fragment = GraphReader().readGraphNodesBiDirect(sourcePath)
            val sennit = generateSennit(50_000 * 3 + 1)

            nodes = separateWithBridges(
                IntNodeGraph(fragment, 0, fragment.size - 1),
                IntNodeGraph(sennit, 0, sennit.size - 1),
                bridgesCount = 1
            )
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(nodes)
        }

    }

}
