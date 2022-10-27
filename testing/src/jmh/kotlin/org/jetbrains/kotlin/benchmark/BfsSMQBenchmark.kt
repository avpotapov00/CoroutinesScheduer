package org.jetbrains.kotlin.benchmark

import org.jetbrains.kotlin.generic.PriorityMeasurementBfsScheduler
import org.jetbrains.kotlin.graph.GraphReader
import org.jetbrains.kotlin.graph.dijkstra.BfsIntNode
import org.jetbrains.kotlin.graph.dijkstra.clearNodesBfs
import org.jetbrains.kotlin.number.scheduler.PriorityLongBfsSchedulerKS
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BfsSMQBenchmark {


    @Benchmark
    fun testSequenceKS(config: Config) {
        val scheduler = PriorityLongBfsSchedulerKS(
            config.nodes,
            startIndex = 0,
            poolSize = config.threads,
            stealSize = config.stealSize,
            pSteal = config.pSteal,
        ).use { scheduler ->
            scheduler.waitForTermination()
            scheduler
        }

        addResultBfs(
            BenchmarkResultWithMetrics(
                "bfs", config.sourcePath, config.pSteal, config.stealSize,
                stealingAttempts = scheduler.stealingAttempts(),
                failedStealing = scheduler.failedStealing(),
                totalTasksProcessed = scheduler.totalTasksProcessed(),
                successStealing = scheduler.successStealing(),
                tasksBetterThanTop = scheduler.tasksLowerThanStolen(),
                stealingTotal = scheduler.stealingTotal(),
                stolenCountSum = scheduler.stolenCountSum(),
                fullBufferTimesSum = scheduler.fullBufferTimesSum(),
                tasksLowerThanStolenIncludingOurEmptiness = scheduler.tasksLowerThanStolenIncludingOurEmptiness(),

                tasksFromBufferBetterThanTop = scheduler.tasksFromBufferBetterThanTop(),
                tasksFromBufferBetterOrEqualThanTop = scheduler.tasksFromBufferBetterOrEqualThanTop(),
                tasksFromBufferBetterOrEqualThanSecondTop = scheduler.tasksFromBufferBetterOrEqualThanSecondTop(),
                tasksFromBufferBetterThanSecondTop = scheduler.tasksFromBufferBetterThanSecondTop(),

                tasksFromBufferBetterThanTopWithoutEmpty = scheduler.tasksFromBufferBetterThanTopWithoutEmpty(),
                tasksFromBufferBetterOrEqualThanTopWithoutEmpty = scheduler.tasksFromBufferBetterOrEqualThanTopWithoutEmpty(),
                tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty = scheduler.tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty(),
                tasksFromBufferBetterThanSecondTopWithoutEmpty = scheduler.tasksFromBufferBetterThanSecondTopWithoutEmpty(),

                filledTimes = scheduler.filledTimes(),
                insertedAfterSteal = scheduler.insertedAfterSteal(),
                insertedAfterBadSteal = scheduler.insertedAfterBadSteal(),
                insertedAfterGoodSteal = scheduler.insertedAfterGoodSteal(),
                nodesCount = config.nodes.size,
                updatesCount = scheduler.updatesCount(),
                updateAttemptsCount = scheduler.updateAttemptsCount(),
                uselessWork = scheduler.uselessWork(),
                abortedUpdates = scheduler.abortedUpdates()
            )
        )
    }


    @State(Scope.Thread)
    open class Config {

        var threads: Int = 72

        @Param("1", "0.5", "0.25", "0.125", "0.0625", "0.03125", "0.015625", "0.0078125", "0.001953125", "0.0009765625")
        var pSteal: Double = 0.03125

        @Param("1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024")
        var stealSize: Int = 32

        @Param(
            "/soc-LiveJournal1.txt",
            "/USA-road-d.W.gr",
//            "/USA-road-d.CTR.gr",
//            "/USA-road-d.USA.gr",
        )
        lateinit var sourcePath: String

        lateinit var nodes: List<BfsIntNode>


        @Setup(Level.Trial)
        fun setup() {
            nodes = GraphReader().readGraphNodesBiDirectBfs(sourcePath)
        }

        @TearDown(Level.Invocation)
        fun clear() {
            clearNodesBfs(nodes)
        }

        @TearDown(Level.Trial)
        fun printAll() {
            val results = readyResultsBfs
            val size = results.size.toDouble()
            val config = results.first()

            val totalTasksProcessed: Double = results.sumOf { it.totalTasksProcessed } / size
            val successStealing: Double = results.sumOf { it.successStealing } / size
            val failedStealing: Double = results.sumOf { it.failedStealing } / size
            val stealingAttempts: Double = results.sumOf { it.stealingAttempts } / size
            val tasksBetterThanTop: Double = results.sumOf { it.tasksBetterThanTop } / size
            val stealingTotal: Double = results.sumOf { it.stealingTotal } / size
            val stolenCountSum: Double = results.sumOf { it.stolenCountSum } / size
            val fullBufferTimesSum: Double = results.sumOf { it.fullBufferTimesSum } / size
            val tasksLowerThanStolenIncludingOurEmptiness: Double =
                results.sumOf { it.tasksLowerThanStolenIncludingOurEmptiness } / size

            val tasksFromBufferBetterThanTop: Double = results.sumOf { it.tasksFromBufferBetterThanTop } / size
            val tasksFromBufferBetterOrEqualThanTop: Double =
                results.sumOf { it.tasksFromBufferBetterOrEqualThanTop } / size
            val tasksFromBufferBetterOrEqualThanSecondTop: Double =
                results.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTop } / size
            val tasksFromBufferBetterThanSecondTop: Double =
                results.sumOf { it.tasksFromBufferBetterThanSecondTop } / size

            val tasksFromBufferBetterThanTopWithoutEmpty: Double =
                results.sumOf { it.tasksFromBufferBetterThanTopWithoutEmpty } / size
            val tasksFromBufferBetterOrEqualThanTopWithoutEmpty: Double =
                results.sumOf { it.tasksFromBufferBetterOrEqualThanTopWithoutEmpty } / size
            val tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty: Double =
                results.sumOf { it.tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty } / size
            val tasksFromBufferBetterThanSecondTopWithoutEmpty: Double =
                results.sumOf { it.tasksFromBufferBetterThanSecondTopWithoutEmpty } / size

            val filledTimes: Double = results.sumOf { it.filledTimes } / size
            val insertedAfterSteal: Double = results.sumOf { it.insertedAfterSteal } / size
            val insertedAfterGoodSteal: Double = results.sumOf { it.insertedAfterGoodSteal } / size
            val insertedAfterBadSteal: Double = results.sumOf { it.insertedAfterBadSteal } / size

            val nodesCount: Int = results.first().nodesCount
            val updatesCount: Double = results.sumOf { it.updatesCount } / size
            val updateAttemptsCount: Double = results.sumOf { it.updateAttemptsCount } / size

            val uselessWork: Double = results.sumOf { it.uselessWork } / size
            val abortedUpdates: Double = results.sumOf { it.abortedUpdates } / size

            println(
                "\nDone,testName=${config.testName},pSteal=${config.pSteal},graphName=${config.graphName},stealSize=${config.stealSize}," +
                        "totalTasksProcessed=${totalTasksProcessed},successStealing=${successStealing},failedStealing=${failedStealing},stealingAttempts=${stealingAttempts},tasksBetterThanTop=${tasksBetterThanTop}," +
                        "stealingTotal=${stealingTotal},stolenCountSum=${stolenCountSum},fullBufferTimesSum=${fullBufferTimesSum},tasksLowerThanStolenIncludingOurEmptiness=${tasksLowerThanStolenIncludingOurEmptiness}," +
                        "tasksFromBufferBetterThanTop=${tasksFromBufferBetterThanTop},tasksFromBufferBetterOrEqualThanTop=${tasksFromBufferBetterOrEqualThanTop},tasksFromBufferBetterThanSecondTop=${tasksFromBufferBetterThanSecondTop},tasksFromBufferBetterOrEqualThanSecondTop=${tasksFromBufferBetterOrEqualThanSecondTop}," +
                        "tasksFromBufferBetterThanTopWithoutEmpty=${tasksFromBufferBetterThanTopWithoutEmpty},tasksFromBufferBetterOrEqualThanTopWithoutEmpty=${tasksFromBufferBetterOrEqualThanTopWithoutEmpty},tasksFromBufferBetterThanSecondTopWithoutEmpty=${tasksFromBufferBetterThanSecondTopWithoutEmpty},tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty=${tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty}," +
                        "filledTimes=${filledTimes},insertedAfterSteal=${insertedAfterSteal},insertedAfterGoodSteal=${insertedAfterGoodSteal},insertedAfterBadSteal=${insertedAfterBadSteal},nodesCount=${nodesCount},updatesCount=${updatesCount},updateAttemptsCount=${updateAttemptsCount}," +
                        "uselessWork=${uselessWork},abortedUpdates=${abortedUpdates}"
            )

            clearMyResults()
        }

    }

    companion object {

        private val results = ArrayList<BenchmarkResultWithMetrics>()

        private val readyResultsBfs: List<BenchmarkResultWithMetrics>
            @Synchronized
            get() = results

        @Synchronized
        private fun clearMyResults() {
            results.clear()
        }

        @Synchronized
        private fun addResultBfs(result: BenchmarkResultWithMetrics) {
            results.add(result)
        }

    }


}
