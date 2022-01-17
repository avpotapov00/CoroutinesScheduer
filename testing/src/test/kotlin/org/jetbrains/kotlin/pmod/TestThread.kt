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

    thread.uncaughtExceptionHandler = Handler()
    thread.start()

    thread.join()
}

private class Handler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        println("Exception")
        throw e
    }

}
