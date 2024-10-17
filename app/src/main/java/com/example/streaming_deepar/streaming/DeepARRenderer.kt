package com.example.streaming_deepar.streaming

import ai.deepar.ar.DeepAR
import android.app.Activity
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.EGLContextFactory
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.pedro.common.toByteArray
import com.pedro.library.rtmp.RtmpStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10


class DeepARRenderer(
    private val deepAR: DeepAR,
    private val rtmpStream: RtmpStream,
    private val context: Activity
) : GLSurfaceView.Renderer {
    private val vertexShaderCode = "attribute vec4 vPosition;" +
            "attribute vec2 vUv;" +
            "varying vec2 uv; " +
            "void main() {" +
            "gl_Position = vPosition;" +
            "uv = vUv;" +
            "}"

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;varying vec2 uv; uniform samplerExternalOES sampler;void main() {  gl_FragColor = texture2D(sampler, uv); }
        """.trimIndent()

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)
    private val TAG = "DeepARRenderer"

    private var vertexBuffer: FloatBuffer? = null
    private var uvbuffer: FloatBuffer? = null
    private var drawListBuffer: ShortBuffer? = null
    private var program = 0
    private var texture = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private var updateTexImage = false

    var isCallInProgress: Boolean = false

    private var mEGLCurrentContext: EGLContext? = null

    private var textureWidth = 0
    private var textureHeight = 0
    private lateinit var byteBuffer: ByteBuffer
    val info = MediaCodec.BufferInfo()

    var matrix: FloatArray = FloatArray(16)
    private lateinit var h264Encoder: H264Encoder
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)

        val bb = ByteBuffer.allocateDirect(squareCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer?.put(squareCoords)
        vertexBuffer?.position(0)

        val bb2 = ByteBuffer.allocateDirect(squareCoords.size * 4)
        bb2.order(ByteOrder.nativeOrder())
        uvbuffer = bb2.asFloatBuffer()
        uvbuffer?.put(uv)
        uvbuffer?.position(0)

        val dlb = ByteBuffer.allocateDirect(drawOrder.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer?.put(drawOrder)
        drawListBuffer?.position(0)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        Matrix.setIdentityM(this.matrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        texture = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)

        textureWidth = width
        textureHeight = height

        GLES20.glTexImage2D(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            0,
            GLES20.GL_RGBA,
            textureWidth,
            textureHeight,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        surfaceTexture = SurfaceTexture(texture).apply {
            setOnFrameAvailableListener {
                Log.d(TAG, "setOnFrameAvailableListener")
            }
        }
        surface = Surface(surfaceTexture)
//        h264Encoder = H264Encoder(textureWidth, textureHeight)
        Log.d(TAG, "width: $textureWidth, height: $textureHeight")
        surfaceTexture!!.setOnFrameAvailableListener { updateTexImage = true }
        // 4 bytes per pixel (RGBA)
        byteBuffer = ByteBuffer.allocateDirect(textureWidth * textureHeight * 4).apply {
                order(ByteOrder.nativeOrder())
            }

        context.runOnUiThread { deepAR.setRenderSurface(surface, textureWidth, textureHeight) }
    }

    override fun onDrawFrame(gl: GL10) {
        GLES20.glFinish()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (updateTexImage) {
            updateTexImage = false
            synchronized(this) {
                surfaceTexture!!.updateTexImage()
            }
        }

        surfaceTexture!!.getTransformMatrix(matrix)

        GLES20.glUseProgram(program)
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        val uvHandle = GLES20.glGetAttribLocation(program, "vUv")
        GLES20.glEnableVertexAttribArray(uvHandle)
        GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, 8, uvbuffer)

        val sampler = GLES20.glGetUniformLocation(program, "sampler")
        GLES20.glUniform1i(sampler, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            drawOrder.size,
            GLES20.GL_UNSIGNED_SHORT,
            drawListBuffer
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(uvHandle)
        GLES20.glUseProgram(0)
        if (isCallInProgress) {
            scope.launch {
                try {
                    byteBuffer.rewind()
                    // Đọc pixel từ framebuffer
                    GLES20.glReadPixels(
                        0, 0, textureWidth, textureHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer
                    )
//                    val h264Buffer = ByteBuffer.wrap(byteBuffer.toByteArray())
                    (rtmpStream.videoSource as RGABSource).setVideo(byteBuffer)
                } catch (e: Exception) {
                    Log.e(TAG, "error: ${e.printStackTrace()}")
                }
            }
        }
    }

    private fun decodeSpsPpsFromBuffer(
        outputBuffer: ByteBuffer,
        length: Int
    ): Pair<ByteBuffer, ByteBuffer>? {
        val csd = ByteArray(length)
        outputBuffer[csd, 0, length]
        outputBuffer.rewind()
        var i = 0
        var spsIndex = -1
        var ppsIndex = -1
        while (i < length - 4) {
            if (csd[i].toInt() == 0 && csd[i + 1].toInt() == 0 && csd[i + 2].toInt() == 0 && csd[i + 3].toInt() == 1) {
                if (spsIndex == -1) {
                    spsIndex = i
                } else {
                    ppsIndex = i
                    break
                }
            }
            i++
        }
        if (spsIndex != -1 && ppsIndex != -1) {
            val sps = ByteArray(ppsIndex)
            System.arraycopy(csd, spsIndex, sps, 0, ppsIndex)
            val pps = ByteArray(length - ppsIndex)
            System.arraycopy(csd, ppsIndex, pps, 0, length - ppsIndex)
            return Pair(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps))
        }
        return null
    }
    class MyContextFactory(private val renderer: DeepARRenderer) : EGLContextFactory {
        override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext {
            val attrib_list = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
            )
            renderer.mEGLCurrentContext =
                egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attrib_list)

            return renderer.mEGLCurrentContext!!
        }

        override fun destroyContext(
            egl: EGL10, display: EGLDisplay,
            context: EGLContext
        ) {
            if (!egl.eglDestroyContext(display, context)) {
            }
        }
    }

    companion object {
        var squareCoords: FloatArray = floatArrayOf(
            -1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f
        )

        var uv: FloatArray = floatArrayOf(
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
        )

        fun loadShader(type: Int, shaderCode: String?): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
