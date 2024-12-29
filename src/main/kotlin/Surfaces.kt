import org.openrndr.color.ColorHSLa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.DrawPrimitive
import org.openrndr.draw.Drawer
import org.openrndr.draw.shadeStyle
import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour

class Surfaces(val segments: Int = 8) {
    val surfaces: MutableList<Surface> = mutableListOf()

    val inputPoints: MutableList<ProjectorPoint> = mutableListOf()
    val outputPoints: MutableList<ProjectorPoint> = mutableListOf()

    fun addRect(): Surface {
        val hue = (surfaces.size * 31 + 31) % 360.0
        val s = Surface(hue, segments = segments)
        inputPoints.add(s.addInputPoint(0.2, 0.2))
        inputPoints.add(s.addInputPoint(0.2, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.2))

        outputPoints.add(s.addOutputPoint(0.2, 0.2))
        outputPoints.add(s.addOutputPoint(0.2, 0.8))
        outputPoints.add(s.addOutputPoint(0.8, 0.8))
        outputPoints.add(s.addOutputPoint(0.8, 0.2))

        surfaces.add(s)
        return s
    }

    fun remove(surface: Surface) {
        surface.destroy()
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
        val closest = inputPoints.minByOrNull { normalizedMousePosition.squaredDistanceTo(it.point) }
        inputPoints.forEach { it.hover = it == closest }
    }

    fun mouseOverOutput(normalizedMousePosition: Vector2) {
        val closest = outputPoints.minByOrNull { normalizedMousePosition.squaredDistanceTo(it.point) }
        outputPoints.forEach { it.hover = it == closest }
    }

    private fun drawPolygons(drawer: Drawer, points: MutableList<ProjectorPoint>) {
        val localPoints = points.map { it.point * drawer.bounds.dimensions }
        val contour = ShapeContour.fromPoints(localPoints, closed = true)

        drawer.stroke = ColorHSLa(points[0].surface.hue, 0.7, 0.5, 1.0).toRGBa()
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
        drawer.shadeStyle = shadeStyle {
            fragmentTransform = """
                vec2 c = va_texCoord0.xy;
                x_fill = texture(p_tex, vec2(c.x, 1.0 - c.y));
            """.trimIndent()
            parameter("tex", textureColorBuffer)
        }
        for (s in surfaces) {
            drawer.vertexBuffer(s.outputMesh, DrawPrimitive.TRIANGLES)
        }
    }
}