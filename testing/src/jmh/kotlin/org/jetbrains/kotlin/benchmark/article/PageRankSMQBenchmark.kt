//package org.jetbrains.kotlin.benchmark.article
//
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.FloatNode
//import org.jetbrains.kotlin.number.scheduler.PriorityLongPageRankScheduler
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 5)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class PageRankSMQBenchmark {
//
//    @Benchmark
//    fun testPageRank(config: Config) {
//        val scheduler = PriorityLongPageRankScheduler(
//            config.nodes,
//            poolSize = config.threads,
//            stealSize = config.stealSize,
//            pSteal = config.pSteal,
//            alpha = 0.85f, tolerance = 0.001f,
//        ).use {
//            it.waitForTermination()
//            it
//        }
//    }
//
//
//    @State(Scope.Thread)
//    open class Config {
//
//        //        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256")
//        var threads: Int = 72
//
//        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
////        @Param("1", "0.25", "0.0625", "0.015625", "0.001953125", "0.0009765625")
//        var pSteal: Double = 0.03125
//
//        //        @Param("2", "8", "32", "128", "512", "1024")
//        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
//        var stealSize: Int = 8
//
//        @Param(
////            "/USA-road-d.CTR.gr",
//            "/soc-LiveJournal1.txt",
//            "/USA-road-d.W.gr",
//            "USA-road-d.W.gr-net-soc-LiveJournal1-3.txt"
////            "/USA-road-d.USA.gr",
//        )
//        lateinit var sourcePath: String
//
//        lateinit var nodes: List<FloatNode>
//
//
//        @Setup(Level.Trial)
//        fun setup() {
//            nodes = GraphReader().readGraphFloatNodesBiDirect(sourcePath)
//        }
//    }
//
//
//}
