import org.openrndr.application
import org.openrndr.draw.*
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.math.mix
import com.google.gson.Gson
import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.dialogs.openFileDialog
import org.openrndr.dialogs.saveFileDialog
import org.openrndr.extra.color.presets.LAWN_GREEN
import org.openrndr.panel.controlManager
import org.openrndr.panel.elements.*
import org.openrndr.panel.style.*
import org.openrndr.shape.Rectangle
import org.openrndr.shape.ShapeContour
import java.io.File

// -- these have to be top-level classes or Gson will silently fail.
private val vf = vertexFormat {
    position(3)
    textureCoordinate(2)
}

class ProjectorPoint(val surface: Surface, var point: Vector2) {
    var hover = false


}

class Surface(val hue: Double, val kind: String="rect") {
    val inputPoints: MutableList<ProjectorPoint> = mutableListOf()
    val outputPoints: MutableList<ProjectorPoint> = mutableListOf()
    var outputMesh: VertexBuffer? = null




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

    fun calculateMesh(outputDrawer: Drawer, segments: Int=8) {
        if (kind == "rect") {
            val positions = listOf(
                Vector3(
                    outputPoints[0].point.x * outputDrawer.width,
                    outputPoints[0].point.y * outputDrawer.height,
                    0.0
                ),
                Vector3(
                    outputPoints[1].point.x * outputDrawer.width,
                    outputPoints[1].point.y * outputDrawer.height,
                    0.0
                ),
                Vector3(
                    outputPoints[2].point.x * outputDrawer.width,
                    outputPoints[2].point.y * outputDrawer.height,
                    0.0
                ),
                Vector3(
                    outputPoints[3].point.x * outputDrawer.width,
                    outputPoints[3].point.y * outputDrawer.height,
                    0.0
                )
            )
            outputMesh = vertexBuffer(vf, 6 * segments * segments)
            outputMesh?.put {
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

                        val t0 = mix(t0y, t3y, (1.0 * u) / segments)
                        val t3 = mix(t0y, t3y, (1.0 * (u + 1)) / segments)
                        val t1 = mix(t1y, t2y, (1.0 * u) / segments)
                        val t2 = mix(t1y, t2y, (1.0 * (u + 1)) / segments)



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

class Surfaces(val segments: Int = 8) {
    val surfaces: MutableList<Surface> = mutableListOf()

    val inputPoints: MutableList<ProjectorPoint> = mutableListOf()
    val outputPoints: MutableList<ProjectorPoint> = mutableListOf()

    fun addRect(): Surface {
        val hue = (this.surfaces.size * 31 + 31) % 360.0
        val s = Surface(hue)
        inputPoints.add(s.addInputPoint(0.2, 0.2))
        inputPoints.add(s.addInputPoint(0.2, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.8))
        inputPoints.add(s.addInputPoint(0.8, 0.2))

        outputPoints.add(s.addOutputPoint(0.2, 0.8))
        outputPoints.add(s.addOutputPoint(0.2, 0.2))
        outputPoints.add(s.addOutputPoint(0.8, 0.2))
        outputPoints.add(s.addOutputPoint(0.8, 0.8))

        this.surfaces.add(s)
        return s
    }

    fun remove(surface: Surface) {
        for (p in surface.inputPoints) {
            inputPoints.remove(p)
        }
        for (p in surface.outputPoints) {
            outputPoints.remove(p)
        }
        surfaces.remove(surface)
    }

    fun clearHovers() {
        inputPoints.forEach {
            it.hover = false
        }
        outputPoints.forEach {
            it.hover = false
        }
    }

    fun mouseDraggedInput(normalizedMousePosition: Vector2): ProjectorPoint? {
        inputPoints.forEach {
            if (it.hover) {
                it.point = normalizedMousePosition
                return it
            }
        }
        return null
    }

    fun mouseDraggedOutput(normalizedMousePosition: Vector2): ProjectorPoint? {
        outputPoints.forEach {
            if (it.hover) {
                it.point = normalizedMousePosition
                return it

            }
        }
        return null
    }

    fun mouseOverInput(normalizedMousePosition: Vector2) {
        val closest = inputPoints.minByOrNull { normalizedMousePosition.squaredDistanceTo(it.point) }
        inputPoints.forEach {
            it.hover = if (it == closest) true else false
        }
    }

    fun mouseOverOutput(normalizedMousePosition: Vector2) {

        val closest = outputPoints.minByOrNull { normalizedMousePosition.squaredDistanceTo(it.point) }
        outputPoints.forEach {
            it.hover = if (it == closest) true else false
        }
    }

    private fun drawPolygons(drawer: Drawer, points: MutableList<ProjectorPoint>) {
        val localPoints: MutableList<Vector2> = mutableListOf()

        for (p in points) {
            localPoints.add(Vector2(p.point.x * drawer.width, p.point.y * drawer.height))
        }
        localPoints.add(
            Vector2(
                points[points.size - 1].point.x * drawer.width,
                points[points.size - 1].point.y * drawer.height
            )
        )

        val wavyContour = ShapeContour.fromPoints(localPoints, closed = true)

        drawer.stroke = ColorHSLa(points[0].surface.hue, 0.7, 0.5, 1.0).toRGBa()
        drawer.strokeWeight = 5.0

        drawer.fill = null
        drawer.contour(wavyContour)

        for (p in points) {
            val radius = if (p.hover) 30.0 else 10.0
            drawer.circle(p.point.x * drawer.width, p.point.y * drawer.height, radius)
        }
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
        for (s in surfaces) {
            s.outputMesh?.let {
                drawer.shadeStyle = shadeStyle {
                    fragmentTransform = "x_fill = texture(p_tex, va_texCoord0.xy);"
                    parameter("tex", textureColorBuffer)
                }
                drawer.vertexBuffer(it, DrawPrimitive.TRIANGLES)
            }
        }
    }



}



private class ProgramState {

    val surfaces = Surfaces(8)
    var showPolygons = true
    var movingPoint: Vector2? = null


    fun copyTo(programState: ProgramState) {

    }

    fun save(file: File) {
        file.writeText(Gson().toJson(this))
    }

    fun load(file: File) {
        Gson().fromJson(file.readText(), ProgramState::class.java).copyTo(this)
    }
}


fun main() = application {
    val previewWidth = 640.0
    val previewHeight = 360.0
    val menuWidth = 200.0
    val seperatorWidth = 10.0
    val outputWidth = 1920.0
    val outputHeight = 1080.0
    val quadrantWidth = outputWidth / 2.0
    val quadrantHeight = outputHeight / 2.0
    configure {
        width = (previewWidth + menuWidth + seperatorWidth).toInt()
        height = (previewHeight * 2.0 + seperatorWidth * 3).toInt()
        windowResizable = false
        title = "fremkalder"
    }
    program {

        val inputPreview = renderTarget(outputWidth.toInt(), outputHeight.toInt()) {
            colorBuffer()
            depthBuffer()
        }
        val inputRect = Rectangle(menuWidth, seperatorWidth, previewWidth, previewHeight)
        val outputPreview = renderTarget(outputWidth.toInt(), outputHeight.toInt()) {
            colorBuffer()
            depthBuffer()
        }
        val outputRect = Rectangle(
            menuWidth,
            previewHeight + seperatorWidth * 2.0,
            previewWidth,
            previewHeight
        )

        drawer.isolatedWithTarget(inputPreview) {
            drawer.clear(ColorRGBa.BLACK)
        }
        drawer.isolatedWithTarget(outputPreview) {
            drawer.clear(ColorRGBa.BLACK)
        }
        val programState = ProgramState()
        mouse.dragged.listen { ut ->
            var p : ProjectorPoint? = null
            if (inputRect.contains(ut.position)) {
                val normP = Vector2(
                    (ut.position.x - inputRect.x) / inputRect.width,
                    (ut.position.y - inputRect.y) / inputRect.height
                )
                 p = programState.surfaces.mouseDraggedInput(normP)
            } else if (outputRect.contains(ut.position)) {
                val normP = Vector2(
                    (ut.position.x - outputRect.x) / outputRect.width,
                    (ut.position.y - outputRect.y) / outputRect.height
                )
                p = programState.surfaces.mouseDraggedOutput(normP)
            }
            p?.let {
                drawer.isolatedWithTarget(outputPreview) {
                    ortho(outputPreview)

                    it.surface.calculateMesh(drawer)
                }

            }



        }
        mouse.moved.listen { ut ->
            programState.surfaces.clearHovers()

            if (inputRect.contains(ut.position)) {
                val normP = Vector2(
                    (ut.position.x - inputRect.x) / inputRect.width,
                    (ut.position.y - inputRect.y) / inputRect.height
                )
                programState.surfaces.mouseOverInput(normP)
            } else if (outputRect.contains(ut.position)) {
                val normP = Vector2(
                    (ut.position.x - outputRect.x) / outputRect.width,
                    (ut.position.y - outputRect.y) / outputRect.height
                )
                programState.surfaces.mouseOverOutput(normP)
            }


        }


        val cm = controlManager {
            layout {
                styleSheet(has class_ "surfaces") {
                    this.width = menuWidth.toInt().px
                }

                styleSheet(has class_ "row") {
                    this.display = Display.FLEX
                    this.flexDirection = FlexDirection.Row
                    this.width = 100.percent


                }


                button {
                    label = "save"
                    clicked {
                        saveFileDialog(supportedExtensions = listOf("JSON" to listOf("json"))) {
                            programState.save(it)
                        }
                    }
                }

                button {
                    label = "load"
                    clicked {
                        openFileDialog(supportedExtensions = listOf("JSON" to listOf("json"))) {
                            programState.load(it)
                        }
                    }
                }
                button {
                    label = "new surface"
                    clicked {
                        val s = programState.surfaces.addRect()
                        drawer.isolatedWithTarget(outputPreview) {
                            ortho(outputPreview)

                            s.calculateMesh(drawer)
                        }

                    }
                }





                watchListDiv("surfaces", watchList = programState.surfaces.surfaces) { surface ->
                    div("row") {

                        button {

                            label = "delete"
                            style = styleSheet() {
                                this.background = Color.RGBa(ColorHSLa(surface.hue, 0.7, 0.5, 1.0).toRGBa())
                            }

                            clicked {
                                programState.surfaces.remove(surface)
                            }
                        }

                    }
                }


            }
        }


        val videoPlayer = VideoPlayerFFMPEG.fromFile(
            "data/video2.mov", PlayMode.VIDEO, VideoPlayerConfiguration().apply {
                useHardwareDecoding = false
                videoFrameQueueSize = 500
                displayQueueCooldown = 5
                //synchronizeToClock = false
            })
        videoPlayer.play()
        videoPlayer.ended.listen {
            videoPlayer.restart()
        }

        extend(cm)




        extend {
            drawer.clear(ColorRGBa.LAWN_GREEN)


            drawer.isolatedWithTarget(inputPreview) {
                drawer.clear(ColorRGBa.BLACK)

                ortho(inputPreview)
                videoPlayer.draw(drawer, 0.0, 0.0, quadrantWidth, quadrantHeight)
            }

            drawer.isolatedWithTarget(outputPreview) {
                drawer.clear(ColorRGBa.BLACK)

                ortho(outputPreview)
                programState.surfaces.drawMeshes(inputPreview.colorBuffer(0), drawer)

            }
            if (programState.showPolygons) {
                drawer.isolatedWithTarget(inputPreview) {
                    ortho(inputPreview)

                    programState.surfaces.drawInputPolygons(drawer)
                }
                drawer.isolatedWithTarget(outputPreview) {
                    ortho(outputPreview)

                    programState.surfaces.drawOutputPolygons(drawer)
                }
            }

            drawer.image(inputPreview.colorBuffer(0), inputRect.x, inputRect.y, inputRect.width, inputRect.height)
            drawer.image(
                outputPreview.colorBuffer(0),
                outputRect.x, outputRect.y, outputRect.width, outputRect.height

            )
        }
    }
}