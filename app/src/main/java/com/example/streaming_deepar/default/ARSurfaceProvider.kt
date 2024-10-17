package com.example.streaming_deepar.default

import ai.deepar.ar.DeepAR
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import java.util.Timer
import java.util.TimerTask

/**
 * Surface provider used for CameraX preview use-case that provides DeepAR's external GL texture
 * wrapped in SurfaceTexture.
 */
class ARSurfaceProvider internal constructor(
    private val context: Context,
    private val deepAR: DeepAR
) : SurfaceProvider {
    private fun printEglState() {
        Log.d(
            tag,
            "display: " + EGL14.eglGetCurrentDisplay().nativeHandle + ", context: " + EGL14.eglGetCurrentContext().nativeHandle
        )
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        Log.d(tag, "Surface requested")
        printEglState()

        // request the external gl texture from deepar
        if (nativeGLTextureHandle == 0) {
            try {
                nativeGLTextureHandle = deepAR.externalGlTexture
            } catch (e: IllegalStateException) {
                request.willNotProvideSurface()
            }

            Log.d(tag, "request new external GL texture")
            printEglState()
        }

        // if external gl texture could not be provided
        if (nativeGLTextureHandle == 0) {
            request.willNotProvideSurface()
            return
        }

        // if external GL texture is provided create SurfaceTexture from it
        // and register onFrameAvailable listener to
        val resolution = request.resolution
        if (surfaceTexture == null) {
            surfaceTexture = SurfaceTexture(nativeGLTextureHandle)
            surfaceTexture!!.setOnFrameAvailableListener { `__`: SurfaceTexture? ->
                if (stop) {
                    return@setOnFrameAvailableListener
                }
                surfaceTexture!!.updateTexImage()
                if (isNotifyDeepar) {
//                    Log.d(tag, "onSurfaceRequested: frame");
                    deepAR.receiveFrameExternalTexture(
                        resolution.width,
                        resolution.height,
                        orientation,
                        mirror,
                        nativeGLTextureHandle
                    )
                }
                Log.d("Tag-Pt","setOnFrameAvailableListener")
            }
        }
        surfaceTexture!!.setDefaultBufferSize(resolution.width, resolution.height)

        if (surface == null) {
            surface = Surface(surfaceTexture)
        }

        // register transformation listener to listen for screen orientation changes
        request.setTransformationInfoListener(ContextCompat.getMainExecutor(context)) { transformationInfo: SurfaceRequest.TransformationInfo ->
            orientation = transformationInfo.rotationDegrees
        }

        request.provideSurface(
            surface!!,
            ContextCompat.getMainExecutor(context)
        ) { result: SurfaceRequest.Result ->
            when (result.resultCode) {
                SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY -> Log.i(
                    tag, "RESULT_SURFACE_USED_SUCCESSFULLY"
                )

                SurfaceRequest.Result.RESULT_INVALID_SURFACE -> Log.i(tag, "RESULT_INVALID_SURFACE")
                SurfaceRequest.Result.RESULT_REQUEST_CANCELLED -> Log.i(
                    tag,
                    "RESULT_REQUEST_CANCELLED"
                )

                SurfaceRequest.Result.RESULT_SURFACE_ALREADY_PROVIDED -> Log.i(
                    tag,
                    "RESULT_SURFACE_ALREADY_PROVIDED"
                )

                SurfaceRequest.Result.RESULT_WILL_NOT_PROVIDE_SURFACE -> Log.i(
                    tag,
                    "RESULT_WILL_NOT_PROVIDE_SURFACE"
                )
            }
        }
    }

    /**
     * Get the mirror flag. Mirror flag tells the DeepAR weather to mirror the camera frame.
     * Usually this is set when using front camera.
     *
     * @return mirror flag
     */
    fun isMirror(): Boolean {
        return mirror
    }

    /**
     * Set the mirror flag. Mirror flag tells the DeepAR weather to mirror the camera frame.
     * Usually this is set when using front camera.
     *
     * @param mirror mirror flag
     */
    fun setMirror(mirror: Boolean) {
        this.mirror = mirror
        if (surfaceTexture == null || surface == null) {
            return
        }


        // when camera changes from front to back, we don't know
        // when exactly it will happen so we pause feeding the frames
        // to deepar for 1 second to avoid mirroring image before
        // the camera actually changed
        isNotifyDeepar = false
        Timer().schedule(object : TimerTask() {
            override fun run() {
                isNotifyDeepar = true
            }
        }, 1000)
    }

    /**
     * Tell the surface provider to stop feeding frames to DeepAR.
     * Should be called in [Activity.onDestroy] ()}.
     */
    fun stop() {
        stop = true
    }

    private var isNotifyDeepar = true
    private var stop = false
    private var mirror = true
    private var orientation = 0

    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var nativeGLTextureHandle = 0

    companion object {
        private val tag: String = ARSurfaceProvider::class.java.simpleName
    }
}
