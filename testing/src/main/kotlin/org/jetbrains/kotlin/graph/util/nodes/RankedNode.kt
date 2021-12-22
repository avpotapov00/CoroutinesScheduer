package org.jetbrains.kotlin.graph.util.nodes

interface RankedNode<T: Comparable<T>> {

    val rank: T

}