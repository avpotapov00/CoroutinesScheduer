package org.jetbrains.kotlin.graph.boruvka

import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.graph.util.readGraphEdges
import org.junit.jupiter.api.Test
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Threads(4)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BenchmarkBoruvkaParallel {

    @Benchmark
    fun testSequence(graph: TestGraph, blackhole: Blackhole) {
        parallelBoruvka(graph.graph.nodes, graph.graph.edges, 4)
        blackhole.consume(graph.graph)
    }

    @State(Scope.Thread)
    open class TestGraph {

        @Param(
            "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv",
            "src/test/resources/data/graphs/twitter_combined.txt"
        )
        lateinit var sourcePath: String

        lateinit var graph: Graph


        @Setup(Level.Trial)
        fun setup() {
            graph = readGraphEdges(sourcePath)
        }

    }


    @Test
    fun `run benchmark`() {
        val options = OptionsBuilder()
            .include(BenchmarkBoruvkaParallel::class.java.simpleName)
            .jvmArgs("-Xms4096M", "-Xmx6144M")
            .build()

        Runner(options).run()
    }
}