import org.openrndr.draw.vertexBuffer
import org.openrndr.draw.vertexFormat
import org.openrndr.math.Vector2
import org.openrndr.math.mix

// -- these have to be top-level classes or Gson will silently fail.
private val vf = vertexFormat {
    position(3)
    textureCoordinate(2)
}

class ConfigSurface {
    var id: Double = 0.0
    var inputPoints: MutableList<Vector2> = mutableListOf()
    var outputPoints: MutableList<Vector2> = mutableListOf()
    var locked = false
    var segments = 8
    var kind = "rect"

    //var outputMesh = vertexBuffer(vf, 6 * segments * segments)
    var showPolygons = true

}

class Surface(val id: Double, val kind: String = "rect", val segments: Int = 8) {
    val inputPoints: MutableList<ProjectorPoint> = mutableListOf()
    val outputPoints: MutableList<ProjectorPoint> = mutableListOf()
    var outputMesh = vertexBuffer(vf, 6 * segments * segments)
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

    fun calculateMesh(drawerDimensions: Vector2) {
        if (kind == "triangle") {
            val positions = outputPoints.map { (it.point * drawerDimensions).xy0 }

            outputMesh.put {
                write(positions[0])
                write(inputPoints[0].point)
                write(positions[1])
                write(inputPoints[1].point)
                write(positions[2])
                write(inputPoints[2].point)


            }
        } else if (kind == "rect") {
            val positions = outputPoints.map { (it.point * drawerDimensions).xy0 }

            outputMesh.put {
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

                        val t0y = mix(inputPoints[0].point, inputPoints[1].point, (1.0 * v) / segments)
                        val t3y = mix(inputPoints[3].point, inputPoints[2].point, (1.0 * v) / segments)
                        val t1y = mix(inputPoints[0].point, inputPoints[1].point, (1.0 * (v + 1)) / segments)
                        val t2y = mix(inputPoints[3].point, inputPoints[2].point, (1.0 * (v + 1)) / segments)

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

    fun destroy() {
        inputPoints.clear()
        outputPoints.clear()
        outputMesh.destroy()
    }
}

private fun Vector2.vFlip() = Vector2(x, 1 - y)
