package com.example.streaming_deepar.streaming

import ai.deepar.ar.AREventListener
import ai.deepar.ar.DeepAR
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import com.pedro.encoder.input.sources.video.VideoSource

/**
 * Created by pedro on 17/10/24.
 */
class DeepARSource(
  context: Context,
  licenseKey: String,
  listener: AREventListener
): VideoSource() {

  val deepAR = DeepAR(context).apply {
    setLicenseKey(licenseKey)
    initialize(context, listener)
  }
  private var running = false

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    return true
  }

  override fun isRunning(): Boolean = running

  override fun release() {
    deepAR.release()
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    deepAR.setRenderSurface(Surface(surfaceTexture), height, width)
    deepAR.startCapture()
    running = true
  }

  override fun stop() {
    deepAR.stopCapture()
    running = false
  }
}