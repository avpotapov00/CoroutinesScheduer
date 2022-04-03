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
)