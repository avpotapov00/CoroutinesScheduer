package org.jetbrains.kotlin.smq

abstract class IndexedThread: Thread() {

    abstract val index: Int

}