package org.jetbrains.kotlin.generic.smq.heap

interface StealingQueue<E> {
    fun steal(): List<E>
    val top: E?
}