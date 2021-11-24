package org.jetbrains.kotlin.scheduler

import kotlinx.coroutines.Runnable
import java.io.Closeable

interface PriorityCoroutineScheduler : Closeable {

    fun execute(block: Runnable, priority: Int)

    fun start()

}