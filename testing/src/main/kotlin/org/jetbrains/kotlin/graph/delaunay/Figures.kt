package org.jetbrains.kotlin.graph.delaunay

import java.util.concurrent.ConcurrentHashMap

data class Point(
    val x: Double,
    val y: Double
) {
    val adjustedTriangles: MutableSet<Triangle> = ConcurrentHashMap.newKeySet()
}

data class Edge(
    val point1: Point,
    val point2: Point
) {

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Edge) return false
        if (this === other) return true

        val samePoints = point1 == other.point1 && point2 == other.point2
        val samePointsReversed = point1 == other.point2 && point2 == other.point1

        return samePoints || samePointsReversed
    }

    override fun hashCode(): Int {
        return (point1.x.toInt() xor point1.y.toInt() xor point2.x.toInt() xor point2.y.toInt()).hashCode()
    }
}

class Triangle(
    a: Point, b: Point, c: Point
) {

    val vertices: Array<Point>

    private lateinit var circumCenter: Point

    var radiusSquared: Double = 0.0

    init {
        vertices = if (isCounterClockwise(a, b, c)) {
            arrayOf(a, b, c)
        } else {
            arrayOf(a, c, b)
        }

        vertices.forEach { it.adjustedTriangles.add(this) }

        updateCircumCircle()
    }

    private fun updateCircumCircle() {
        val p0 = vertices[0]
        val p1 = vertices[1]
        val p2 = vertices[2]

        val dA = p0.x * p0.x + p0.y * p0.y
        val dB = p1.x * p1.x + p1.y * p1.y
        val dC = p2.x * p2.x + p2.y * p2.y

        val aux1 = dA * (p2.y - p1.y) + dB * (p0.y - p2.y) + dC * (p1.y - p0.y)
        val aux2 = -(dA * (p2.x - p1.x) + dB * (p0.x - p2.x) + dC * (p1.x - p0.x))
        val div = 2 * (p0.x * (p2.y - p1.y) + p1.x * (p0.y - p2.y) + p2.x * (p1.y - p0.y))

        val center = Point(aux1 / div, aux2 / div)
        circumCenter = center
        radiusSquared = (center.x - p0.x) * (center.x - p0.x) + (center.y - p0.y) * (center.y - p0.y)
    }


    private fun isCounterClockwise(point1: Point, point2: Point, point3: Point): Boolean {
        val result = (point2.x - point1.x) * (point3.y - point1.y) -
                (point3.x - point1.x) * (point2.y - point1.y)
        return result > 0
    }

    fun isPointInsideCircumCircle(point: Point): Boolean {
        val dSquared = (point.x - circumCenter.x) * (point.x - circumCenter.x) +
                (point.y - circumCenter.y) * (point.y - circumCenter.y)
        return dSquared < radiusSquared
    }

}


