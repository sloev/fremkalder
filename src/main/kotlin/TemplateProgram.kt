import com.google.gson.Gson
import org.openrndr.application
import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.dialogs.openFileDialog
import org.openrndr.dialogs.saveFileDialog
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.color.presets.LAWN_GREEN
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.math.Vector2
import org.openrndr.panel.controlManager
import org.openrndr.panel.elements.button
import org.openrndr.panel.elements.clicked
import org.openrndr.panel.elements.div
import org.openrndr.panel.elements.watchListDiv
import org.openrndr.panel.style.*
import org.openrndr.shape.Rectangle
import java.io.File

class ProjectorPoint(val surface: Surface, var point: Vector2) {
    var hover = false
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
        val outputPreview = renderTarget(outputWidth.toInt(), outputHeight.toInt()) {
            colorBuffer()
            depthBuffer()
        }
        val inputRect = Rectangle(menuWidth, seperatorWidth, previewWidth, previewHeight)
        val outputRect = inputRect.movedBy(Vector2(0.0, inputRect.height + seperatorWidth))

        drawer.isolatedWithTarget(inputPreview) {
            drawer.clear(ColorRGBa.BLACK)
        }
        drawer.isolatedWithTarget(outputPreview) {
            drawer.clear(ColorRGBa.BLACK)
        }

        val programState = ProgramState()

        mouse.dragged.listen { ut ->
            val p = when {
                inputRect.contains(ut.position) ->
                    programState.surfaces.mouseDraggedInput(inputRect.normalized(ut.position))

                outputRect.contains(ut.position) ->
                    programState.surfaces.mouseDraggedOutput(outputRect.normalized(ut.position))

                else -> null
            }
            p?.surface?.let {
                drawer.isolatedWithTarget(outputPreview) {
                    ortho(outputPreview)
                    it.calculateMesh(drawer.bounds.dimensions)
                }
            }
        }

        mouse.moved.listen { ut ->
            programState.surfaces.clearHovers()

            if (inputRect.contains(ut.position)) {
                programState.surfaces.mouseOverInput(inputRect.normalized(ut.position))
            } else if (outputRect.contains(ut.position)) {
                programState.surfaces.mouseOverOutput(outputRect.normalized(ut.position))
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
                            s.calculateMesh(this.bounds.dimensions)
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
                clear(ColorRGBa.BLACK)
                ortho(inputPreview)
                videoPlayer.draw(drawer, 0.0, 0.0, quadrantWidth, quadrantHeight)
            }

            drawer.isolatedWithTarget(outputPreview) {
                clear(ColorRGBa.BLACK)
                ortho(outputPreview)
                programState.surfaces.drawMeshes(inputPreview.colorBuffer(0), this)

            }
            if (programState.showPolygons) {
                drawer.isolatedWithTarget(inputPreview) {
                    ortho(inputPreview)
                    programState.surfaces.drawInputPolygons(this)
                }
                drawer.isolatedWithTarget(outputPreview) {
                    ortho(outputPreview)
                    programState.surfaces.drawOutputPolygons(this)
                }
            }

            drawer.image(
                inputPreview.colorBuffer(0),
                inputRect.x, inputRect.y, inputRect.width, inputRect.height
            )
            drawer.image(
                outputPreview.colorBuffer(0),
                outputRect.x, outputRect.y, outputRect.width, outputRect.height
            )
        }
    }
}

private fun Rectangle.normalized(pos: Vector2) = (pos - corner) / dimensions
