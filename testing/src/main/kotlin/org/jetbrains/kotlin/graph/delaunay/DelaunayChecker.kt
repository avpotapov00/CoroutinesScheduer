package org.jetbrains.kotlin.graph.delaunay

fun checkTriangulation(points: List<Point>, triangulation: List<Triangle>) {
    val condition =
        points.all { point -> triangulation.any { triangle -> triangle.vertices.contains(point) } }
    check(condition) { "Not all points used" }

    triangulation.forEach { triangle ->
        val correct = points.filter { point -> !triangle.vertices.contains(point) }
            .none { triangle.isPointInsideCircumCircle(it) }
        check(correct) { "Triangle contains too much points" }
    }

    triangulation.forEach { triangle ->
        check(triangle.vertices.all { points.contains(it) }) { "Redundant triangles found" }
    }

}