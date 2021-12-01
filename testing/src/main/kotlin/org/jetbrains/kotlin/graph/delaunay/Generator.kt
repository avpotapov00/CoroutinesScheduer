package org.jetbrains.kotlin.graph.delaunay

import java.io.File
import kotlin.random.Random

/**
 * Format:
 * maxX maxY
 * point1_X, point1_Y
 * ...
 * pointN_X, pointN_Y
 */
fun generatePoints(amount: Int, maxX: Double, maxY: Double, fileName: String){
    val point0 = Point(0.0, 0.0)
    val point1 = Point(0.0, maxY)
    val point2 = Point(maxX, maxY)
    val point3 = Point(maxX, 0.0)
    val points = mutableListOf(point0, point1, point2, point3)

    val random = Random(System.currentTimeMillis())
    for (i in 0..amount - 4) {
        val pointX = random.nextDouble() * maxX;
        val pointY = random.nextDouble() * maxY;
        points.add(Point(pointX, pointY));
    }

    File(fileName).bufferedWriter().use {  writer ->
        writer.write("$maxX $maxY\n")
        points.forEach { point ->
            writer.write("${point.x} ${point.y}\n")
        }
    }

}

fun main() {
    val path = "testing/src/main/resources/data.delaunay/"

    for (index in 0 until 30) {
        generatePoints(2000, 500.0, 500.0, path + "sample_$index")
    }
}

