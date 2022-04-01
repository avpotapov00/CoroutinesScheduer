package org.jetbrains.kotlin.benchmark

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodesBfs
import org.jetbrains.kotlin.number.scheduler.PriorityLongBfsSchedulerKS
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BfsSMQBenchmark {

    @Benchmark
    fun testSequenceKS(config: Config) {
        PriorityLongBfsSchedulerKS(
            config.nodes,
            startIndex = 0,
            poolSize = config.threads,
            stealSize = config.stealSize,
            pSteal = config.pSteal
        ).use {
            it.waitForTermination()
            addResult(
                BenchmarkResultWithMetrics(
                    "bfs_ks", config.sourcePath, config.pSteal, config.stealSize,
                    it.retrievalsS.sumOf { counter -> counter.value.toLong() },
                    it.successSteals.sumOf { counter -> counter.value.toLong() }
                )
            )
        }
    }

    @State(Scope.Thread)
    open class Config {

        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
        var threads: Int = 128

        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
        var pSteal: Double = 0.2

        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
        var stealSize: Int = 1

        @Param(
            "/USA-road-d.W.gr",
            "/USA-road-d.USA.gr"
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<BfsIntNode>


        @Setup(Level.Trial)
        fun setup() {
            nodes = GraphReader().readGraphNodesBiDirectBfs(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodesBfs(nodes)
        }

        @TearDown(Level.Trial)
        fun printAll() {
            val results = readyResults

            val size = results.size.toDouble()
            val avgSuccess = results.sumOf { it.successSteals } / size
            val avgRetrievals = results.sumOf { it.retrievals } / size
            val config = results.first()

            println("\nDone,${config.testName},${config.pSteal},${config.graphName},${config.stealSize},${avgRetrievals},${avgSuccess}")

            clearResults()
        }

    }

    companion object {

        private val results = ArrayList<BenchmarkResultWithMetrics>()

        val readyResults: List<BenchmarkResultWithMetrics>
            @Synchronized
            get() = results

        @Synchronized
        fun clearResults() {
            results.clear()
        }

        @Synchronized
        fun addResult(result: BenchmarkResultWithMetrics) {
            results.add(result)
        }

    }
}