import org.openrndr.draw.vertexBuffer
import org.openrndr.draw.vertexFormat
import org.openrndr.math.Vector2
import org.openrndr.math.mix

// -- these have to be top-level classes or Gson will silently fail.

class ConfigSurface {
    var id: Double = 0.0
    var inputPoints: MutableList<Vector2> = mutableListOf()
    var outputPoints: MutableList<Vector2> = mutableListOf()
    var locked = false
    var kind = "rect"

    var showPolygons = true

}

class Surface(val id: Double, val kind: String = "rect") {
    val inputPoints: MutableList<ProjectorPoint> = mutableListOf()
    val outputPoints: MutableList<ProjectorPoint> = mutableListOf()
    var locked = false

    fun addInputPoint(x: Double, y: Double): ProjectorPoint {
        val p = ProjectorPoint(this, Vector2(x, y))
        inputPoints.add(p)
        return p
    }

    fun addOutputPoint(x: Double, y: Double): ProjectorPoint {
        val p = ProjectorPoint(this, Vector2(x, y))
        outputPoints.add(p)
        return p
    }

    fun destroy() {
        inputPoints.clear()
        outputPoints.clear()
    }
}

private fun Vector2.vFlip() = Vector2(x, 1 - y)
