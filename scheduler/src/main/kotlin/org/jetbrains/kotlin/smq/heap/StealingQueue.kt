package org.jetbrains.kotlin.smq.heap

interface StealingQueue<E> {
    fun steal(): List<E>
    fun top(): E? // TODO: val top: E?
}