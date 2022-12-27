package org.jetbrains.kotlin.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodes
import org.jetbrains.kotlin.number.scheduler.NonBlockingAdaptiveByPStealLongDijkstraScheduler
import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongDijkstraScheduler
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
        val scheduler = NonBlockingAdaptiveByPStealLongDijkstraScheduler(
            config.nodes,
            startIndex = 0,
            poolSize = config.threads,
            stealSizeInitialPower = config.stealSize,
            pStealInitialPower = config.pStealInitialPower,
            learningRate = config.learningRate,
            initialMomentum = config.initialMomentum,
            reverseMomentum = config.reverseMomentum,
            k1 = config.k1,
            k2 = config.k2,
//            startMetricsUpdateIterations = config.startMetricsUpdateIterations,
//            restMetricsUpdateIterations = config.restMetricsUpdateIterations,
//            metricsChangeStepsCount = config.metricsChangeStepsCount,
//            metricsChangeConsiderableDelta = 0.0,
//            stealSizeTasksCheckCount = config.stealSizeTasksCheckCount
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
                learningRate = config.learningRate,
                initialMomentum = config.initialMomentum,
                reverseMomentum = config.reverseMomentum,
                k1 = config.k1,
                k2 = config.k2,
//                pStealDegrees = scheduler.pStealPower(),
//                pStealUpdateCount = scheduler.parametersUpdateCount(),
//                totalTasksProcessed = scheduler.totalTasksProcessed(),
//                minPSteal = scheduler.minPSteal(),
//                maxPSteal = scheduler.maxPSteal(),
//                pStealUpdateCountAfter = scheduler.parametersUpdateCountAfterSwitch(),
//                pStealUpdateCountBefore = scheduler.parametersUpdateCountBeforeSwitch(),
//                pStealPowerOnBias = scheduler.pStealPowerOnBias(),
//                maxPStealAfter = scheduler.maxPStealAfterSwitch(),
//                minPStealAfter = scheduler.minPStealAfterSwitch(),
//                maxPStealBefore = scheduler.maxPStealBeforeSwitch(),
//                minPStealBefore = scheduler.minPStealBeforeSwitch(),
            ),
        )
    }


    @State(Scope.Thread)
    open class Config {

        var threads: Int = 72

        @Param(
            "0",  // ""1", // 0
//            "1",  // "0.5", // 1
            "2",  // "0.25", // 2
//            "3",  // "0.125", // 3
            "4",  // "0.0625", // 4
//            "5",  // "0.03125", // 5
            "6",  // "0.015625", // 6
//            "7",  // "0.0078125", // 7
//            "8",  // "0.001953125", // 8
//            "9",  // "0.0009765625" // 9
        )
        var pStealInitialPower: Int = 5

        @Param(
//            "0", // ""1",
//            "1", // //            "2",
            "2", // "4",
//            "3", // //            "8",
//            "4", // "16",
//            "5", // "32",
//            "6", // //            "64",
//            "7", // "128",
//            "8", // //            "256",
//            "9",
//            "10" // //            "1024"
        )
        var stealSize: Int = 32

        @Param(
//            "/soc-LiveJournal1.txt",
            "/USA-road-d.W.gr",
//            "/USA-road-d.CTR.gr",
//            "/USA-road-d.USA.gr",
        )
        lateinit var sourcePath: String

        @Param(
//            "0.6" // mq
//            "0.7", // mq2
//            "0.8", // mq3
//            "0.9", // mq4
            "0.95", // mq5
        )
        var k1: Double = 0.6

        @Param(
//            "0.06",
            "0.1",
            "0.2",
//            "0.25",
            "0.3",
//            "0.35",
            "0.4",
//            "0.5"
        )
        var k2: Double = 0.06

        @Param(
            "1.0",
            "0.1",
            "0.01",
            "0.001",
//            "0.0001",
//            "0.00001",
//            "0.000001"
        )
        var learningRate: Double = 1.0

        @Param(
            "0.1",
            "0.01",
            "0.001",
            "0.0001",
//            "0.00001"
        )
        var reverseMomentum: Double = 1.0

        @Param(
//            "1000.0",
            "100.0",
            "10.0",
            "1.0",
//            "0.1",
//            "0.01",
//            "0.001"
        )
        var initialMomentum: Double = 1.0


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
            val config = results.last()

//            val pStealTotal = results.flatMap { it.pStealDegrees }
//            val minPSteal = results.flatMap { it.minPSteal }
//            val maxPSteal = results.flatMap { it.maxPSteal }

//            val pStealAvg = pStealTotal.average()
//            val pStealMean = pStealTotal.mean()
//            val pStealDispersion = pStealTotal.dispersion()
//
//            val minPStealAvg = minPSteal.average()
//            val minPStealMean = minPSteal.mean()
//            val minPStealDispersion = minPSteal.dispersion()
//
//            val maxPStealAvg = maxPSteal.average()
//            val maxPStealMean = maxPSteal.mean()
//            val maxPStealDispersion = maxPSteal.dispersion()

            val resultMap = mapOf(
                "testName" to config.testName,
                "pSteal" to config.pSteal,
                "graphName" to config.graphName,
                "stealSize" to config.stealSize,
                "k1" to config.k1,
                "k2" to config.k2,
                "learningRate" to config.learningRate,
                "initialMomentum" to config.initialMomentum,
                "reverseMomentum" to config.reverseMomentum,
//                "startMetricsUpdateIterations" to startMetricsUpdateIterations,
//                "restMetricsUpdateIterations" to restMetricsUpdateIterations,
//                "metricsChangeStepsCount" to metricsChangeStepsCount,
//                "pStealAvg" to pStealAvg,
//                "pStealMean" to pStealMean,
//                "pStealDispersion" to pStealDispersion,
//                "total" to config.totalTasksProcessed,
//                "minPStealAvg" to minPStealAvg,
//                "minPStealMean" to minPStealMean,
//                "minPStealDispersion" to minPStealDispersion,
//                "maxPStealAvg" to maxPStealAvg,
//                "maxPStealMean" to maxPStealMean,
//                "maxPStealDispersion" to maxPStealDispersion,
//                "pStealUpdatesBefore" to config.pStealUpdateCountBefore,
//                "pStealUpdatesAfter" to config.pStealUpdateCountAfter,
//                "pStealPowerOnBias" to config.pStealPowerOnBias,
//                "minPStealBefore" to config.minPStealBefore,
//                "maxPStealBefore" to config.maxPStealBefore,
//                "minPStealAfter" to config.minPStealAfter,
//                "maxPStealAfter" to config.maxPStealAfter,
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
        val k1: Double,
        val k2: Double,
        val learningRate: Double,
        val initialMomentum: Double,
        val reverseMomentum: Double,
//        val pStealDegrees: List<Int>,
//        val pStealUpdateCount: List<Int>,
//        val pStealUpdateCountBefore: List<Int>,
//        val pStealUpdateCountAfter: List<Int>,
//        val pStealPowerOnBias: List<Int>,
//        val totalTasksProcessed: List<Int>,
//        val minPSteal: List<Int>,
//        val maxPSteal: List<Int>,
//        val minPStealBefore: List<Int>,
//        val maxPStealBefore: List<Int>,
//        val minPStealAfter: List<Int>,
//        val maxPStealAfter: List<Int>,
    )


}