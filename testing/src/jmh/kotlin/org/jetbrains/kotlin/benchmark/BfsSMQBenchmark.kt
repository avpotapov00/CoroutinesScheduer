//package org.jetbrains.kotlin.benchmark
//
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodesBfs
//import org.jetbrains.kotlin.number.scheduler.NonBlockingLongBfsScheduler
//import org.jetbrains.kotlin.number.scheduler.PriorityLongBfsSchedulerKS
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 3)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class BfsSMQBenchmark {
//
//    @Benchmark
//    fun testSequenceKS(config: Config) {
//        val scheduler = PriorityLongBfsSchedulerKS(
//            config.nodes,
//            startIndex = 0,
//            poolSize = config.threads,
//            stealSize = config.stealSize,
//            pSteal = config.pSteal
//        ).use {
//            it.waitForTermination()
//            it
//        }
//
//        addResultBfs(
//            BenchmarkResultWithMetrics(
//                "bfs", config.sourcePath, config.pSteal, config.stealSize,
//                stealingAttempts = scheduler.stealingAttempts(),
//                failedStealing = scheduler.failedStealing(),
//                totalTasksProcessed = scheduler.totalTasksProcessed(),
//                successStealing = scheduler.successStealing(),
//                tasksBetterThanTop = scheduler.tasksLowerThanStolen()
//            )
//        )
//    }
//
//
//    @State(Scope.Thread)
//    open class Config {
//
//        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
//        var threads: Int = 76
//
//        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
//        var pSteal: Double = 0.03125
//
//        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
//        var stealSize: Int = 8
//
//        @Param(
//            "/USA-road-d.CTR.gr",
//            "/soc-LiveJournal1.txt",
//            "/USA-road-d.W.gr",
//            "/USA-road-d.USA.gr",
//        )
//        lateinit var sourcePath: String
//
//        lateinit var nodes: List<BfsIntNode>
//
//
//        @Setup(Level.Trial)
//        fun setup() {
//            nodes = GraphReader().readGraphNodesBiDirectBfs(sourcePath)
//        }
//
//        @TearDown(Level.Invocation)
//        fun clear() {
//            clearNodesBfs(nodes)
//        }
//
//        @TearDown(Level.Trial)
//        fun printAll() {
//            val results = readyResultsBfs
//            val size = results.size.toDouble()
//            val config = results.first()
//
//            val totalTasksProcessed: Double = results.sumOf { it.totalTasksProcessed } / size
//            val successStealing: Double = results.sumOf { it.successStealing } / size
//            val failedStealing: Double = results.sumOf { it.failedStealing } / size
//            val stealingAttempts: Double = results.sumOf { it.stealingAttempts } / size
//            val tasksBetterThanTop: Double = results.sumOf { it.tasksBetterThanTop } / size
//
//            println(
//                "\nDone,${config.testName},${config.pSteal},${config.graphName},${config.stealSize}," +
//                        "${totalTasksProcessed},${successStealing},${failedStealing},${stealingAttempts},${tasksBetterThanTop}"
//            )
//
//            clearMyResultsBfs()
//        }
//
//    }
//
//    companion object {
//
//        private val results = ArrayList<BenchmarkResultWithMetrics>()
//
//        private val readyResultsBfs: List<BenchmarkResultWithMetrics>
//            @Synchronized
//            get() = results
//
//        @Synchronized
//        private fun clearMyResultsBfs() {
//            results.clear()
//        }
//
//        @Synchronized
//        private fun addResultBfs(result: BenchmarkResultWithMetrics) {
//            results.add(result)
//        }
//
//    }
//
//}
