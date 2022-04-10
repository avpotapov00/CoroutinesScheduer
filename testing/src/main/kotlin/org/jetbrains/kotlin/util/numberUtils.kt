package org.jetbrains.kotlin.util

val Long.firstFromLong get() = (this shr 32)

val Long.secondFromLong get() = this.toInt()

fun Int.zip(other: Int) = this.toLong() shl 32 or (other.toLong() and 0xffffffffL)

fun Long.unzip() = this.firstFromLong to this.secondFromLong

fun indexedBinarySearch(l: List<Long>, key: Long): Int {
    val keyFirst = key.firstFromLong

    var low = 0
    var high = l.size - 1
    while (low <= high) {
        val mid = low + high ushr 1
        val midVal = l[mid]
        val firstFromLongMidVal = midVal.firstFromLong
        if (firstFromLongMidVal < keyFirst) low = mid + 1 else if (firstFromLongMidVal > keyFirst) high =
            mid - 1 else return mid // key found
    }
    return low // key not found
}

