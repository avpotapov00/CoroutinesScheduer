package org.jetbrains.kotlin.graph.pagerank

import org.jetbrains.kotlin.graph.util.IntPhaser
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

internal class IntPhaserTest {

    @Test
    fun `int phaser test`() {
        val intPhaser = IntPhaser()

        intPhaser.register()
        thread {
            Thread.sleep(1000)
            intPhaser.arriveAndDeregister()
        }

        println("On phaser")
        val time = measureTimeMillis {
            intPhaser.lockAndAwait()
        }

        println("Done in $time")
    }


}