import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Vector2
import org.openrndr.math.mix
import org.openrndr.shape.ShapeContour

fun idToColor(id: Double): ColorRGBa {
    return ColorHSLa((id * 31) % 360, 1.0 - ((id * 71) % 600.0) / 800.0, 0.4, 1.0).toRGBa()
}

private val vf = vertexFormat {
    position(3)
    textureCoordinate(2)
}

class Surfaces() {
    val surfaces: MutableList<Surface> = mutableListOf()

    val inputPoints: MutableList<ProjectorPoint> = mutableListOf()
    val outputPoints: MutableList<ProjectorPoint> = mutableListOf()
    private var outputMesh: VertexBuffer? = null
    var surfaceCounts: HashMap<String, Int> = hashMapOf(
        "triangle" to 0,
        "rect" to 0
    )
    var segments = 8


    fun addTriangle(): Surface {
        val id = surfaces.size + 1.0
        val s = Surface(id, kind = "triangle")
        inputPoints.add(s.addInputPoint(0.2, 0.2))
        inputPoints.add(s.addInputPoint(0.2, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.8))

        outputPoints.add(s.addOutputPoint(0.2, 0.2))
        outputPoints.add(s.addOutputPoint(0.2, 0.8))
        outputPoints.add(s.addOutputPoint(0.8, 0.8))

        surfaces.add(s)
        incrementSurfaceCounts(s.kind)
        return s
    }
    fun incrementSurfaceCounts(kind:String){
        surfaceCounts[kind] = surfaceCounts.getOrDefault(kind, 0)+1

    }

    fun calculateMesh(drawerDimensions: Vector2) {
        println("surfacecounts: ${surfaceCounts}")
        val surfaceCountTotal = surfaceCounts.getOrDefault("triangle", 0) + surfaceCounts.getOrDefault("rect", 0)
        if (surfaceCountTotal > 0) {
            outputMesh = vertexBuffer(
                vf,
                (surfaceCounts.getOrDefault("triangle", 0) * 3) +
                        (surfaceCounts.getOrDefault("rect", 0) * 6 * segments * segments)
            )
            outputMesh?.put {
                for (s in surfaces) {
                    val positions = s.outputPoints.map { (it.point * drawerDimensions).xy0 }

                    if (s.kind == "triangle") {
                        write(positions[0])
                        write(s.inputPoints[0].point)
                        write(positions[1])
                        write(s.inputPoints[1].point)
                        write(positions[2])
                        write(s.inputPoints[2].point)
                    } else if (s.kind == "rect") {
                        for (v in 0 until segments) {
                            for (u in 0 until segments) {
                                // vertical first
                                val p0y = mix(positions[0], positions[1], (1.0 * v) / segments)
                                val p3y = mix(positions[3], positions[2], (1.0 * v) / segments)
                                val p1y = mix(positions[0], positions[1], (1.0 * (v + 1)) / segments)
                                val p2y = mix(positions[3], positions[2], (1.0 * (v + 1)) / segments)

                                val p0 = mix(p0y, p3y, (1.0 * u) / segments)
                                val p3 = mix(p0y, p3y, (1.0 * (u + 1)) / segments)
                                val p1 = mix(p1y, p2y, (1.0 * u) / segments)
                                val p2 = mix(p1y, p2y, (1.0 * (u + 1)) / segments)

                                val t0y = mix(s.inputPoints[0].point, s.inputPoints[1].point, (1.0 * v) / segments)
                                val t3y = mix(s.inputPoints[3].point, s.inputPoints[2].point, (1.0 * v) / segments)
                                val t1y =
                                    mix(s.inputPoints[0].point, s.inputPoints[1].point, (1.0 * (v + 1)) / segments)
                                val t2y =
                                    mix(s.inputPoints[3].point, s.inputPoints[2].point, (1.0 * (v + 1)) / segments)

                                val t0 = mix(t0y, t3y, (1.0 * u) / segments)//.vFlip()
                                val t3 = mix(t0y, t3y, (1.0 * (u + 1)) / segments)//.vFlip()
                                val t1 = mix(t1y, t2y, (1.0 * u) / segments)//.vFlip()
                                val t2 = mix(t1y, t2y, (1.0 * (u + 1)) / segments)//.vFlip()

                                write(p0)
                                write(t0)
                                write(p1)
                                write(t1)
                                write(p3)
                                write(t3)

                                write(p1)
                                write(t1)
                                write(p2)
                                write(t2)
                                write(p3)
                                write(t3)

                            }
                        }
                    }
                }
            }
        }
    }


