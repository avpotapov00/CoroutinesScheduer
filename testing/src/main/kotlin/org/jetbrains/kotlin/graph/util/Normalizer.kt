package org.jetbrains.kotlin.graph.util

import java.io.BufferedWriter
import java.io.File
import kotlin.random.Random

private fun reformatGraphFile(file: File, destination: File) {
    file.bufferedReader().useLines { lines ->
        destination.bufferedWriter().use { writer ->
            lines.forEach { line ->
                writer.append(line.replace(",", " "))
                writer.appendLine()
            }
        }
    }
}

private fun reformatGraph(file: File, destination: File) {
    val graph = readGraphEdges(file.absolutePath)
    val random = Random(0)

    destination.bufferedWriter().use { writer ->
        writer.writeLine(graph.nodes.toString())
        graph.edges.forEach { edge ->
            writer.writeLine("${edge.from} ${edge.to} ${random.nextInt(0, 100)}")
        }
    }
}

private fun BufferedWriter.writeLine(content: String) {
    write(content)
    newLine()
}

fun main() {
    val graph =
        File("E:\\Diploma\\dm-examples-master\\CoroutinesScheduer\\testing\\src\\test\\resources\\data\\twitter_combined.txt")
    val destination =
        File("E:\\Diploma\\dm-examples-master\\CoroutinesScheduer\\testing\\src\\test\\resources\\data\\graphs\\twitter_combined.txt")
    reformatGraph(
        file = graph,
        destination = destination
    )
}