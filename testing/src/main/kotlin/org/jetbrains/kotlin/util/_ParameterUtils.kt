package org.jetbrains.kotlin.util

import kotlin.math.pow
import kotlin.system.exitProcess

fun calculatePSteal(power: Int): Double {
    return 1.0 / 1.shl(power)
}

fun calculatePSteal(power: Double): Double {
    if (power < 0) {
        println("Illegal power: $power")
        exitProcess(1)
    }
    return 1.0 / 2.0.pow(power)
}

fun calculateStealSize(power: Int): Int {
    return 1.shl(power)
}