package org.jetbrains.kotlin.graph.pagerank

import org.junit.jupiter.api.Test

internal class SequentialPageRankKtTest {

    @Test
    fun `page rank test`() {
        val file = "src/test/resources/data/graphs/DCh-Miner_miner-disease-chemical.tsv"
        val nodes = readGraphNodes(file)

        pagerankSequential(nodes, 0.5f, 0.1f)
    }

}