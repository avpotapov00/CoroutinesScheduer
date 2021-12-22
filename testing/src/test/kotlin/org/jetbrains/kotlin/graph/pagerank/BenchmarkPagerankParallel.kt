package org.jetbrains.kotlin.graph.pagerank

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.scheduler.ExperimentalPriorityCoroutineScheduler
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
open class BenchmarkPagerankParallel {

    @Benchmark
    fun testSequence(config: Config) = runBlocking {
        pagerankParallelPush(config.nodes, 0.85f, 0.01f, config.threads)
    }

    @State(Scope.Thread)
    open class Config {

        @Param(
            "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv",
            "src/test/resources/data/graphs/twitter_combined.txt",
            "src/test/resources/data/graphs/musae_DE_edges_ok.csv",
        )
        lateinit var sourcePath: String

        @Param(
            "1", "2", "3", "4", "5", "6"
        )
        var threads: Int = 0

        lateinit var nodes: List<Node>

        @Setup(Level.Trial)
        fun setup() {
            nodes = readGraphNodes(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(0f, nodes)
        }

    }

    @Test
    fun `run benchmark`() {
        val options = OptionsBuilder()
            .include(BenchmarkPagerankParallel::class.java.simpleName)
            .jvmArgs("-Xms4096M", "-Xmx6144M")
            .build()

        Runner(options).run()
    }
}