package org.jetbrains.kotlin.graph.dijkstra

import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.graph.util.nodes.clearNodes
import org.jetbrains.kotlin.graph.util.readGraphNodes
import org.junit.Test
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
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class SequenceBenchmark {

    @Benchmark
    fun testSequence(graph: TestGraph, blackhole: Blackhole) {
        shortestPathSequential(graph.nodes[0])
        blackhole.consume(graph.nodes)
    }

    @State(Scope.Thread)
    open class TestGraph{

        lateinit var nodes: List<Node>

        @Setup(Level.Iteration)
        fun setup() {
            nodes = readGraphNodes("resources/data/DCh-Miner_miner-disease-chemical.tsv")
        }

        @TearDown(Level.Iteration)
        fun tearDown() {
            clearNodes(nodes)
        }

    }


    @Test
    fun `run benchmark`() {
        val options = OptionsBuilder()
            .include(SequenceBenchmark::class.java.simpleName)
            .build()

        Runner(options).run()
    }
}
