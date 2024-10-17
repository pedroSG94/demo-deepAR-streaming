package com.example.streaming_deepar.streaming

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import com.pedro.encoder.input.sources.video.VideoSource
import java.nio.ByteBuffer

class RGABSource : VideoSource() {
    private var encoder: H264Encoder? = null
    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        Log.d("RGABSource","width: $width, height: $height")
        return true
    }

    override fun isRunning(): Boolean = encoder != null

    override fun release() {}

    override fun start(surfaceTexture: SurfaceTexture) {
        Log.d("RGABSource","width: $width, height: $height")
        encoder = H264Encoder(width, height, Surface(surfaceTexture))
    }

    override fun stop() {
        encoder?.release()
        encoder = null
    }

    fun setVideo(rgbaBuffer: ByteBuffer) {
        encoder?.encodeRgbaToH264(rgbaBuffer)
    }
}