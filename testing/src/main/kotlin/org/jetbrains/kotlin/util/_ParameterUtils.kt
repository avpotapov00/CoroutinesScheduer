package org.jetbrains.kotlin.util

fun calculatePSteal(power: Int): Double {
    return 1.0 / 1.shl(power)
}

fun calculateStealSize(power: Int): Int {
    return 1.shl(power)
}