//package org.jetbrains.kotlin.benchmark
//
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.boruvka.intNodesToEdges
//import org.jetbrains.kotlin.graph.util.edges.Edge
//import org.jetbrains.kotlin.number.scheduler.parallelBoruvkaExp
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 3)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class BoruvkaSMQBenchmark {
//
//    @Benchmark
//    fun testSequence(config: Config) {
//        val result = parallelBoruvkaExp(
//            config.edges,
//            config.nodes,
//            poolSize = config.threads,
//            stealSize = config.stealSize,
//            pSteal = config.pSteal
//        )
//        val scheduler = result.first
//
////        addResultBoruvka(
////            BenchmarkResultWithMetrics(
////                "dijkstra", config.sourcePath, config.pSteal, config.stealSize,
////                stealingAttempts = scheduler.stealingAttempts(),
////                failedStealing = scheduler.failedStealing(),
////                totalTasksProcessed = scheduler.totalTasksProcessed(),
////                successStealing = scheduler.successStealing(),
////                tasksBetterThanTop = scheduler.tasksLowerThanStolen()
////            )
////        )
//    }
//
//    @State(Scope.Thread)
//    open class Config {
//
//        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
//        var threads: Int = 128
//
//        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125")
//        var pSteal: Double = 0.03125
//
//        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
//        var stealSize: Int = 8
//
//        @Param(
//            "/USA-road-d.W.gr",
////            "/USA-road-d.USA.gr"
//        )
//        lateinit var sourcePath: String
//
//        var nodes: Int = 0
//
//        lateinit var edges: List<Edge>
//
//        @Setup(Level.Trial)
//        fun setup() {
//            val graph = GraphReader().readGraphNodesBiDirect(sourcePath)
//            val (nodesCount, edges) = intNodesToEdges(graph)
//            nodes = nodesCount
//            this.edges = edges
//        }
//
//        @TearDown(Level.Trial)
//        fun printAll() {
//            val results = readyResultsBoruvka
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
//            clearResultsBoruvka()
//        }
//
//    }
//
//    companion object {
//
//        private val results = ArrayList<BenchmarkResultWithMetrics>()
//
//        val readyResultsBoruvka: List<BenchmarkResultWithMetrics>
//            @Synchronized
//            get() = results
//
//        @Synchronized
//        fun clearResultsBoruvka() {
//            results.clear()
//        }
//
//        @Synchronized
//        fun addResultBoruvka(result: BenchmarkResultWithMetrics) {
//            results.add(result)
//        }
//
//    }
//
//}