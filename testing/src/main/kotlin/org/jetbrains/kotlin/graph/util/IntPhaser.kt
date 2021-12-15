package org.jetbrains.kotlin.graph.util

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

class IntPhaser {

    private val lock = ReentrantLock()

    private val condition = lock.newCondition()

    private val counter = atomic(0)

    fun arriveAndDeregister() {
        if (counter.decrementAndGet() <= 0) {
            lock.withLock {
                condition.signal()
            }
        }
    }

    fun register() {
        counter.incrementAndGet()
    }

    fun lockAndAwait() {
        lock.withLock {
            while (counter.value != 0) {
                condition.await()
            }
        }
    }

}
