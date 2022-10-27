package org.jetbrains.kotlin.generic.smq.heap

interface StealingQueue<E> {
    fun steal(): List<E>
    val top: E?

    fun steal(data: MutableList<E>)
    fun steal(data: Array<E?>)
}