//package org.jetbrains.kotlin.benchmark.full
//
//import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodesBfs
//import org.jetbrains.kotlin.number.scheduler.NonBlockingFullAdaptiveLongBfsScheduler
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 3)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class AdaptiveBfsSchedulerBenchmark {
//
//    @Benchmark
//    fun testDijkstra(config: Config) {
//        val scheduler = NonBlockingFullAdaptiveLongBfsScheduler(
//            config.nodes,
//            startIndex = 0,
//            poolSize = config.threads,
//            stealSizeInitialPower = config.stealSize,
//            pStealInitialPower = config.pStealInitialPower,
//            learningRate = config.learningRate,
//            initialMomentum = config.initialMomentum,
//            k1 = config.k1,
//            k2 = 1 - config.k1,
//            pStealWindow = config.pStealWindow,
//            stealSizeWindow = config.stealSizeWindow,
//            bufferEfficientFactor = config.bufferEfficientFactor
//        ).use { scheduler ->
//            scheduler.waitForTermination()
//            scheduler
//        }
//
//        scheduler.pStealUpdateCount()
//        scheduler.stealSizeUpdateCount()
//
//        addResultAdaptiveDijkstra(
//            AdaptiveBenchmarkResult(
//                testName = "bfs",
//                graphName = config.sourcePath,
//                pSteal = 1.0 / 1.shl(config.pStealInitialPower),
//                stealSize = config.stealSize,
//                learningRate = config.learningRate,
//                initialMomentum = config.initialMomentum,
//                k1 = config.k1,
//                stealSizeWindow = config.stealSizeWindow,
//                pStealWindow = config.pStealWindow,
//                bufferEfficientFactor = config.bufferEfficientFactor,
//                pStealUpdateCount = scheduler.pStealUpdateCountAvg(),
//                stealSizeUpdateCount = scheduler.stealSizeUpdateCountAvg(),
//            ),
//        )
//    }
//
// // Params: {bufferEfficientFactor=0.14, stealSizeWindow=10, learningRate=0.1, initialMomentum=100.0, k1=0.8, pStealWindow=1000}
//    @State(Scope.Thread)
//    open class Config {
//
//        var threads: Int = 72
//
//        @Param(
//            "/home/ubuntu/data/soc-LiveJournal1.txt",
//            "/home/ubuntu/data/USA-road-d.W.gr",
////            "/USA-road-d.CTR.gr",
////            "/USA-road-d.USA.gr",
//        )
//        lateinit var sourcePath: String
//
//        @Param(
//            "0",  // ""1", // 0
////            "1",  // "0.5", // 1
//            "2",  // "0.25", // 2
////            "3",  // "0.125", // 3
//            "4",  // "0.0625", // 4
////            "5",  // "0.03125", // 5
//            "6",  // "0.015625", // 6
////            "7",  // "0.0078125", // 7
////            "8",  // "0.001953125", // 8
////            "9",  // "0.0009765625" // 9
//        )
//        var pStealInitialPower: Int = 5
//
//        @Param(
////            "0", // ""1",
////            "1", // //            "2",
//            "2", // "4",
////            "3", // //            "8",
//            "4", // "16",
////            "5", // "32",
//            "6", // //            "64",
////            "7", // "128",
//            "8", // //            "256",
////            "9",
////            "10" // //            "1024"
//        )
//        var stealSize: Int = 32
//
//        @Param(
//            "0.8",  // mq5
//        )
//        var k1: Double = 0.7
//
//        @Param(
//            "0.1",
//        )
//        var learningRate: Double = 0.1
//
//
//        @Param(
//            "100.0",
//        )
//        var initialMomentum: Double = 100.0
//
//        @Param(
//            "10",
//        )
//        var stealSizeWindow: Int = 10
//
//        @Param(
//            "1000",
//        )
//        var pStealWindow: Int = 1000
//
//        @Param(
//            "0.14",
//        )
//        var bufferEfficientFactor: Double = 0.14
//
//        lateinit var nodes: List<BfsIntNode>
//
//        @Setup(Level.Trial)
//        fun setup() {
//            nodes = GraphReader().readGraphNodesBiDirectFromFileBfs(sourcePath)
//        }
//
//        @TearDown(Level.Invocation)
//        fun clear() {
//            clearNodesBfs(nodes)
//        }
//
//        @TearDown(Level.Trial)
//        fun printAll() {
//            val results = readyResultsDijkstra
//            val config = results.last()
//
//            val resultMap = mapOf(
//                "testName" to config.testName,
//                "pSteal" to config.pSteal,
//                "graphName" to config.graphName,
//                "stealSize" to config.stealSize,
//                "k1" to config.k1,
//                "learningRate" to config.learningRate,
//                "initialMomentum" to config.initialMomentum,
//                "stealSizeWindow" to config.stealSizeWindow,
//                "pStealWindow" to config.pStealWindow,
//                "bufferEfficientFactor" to config.bufferEfficientFactor,
//                "pStealUpdateCount" to results.averageBy { pStealUpdateCount },
//                "stealSizeUpdateCount" to results.averageBy { stealSizeUpdateCount }
//            )
//
//            println(objectMapper.writeValueAsString(resultMap))
//
//            clearMyResults()
//        }
//
//    }
//
//    companion object {
//
//        private val results = ArrayList<AdaptiveBenchmarkResult>()
//
//        val objectMapper = jacksonObjectMapper()
//
//        private val readyResultsDijkstra: List<AdaptiveBenchmarkResult>
//            @Synchronized
//            get() = results
//
//        @Synchronized
//        private fun clearMyResults() {
//            results.clear()
//        }
//
//        @Synchronized
//        private fun addResultAdaptiveDijkstra(
//            result: AdaptiveBenchmarkResult
//        ) {
//            results.add(result)
//        }
//
//        @JvmStatic
//        inline fun List<AdaptiveBenchmarkResult>.averageBy(extractor: AdaptiveBenchmarkResult.() -> Double): Double {
//            return sumOf { extractor(it) } / size.toDouble()
//        }
//
//    }
//
//    class AdaptiveBenchmarkResult(
//        val testName: String,
//        val graphName: String,
//        val pSteal: Double,
//        val stealSize: Int,
//        val k1: Double,
//        val learningRate: Double,
//        val initialMomentum: Double,
//        val stealSizeWindow: Int,
//        val pStealWindow: Int,
//        val bufferEfficientFactor: Double,
//        val pStealUpdateCount: Double,
//        val stealSizeUpdateCount: Double,
//    )
//
//
//}