//package org.jetbrains.kotlin.benchmark
//
//import org.jetbrains.kotlin.graph.GraphReader
//import org.jetbrains.kotlin.graph.dijkstra.IntNode
//import org.jetbrains.kotlin.graph.dijkstra.clearNodes
//import org.jetbrains.kotlin.mq.shortestPathParallel
//import org.jetbrains.kotlin.mq.singleThreadMQDijkstra
//import org.jetbrains.kotlin.mq.singleThreadPriorityQueueDijkstra
//import org.openjdk.jmh.annotations.*
//import java.util.concurrent.TimeUnit
//
//@Warmup(iterations = 1)
//@Measurement(iterations = 8)
//@Fork(value = 1)
//@Threads(1)
//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.SECONDS)
//open class BaselineBenchmark {
//
//    @Benchmark
//    fun testWith128ThreadMQ(config: Config) {
//        shortestPathParallel(config.nodes, 0, 128)
//    }
//
//    @State(Scope.Thread)
//    open class Config {
//
//        @Param(
//            "/USA-road-d.W.gr",
//            "/USA-road-d.USA.gr"
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