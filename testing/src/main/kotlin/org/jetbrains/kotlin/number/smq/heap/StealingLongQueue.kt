package org.jetbrains.kotlin.number.smq.heap

interface StealingLongQueue {
    fun steal(): List<Long>
    fun steal(data: MutableList<Long>)
    fun steal(data: LongArray)
    val top: Long
}