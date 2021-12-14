package org.jetbrains.kotlin.graph.pagerank

import kotlinx.atomicfu.atomic

class AtomicFloat(
    initial: Float
) {

    private val bits = atomic(initial.toBits())

    var value: Float
        get() = Float.fromBits(bits.value)
        set(value) {
            bits.value = value.toBits()
        }

    fun compareAndSet(oldValue: Float, newValue: Float): Boolean {
        return bits.compareAndSet(oldValue.toBits(), newValue.toBits())
    }

}