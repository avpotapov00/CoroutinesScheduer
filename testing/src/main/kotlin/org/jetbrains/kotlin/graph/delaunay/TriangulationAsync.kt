package org.jetbrains.kotlin.graph.delaunay

import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TriangulationAsync(
    private val points: List<Point>
) {

    private val border: List<Triangle>

    init {
        val tri1 = Triangle(points[0], points[1], points[2]);
        val tri2 = Triangle(points[0], points[2], points[3]);

        border = listOf(tri1, tri2)
    }

    suspend fun bowyerWatson(dispatcher: CoroutineDispatcher): Iterable<Triangle> =
        coroutineScope {
            val triangulation: MutableSet<Triangle> = ConcurrentHashMap.newKeySet<Triangle>().also { it.addAll(border) }

            points.forEach { point ->
                val badTriangles = findBadTriangles(point, triangulation)
                val polygon = findHoleBoundaries(badTriangles)

                triangulation.removeIf { badTriangles.contains(it) }

                badTriangles.map { triangle ->
                    launch(dispatcher) {
                        for (vertex in triangle.vertices) {
                            vertex.adjustedTriangles.remove(triangle)
                        }
                    }
                }.joinAll()

                polygon.map { edge ->
                    launch(dispatcher) {
                        if (edge.point1 == point || edge.point2 == point) return@launch

                        val triangle = Triangle(point, edge.point1, edge.point2);
                        triangulation.add(triangle)
                    }
                }.joinAll()
            }

            return@coroutineScope triangulation
        }

    private fun findHoleBoundaries(badTriangles: Set<Triangle>): List<Edge> {
        val edges = mutableListOf<Edge>();

        for (triangle in badTriangles) {
            edges.add(Edge(triangle.vertices[0], triangle.vertices[1]));
            edges.add(Edge(triangle.vertices[1], triangle.vertices[2]));
            edges.add(Edge(triangle.vertices[2], triangle.vertices[0]));
        }

        return edges.groupBy { it }.filter { it.value.size == 1 }.map { it.value[0] }
    }

    private fun findBadTriangles(point: Point, triangles: MutableSet<Triangle>): Set<Triangle> {
        return triangles.filter { it.isPointInsideCircumCircle(point) }.toHashSet()
    }

}

fun main(): Unit = runBlocking {
    val file = "testing/src/main/resources/data.delaunay/sample_0"
    val points = readPoints(File(file))

    val result = TriangulationAsync(points).bowyerWatson(Dispatchers.Default)

    checkTriangulation(points, result.toList())
}

