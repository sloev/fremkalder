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

    val videoWriter = VideoWriter()
    var inputFormat = "rgba"
    var frameRate = 25
    var ffmpegOutput = File("ffmpegOutput.txt")


    val ffmpegFile = videoWriter.findFfmpeg()
    val socatFile = "/usr/bin/socat"

    val cmd = """
            $ffmpegFile -hide_banner -threads 1 -filter_threads 1 \
            -f rawvideo -vcodec rawvideo \
            -s "${width}x${height}" -pix_fmt "$inputFormat" -r "$frameRate" -i - \
            -threads 0 -frame_drop_threshold -1 -g 1 -fps_mode:v vfr \
            -vf "vflip" -c:v libx264 -tune zerolatency -muxdelay 0 -flags2 '+fast' \
            -f mpegts "pipe:1" | $socatFile - udp-sendto:$udpIp:$udpPort,broadcast
        """.trimIndent()


    var channel: WritableByteChannel? = null
    val frameBuffer: ByteBuffer = when (inputFormat) {
        "rgba" -> createByteBuffer(width * height * 4)
        "rgba64le" -> createByteBuffer(width * height * 8)
        else -> throw RuntimeException("unsupported format $inputFormat")
    }
    fun start() {

        val pb = ProcessBuilder().command("/bin/sh", "-c", cmd)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ffmpegOutput)


        try {
            val ffmpeg = pb.start()
            channel = Channels.newChannel(ffmpeg.outputStream)
        } catch (e: IOException) {
            throw RuntimeException("failed to launch ffmpeg", e)
        }
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