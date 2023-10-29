package org.jetbrains.kotlin.reader

import java.io.File

/**
 * Checks that all files in the provided directory represent completed JMH results.
 */
fun main() {
    val dir = File("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/out/results")
    val filesWithoutResults = dir.listFiles()!!.filter { it.name.contains("t_") }
        .filter { file ->
            !file.useLines { lines ->
                lines.any { "# Run complete. Total time" in it }
            }
        }

    check(filesWithoutResults.isEmpty()) {
        println("Bad files: $filesWithoutResults")
    }
}