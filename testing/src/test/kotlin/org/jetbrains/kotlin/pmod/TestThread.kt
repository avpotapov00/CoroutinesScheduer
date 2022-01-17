package org.jetbrains.kotlin.pmod

import org.jetbrains.kotlin.smq.IndexedThread

class TestThread(
    private val block: () -> Unit,
    override val index: Int = 0
) : IndexedThread() {

    override fun run() {
        block()
    }

}

fun withTestThread(index: Int = 0, block: () -> Unit) {
    val thread = TestThread(block, index)
    thread.start()
    thread.join()
}
