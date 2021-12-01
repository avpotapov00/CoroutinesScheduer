package org.jetbrains.kotlin.graph.delaunay

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class TriangulationParallel(
    private val points: List<Point>
) {

    private val border: List<Triangle>

    init {
        val tri1 = Triangle(points[0], points[1], points[2]);
        val tri2 = Triangle(points[0], points[2], points[3]);

        border = listOf(tri1, tri2)
    }

    fun bowyerWatson(): Iterable<Triangle> {
        val triangulation: MutableSet<Triangle> = ConcurrentHashMap.newKeySet<Triangle>().also { it.addAll(border) }

        val threads = Executors.newFixedThreadPool(2)

        points.forEach { point ->
            val badTriangles = findBadTriangles(point, triangulation)
            val polygon = findHoleBoundaries(badTriangles)

            triangulation.removeIf { badTriangles.contains(it) }

            badTriangles.map { triangle ->
                threads.submit {
                    for (vertex in triangle.vertices) {
                        vertex.adjustedTriangles.remove(triangle)
                    }
                }
            }.forEach { it.get() }

            polygon.map { edge ->
                threads.submit {
                    if (edge.point1 != point && edge.point2 != point) {

                        val triangle = Triangle(point, edge.point1, edge.point2);
                        triangulation.add(triangle)
                    }
                }
            }.forEach { it.get() }
        }

        return triangulation
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

fun main() {
    val q: BlockingQueue<Int> = ArrayBlockingQueue<Int>(10)
    val t = thread {
        try {
            val x = q.take()
            println("v: $x")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        println("Ended")
    }

    Thread.sleep(100)

    t.interrupt()
    t.join()
}