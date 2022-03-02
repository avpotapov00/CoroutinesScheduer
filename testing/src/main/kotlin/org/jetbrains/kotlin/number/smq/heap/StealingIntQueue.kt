package org.jetbrains.kotlin.number.smq.heap

interface StealingIntQueue {
    fun steal(): List<Long>
    val top: Long
}