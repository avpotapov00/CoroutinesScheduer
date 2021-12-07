package org.jetbrains.kotlin.graph.dijkstra

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.graph.util.nodes.clearNodes
import org.jetbrains.kotlin.graph.util.readGraphNodes
import org.jetbrains.kotlin.scheduler.ExperimentalPriorityCoroutineScheduler
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
open class BenchmarkDijkstraParallel {

    @Benchmark
    fun testSequence(graph: TestGraph, blackhole: Blackhole)  {
        shortestPathParallel(graph.nodes[0], workers = 4)
        blackhole.consume(graph.nodes)
    }

    @State(Scope.Thread)
    open class TestGraph {

        @Param(
            "src/test/resources/data/DCh-Miner_miner-disease-chemical.tsv",
            "src/test/resources/data/twitter_combined.txt"
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<Node>


        @Setup(Level.Trial)
        fun setup() {
            nodes = readGraphNodes(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(nodes)
        }

    }


    @Test
    fun `run benchmark`() {
        val options = OptionsBuilder()
            .include(BenchmarkDijkstraParallel::class.java.simpleName)
            .jvmArgs("-Xms4096M", "-Xmx6144M")
            .build()

        Runner(options).run()
    }
}