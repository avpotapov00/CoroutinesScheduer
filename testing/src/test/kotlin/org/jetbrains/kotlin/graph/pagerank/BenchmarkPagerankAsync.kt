//package org.jetbrains.kotlin.graph.pagerank
//
//import kotlinx.coroutines.CoroutineDispatcher
//import kotlinx.coroutines.runBlocking
//import org.jetbrains.kotlin.generic.dispatcher.PriorityQueueCoroutineDispatcher
//import org.jetbrains.kotlin.generic.scheduler.SMQPriorityCoroutineScheduler
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
//open class BenchmarkPagerankAsync {
//
//    @Benchmark
//    fun testSequence(config: Config) = runBlocking {
//        pagerankAsyncPush(config.nodes, 0.85f, 0.01f, config.dispatcher)
//    }
//
//    @State(Scope.Thread)
//    open class Config {
//
//        @Param(
//            "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv",
//            "src/test/resources/data/graphs/twitter_combined.txt",
//            "src/test/resources/data/graphs/musae_DE_edges_ok.csv",
//        )
//        lateinit var sourcePath: String
//
//        @Param(
//            "1", "2", "3", "4", "5", "6"
//        )
//        private var threads: Int = 0
//
//        @Param("0.1" ,"0.2", "0.04", "0.016")
//        private var pSteal = 0.1
//
//        lateinit var nodes: List<Node>
//
//        lateinit var dispatcher: CoroutineDispatcher
//
//        private lateinit var scheduler: SMQPriorityCoroutineScheduler
//
//        @Setup(Level.Trial)
//        fun setup() {
//            scheduler = SMQPriorityCoroutineScheduler(threads, postponeThreadsStart = true, pSteal = pSteal)
//            dispatcher = PriorityQueueCoroutineDispatcher(scheduler)
//            nodes = readGraphNodes(sourcePath)
//        }
//
//        @TearDown(Level.Invocation)
//        fun clear() {
//            clearNodes(0f, nodes)
//        }
//
//        @TearDown(Level.Trial)
//        fun closeDispatcher() {
//            scheduler.close()
//        }
//
//    }
//
//
//    @Test
//    fun `run benchmark`() {
//        val options = OptionsBuilder()
//            .include(BenchmarkPagerankAsync::class.java.simpleName)
//            .jvmArgs("-Xms4096M", "-Xmx6144M")
//            .build()
//
//        Runner(options).run()
//    }
//}