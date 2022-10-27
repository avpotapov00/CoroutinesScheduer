//package org.jetbrains.kotlin.benchmark
//
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.IntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodes
//import org.jetbrains.kotlin.number.scheduler.calc.AdaptiveDijkstraSchedulerMetrics
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 5)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class AdaptiveDijkstraSMQMetricsBenchmark {
//
//    @Benchmark
//    fun testDijkstra(config: Config) {
//        val scheduler = AdaptiveDijkstraSchedulerMetrics(
//            config.nodes,
//            startIndex = 0,
//            poolSize = config.threads,
//            stealSize = config.stealSize,
//            pSteal = config.pSteal,
//            checkCount = Integer.min(10, (1024 / config.pSteal).toInt())
//        ).use { scheduler ->
//            scheduler.waitForTermination()
//            scheduler
//        }
//
//        addResultDijkstra(
//            BenchmarkResultWithMetrics(
//                "dijkstra", config.sourcePath, config.pSteal, config.stealSize,
//                stealingAttempts = scheduler.stealingAttempts(),
//                failedStealing = scheduler.failedStealing(),
//                totalTasksProcessed = scheduler.totalTasksProcessed(),
//                successStealing = scheduler.successStealing(),
//                tasksBetterThanTop = scheduler.tasksLowerThanStolen(),
//                stealingTotal = scheduler.stealingTotal(),
//                stolenCountSum = scheduler.stolenCountSum(),
//                fullBufferTimesSum = scheduler.fullBufferTimesSum(),
//                tasksLowerThanStolenIncludingOurEmptiness = scheduler.tasksLowerThanStolenIncludingOurEmptiness(),
//
//                tasksFromBufferBetterThanTop = scheduler.tasksFromBufferBetterThanTop(),
//                tasksFromBufferBetterOrEqualThanTop = scheduler.tasksFromBufferBetterOrEqualThanTop(),
//                tasksFromBufferBetterOrEqualThanSecondTop = scheduler.tasksFromBufferBetterOrEqualThanSecondTop(),
//                tasksFromBufferBetterThanSecondTop = scheduler.tasksFromBufferBetterThanSecondTop(),
//
//                tasksFromBufferBetterThanTopWithoutEmpty = scheduler.tasksFromBufferBetterThanTopWithoutEmpty(),
//                tasksFromBufferBetterOrEqualThanTopWithoutEmpty = scheduler.tasksFromBufferBetterOrEqualThanTopWithoutEmpty(),
//                tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty = scheduler.tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty(),
//                tasksFromBufferBetterThanSecondTopWithoutEmpty = scheduler.tasksFromBufferBetterThanSecondTopWithoutEmpty(),
//
//                filledTimes = scheduler.filledTimes(),
//                insertedAfterSteal = scheduler.insertedAfterSteal(),
//                insertedAfterBadSteal = scheduler.insertedAfterBadSteal(),
//                insertedAfterGoodSteal = scheduler.insertedAfterGoodSteal(),
//                nodesCount = config.nodes.size,
//            )
//        )
//    }
//
//
//    @State(Scope.Thread)
//    open class Config {
//
//        //        @Param("2", "4", "8", "16", "32", "64", "76")
//        var threads: Int = 72
//
////        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
////        @Param("1", "0.25", "0.0625", "0.015625", "0.001953125", "0.0009765625")
//        @Param(
//            "0.1",
//            "0.15",
//            "0.2",
//            "0.25",
//            "0.3",
//            "0.35",
//            "0.4",
//            "0.45",
//            "0.5",
//            "0.55",
//            "0.6",
//            "0.65",
//            "0.7",
//            "0.75",
//            "0.8",
//            "0.85",
//            "0.9"
//        )
//        var pSteal: Double = 0.03125
//
//        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
////        @Param("1", "4", "16", "64", "256", "1024")
//        var stealSize: Int = 32
//
//        @Param(
//            "/soc-LiveJournal1.txt",
//            "/USA-road-d.W.gr",
////            "/USA-road-d.CTR.gr",
////            "/USA-road-d.USA.gr",
//        )
//        lateinit var sourcePath: String
//
//        lateinit var nodes: List<IntNode>
//
//
//        @Setup(Level.Trial)
//        fun setup() {
//            nodes = GraphReader().readGraphNodesBiDirect(sourcePath)
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
//            val size = results.size.toDouble()
//            val config = results.first()
//
//            val totalTasksProcessed: Double = results.sumOf { it.totalTasksProcessed } / size
//            val successStealing: Double = results.sumOf { it.successStealing } / size
//            val failedStealing: Double = results.sumOf { it.failedStealing } / size
//            val stealingAttempts: Double = results.sumOf { it.stealingAttempts } / size
//            val tasksBetterThanTop: Double = results.sumOf { it.tasksBetterThanTop } / size
//            val stealingTotal: Double = results.sumOf { it.stealingTotal } / size
//            val stolenCountSum: Double = results.sumOf { it.stolenCountSum } / size
//            val fullBufferTimesSum: Double = results.sumOf { it.fullBufferTimesSum } / size
//            val tasksLowerThanStolenIncludingOurEmptiness: Double =
//                results.sumOf { it.tasksLowerThanStolenIncludingOurEmptiness } / size
//
//            val tasksFromBufferBetterThanTop: Double = results.sumOf { it.tasksFromBufferBetterThanTop } / size
//            val tasksFromBufferBetterOrEqualThanTop: Double =
//                results.sumOf { it.tasksFromBufferBetterOrEqualThanTop } / size
//            val tasksFromBufferBetterOrEqualThanSecondTop: Double =
//                results.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTop } / size
//            val tasksFromBufferBetterThanSecondTop: Double =
//                results.sumOf { it.tasksFromBufferBetterThanSecondTop } / size
//
//            val tasksFromBufferBetterThanTopWithoutEmpty: Double =
//                results.sumOf { it.tasksFromBufferBetterThanTopWithoutEmpty } / size
//            val tasksFromBufferBetterOrEqualThanTopWithoutEmpty: Double =
//                results.sumOf { it.tasksFromBufferBetterOrEqualThanTopWithoutEmpty } / size
//            val tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty: Double =
//                results.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty } / size
//            val tasksFromBufferBetterThanSecondTopWithoutEmpty: Double =
//                results.sumOf { it.tasksFromBufferBetterThanSecondTopWithoutEmpty } / size
//
//            val filledTimes: Double = results.sumOf { it.filledTimes } / size
//            val insertedAfterSteal: Double = results.sumOf { it.insertedAfterSteal } / size
//            val insertedAfterGoodSteal: Double = results.sumOf { it.insertedAfterGoodSteal } / size
//            val insertedAfterBadSteal: Double = results.sumOf { it.insertedAfterBadSteal } / size
//
//            val nodesCount: Double = totalTasksProcessed / results.first().nodesCount
//
//            println(
//                "\nDone,${config.testName},${config.pSteal},${config.graphName},${config.stealSize}," +
//                        "${totalTasksProcessed},${successStealing},${failedStealing},${stealingAttempts},${tasksBetterThanTop}," +
//                        "${stealingTotal},${stolenCountSum},${fullBufferTimesSum},${tasksLowerThanStolenIncludingOurEmptiness}," +
//                        "${tasksFromBufferBetterThanTop},${tasksFromBufferBetterOrEqualThanTop},${tasksFromBufferBetterThanSecondTop},${tasksFromBufferBetterOrEqualThanSecondTop}," +
//                        "${tasksFromBufferBetterThanTopWithoutEmpty},${tasksFromBufferBetterOrEqualThanTopWithoutEmpty},${tasksFromBufferBetterThanSecondTopWithoutEmpty},${tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty}," +
//                        "${filledTimes},${insertedAfterSteal},${insertedAfterGoodSteal},${insertedAfterBadSteal},${nodesCount}"
//            )
//
//            clearMyResults()
//        }
//
//    }
//
//    companion object {
//
//        private val results = ArrayList<BenchmarkResultWithMetrics>()
//
//        private val readyResultsDijkstra: List<BenchmarkResultWithMetrics>
//            @Synchronized
//            get() = results
//
//        @Synchronized
//        private fun clearMyResults() {
//            results.clear()
//        }
//
//        @Synchronized
//        private fun addResultDijkstra(result: BenchmarkResultWithMetrics) {
//            results.add(result)
//        }
//
//    }
//
//}
