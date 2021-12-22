package org.jetbrains.kotlin.graph.pagerank

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.dispatcher.PriorityQueueCoroutineDispatcher
import org.jetbrains.kotlin.scheduler.ExperimentalPriorityCoroutineScheduler
import org.junit.jupiter.api.Test

internal class AsyncPageRankKtTest {

    @Test
    fun `run async pagerank`() = runBlocking {
        val scheduler = ExperimentalPriorityCoroutineScheduler(4, startThreads = true, pSteal = 0.05)
        val dispatcher = PriorityQueueCoroutineDispatcher(scheduler)

        val file = "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv"
        val nodes = readGraphNodes(file)

        pagerankParallelPush(nodes, 0.85f, 1e-2f, 4)

        println("Done")
        dispatcher.close()
    }

}