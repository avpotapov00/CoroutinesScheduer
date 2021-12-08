package org.jetbrains.kotlin.graph.bfs

import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.graph.util.nodes.clearNodes
import org.jetbrains.kotlin.graph.util.readGraphNodesBiDirect
import org.junit.jupiter.api.Test
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Threads(8)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BenchmarkBfsParallel {

    @Benchmark
    fun testSequence(graph: TestGraph, blackhole: Blackhole) {
        bfsParallel(graph.nodes[0])
        blackhole.consume(graph.nodes)
    }

    @State(Scope.Thread)
    open class TestGraph {

        @Param(
            "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv",
            "src/test/resources/data/graphs/twitter_combined.txt",
            "src/test/resources/data/graphs/musae_DE_edges_ok.csv"
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<Node>

        @Setup(Level.Trial)
        fun setup() {
            nodes = readGraphNodesBiDirect(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(nodes)
        }

    }


    @Test
    fun `run benchmark`() {
        val options = OptionsBuilder()
            .include(BenchmarkBfsParallel::class.java.simpleName)
            .jvmArgs("-Xms4096M", "-Xmx6144M")
            .build()

        Runner(options).run()
    }
}