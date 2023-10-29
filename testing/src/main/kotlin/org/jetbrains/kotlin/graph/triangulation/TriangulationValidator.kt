package org.jetbrains.kotlin.graph.triangulation

import org.kynosarges.tektosyne.geometry.LineD
import org.kynosarges.tektosyne.geometry.PointD
import org.kynosarges.tektosyne.geometry.Voronoi
import org.kynosarges.tektosyne.geometry.VoronoiResults
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.Comparator
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.pow
import kotlin.math.sqrt

fun main() {
    val points = listOf(
        PointD(2.0, 3.0),
        PointD(-1.0, 1.0),
        PointD(1.0, -1.0),
        PointD(-1.0, -1.0),
    )

    val result = calculateTriangulation(points)

    visualiseDelaunay(result, points)
    check(isDelaunayTriangulation(result.triangles, points)) { "Bad triangulation" }
}

private fun visualiseDelaunay(
    result: TriangulationResult,
    points: List<PointD>
) {
    val frame = JFrame("Триангуляция Делоне")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(FRAME_SIZE, FRAME_SIZE)
    frame.add(object : JPanel() {
        override fun paintComponent(g: Graphics) {
            g as Graphics2D
            super.paintComponent(g)
            g.color = Color.BLACK
            g.drawLine(0, FRAME_SIZE / 2, FRAME_SIZE, FRAME_SIZE / 2)
            g.drawLine(FRAME_SIZE / 2, 0, FRAME_SIZE / 2, FRAME_SIZE)
            for (x in -5..5) {
                if (x != 0) {
                    g.drawString(x.toString(), x.toDisplayPointInt, (-0.1).toDisplayPointInt)
                }
                g.drawLine(x.toDisplayPointInt, (-0.1).toDisplayPointInt, x.toDisplayPointInt, 0.1.toDisplayPointInt)
            }
            for (y in -5..5) {
                g.drawString(y.toString(), (0.05).toDisplayPointInt, y.toDisplayPointInt)
                g.drawLine((-0.1).toDisplayPointInt, y.toDisplayPointInt, (0.1).toDisplayPointInt, y.toDisplayPointInt)
            }

            val stroke = g.stroke
            g.stroke = BasicStroke(2f)

            for (edge in result.edges) {
                val p1 = edge.start
                val p2 = edge.end

                val x1 = p1.x
                val y1 = p1.y
                val x2 = p2.x
                val y2 = p2.y

                g.drawLine(x1.toDisplayPointInt, y1.toDisplayPointInt, x2.toDisplayPointInt, y2.toDisplayPointInt)
            }
            g.stroke = stroke


            g.color = Color.BLUE
            for (triangle in result.triangles) {
                val center = triangle.center
                val radius = sqrt(
                    (center.x.toDisplayPoint - triangle.first.x.toDisplayPoint).pow(2) +
                            (center.y.toDisplayPoint - triangle.first.y.toDisplayPoint).pow(2)
                ).toInt()

                g.drawOval(
                    center.x.toDisplayPointInt - radius,
                    center.y.toDisplayPointInt - radius,
                    radius * 2,
                    radius * 2
                )
            }

            g.color = Color.RED
            for (node in points) {
                g.fillOval(node.x.toDisplayPointInt, node.y.toDisplayPointInt, 5, 5)
            }
        }
    })
    frame.isVisible = true
}

class Triangle(
    firstRaw: PointD,
    secondRaw: PointD,
    thirdRaw: PointD,
) {

    val first: PointD
    val second: PointD
    val third: PointD
    val center: PointD

    init {
        val dots = listOf(firstRaw, secondRaw, thirdRaw).sortedWith(pointComparator)
        first = dots[0]
        second = dots[1]
        third = dots[2]
        center = findCenter(first, second, third)
    }

    fun contains(pointD: PointD): Boolean {
        return pointD == first || pointD == second || pointD == third
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Triangle

        if (first != other.first) return false
        if (second != other.second) return false
        if (third != other.third) return false

        return true
    }

    override fun hashCode(): Int {
        var result = first.hashCode()
        result = 31 * result + second.hashCode()
        result = 31 * result + third.hashCode()
        return result
    }

    override fun toString(): String {
        return "Triangle(first=$first, second=$second, third=$third)"
    }

}

