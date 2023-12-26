package org.jetbrains.kotlin.runner

import org.jetbrains.kotlin.graph.GraphReader
import java.io.File
import java.util.Random

fun main() {
    check()
}

private fun check() {
    val graph =
        GraphReader().readGraphNodesBiDirectFromFile("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.Full.gr")
    createParallelRunner(graph).use { scheduler ->
        scheduler.waitForTermination()
    }
    val allSet = graph.all { it.distance != Int.MAX_VALUE }
    val allPositive = graph.all { it.distance >= 0 }
    println(allSet)
    println(allPositive)
}

private fun fix() {
    val source = File("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/out.dimacs9-USA")

    val regex = Regex("(.*)\\s+(.*)")
    var totalEdges: Int = 0
    var totalNodes: Int = 0
    source.bufferedReader().useLines { lines ->
        var minValue = Int.MAX_VALUE
        var maxValue = Int.MIN_VALUE
        var edges = 0
        val nodes = hashSetOf<Int>()
        lines.filter { !it.startsWith("%") }
            .forEach { line ->
                val values = regex.find(line)!!.groupValues.drop(1).map { it.toInt() }
                values.forEach { v ->
                    minValue = minValue.coerceAtMost(v)
                    maxValue = maxValue.coerceAtLeast(v)
                    nodes.add(v)
                }
                edges++
            }
        println("Min = $minValue")
        println("Max = $maxValue")
        println("Edges = $edges")
        println("Nodes = ${nodes.size}")
        totalEdges = edges
        totalNodes = nodes.size
    }

    val random = Random(0)
    val dest = File("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/graphs/USA-road-d.Full.gr")
    dest.bufferedWriter().use { writer ->
        writer.appendLine("p sp $totalNodes $totalEdges")
        source.bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("%") }
                .forEach { line ->
                    val values = regex.find(line)!!.groupValues.drop(1).map { it.toInt() }
                    check(values.size == 2)
                    val (from, to) = values
                    val weight = random.nextInt(255) + 1
                    writer.appendLine("a $from $to $weight")
                }
        }
    }
}