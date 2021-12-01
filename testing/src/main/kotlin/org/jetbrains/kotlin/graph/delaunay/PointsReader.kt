package org.jetbrains.kotlin.graph.delaunay

import java.io.File


fun readPoints(file: File): List<Point> = file.bufferedReader().use { reader ->

    val firstLine = reader.readLine().split(" ")

    val x = firstLine[0].toDouble()
    val y = firstLine[1].toDouble()

    val points = mutableListOf<Point>()

    var line = reader.readLine()
    while (line != null) {
        val (pointX, pointY) = line.split(" ").map { it.toDouble() }
        points.add(Point(pointX, pointY))

        line = reader.readLine()
    }

    points
}
