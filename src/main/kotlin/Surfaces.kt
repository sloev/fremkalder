import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.DrawPrimitive
import org.openrndr.draw.Drawer
import org.openrndr.draw.shadeStyle
import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour

fun idToColor(id:Double):ColorRGBa {
    return ColorHSLa((id *31 )%360, 1.0-((id*71)%600.0)/800.0, 0.4, 1.0).toRGBa()
}
class Surfaces() {
    val surfaces: MutableList<Surface> = mutableListOf()

    val inputPoints: MutableList<ProjectorPoint> = mutableListOf()
    val outputPoints: MutableList<ProjectorPoint> = mutableListOf()

    fun addTriangle(): Surface {
        val id = surfaces.size+1.0
        val s = Surface(id, kind="triangle")
        inputPoints.add(s.addInputPoint(0.2, 0.2))
        inputPoints.add(s.addInputPoint(0.2, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.8))

        outputPoints.add(s.addOutputPoint(0.2, 0.2))
        outputPoints.add(s.addOutputPoint(0.2, 0.8))
        outputPoints.add(s.addOutputPoint(0.8, 0.8))

        surfaces.add(s)
        return s
    }


    fun addRect(segments: Int = 8): Surface {
        val id = surfaces.size + 1.0
        val s = Surface(id, kind="rect", segments = segments)
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
        for (p in inputPoints.sortedWith(compareBy { normalizedMousePosition.squaredDistanceTo(it.point)})){
            if (!p.surface.locked){
                p.hover=true
                return
            }
        }
    }

    fun mouseOverOutput(normalizedMousePosition: Vector2) {
        for (p in outputPoints.sortedWith(compareBy { normalizedMousePosition.squaredDistanceTo(it.point)})){
            if (!p.surface.locked){
                p.hover=true
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