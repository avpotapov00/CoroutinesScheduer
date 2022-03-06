package org.jetbrains.kotlin.util

val Long.firstFromLong get() = (this shr 32)

val Long.secondFromLong get() = this.toInt()

fun Int.zip(other: Int) = this.toLong() shl 32 or (other.toLong() and 0xffffffffL)

fun Long.unzip() = this.firstFromLong to this.secondFromLong