/**
 * Сетка от -5 до 5 => 0 до 500
 * 500 / 10 = 50
 * 2 => (2 + 5) * 50
 */
val Double.toDisplayPoint: Double get() = ((this + MAX_GRID / 2) * FACTOR)
val Int.toDisplayPoint: Double get() = this.toDouble().toDisplayPoint

val Double.toDisplayPointInt: Int get() = this.toDisplayPoint.toInt()
val Int.toDisplayPointInt: Int get() = this.toDisplayPoint.toInt()

fun findCenter(a: PointD, b: PointD, c: PointD): PointD {
    data class Line(val slope: Double, val yIntercept: Double)

    fun midpoint(p1: PointD, p2: PointD) = PointD((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
    fun lineFromPoints(p1: PointD, p2: PointD): Line {
        val slope = (p2.y - p1.y) / (p2.x - p1.x)
        val yIntercept = p1.y - slope * p1.x
        return Line(slope, yIntercept)
    }

    val midAB = midpoint(a, b)
    val midBC = midpoint(b, c)

    val lineAB = lineFromPoints(a, b)
    val lineBC = lineFromPoints(b, c)

    // Перпендикуляр к AB, проходящий через midAB
    val perpAB = if (lineAB.slope == 0.0) Line(Double.POSITIVE_INFINITY, 0.0) else Line(-1 / lineAB.slope, midAB.y + midAB.x / lineAB.slope)

    // Перпендикуляр к BC, проходящий через midBC
    val perpBC = if (lineBC.slope == 0.0) Line(Double.POSITIVE_INFINITY, 0.0) else Line(-1 / lineBC.slope, midBC.y + midBC.x / lineBC.slope)

    // Теперь найдем точку пересечения этих двух перпендикуляров
    val x = (perpAB.yIntercept - perpBC.yIntercept) / (perpBC.slope - perpAB.slope)
    val y = perpAB.slope * x + perpAB.yIntercept

    return PointD(x, y)
}

fun getTriangles(result: VoronoiResults): List<Triangle> {
    val edgeMap = hashMapOf<PointD, MutableSet<PointD>>()
    for (edge in result.delaunayEdges()) {
        val p1 = edge.start
        val p2 = edge.end
        edgeMap.getOrPut(p1) { hashSetOf() }.add(p2)
        edgeMap.getOrPut(p2) { hashSetOf() }.add(p1)
    }
    val triangles = hashSetOf<Triangle>()

    for (edge in result.delaunayEdges()) {
        val p1 = edge.start
        val p2 = edge.end
        val p1Neighbours = edgeMap[p1] ?: error("Bad point $p1")
        val p2Neighbours = edgeMap[p2] ?: error("Bad point $p2")

        val p3Set = p1Neighbours.intersect(p2Neighbours)
        for (p3 in p3Set) {
            triangles.add(Triangle(p1, p2, p3))
        }
    }

    return triangles.toList()
}

fun isDelaunayTriangulation(triangles: List<Triangle>, points: List<PointD>): Boolean {
    for (triangle in triangles) {
        val center = triangle.center
        val radius = triangle.first.distance(center)

        for (point in points) {
            if (!triangle.contains(point) && point.distance(center) < radius) {
                return false
            }
        }
    }
    return true
}

fun PointD.distance(other: PointD): Double = sqrt((x - other.x).pow(2) + (y - other.y).pow(2))


data class TriangulationResult(
    val points: List<PointD>,
    val triangles: List<Triangle>,
    val edges: List<LineD>
)

fun calculateTriangulation(points: List<PointD>): TriangulationResult {
    val voronoi = Voronoi.findAll(points.toTypedArray())
    val triangles = getTriangles(voronoi)

    return TriangulationResult(points, triangles, voronoi.delaunayEdges().toList())
}


private val pointComparator: Comparator<PointD> = compareBy<PointD> { it.x }.thenComparing { p -> p.y }
private const val FRAME_SIZE = 500
private const val MAX_GRID = 10
private const val FACTOR = FRAME_SIZE.toDouble() / MAX_GRID