    fun addRect(): Surface {
        val id = surfaces.size + 1.0
        val s = Surface(id, kind = "rect")
        inputPoints.add(s.addInputPoint(0.2, 0.2))
        inputPoints.add(s.addInputPoint(0.2, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.2))

        outputPoints.add(s.addOutputPoint(0.2, 0.2))
        outputPoints.add(s.addOutputPoint(0.2, 0.8))
        outputPoints.add(s.addOutputPoint(0.8, 0.8))
        outputPoints.add(s.addOutputPoint(0.8, 0.2))

        surfaces.add(s)
        incrementSurfaceCounts(s.kind)
        return s
    }

    fun remove(surface: Surface) {
        surface.destroy()
        surfaceCounts[surface.kind] = surfaceCounts.getOrDefault(surface.kind, 0)-1
        surfaces.remove(surface)
    }

    fun clearHovers() {
        inputPoints.forEach { it.hover = false }
        outputPoints.forEach { it.hover = false }
    }

    fun mouseDraggedInput(normalizedMousePosition: Vector2): ProjectorPoint? {
        val hoverPoint = inputPoints.firstOrNull { it.hover }
        hoverPoint?.point = normalizedMousePosition
        return hoverPoint
    }

    fun mouseDraggedOutput(normalizedMousePosition: Vector2): ProjectorPoint? {
        val hoverPoint = outputPoints.firstOrNull { it.hover }
        hoverPoint?.point = normalizedMousePosition
        return hoverPoint
    }

    fun mouseOverInput(normalizedMousePosition: Vector2) {
        for (p in inputPoints.sortedWith(compareBy { normalizedMousePosition.squaredDistanceTo(it.point) })) {
            if (!p.surface.locked) {
                p.hover = true
                return
            }
        }
    }

    fun mouseOverOutput(normalizedMousePosition: Vector2) {
        for (p in outputPoints.sortedWith(compareBy { normalizedMousePosition.squaredDistanceTo(it.point) })) {
            if (!p.surface.locked) {
                p.hover = true
                return
            }
        }
    }

    private fun drawPolygons(drawer: Drawer, points: MutableList<ProjectorPoint>) {
        val localPoints = points.map { it.point * drawer.bounds.dimensions }
        val contour = ShapeContour.fromPoints(localPoints, closed = true)

        drawer.stroke = idToColor(points[0].surface.id)
        drawer.strokeWeight = 5.0
        drawer.fill = null
        drawer.contour(contour)
        drawer.circles(localPoints, points.map { if (it.hover) 30.0 else 10.0 })
    }


    fun drawInputPolygons(drawer: Drawer) {
        for (s in surfaces) {
            drawPolygons(drawer, s.inputPoints)
        }
    }

    fun drawOutputPolygons(drawer: Drawer) {
        for (s in surfaces) {
            drawPolygons(drawer, s.outputPoints)
        }
    }

    fun drawMeshes(textureColorBuffer: ColorBuffer, drawer: Drawer) {
        outputMesh?.let {
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                vec2 c = va_texCoord0.xy;
                x_fill = texture(p_tex, vec2(c.x, 1.0 - c.y));
            """.trimIndent()
                parameter("tex", textureColorBuffer)
            }
            drawer.vertexBuffer(it, DrawPrimitive.TRIANGLES)
        }

    }

    fun clear() {
        inputPoints.clear()
        outputPoints.clear()
        outputMesh= null
    }
}