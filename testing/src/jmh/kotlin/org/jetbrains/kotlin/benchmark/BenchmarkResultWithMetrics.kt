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
    val retrievals: Long,
    val successSteals: Long
)