//package org.jetbrains.kotlin.number.dijkstra
//
//import org.jetbrains.kotlin.graph.dijkstra.shortestPathParallel
//import org.jetbrains.kotlin.graph.util.nodes.Node
//import org.jetbrains.kotlin.graph.util.nodes.clearNodes
//import org.jetbrains.kotlin.graph.util.readGraphNodesBiDirect
//import org.junit.jupiter.api.Test
//import org.openjdk.jmh.annotations.*
//import org.openjdk.jmh.runner.Runner
//import org.openjdk.jmh.runner.options.OptionsBuilder
//import java.util.concurrent.TimeUnit
//
//@BenchmarkMode(Mode.Throughput)
//@Warmup(iterations = 3)
//@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
//@Threads(1)
//@Fork(1)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class BenchmarkDijkstraIntParallel {
//
//    @Benchmark
//    fun testSequence(config: Config)  {
//        shortestPathParallel(config.nodes[0], workers = config.threads)
//    }
//
//    @State(Scope.Thread)
//    open class Config {
//
//        @Param(
//            "1", "2", "3", "4", "5", "6"
//        )
//        var threads: Int = 0
//
//        @Param(
//            "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv",
//            "src/test/resources/data/graphs/twitter_combined.txt",
//            "src/test/resources/data/graphs/musae_DE_edges_ok.csv",
//        )
//        lateinit var sourcePath: String
//
//        lateinit var nodes: List<Node>
//
//
//        @Setup(Level.Trial)
//        fun setup() {
//            nodes = readGraphNodesBiDirect(sourcePath)
//        }
//
//        @TearDown(Level.Invocation)
//        fun clear() {
//            clearNodes(nodes)
//        }
//
//    }
//
//
//    @Test
//    fun `run benchmark`() {
//        val options = OptionsBuilder()
//            .include(BenchmarkDijkstraIntParallel::class.java.simpleName)
//            .jvmArgs("-Xms4096M", "-Xmx6144M")
//            .build()
//
//        Runner(options).run()
//    }
//}