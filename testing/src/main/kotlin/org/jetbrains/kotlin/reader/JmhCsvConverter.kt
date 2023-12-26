package org.jetbrains.kotlin.reader

import java.io.File

fun main() {
    val dir = File("/Users/Aleksandr.Potapov/Documents/Work/CoroutinesScheduer/history/26_12_23_mq1")
    val txtDir = File(dir.absolutePath + "/new_txt/")
    val regex = "\\s+".toRegex()

    txtDir.listFiles()!!.filter { ".txt" in it.name }
        .forEach { sourceFile ->
            val destinationFileNam = sourceFile.name.replace(".txt", ".csv")
            val destinationFile = File(dir.absolutePath + "/csv/$destinationFileNam")

            sourceFile.bufferedReader().use { reader ->
                while ("Do not assume the numbers tell you what you want them to tell." !in reader.readLine()) {
                }
                val emptyLine = reader.readLine()
                check(emptyLine.isEmpty())

                val header = reader.readLine()
                val columns = header.split(regex)
                println("Header: $columns")
                val csvValues = mutableListOf<List<String>>()

                var line = reader.readLine()
                while (line != null && line.isNotEmpty()) {
                    val values = line.split(regex).filter { it != "?" && it != "±" }
                        .map { if (it.contains(",")) "\"$it\"" else it }
                    check(values.size == columns.size) { "Bad line: $line. Values: $values" }
                    csvValues.add(values)
                    line = reader.readLine()
                }
                destinationFile.bufferedWriter().use { writer ->
                    writer.appendLine(columns.joinToString(","))
                    csvValues.forEach { lineValues ->
                        writer.appendLine(lineValues.joinToString(","))
                    }
                }
            }

        }
}