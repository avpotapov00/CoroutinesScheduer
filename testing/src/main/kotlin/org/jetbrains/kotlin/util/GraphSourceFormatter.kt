package org.jetbrains.kotlin.util

import java.io.File

fun main() {
    val file =
        File("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/testing/src/jmh/resources/soc-LiveJournal1.txt")
    val resultFile =
        File("/Users/aleksandrpotapov/Documents/CoroutinesScheduer/testing/src/jmh/resources/soc-LiveJournal1_fixed.txt")

    resultFile.bufferedWriter().use { writer ->
        file.bufferedReader().useLines { lineSequence ->
            lineSequence.forEach { line ->
                if (!line.startsWith("a")) {
                    writer.appendLine(line)
                } else {
                    val strings = line.split(" ")
                    check(strings.size == 4)
                    val (_, from, to, w) = strings

                    writer.appendLine("a ${from.toInt() + 1} ${to.toInt() + 1} $w")
                }
            }
        }
    }

}