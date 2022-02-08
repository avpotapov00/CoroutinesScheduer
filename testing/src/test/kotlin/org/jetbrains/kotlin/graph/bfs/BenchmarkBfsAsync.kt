package org.jetbrains.kotlin.graph.bfs


import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.graph.util.nodes.Node
import org.jetbrains.kotlin.graph.util.nodes.clearNodes
import org.jetbrains.kotlin.graph.util.readGraphNodesBiDirect
import org.jetbrains.kotlin.scheduler.SMQPriorityCoroutineScheduler
import org.junit.jupiter.api.Test
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BenchmarkBfsAsync {

    @Benchmark
    fun asyncBFSTest(graph: TestGraph) = runBlocking {
        asyncBFS(graph.dispatcher, graph.nodes[0])
    }

    @Benchmark
    fun asyncBFSTestGlobalScope(graph: TestGraph) = runBlocking {
        asyncBFSGlobalScope(graph.dispatcher, graph.nodes[0])
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

        lateinit var nodes: List<Node>

        lateinit var dispatcher: CoroutineDispatcher

        private lateinit var scheduler: SMQPriorityCoroutineScheduler

        @Setup(Level.Trial)
        fun setup() {
            scheduler = SMQPriorityCoroutineScheduler(threads, postponeThreadsStart = true, pSteal = pSteal)
            dispatcher = PriorityQueueCoroutineDispatcher(scheduler)
            nodes = readGraphNodesBiDirect(sourcePath)
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
            .include(BenchmarkBfsAsync::class.java.simpleName)
            .jvmArgs("-Xms4096M", "-Xmx6144M")
            .build()

        Runner(options).run()
    }
}