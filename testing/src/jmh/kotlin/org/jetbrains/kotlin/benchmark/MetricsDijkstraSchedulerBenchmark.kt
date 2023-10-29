//package org.jetbrains.kotlin.benchmark
//
//import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.IntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodes
//import org.jetbrains.kotlin.number.scheduler.NonBlockingLongDijkstraScheduler
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//// Для сбора метрик
//@Warmup(iterations = 1)
//@Measurement(iterations = 3)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class MetricsDijkstraSchedulerBenchmark {
//    @Benchmark
//    fun testDijkstra(config: Config) {
//        val scheduler = NonBlockingLongDijkstraScheduler(
//            config.nodes,
//            startIndex = 0,
//            poolSize = config.threads,
//            stealSizeInitialPower = config.stealSize,
//            pStealInitialPower = config.pStealInitialPower,
//        ).use { scheduler ->
//            scheduler.waitForTermination()
//            scheduler
//        }
//
//        addResultAdaptiveDijkstra(
//            AdaptiveBenchmarkResult(
//                testName = "dijkstra",
//                graphName = config.sourcePath,
//                pStealPower = config.pStealInitialPower,
//                stealSize = config.stealSize,
//                totalTasksProcessed = scheduler.totalTasksProcessed(),
//                successStealing = scheduler.successStealing(),
//                failedStealing = 0L,
//                stealingAttempts = scheduler.stealingAttempts(),
//                tasksLowerThanStolen = scheduler.tasksLowerThanStolen(),
//                stealingTotal = scheduler.stealingTotal(),
//                stolenCountSum = scheduler.stolenCountSum(),
//                stolenCountSumWithoutEmpty = scheduler.stolenCountSumWithoutEmpty(),
//                fullBufferTimesSum = scheduler.fullBufferTimesSum(),
//                tasksLowerThanStolenIncludingOurEmptiness = scheduler.tasksLowerThanStolenIncludingOurEmptiness(),
//                tasksFromBufferBetterThanTop = scheduler.tasksFromBufferBetterThanTop(),
//                tasksFromBufferBetterOrEqualThanTop = scheduler.tasksFromBufferBetterOrEqualThanTop(),
//                tasksFromBufferBetterThanTopWithoutEmpty = scheduler.tasksFromBufferBetterThanTopWithoutEmpty(),
//                tasksFromBufferBetterOrEqualThanTopWithoutEmpty = scheduler.tasksFromBufferBetterOrEqualThanTopWithoutEmpty(),
//                tasksFromBufferBetterThanSecondTop = scheduler.tasksFromBufferBetterThanSecondTop(),
//                tasksFromBufferBetterOrEqualThanSecondTop = scheduler.tasksFromBufferBetterOrEqualThanSecondTop(),
//                tasksFromBufferBetterThanSecondTopWithoutEmpty = scheduler.tasksFromBufferBetterThanSecondTopWithoutEmpty(),
//                tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty = scheduler.tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty(),
//                filledTimes = scheduler.filledTimes(),
//                insertedAfterSteal = scheduler.insertedAfterSteal(),
//                insertedAfterGoodSteal = scheduler.insertedAfterGoodSteal(),
//                insertedAfterBadSteal = scheduler.insertedAfterBadSteal(),
//                updatesCount = scheduler.updatesCount(),
//                updateAttemptsCount = scheduler.updateAttemptsCount(),
//                uselessWork = scheduler.uselessWork(),
//                abortedUpdates = scheduler.abortedUpdates(),
//            ),
//        )
//    }
//
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
////            "9",
//            "10" // //            "1024"
//        )
//        var stealSize: Int = 2
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
//            val resultMap = mapOf(
//                "testName" to config.testName,
//                "pSteal" to config.pStealPower,
//                "graphName" to config.graphName,
//                "stealSize" to config.stealSize,
//                "totalTasksProcessed" to readyResultsDijkstra.averageBy { totalTasksProcessed },
//                "successStealing" to readyResultsDijkstra.averageBy { successStealing },
//                "failedStealing" to readyResultsDijkstra.averageBy { failedStealing },
//                "stealingAttempts" to readyResultsDijkstra.averageBy { stealingAttempts },
//                "tasksLowerThanStolen" to readyResultsDijkstra.averageBy { tasksLowerThanStolen },
//                "stealingTotal" to readyResultsDijkstra.averageBy { stealingTotal },
//                "stolenCountSum" to readyResultsDijkstra.averageBy { stolenCountSum },
//                "stolenCountSumWithoutEmpty" to readyResultsDijkstra.averageBy { stolenCountSumWithoutEmpty },
//                "fullBufferTimesSum" to readyResultsDijkstra.averageBy { fullBufferTimesSum },
//                "tasksLowerThanStolenIncludingOurEmptiness" to readyResultsDijkstra.averageBy { tasksLowerThanStolenIncludingOurEmptiness },
//                "tasksFromBufferBetterThanTop" to readyResultsDijkstra.averageBy { tasksFromBufferBetterThanTop },
//                "tasksFromBufferBetterOrEqualThanTop" to readyResultsDijkstra.averageBy { tasksFromBufferBetterOrEqualThanTop },
//                "tasksFromBufferBetterThanTopWithoutEmpty" to readyResultsDijkstra.averageBy { tasksFromBufferBetterThanTopWithoutEmpty },
//                "tasksFromBufferBetterOrEqualThanTopWithoutEmpty" to readyResultsDijkstra.averageBy { tasksFromBufferBetterOrEqualThanTopWithoutEmpty },
//                "tasksFromBufferBetterThanSecondTop" to readyResultsDijkstra.averageBy { tasksFromBufferBetterThanSecondTop },
//                "tasksFromBufferBetterOrEqualThanSecondTop" to readyResultsDijkstra.averageBy { tasksFromBufferBetterOrEqualThanSecondTop },
//                "tasksFromBufferBetterThanSecondTopWithoutEmpty" to readyResultsDijkstra.averageBy { tasksFromBufferBetterThanSecondTopWithoutEmpty },
//                "tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty" to readyResultsDijkstra.averageBy { tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty },
//                "filledTimes" to readyResultsDijkstra.averageBy { filledTimes },
//                "insertedAfterSteal" to readyResultsDijkstra.averageBy { insertedAfterSteal },
//                "insertedAfterGoodSteal" to readyResultsDijkstra.averageBy { insertedAfterGoodSteal },
//                "insertedAfterBadSteal" to readyResultsDijkstra.averageBy { insertedAfterBadSteal },
//                "updatesCount" to readyResultsDijkstra.averageBy { updatesCount },
//                "updateAttemptsCount" to readyResultsDijkstra.averageBy { updateAttemptsCount },
//                "uselessWork" to readyResultsDijkstra.averageBy { uselessWork },
//                "abortedUpdates" to readyResultsDijkstra.averageBy { abortedUpdates },
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
//        inline fun List<AdaptiveBenchmarkResult>.averageBy(extractor: AdaptiveBenchmarkResult.() -> Long): Double {
//            return sumOf { extractor(it) } / size.toDouble()
//        }
//
//    }
//
//
//    class AdaptiveBenchmarkResult(
//        val testName: String,
//        val graphName: String,
//        val pStealPower: Int,
//        val stealSize: Int,
//        val totalTasksProcessed: Long,
//        val successStealing: Long,
//        val failedStealing: Long,
//        val stealingAttempts: Long,
//        val tasksLowerThanStolen: Long,
//        val stealingTotal: Long,
//        val stolenCountSum: Long,
//        val stolenCountSumWithoutEmpty: Long,
//        val fullBufferTimesSum: Long,
//        val tasksLowerThanStolenIncludingOurEmptiness: Long,
//        val tasksFromBufferBetterThanTop: Long,
//        val tasksFromBufferBetterOrEqualThanTop: Long,
//        val tasksFromBufferBetterThanTopWithoutEmpty: Long,
//        val tasksFromBufferBetterOrEqualThanTopWithoutEmpty: Long,
//        val tasksFromBufferBetterThanSecondTop: Long,
//        val tasksFromBufferBetterOrEqualThanSecondTop: Long,
//        val tasksFromBufferBetterThanSecondTopWithoutEmpty: Long,
//        val tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty: Long,
//        val filledTimes: Long,
//        val insertedAfterSteal: Long,
//        val insertedAfterGoodSteal: Long,
//        val insertedAfterBadSteal: Long,
//        val updatesCount: Long,
//        val updateAttemptsCount: Long,
//        val uselessWork: Long,
//        val abortedUpdates: Long,
//    )
//
//}