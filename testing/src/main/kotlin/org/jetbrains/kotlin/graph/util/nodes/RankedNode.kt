package org.jetbrains.kotlin.graph.util.nodes

interface RankedNode<T> {

    val rank: Comparable<T>

}