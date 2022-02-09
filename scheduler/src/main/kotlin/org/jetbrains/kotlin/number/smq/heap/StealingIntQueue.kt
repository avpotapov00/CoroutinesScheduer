package org.jetbrains.kotlin.number.smq.heap

interface StealingIntQueue {
    fun steal(): List<Int>
    val top: Int?
}