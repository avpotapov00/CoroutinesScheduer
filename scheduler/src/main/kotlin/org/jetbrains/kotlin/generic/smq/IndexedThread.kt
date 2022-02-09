package org.jetbrains.kotlin.generic.smq

abstract class IndexedThread : Thread() {
    abstract val index: Int
}

class IndexedThreadImpl(override val index: Int, private val block: Runnable) : IndexedThread() {

    override fun run() {
        block.run()
    }

}