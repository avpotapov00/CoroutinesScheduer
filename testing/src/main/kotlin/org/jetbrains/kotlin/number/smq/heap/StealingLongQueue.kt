package org.jetbrains.kotlin.number.smq.heap

interface StealingLongQueue {
    fun steal(): List<Long>
    val top: Long
}