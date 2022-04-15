//package org.jetbrains.kotlin.benchmark
//
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.IntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodes
//import org.jetbrains.kotlin.number.scheduler.AdaptivePriorityLongDijkstraScheduler
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 3)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class AdaptiveDijkstraSMQBenchmark {
//
//    @Benchmark
//    fun testDijkstra(config: Config) {
//        AdaptivePriorityLongDijkstraScheduler(
//            config.nodes,
//            startIndex = 0,
//            poolSize = config.threads,
//            stealSize = config.stealSize,
//            pSteal = config.pSteal,
//            checkAttemptsCount = config.period
//        ).use { scheduler ->
//            scheduler.waitForTermination()
////            val pSteals = scheduler.threads.joinToString(",") { log2(it.pStealLocal).toInt().toString() }
////            val stealSizes = scheduler.queues.joinToString(",") { it.stealSize.toString() }
////
////            println("\nDone ${config.pSteal}, ${config.stealSize}")
////            println(pSteals)
////            println(stealSizes)
//        }
//    }
//
//
//    @State(Scope.Thread)
//    open class Config {
//
//        //                @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
//        var threads: Int = 128
//
////        @Param("100")
//        var period: Int = 1000
//
////         @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
//        @Param("1", "0.25", "0.0625", "0.015625", "0.001953125", "0.0009765625")
//        var pSteal: Double = 0.03125
//
////         @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
//        @Param("1", "4", "16", "64", "256", "1024")
//        var stealSize: Int = 32
//
//        @Param(
//            "/soc-LiveJournal1.txt",
//            "/USA-road-d.W.gr",
//            "/USA-road-d.CTR.gr",
//            "/USA-road-d.USA.gr",
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
//    }
//
//}
