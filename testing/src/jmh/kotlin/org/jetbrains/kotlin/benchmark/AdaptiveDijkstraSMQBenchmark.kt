//package org.jetbrains.kotlin.benchmark
//
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.IntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodes
//import org.jetbrains.kotlin.number.scheduler.AdaptiveDijkstraScheduler
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
//        val scheduler = AdaptiveDijkstraScheduler(
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
////        val sizes = scheduler.threads.map { it.stealSizeLocal }
////        val avg = sizes.average()
////        val middle = sizes.sorted()[sizes.size / 2]
////
////        println("Dij, graph=${config.sourcePath}, pSteal=${config.pSteal}, stealSize=${config.stealSize} -> $avg, $middle")
//    }
//
//
//    @State(Scope.Thread)
//    open class Config {
//
//        //        @Param("2", "4", "8", "16", "32", "64", "76")
//        var threads: Int = 72
//
//        //        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
//        @Param("1", "0.25", "0.0625", "0.015625", "0.001953125", "0.0009765625")
//        var pSteal: Double = 0.03125
//
//        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
//        @Param("1", "4", "16", "64", "256", "1024")
//        var stealSize: Int = 2
//
//        @Param(
//            "/soc-LiveJournal1.txt",
//            "/USA-road-d.W.gr",
////            "/USA-road-d.CTR.gr",
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
