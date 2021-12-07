package org.jetbrains.kotlin.graph.boruvka

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.graph.util.readGraph
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
open class BenchmarkBoruvkaAsync {

    @Benchmark
    fun testSequence(graph: TestGraph, blackhole: Blackhole) = runBlocking {
        asyncBoruvka(graph.graph.nodes, graph.graph.edges, graph.dispatcher)
        blackhole.consume(graph.graph)
    }

    @State(Scope.Thread)
    open class TestGraph {

        @Param(
            "src/test/resources/data/DCh-Miner_miner-disease-chemical.tsv",
            "src/test/resources/data/twitter_combined.txt"
        )
        lateinit var sourcePath: String

        lateinit var graph: Graph

        lateinit var dispatcher: CoroutineDispatcher

        private lateinit var scheduler: ExperimentalPriorityCoroutineScheduler

        @Setup(Level.Trial)
        fun setup() {
            scheduler = ExperimentalPriorityCoroutineScheduler(4, startThreads = true, pSteal = 0.05)
            dispatcher = PriorityQueueCoroutineDispatcher(scheduler)
            graph = readGraph(sourcePath)
        }

        @TearDown(Level.Trial)
        fun closeDispatcher() {
            scheduler.close()
        }

    }


    @Test
    fun `run benchmark`() {
        val options = OptionsBuilder()
            .include(BenchmarkBoruvkaAsync::class.java.simpleName)
            .jvmArgs("-Xms4096M", "-Xmx6144M")
            .build()

        Runner(options).run()
    }
}