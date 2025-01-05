import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.ffmpeg.VideoWriter
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.Buffer

import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel


fun createByteBuffer(size: Int): ByteBuffer {
    return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
}

class Broadcast(
    val width: Int, val height: Int, val udpIp: String = "255.255.255.255", val udpPort: String = "12345"
) {
    var broadcasting = false
    val videoWriter = VideoWriter()
    var inputFormat = "rgba"
    var frameRate = 30
    var ffmpegOutput = File("ffmpegOutput.txt")


    val ffmpegFile = videoWriter.findFfmpeg()
    val socatFile = "/usr/bin/socat"

    val cmd = """
            $ffmpegFile  \
            -re  \
            -f rawvideo -vcodec rawvideo \
            -s "${width}x${height}" -pix_fmt "$inputFormat" -i - \
            -c:v mpeg2video -q:v 20 -pix_fmt yuv420p -g 1 -threads 2 \
            -vf "vflip" -f mpegts -omit_video_pes_length 1 "pipe:1"   | $socatFile - udp-sendto:$udpIp:$udpPort,broadcast
        """.trimIndent()

    var channel: WritableByteChannel? = null
    val frameBuffer: ByteBuffer = when (inputFormat) {
        "rgba" -> createByteBuffer(width * height * 4)
        "rgba64le" -> createByteBuffer(width * height * 8)
        else -> throw RuntimeException("unsupported format $inputFormat")
    }
    var ffmpeg: Process? = null

    fun start() {

        val pb = ProcessBuilder().command("/bin/sh", "-c", cmd)

        pb.redirectErrorStream(true)
        pb.redirectOutput(ffmpegOutput)


        try {

             ffmpeg = pb.start()
            ffmpeg?.let {
                channel = Channels.newChannel(it.outputStream)
            }
            broadcasting = true
        } catch (e: IOException) {
            throw RuntimeException("failed to launch ffmpeg", e)
        }
    }
    fun stop() {
        ffmpeg?.destroy()
        broadcasting = false
    }

    fun outputFrame(frame: ColorBuffer) {
        val frameBytes =
            frame.effectiveWidth * frame.effectiveHeight * frame.format.componentCount * frame.type.componentSize
        require(frameBytes == frameBuffer.capacity()) {
            "frame size/format/type mismatch. ($width x $height) vs ({${frame.effectiveWidth} x ${frame.effectiveHeight})"
        }
        (frameBuffer as Buffer).rewind()
        frame.read(frameBuffer)
        (frameBuffer as Buffer).rewind()
        try {
            channel?.write(frameBuffer)
        } catch (e: IOException) {
            throw RuntimeException("failed to write frame", e)
        }

    }
}