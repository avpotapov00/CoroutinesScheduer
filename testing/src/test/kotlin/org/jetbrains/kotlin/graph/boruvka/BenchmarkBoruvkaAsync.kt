package org.jetbrains.kotlin.graph.boruvka

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.graph.util.edges.Graph
import org.jetbrains.kotlin.graph.util.readGraphEdges
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
@Threads(1)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BenchmarkBoruvkaAsync {

    @Benchmark
    fun asyncBoruvkaTest(graph: TestGraph) = runBlocking {
        asyncBoruvka(graph.graph.nodes, graph.graph.edges, graph.dispatcher)
    }

    @Benchmark
    fun asyncBoruvkaGlobalScopeTest(graph: TestGraph) = runBlocking {
        asyncBoruvkaGlobalScope(graph.graph.nodes, graph.graph.edges, graph.dispatcher)
    }

    @State(Scope.Thread)
    open class TestGraph {

        @Param(
            "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv",
            "src/test/resources/data/graphs/twitter_combined.txt",
            "src/test/resources/data/graphs/musae_DE_edges_ok.csv",
        )
        lateinit var sourcePath: String

        @Param(
            "1", "2", "3", "4", "5", "6"
        )
        private var threads: Int = 0

        @Param("0.1" ,"0.2", "0.04", "0.016")
        private var pSteal = 0.1

        lateinit var graph: Graph

        lateinit var dispatcher: CoroutineDispatcher

        private lateinit var scheduler: ExperimentalPriorityCoroutineScheduler

        @Setup(Level.Trial)
        fun setup() {
            scheduler = ExperimentalPriorityCoroutineScheduler(threads, startThreads = true, pSteal = pSteal)
            dispatcher = PriorityQueueCoroutineDispatcher(scheduler)
            graph = readGraphEdges(sourcePath)
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