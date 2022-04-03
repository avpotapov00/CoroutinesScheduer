package org.jetbrains.kotlin.graph.util.generator

import org.jetbrains.kotlin.graph.dijkstra.IntEdge
import org.jetbrains.kotlin.graph.dijkstra.IntNode
import org.jetbrains.kotlin.util.addEdge
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class SennitGeneratorKtTest {

    @Test
    fun `should separate withBridges`() {
        val segment = IntNodeGraph(makeStar(), 1, 3)
        val bridge = IntNodeGraph(makeRom(), 1, 2)

        val united = separateWithBridges(segment, bridge, 2)

        assertEquals(23, united.size)

        assertEqualsIgnoreOrder(listOf(IntEdge(1, 1), IntEdge(4, 1)), united[0].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(2, 1), IntEdge(0, 1)), united[1].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(3, 1), IntEdge(1, 1)), united[2].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(4, 1), IntEdge(2, 1), IntEdge(6, 1)), united[3].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(0, 1), IntEdge(3, 1)), united[4].outgoingEdges)

        assertEqualsIgnoreOrder(simpleEdges(6, 7), united[5].outgoingEdges)
        assertEqualsIgnoreOrder(simpleEdges(3, 5, 8), united[6].outgoingEdges)
        assertEqualsIgnoreOrder(simpleEdges(6, 7), united[8].outgoingEdges)
        assertEqualsIgnoreOrder(simpleEdges(8, 10, 5), united[7].outgoingEdges)

        assertEqualsIgnoreOrder(listOf(IntEdge(10, 1), IntEdge(13, 1)), united[9].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(11, 1), IntEdge(9, 1), IntEdge(7, 1)), united[10].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(12, 1), IntEdge(10, 1)), united[11].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(13, 1), IntEdge(11, 1), IntEdge(15, 1)), united[12].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(9, 1), IntEdge(12, 1)), united[13].outgoingEdges)

        assertEqualsIgnoreOrder(simpleEdges(15, 16), united[14].outgoingEdges)
        assertEqualsIgnoreOrder(simpleEdges(12, 14, 17), united[15].outgoingEdges)
        assertEqualsIgnoreOrder(simpleEdges(15, 16), united[17].outgoingEdges)
        assertEqualsIgnoreOrder(simpleEdges(17, 14, 19), united[16].outgoingEdges)

        assertEqualsIgnoreOrder(listOf(IntEdge(19, 1), IntEdge(22, 1)), united[18].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(20, 1), IntEdge(18, 1), IntEdge(16, 1)), united[19].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(21, 1), IntEdge(19, 1)), united[20].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(22, 1), IntEdge(20, 1)), united[21].outgoingEdges)
        assertEqualsIgnoreOrder(listOf(IntEdge(18, 1), IntEdge(21, 1)), united[22].outgoingEdges)
    }

    private fun simpleEdges(vararg to: Int): List<IntEdge> {
        return to.map { IntEdge(it, 1) }
    }

    private fun <T> assertEqualsIgnoreOrder(expected: List<T>, actual: List<T>) {
        assertEquals(expected = expected.toSet(), actual = actual.toSet())
    }

    private fun makeRom(): List<IntNode> {
        val bridge = (0 until 4).map { IntNode() }

        bridge.addEdge(0, 1, 1)
        bridge.addEdge(1, 3, 1)
        bridge.addEdge(3, 2, 1)
        bridge.addEdge(2, 0, 1)

        return bridge
    }

    private fun makeStar(): List<IntNode> {
        val segment = (0 until 5).map { IntNode() }
        for (i in 0 until 4) {
            segment.addEdge(i, i + 1, 1)
        }
        segment.addEdge(0, 4, 1)
        return segment
    }


}