package org.jetbrains.kotlin.graph.bfs


import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.graph.dijkstra.asyncDijkstra
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
@Threads(8)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BenchmarkBfsDchMiner {

    @Benchmark
    fun testSequence(graph: TestGraph, blackhole: Blackhole) = runBlocking {
        asyncBFS(graph.dispatcher, graph.nodes[0])
        blackhole.consume(graph.nodes)
    }

    @State(Scope.Thread)
    open class TestGraph {

        lateinit var nodes: List<Node>

        lateinit var dispatcher: CoroutineDispatcher

        private lateinit var scheduler: ExperimentalPriorityCoroutineScheduler

        @Setup(Level.Trial)
        fun setup() {
            scheduler = ExperimentalPriorityCoroutineScheduler(4, startThreads = true, pSteal = 0.05)
            dispatcher = PriorityQueueCoroutineDispatcher(scheduler)
            nodes = readGraphNodes("src/test/resources/data/DCh-Miner_miner-disease-chemical.tsv")
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(nodes)
        }

        @TearDown(Level.Trial)
        fun closeDispatcher() {
            scheduler.close()
        }

    }


    @Test
    fun `run benchmark`() {
        val options = OptionsBuilder()
            .include(BenchmarkBfsDchMiner::class.java.simpleName)
            .build()

        Runner(options).run()
    }
}