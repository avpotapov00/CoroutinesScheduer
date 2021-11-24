package org.jetbrains.kotlin.smq.heap

interface Stealable<E> {

    fun steal(): List<E>

    fun top(): E?
}