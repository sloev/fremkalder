import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.openrndr.application
import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.dialogs.openFileDialog
import org.openrndr.dialogs.saveFileDialog
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.draw.vertexBuffer
import org.openrndr.extra.color.presets.DARK_SLATE_GRAY
import org.openrndr.extra.color.presets.LAWN_GREEN
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.math.Vector2
import org.openrndr.panel.controlManager
import org.openrndr.panel.elements.*
import org.openrndr.panel.style.*
import org.openrndr.shape.Rectangle
import java.io.File
import java.time.Instant

class ProjectorPoint(val surface: Surface, var point: Vector2) {
    var hover = false
}

fun loadVideo(fileName: String): VideoPlayerFFMPEG {
    val videoPlayer = VideoPlayerFFMPEG.fromFile(

        fileName, PlayMode.VIDEO, VideoPlayerConfiguration().apply {
            useHardwareDecoding = false
            videoFrameQueueSize = 500
            displayQueueCooldown = 5
            //synchronizeToClock = false
        })

    videoPlayer.play()
    videoPlayer.ended.listen {
        videoPlayer.restart()
    }
    return videoPlayer
}

class ProgramState {
    var surfaces = Surfaces()
    var showPolygons = true
    var dirty = false


    fun save(file: File) {
        val configs: MutableList<ConfigSurface> = mutableListOf()

        for (s in surfaces.surfaces) {
            val cs = ConfigSurface()
            cs.locked = s.locked
            cs.id = s.id
            cs.kind = s.kind
            cs.showPolygons = showPolygons
            for (ip in s.inputPoints) {
                cs.inputPoints.add(ip.point)
            }
            for (ip in s.outputPoints) {
                cs.outputPoints.add(ip.point)
            }
            configs.add(cs)
        }

        file.writeText(Gson().toJson(configs))
        dirty = false
    }

    fun load(file: File) {
        val typeToken = object : TypeToken<MutableList<ConfigSurface>>() {}
        val configs: MutableList<ConfigSurface> = Gson().fromJson(file.readText(), typeToken.type)
        this.surfaces.clear()


        for (cs in configs) {
            val s = Surface(cs.id, cs.kind)
            for (ip in cs.inputPoints) {
                val p = ProjectorPoint(s, ip)
                s.inputPoints.add(p)
                this.surfaces.inputPoints.add(p)
            }
            for (ip in cs.outputPoints) {
                val p = ProjectorPoint(s, ip)
                s.outputPoints.add(p)
                this.surfaces.outputPoints.add(p)
            }
            s.locked = cs.locked


            this.surfaces.surfaces.add(s)
            this.surfaces.incrementSurfaceCounts(s.kind)
            this.showPolygons = cs.showPolygons


        }
        dirty = false
    }
}


