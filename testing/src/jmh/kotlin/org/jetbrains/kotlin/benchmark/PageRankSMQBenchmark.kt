package org.jetbrains.kotlin.benchmark

import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.FloatNode
import org.jetbrains.kotlin.number.scheduler.NonBlockingPriorityLongPageRankScheduler
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit


@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class PageRankSMQBenchmark {

    @Benchmark
    fun testPageRank(config: Config) {
        val scheduler = NonBlockingPriorityLongPageRankScheduler(
            config.nodes,
            poolSize = config.threads,
            stealSize = config.stealSize,
            pSteal = config.pSteal,
            alpha = 0.85f, tolerance = 0.001f,
        ).use {
            it.waitForTermination()
            it
        }

        addResultPageRank(
            BenchmarkResultWithMetrics(
                "pagerank", config.sourcePath, config.pSteal, config.stealSize,
                stealingAttempts = scheduler.stealingAttempts(),
                failedStealing = scheduler.failedStealing(),
                totalTasksProcessed = scheduler.totalTasksProcessed(),
                successStealing = scheduler.successStealing(),
                tasksBetterThanTop = scheduler.tasksLowerThanStolen()
            )
        )
    }


    @State(Scope.Thread)
    open class Config {

        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
        var threads: Int = 128

        //        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
        @Param("1", "0.25", "0.0625", "0.015625", "0.001953125", "0.0009765625")
        var pSteal: Double = 0.03125

        @Param("2", "8", "32", "128", "512", "1024")
        var stealSize: Int = 8

        @Param(
//            "/USA-road-d.CTR.gr",
//            "/soc-LiveJournal1.txt",
            "/USA-road-d.W.gr",
//            "/USA-road-d.USA.gr",
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<FloatNode>


        @Setup(Level.Trial)
        fun setup() {
            nodes = GraphReader().readGraphFloatNodesBiDirect(sourcePath)
        }


        @TearDown(Level.Trial)
        fun printAll() {
            val results = readyResultsPageRank
            val size = results.size.toDouble()
            val config = results.first()

            val totalTasksProcessed: Double = results.sumOf { it.totalTasksProcessed } / size
            val successStealing: Double = results.sumOf { it.successStealing } / size
            val failedStealing: Double = results.sumOf { it.failedStealing } / size
            val stealingAttempts: Double = results.sumOf { it.stealingAttempts } / size
            val tasksBetterThanTop: Double = results.sumOf { it.tasksBetterThanTop } / size

            println(
                "\nDone,${config.testName},${config.pSteal},${config.graphName},${config.stealSize}," +
                        "${totalTasksProcessed},${successStealing},${failedStealing},${stealingAttempts},${tasksBetterThanTop}"
            )

            clearResultsPageRank()
        }
    }

    companion object {

        private val results = ArrayList<BenchmarkResultWithMetrics>()

        private val readyResultsPageRank: List<BenchmarkResultWithMetrics>
            @Synchronized
            get() = results

        @Synchronized
        private fun clearResultsPageRank() {
            results.clear()
        }

        @Synchronized
        private fun addResultPageRank(result: BenchmarkResultWithMetrics) {
            results.add(result)
        }

    }

}
