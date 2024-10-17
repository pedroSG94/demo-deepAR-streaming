package com.example.streaming_deepar.streaming

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class H264Encoder(private val width: Int, private val height: Int, private val surface: Surface) {
    private var mediaCodec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()


    private var yuvBuffer: ByteArray =
        ByteArray((width * height * 3) / 2) //

    init {
        setupMediaCodec()
    }


    private fun setupMediaCodec() {
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            val format =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width , height)
                    .apply {
                        setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, 1200 * 1000)  // Thay đổi bitrate nếu cần
                        setInteger(MediaFormat.KEY_FRAME_RATE, 30)    // Giảm FPS xuống
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                        setInteger(MediaFormat.KEY_ROTATION, 0)
                    }

            configure(format, surface, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.createInputSurface()
            start()
        }
    }


    fun encodeRgbaToH264(rgbaBuffer: ByteBuffer) {
        var encodedOutput: Pair<ByteBuffer, MediaCodec.BufferInfo>? = null

        rgbaToYuv420(rgbaBuffer, yuvBuffer, width, height)

        val inputBufferIndex = mediaCodec?.dequeueInputBuffer(0) ?: return
        if (inputBufferIndex >= 0) {
            val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)!!.clone()
            inputBuffer.clear()

            val inputBufferCapacity = inputBuffer.capacity()
            val yuvBufferSize = yuvBuffer.size

            if (inputBufferCapacity >= yuvBufferSize) {
                inputBuffer.put(yuvBuffer)
                mediaCodec?.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    yuvBufferSize,
                    System.nanoTime() / 1000,
                    0
                )
            } else {
                Log.e(
                    "H264Encoder",
                    "Input buffer is smaller than YUV buffer! Input Buffer Size: $inputBufferCapacity, YUV Buffer Size: $yuvBufferSize"
                )
                return
            }
        }


        val outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 1000) ?: return
        if (outputBufferIndex >= 0) {
            val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferIndex)!!.clone()
            encodedOutput = Pair(outputBuffer, bufferInfo)
            //indicate that we want render the surface
            mediaCodec?.releaseOutputBuffer(outputBufferIndex, true)
        } else {
            Log.e("H264Encoder", "No output buffer available!")
        }
        //we don't need return the output because surface will be handle the output
//    return encodedOutput
    }


    private fun rgbaToYuv420(
        rgbaBuffer: ByteBuffer,
        yuvBuffer: ByteArray,
        width: Int,
        height: Int
    ) {
        var yIndex = 0
        var uIndex = width * height
        var vIndex = uIndex + (width * height / 4)

        for (i in 0 until height) {
            for (j in 0 until width) {
                val rgbaIndex = (i * width + j) * 4
                val r = rgbaBuffer.get(rgbaIndex).toInt() and 0xFF
                val g = rgbaBuffer.get(rgbaIndex + 1).toInt() and 0xFF
                val b = rgbaBuffer.get(rgbaIndex + 2).toInt() and 0xFF


                val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val u = ((b - y) * 0.565 + 128).toInt()
                val v = ((r - y) * 0.713 + 128).toInt()


                yuvBuffer[yIndex++] = y.toByte()


                if (i % 2 == 0 && j % 2 == 0) {
                    if (uIndex < yuvBuffer.size) {
                        yuvBuffer[uIndex++] = u.toByte()
                    }
                    if (vIndex < yuvBuffer.size) {
                        yuvBuffer[vIndex++] = v.toByte()
                    }
                }
            }
        }
    }


    fun release() {
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null
    }
}