fun main() = application {
    val previewWidth = 640.0
    val previewHeight = 360.0
    val previewQuadrantWidth = previewWidth / 2.0
    val previewQuadrantHeight = previewHeight / 2.0

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
        val inputQuadrant1 = Rectangle(menuWidth, seperatorWidth, previewQuadrantWidth, previewQuadrantHeight)
        val inputQuadrant2 =
            Rectangle(menuWidth + previewQuadrantWidth, seperatorWidth, previewQuadrantWidth, previewQuadrantHeight)
        val inputQuadrant3 =
            Rectangle(menuWidth, seperatorWidth + previewQuadrantHeight, previewQuadrantWidth, previewQuadrantHeight)
        val inputQuadrant4 = Rectangle(
            menuWidth + previewQuadrantWidth,
            seperatorWidth + previewQuadrantHeight,
            previewQuadrantWidth,
            previewQuadrantHeight
        )

        val outputRect = inputRect.movedBy(Vector2(0.0, inputRect.height + seperatorWidth))
        val broadcast = Broadcast(outputWidth.toInt(), outputHeight.toInt())
        var videoplayers: MutableList<VideoPlayerFFMPEG?> = mutableListOf(null, null, null, null)

        drawer.isolatedWithTarget(inputPreview) {
            drawer.clear(ColorRGBa.TRANSPARENT)
        }
        drawer.isolatedWithTarget(outputPreview) {
            drawer.clear(ColorRGBa.TRANSPARENT)
        }

        val programState = ProgramState()

        mouse.dragged.listen { ut ->
            if (programState.showPolygons) {
                val p = when {
                    inputRect.contains(ut.position) ->
                        programState.surfaces.mouseDraggedInput(inputRect.normalized(ut.position))

                    outputRect.contains(ut.position) ->
                        programState.surfaces.mouseDraggedOutput(outputRect.normalized(ut.position))

                    else -> null
                }
                p?.let {
                    drawer.isolatedWithTarget(outputPreview) {
                        ortho(outputPreview)
                        programState.surfaces.calculateMesh(drawer.bounds.dimensions)
                    }
                    programState.dirty = true
                }
            }
        }

        mouse.moved.listen { ut ->
            programState.surfaces.clearHovers()
            if (programState.showPolygons) {
                if (inputRect.contains(ut.position)) {
                    programState.surfaces.mouseOverInput(inputRect.normalized(ut.position))

                } else if (outputRect.contains(ut.position)) {
                    programState.surfaces.mouseOverOutput(outputRect.normalized(ut.position))

                }
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
                div("row") {

                    watchObjectDiv("savebutton", watchObject = object {
                        // for primitive types we have to use property references
                        val dirty = programState::dirty
                    }) {
                        div {
                            button {
                                label = "save"
                                style = styleSheet() {
                                    this.background =
                                        Color.RGBa(if (watchObject.dirty.get()) ColorRGBa.RED else ColorRGBa.GRAY)
                                }
                                clicked {
                                    saveFileDialog(supportedExtensions = listOf("FREMKALDER" to listOf("fremkalder"))) {
                                        programState.save(it)
                                    }
                                }
                            }
                        }
                    }

                    button {
                        label = "load"

                        clicked {
                            openFileDialog(supportedExtensions = listOf("FREMKALDER" to listOf("fremkalder"))) {
                                programState.load(it)
                                drawer.isolatedWithTarget(outputPreview) {
                                    ortho(outputPreview)
                                    programState.surfaces.calculateMesh(drawer.bounds.dimensions)

                                }
                            }
                        }
                    }
                }
                div("row") {
                    watchObjectDiv(watchObject = object {
                        val showPolygons = programState::showPolygons
                    }) {
                        toggle {
                            label = "mapping"
                            value = watchObject.showPolygons.get()
                            events.valueChanged.listen {
                                value = it.newValue
                                programState.showPolygons = !watchObject.showPolygons.get()
                            }
                        }
                    }
                    watchObjectDiv(watchObject = object {
                        val broadcasting = broadcast::broadcasting
                    }) {
                        toggle {
                            label = "live"
                            value = watchObject.broadcasting.get()
                            events.valueChanged.listen {
                                if (it.newValue) {
                                    broadcast.start()
                                } else {
                                    broadcast.stop()
                                }

                            }
                        }
                    }
                }
                watchObjectDiv(watchObject = object {
                    val mappingMode = programState::showPolygons
                }) {


                    if (watchObject.mappingMode.get()) {

                        div("row") {
                            button {
                                label = "+ rect"
                                clicked {
                                    programState.surfaces.addRect()
                                    drawer.isolatedWithTarget(outputPreview) {
                                        ortho(outputPreview)
                                        programState.surfaces.calculateMesh(this.bounds.dimensions)
                                    }
                                    programState.dirty = true
                                }
                            }
                            button {
                                label = "+ triangle"
                                clicked {
                                    programState.surfaces.addTriangle()
                                    drawer.isolatedWithTarget(outputPreview) {
                                        ortho(outputPreview)
                                        programState.surfaces.calculateMesh(this.bounds.dimensions)
                                    }
                                    programState.dirty = true
                                }
                            }
                        }

                        watchListDiv("surfaces", watchList = programState.surfaces.surfaces) { surface ->
                            div("row") {

                                button {
                                    label = "delete"
                                    style = styleSheet() {
                                        this.background = Color.RGBa(idToColor(surface.id))

                                    }

                                    clicked {
                                        programState.surfaces.remove(surface)
                                        programState.dirty = true

                                    }
                                }
                                toggle {
                                    label = "lock"
                                    value = surface.locked
                                    events.valueChanged.listen {
                                        value = it.newValue
                                        surface.locked = value
                                        programState.dirty = true

                                    }
                                }

                            }
                        }
                    }
                }
            }
        }





        extend(cm)
        window.drop.listen { dropped ->
            val firstVideo = dropped.files.firstOrNull {
                File(it).extension.lowercase() in listOf("mov", "MOV")

            }
            if (firstVideo != null) {
                if (inputQuadrant1.contains(dropped.position)) {
                    videoplayers[0] = loadVideo(firstVideo)
                    println("load video player 0")

                } else if (inputQuadrant2.contains(dropped.position)) {
                    videoplayers[1] = loadVideo(firstVideo)
                    println("load video player 1")


                } else if (inputQuadrant3.contains(dropped.position)) {
                    videoplayers[2] = loadVideo(firstVideo)
                    println("load video player 2")


                } else if (inputQuadrant4.contains(dropped.position)) {
                    videoplayers[3] = loadVideo(firstVideo)
                    println("load video player 3")


                }

            }
        }
        extend {
            drawer.clear(ColorRGBa.DARK_SLATE_GRAY)

            drawer.isolatedWithTarget(inputPreview) {
                clear(ColorRGBa.TRANSPARENT)
                ortho(inputPreview)
                videoplayers[0]?.draw(drawer, 0.0, 0.0, quadrantWidth, quadrantHeight)
                videoplayers[1]?.draw(drawer, quadrantWidth, 0.0, quadrantWidth, quadrantHeight)
                videoplayers[2]?.draw(drawer, 0.0, quadrantHeight, quadrantWidth, quadrantHeight)
                videoplayers[3]?.draw(drawer, quadrantWidth, quadrantHeight, quadrantWidth, quadrantHeight)

            }

            drawer.isolatedWithTarget(outputPreview) {
                clear(ColorRGBa.TRANSPARENT)
                ortho(outputPreview)
                programState.surfaces.drawMeshes(inputPreview.colorBuffer(0), this)

            }
            if (broadcast.broadcasting) {
                broadcast.outputFrame(outputPreview.colorBuffer(0))
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
            drawer.fill = ColorRGBa.BLACK
            drawer.stroke = null
            drawer.rectangle(inputRect)
            drawer.rectangle(outputRect)

            drawer.image(
                inputPreview.colorBuffer(0),
                inputRect.x, inputRect.y, inputRect.width, inputRect.height
            )
            if (programState.showPolygons) {
                drawer.image(
                    outputPreview.colorBuffer(0),
                    outputRect.x, outputRect.y, outputRect.width, outputRect.height
                )
            }

        }
    }
}

private fun Rectangle.normalized(pos: Vector2) = (pos - corner) / dimensions
