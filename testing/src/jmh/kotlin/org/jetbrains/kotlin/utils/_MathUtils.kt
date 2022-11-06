package org.jetbrains.kotlin.utils

fun dispersionFromList(data: List<Int>): Double {
    val avg = data.average()

    return data.sumOf { (it - avg) * (it - avg) } / (data.size - 1)
}

fun meanFromList(data: List<Int>): Double {
    val sorted = data.sorted()
    val halfIndex = data.size / 2

    if (data.size % 2 == 1) {
        return sorted[halfIndex].toDouble()
    }

    return (sorted[halfIndex] + sorted[halfIndex - 1]) / 2.0
}

fun List<Int>.dispersion() = dispersionFromList(this)

fun List<Int>.mean() = meanFromList(this)
