package org.jetbrains.kotlin.benchmark

/**
 * @author Потапов Александр
 * @since 27.03.2022
 */
data class BenchmarkResultWithMetrics(
    val testName: String,
    val graphName: String,
    val pSteal: Double,
    val stealSize: Int,
    val totalTasksProcessed: Long,
    val successStealing: Long,
    val failedStealing: Long,
    val stealingAttempts: Long,
    val tasksBetterThanTop: Long,
    val stealingTotal: Long,
    val stolenCountSum: Long,
    val fullBufferTimesSum: Long,
    val tasksLowerThanStolenIncludingOurEmptiness: Long,

    val tasksFromBufferBetterThanTop: Long,
    var tasksFromBufferBetterOrEqualThanTop : Long,
    var tasksFromBufferBetterOrEqualThanSecondTop : Long,
    var tasksFromBufferBetterThanSecondTop : Long,

    var tasksFromBufferBetterThanTopWithoutEmpty : Long,
    var tasksFromBufferBetterOrEqualThanTopWithoutEmpty : Long,
    var tasksFromBufferBetterOrEqualThanSecondTopWithoutEmpty : Long,
    var tasksFromBufferBetterThanSecondTopWithoutEmpty : Long,

    var filledTimes: Long
)