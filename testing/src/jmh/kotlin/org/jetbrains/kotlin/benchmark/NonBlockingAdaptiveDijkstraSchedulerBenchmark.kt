package org.jetbrains.kotlin.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.NonBlockingAdaptiveLongDijkstraScheduler
import org.jetbrains.kotlin.utils.dispersion
import org.jetbrains.kotlin.utils.mean
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit


@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class AdaptiveDijkstraSchedulerBenchmark {

    @Benchmark
    fun testDijkstra(config: Config) {
        val scheduler = NonBlockingAdaptiveLongDijkstraScheduler(
            config.nodes,
            startIndex = 0,
            poolSize = config.threads,
            stealSizeInitialPower = config.stealSize,
            pStealInitialPower = config.pStealInitialPower,
            metricsUpdateIterations = config.metricsUpdateThreshold,
        ).use { scheduler ->
            scheduler.waitForTermination()
            scheduler
        }

        addResultAdaptiveDijkstra(
            AdaptiveBenchmarkResult(
                testName = "dijkstra",
                graphName = config.sourcePath,
                pSteal = 1.0 / 1.shl(config.pStealInitialPower),
                stealSize = config.stealSize,
                metricsUpdateThreshold = config.metricsUpdateThreshold,
                pStealDegrees = scheduler.pStealPower(),
                parametersUpdateCount = scheduler.parametersUpdateCount(),
                minPSteal = scheduler.minPSteal(),
                maxPSteal = scheduler.maxPSteal(),
            ),
        )
    }


    @State(Scope.Thread)
    open class Config {

        var threads: Int = 72

        @Param(
//            "0",  // ""1", // 0
            "1",  // "0.5", // 1
            "2",  // "0.25", // 2
//            "3",  // "0.125", // 3
            "4",  // "0.0625", // 4
//            "5",  // "0.03125", // 5
            "6",  // "0.015625", // 6
//            "7",  // "0.0078125", // 7
            "8",  // "0.001953125", // 8
//            "9",  // "0.0009765625" // 9
        )
        var pStealInitialPower: Int = 5

        @Param(
            "0", // ""1",
//            "1", // //            "2",
            "2", // "4",
//            "3", // //            "8",
            "4", // "16",
//            "5", // "32",
            "6", // //            "64",
//            "7", // "128",
            "8", // //            "256",
//            "9",
//            "10" // //            "1024"
        )
        var stealSize: Int = 32

        @Param(
            "/soc-LiveJournal1.txt",
            "/USA-road-d.W.gr",
//            "/USA-road-d.CTR.gr",
//            "/USA-road-d.USA.gr",
        )
        lateinit var sourcePath: String

        @Param(
            "1000",
            "5000",
            "10000",
            "30000",
        )
        var metricsUpdateThreshold: Int = 100

        lateinit var nodes: List<IntNode>


        @Setup(Level.Trial)
        fun setup() {
            nodes = GraphReader().readGraphNodesBiDirect(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodes(nodes)
        }

        @TearDown(Level.Trial)
        fun printAll() {
            val results = readyResultsDijkstra
            val config = results.first()

            val pStealTotal = results.flatMap { it.pStealDegrees }
            val parametersUpdateCountTotal = results.flatMap { it.parametersUpdateCount }
            val minPSteal = results.flatMap { it.minPSteal }
            val maxPSteal = results.flatMap { it.maxPSteal }

            val pStealAvg = pStealTotal.average()
            val pStealMean = pStealTotal.mean()
            val pStealDispersion = pStealTotal.dispersion()

            val parametersUpdateAvg = parametersUpdateCountTotal.average()
            val parametersUpdateMean = parametersUpdateCountTotal.mean()
            val parametersUpdateDispersion = parametersUpdateCountTotal.dispersion()

            val minPStealAvg = minPSteal.average()
            val minPStealMean = minPSteal.mean()
            val minPStealDispersion = minPSteal.dispersion()

            val maxPStealAvg = maxPSteal.average()
            val maxPStealMean = maxPSteal.mean()
            val maxPStealDispersion = maxPSteal.dispersion()

            val resultMap = mapOf(
                "testName" to config.testName,
                "pSteal" to config.pSteal,
                "graphName" to config.graphName,
                "stealSize" to config.stealSize,
                "metricsUpdateThreshold" to metricsUpdateThreshold,
                "pStealAvg" to pStealAvg,
                "pStealMean" to pStealMean,
                "pStealDispersion" to pStealDispersion,
                "parametersUpdateAvg" to parametersUpdateAvg,
                "parametersUpdateMean" to parametersUpdateMean,
                "parametersUpdateDispersion" to parametersUpdateDispersion,
                "minPStealAvg" to minPStealAvg,
                "minPStealMean" to minPStealMean,
                "minPStealDispersion" to minPStealDispersion,
                "maxPStealAvg" to maxPStealAvg,
                "maxPStealMean" to maxPStealMean,
                "maxPStealDispersion" to maxPStealDispersion
            )

            println(objectMapper.writeValueAsString(resultMap))

            clearMyResults()
        }

    }

    companion object {

        private val results = ArrayList<AdaptiveBenchmarkResult>()

        val objectMapper = jacksonObjectMapper()

        private val readyResultsDijkstra: List<AdaptiveBenchmarkResult>
            @Synchronized
            get() = results

        @Synchronized
        private fun clearMyResults() {
            results.clear()
        }

        @Synchronized
        private fun addResultAdaptiveDijkstra(
            result: AdaptiveBenchmarkResult
        ) {
            results.add(result)
        }

    }

    class AdaptiveBenchmarkResult(
        val testName: String,
        val graphName: String,
        val pSteal: Double,
        val stealSize: Int,
        val metricsUpdateThreshold: Int, // берутся из теста
        val pStealDegrees: List<Int>,
        val parametersUpdateCount: List<Int>,
        val minPSteal: List<Int>,
        val maxPSteal: List<Int>,
    )


}