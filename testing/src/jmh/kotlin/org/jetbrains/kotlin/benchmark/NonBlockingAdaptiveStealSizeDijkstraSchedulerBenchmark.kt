//package org.jetbrains.kotlin.benchmark
//
//import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.IntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodes
//import org.jetbrains.kotlin.number.scheduler.NonBlockingAdaptiveByStealSizeLongDijkstraScheduler
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 3)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class NonBlockingAdaptiveStealSizeDijkstraSchedulerBenchmark {
//
//
//    @Benchmark
//    fun testDijkstra(config: Config) {
//        val scheduler = NonBlockingAdaptiveByStealSizeLongDijkstraScheduler(
//            config.nodes,
//            startIndex = 0,
//            poolSize = config.threads,
//            stealSizeInitialPower = config.stealSize,
//            pStealInitialPower = config.pStealInitialPower,
//            stealSizeWindow = config.stealSizeWindow,
//            bufferEfficientFactor = config.bufferEfficientFactor
//        ).use { scheduler ->
//            scheduler.waitForTermination()
//            scheduler
//        }
//
//        addResultAdaptiveDijkstra(
//            AdaptiveBenchmarkResult(
//                testName = "dijkstra",
//                graphName = config.sourcePath,
//                pSteal = 1.0 / 1.shl(config.pStealInitialPower),
//                stealSize = config.stealSize,
//                stealSizeWindow = config.stealSizeWindow,
//                bufferEfficientFactor = config.bufferEfficientFactor,
//                stealSizeUpdateCountAverage = scheduler.stealSizeUpdateCountAverage()
//            ),
//        )
//    }
//
//    @State(Scope.Thread)
//    open class Config {
//
//        var threads: Int = 72
//
//        @Param(
//            "/home/admin/graphs/soc-LiveJournal1.txt",
//            "/home/admin/graphs/USA-road-d.W.gr",
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
//            "8",  // "0.001953125", // 8
////            "9",  // "0.0009765625" // 9
//        )
//        var pStealInitialPower: Int = 5
//
//        @Param(
//            "0", // ""1",
////            "1", // //            "2",
//            "2", // "4",
////            "3", // //            "8",
//            "4", // "16",
////            "5", // "32",
//            "6", // //            "64",
////            "7", // "128",
//            "8", // //            "256",
////            "9", // "512"
//            "10" // //            "1024"
//        )
//        var stealSize: Int = 32
//
//        @Param(
//            "100",
//            "200",
//            "10"
//        )
//        var stealSizeWindow: Int = 1000
//
//        @Param(
//            "0.14",
//            "0.05",
//            "-10000.0", // off
//        )
//        var bufferEfficientFactor: Double = 0.14
//
//        lateinit var nodes: List<IntNode>
//
//        @Setup(Level.Trial)
//        fun setup() {
//            nodes = GraphReader().readGraphNodesBiDirectFromFile(sourcePath)
//        }
//
//        @TearDown(Level.Invocation)
//        fun clear() {
//            clearNodes(nodes)
//        }
//
//        @TearDown(Level.Trial)
//        fun printAll() {
//            val results = readyResultsDijkstra
//            val config = results.last()
//
//
//            val resultMap = mapOf(
//                "testName" to config.testName,
//                "pSteal" to config.pSteal,
//                "graphName" to config.graphName,
//                "stealSize" to config.stealSize,
//                "stealSizeWindow" to config.stealSizeWindow,
//                "bufferEfficientFactor" to config.bufferEfficientFactor,
//                "stealSizeUpdateCountAverage" to config.stealSizeUpdateCountAverage
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
//    }
//
//    class AdaptiveBenchmarkResult(
//        val testName: String,
//        val graphName: String,
//        val pSteal: Double,
//        val stealSize: Int,
//        val stealSizeWindow: Int,
//        val bufferEfficientFactor: Double,
//        val stealSizeUpdateCountAverage: Double
//    )
//
//
//}