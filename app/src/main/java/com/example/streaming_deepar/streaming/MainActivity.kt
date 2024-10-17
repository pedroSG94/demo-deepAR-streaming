package com.example.streaming_deepar.streaming

import ai.deepar.ar.ARErrorType
import ai.deepar.ar.AREventListener
import ai.deepar.ar.CameraResolutionPreset
import ai.deepar.ar.DeepAR
import ai.deepar.ar.DeepARImageFormat
import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.Image
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.streaming_deepar.R
import com.example.streaming_deepar.streaming.DeepARRenderer.MyContextFactory
import com.google.common.util.concurrent.ListenableFuture
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.library.rtmp.RtmpStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutionException
import kotlin.math.min

class MainActivity : AppCompatActivity(), AREventListener, ConnectChecker {
    // Default camera lens value, change to CameraSelector.LENS_FACING_BACK to initialize with back camera
    private val defaultLensFacing = CameraSelector.LENS_FACING_FRONT
    private val lensFacing = defaultLensFacing
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var buffers: Array<ByteBuffer?>? = null
    private var allocatedBufferSize = 0
    private var currentBuffer = 0
    private var deepAR: DeepAR? = null
    private var surfaceView: GLSurfaceView? = null
    private var renderer: DeepARRenderer? = null
    private var currentEffect = 0
    private var isPrepare = false

    //    private RtcEngine mRtcEngine;
    private var callInProgress = false
    private val rtmpDeepAR: RtmpDeepAR by lazy {
        RtmpDeepAR(context = this, connectChecker= this, videoSource = NoVideoSource(), audioSource = MicrophoneSource()).apply {
            getGlInterface().autoHandleOrientation = true
            getStreamClient().setBitrateExponentialFactor(0.5f)
        }
    }

    private val rtmpStream: RtmpStream by lazy {
        RtmpStream(context = this, connectChecker= this, videoSource = RGABSource(), audioSource = MicrophoneSource()).apply {
            getGlInterface().autoHandleOrientation = true
            getStreamClient().setBitrateExponentialFactor(0.5f)
        }
    }
    private val vBitrate = 1200 * 1000
    private var rotation = 0
    private val sampleRate = 32000
    private val isStereo = true
    private val aBitrate = 128 * 1000

    private var remoteViewContainer: FrameLayout? = null
    var effects: ArrayList<String> = arrayListOf()
    lateinit var preview: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepAR = DeepAR(this)
        deepAR!!.setLicenseKey("160917e95e2b31ddff1bfc3a34afd89323efaa94252e35271161eb0ff51d512fa71025338787930a")
        deepAR!!.initialize(this, this)
        deepAR?.startCapture()
        setContentView(R.layout.activity_main_streaming)
        preview = findViewById(R.id.localPreview)
        callInProgress = false
        remoteViewContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)
    }

    override fun onStart() {
        super.onStart()
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            ),
            1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        for (grantResult in grantResults) {
//            if (grantResult != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this, permissions, requestCode)
//                return
//            }
//        }
        setup()
    }

    override fun onConnectionStarted(url: String) {

    }

    override fun onAuthError() {

    }

    override fun onAuthSuccess() {

    }

    override fun onConnectionFailed(reason: String) {
        Log.d(TAG,"onConnectionFailed: $reason")
    }

    override fun onConnectionSuccess() {

    }

    override fun onDisconnect() {

    }

    private fun initializeFilters() {
        effects = ArrayList()
        effects.add("none")
        effects.add("viking_helmet.deepar")
        effects.add("MakeupLook.deepar")
        effects.add("Split_View_Look.deepar")
        effects.add("Emotions_Exaggerator.deepar")
        effects.add("Emotion_Meter.deepar")
        effects.add("Stallone.deepar")
        effects.add("flower_face.deepar")
        effects.add("galaxy_background.deepar")
        effects.add("Humanoid.deepar")
        effects.add("Neon_Devil_Horns.deepar")
        effects.add("Ping_Pong.deepar")
        effects.add("Pixel_Hearts.deepar")
        effects.add("Snail.deepar")
        effects.add("Hope.deepar")
        effects.add("Vendetta_Mask.deepar")
        effects.add("Fire_Effect.deepar")
        effects.add("burning_effect.deepar")
        effects.add("Elephant_Trunk.deepar")
    }

   private fun setup() {
        setupCamera()
        initializeEngine()
        initializeFilters()

        //        setupVideoConfig();
        surfaceView = GLSurfaceView(this)
        surfaceView!!.setEGLContextClientVersion(2)
        surfaceView!!.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        renderer = DeepARRenderer(deepAR!!, rtmpStream, this)

        surfaceView!!.setEGLContextFactory(MyContextFactory(renderer!!))

        surfaceView!!.setRenderer(renderer)
        surfaceView!!.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        preview = findViewById<FrameLayout>(R.id.localPreview)
        preview.addView(surfaceView)

        val btn = findViewById<Button>(R.id.startCall)
        //        mRtcEngine.setExternalVideoSource(true, true, true);
        btn.setOnClickListener {
            if (callInProgress) {
                callInProgress = false
                renderer!!.isCallInProgress = false
                //                    mRtcEngine.leaveChannel();
                onRemoteUserLeft()
                btn.text = "Start the call"
            } else {
                callInProgress = true
                renderer!!.isCallInProgress = true
                joinChannel()
                btn.text = "End the call"
            }
        }
        findViewById<ImageButton>(R.id.previousMask).setOnClickListener {
            gotoNext()
        }
        findViewById<ImageButton>(R.id.nextMask).setOnClickListener {
            gotoPrevious()
        }
        prepare()
    }

    private fun prepare() {
        val prepared = try {
            Log.d(TAG,"width: ${preview.width}, height: ${preview.height}")
            rtmpStream.prepareVideo(preview.width, preview.height, vBitrate, rotation = rotation) &&
                    rtmpStream.prepareAudio(sampleRate, isStereo, aBitrate)
        } catch (e: IllegalArgumentException) {
            false
        }
        isPrepare = prepared
        if (!prepared) {
            Log.d(TAG, "prepared false")
        }
    }

    private fun gotoNext() {
        currentEffect = (currentEffect + 1) % effects!!.size
        deepAR!!.switchEffect("effect", getFilterPath(effects!![currentEffect]))
    }

    private fun gotoPrevious() {
        currentEffect = (currentEffect - 1 + effects!!.size) % effects!!.size
        deepAR!!.switchEffect("effect", getFilterPath(effects!![currentEffect]))
    }


    private fun getFilterPath(filterName: String): String? {
        if (filterName == "none") {
            return null
        }
        return "file:///android_asset/$filterName"
    }

    private val screenOrientation: Int
        /*
        get interface orientation from
        https://stackoverflow.com/questions/10380989/how-do-i-get-the-current-orientation-activityinfo-screen-orientation-of-an-a/10383164
     */
        get() {
            val rotation = windowManager.defaultDisplay.rotation
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)
            val width = dm.widthPixels
            val height = dm.heightPixels
            val orientation: Int
            // if the device's natural orientation is portrait:
            if ((rotation == Surface.ROTATION_0
                        || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height
            ) {
                when (rotation) {
                    Surface.ROTATION_0 -> orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    Surface.ROTATION_90 -> orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    Surface.ROTATION_180 -> orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

                    Surface.ROTATION_270 -> orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

                    else -> {
                        Log.e(
                            TAG, "Unknown screen orientation. Defaulting to " +
                                    "portrait."
                        )
                        orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            } else {
                when (rotation) {
                    Surface.ROTATION_0 -> orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    Surface.ROTATION_90 -> orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    Surface.ROTATION_180 -> orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

                    Surface.ROTATION_270 -> orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

                    else -> {
                        Log.e(
                            TAG, "Unknown screen orientation. Defaulting to " +
                                    "landscape."
                        )
                        orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
            }

            return orientation
        }

    private fun setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture!!.addListener({
            try {
                val cameraProvider = cameraProviderFuture!!.get()
                bindImageAnalysis(cameraProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider) {
        val cameraPreset = CameraResolutionPreset.P640x480
        val width: Int
        val height: Int
        val orientation = screenOrientation
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            width = cameraPreset.width
            height = cameraPreset.height
        } else {
            width = cameraPreset.height
            height = cameraPreset.width
        }

        val imageAnalysis = ImageAnalysis.Builder().setTargetResolution(Size(width, height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { image -> //image.getImageInfo().getTimestamp();
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val imageBufferSize = ySize + uSize + vSize
            if (allocatedBufferSize < imageBufferSize) {
                initializeBuffers(imageBufferSize)
            }

            val byteData = ByteArray(imageBufferSize)

            val width = image.width
            val yStride = image.planes[0].rowStride
            val uStride = image.planes[1].rowStride
            val vStride = image.planes[2].rowStride
            var outputOffset = 0
            if (width == yStride) {
                yBuffer[byteData, outputOffset, ySize]
                outputOffset += ySize
            } else {
                var inputOffset = 0
                while (inputOffset < ySize) {
                    yBuffer.position(inputOffset)
                    yBuffer[byteData, outputOffset, min(
                        yBuffer.remaining().toDouble(),
                        width.toDouble()
                    )
                        .toInt()]
                    outputOffset += width
                    inputOffset += yStride
                }
            }
            //U and V are swapped
            if (width == vStride) {
                vBuffer[byteData, outputOffset, vSize]
                outputOffset += vSize
            } else {
                var inputOffset = 0
                while (inputOffset < vSize) {
                    vBuffer.position(inputOffset)
                    vBuffer[byteData, outputOffset, min(
                        vBuffer.remaining().toDouble(),
                        width.toDouble()
                    )
                        .toInt()]
                    outputOffset += width
                    inputOffset += vStride
                }
            }
            if (width == uStride) {
                uBuffer[byteData, outputOffset, uSize]
                outputOffset += uSize
            } else {
                var inputOffset = 0
                while (inputOffset < uSize) {
                    uBuffer.position(inputOffset)
                    uBuffer[byteData, outputOffset, min(
                        uBuffer.remaining().toDouble(),
                        width.toDouble()
                    )
                        .toInt()]
                    outputOffset += width
                    inputOffset += uStride
                }
            }

            buffers!![currentBuffer]!!.put(byteData)
            buffers!![currentBuffer]!!.position(0)
            if (deepAR != null) {
                deepAR!!.receiveFrame(
                    buffers!![currentBuffer],
                    image.width, image.height,
                    image.imageInfo.rotationDegrees,
                    lensFacing == CameraSelector.LENS_FACING_FRONT,
                    DeepARImageFormat.YUV_420_888,
                    image.planes[1].pixelStride
                )
            }
            currentBuffer = (currentBuffer + 1) % NUMBER_OF_BUFFERS
            image.close()
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle((this as LifecycleOwner), cameraSelector, imageAnalysis)
    }

    private fun initializeBuffers(size: Int) {
        if (buffers == null) {
            buffers = arrayOfNulls(NUMBER_OF_BUFFERS)
        }
        for (i in 0 until NUMBER_OF_BUFFERS) {
            buffers!![i] = ByteBuffer.allocateDirect(size)
            buffers!![i]?.order(ByteOrder.nativeOrder())
            buffers!![i]?.position(0)
        }
        allocatedBufferSize = size
    }

    fun setRemoteViewWeight(weight: Float) {
        val params = remoteViewContainer!!.layoutParams as LinearLayout.LayoutParams
        params.weight = weight
        remoteViewContainer!!.layoutParams = params
    }

    override fun onResume() {
        super.onResume()
        if (surfaceView != null) {
            surfaceView!!.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (surfaceView != null) {
            surfaceView!!.onPause()
        }
    }

    override fun onStop() {
        var cameraProvider: ProcessCameraProvider? = null
        try {
            cameraProvider = cameraProviderFuture!!.get()
            cameraProvider.unbindAll()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        deepAR!!.release()
        //        mRtcEngine.leaveChannel();
//        RtcEngine.destroy();
    }

    private fun initializeEngine() {

    }

    private fun joinChannel() {
        if (!rtmpStream.isStreaming && isPrepare) {
            rtmpStream.startStream("rtmps://boosty-live-17-10-ezmpbxio.rtmp.livekit.cloud/x/sLZfvJLZrtYo")
        } else {
            rtmpStream.stopStream()
        }
    }

    private fun onRemoteUserLeft() {
        remoteViewContainer!!.removeAllViews()
        setRemoteViewWeight(0f)
    }

    override fun screenshotTaken(bitmap: Bitmap) {
    }

    override fun videoRecordingStarted() {
        Log.d(TAG,"videoRecordingStarted")
    }

    override fun videoRecordingFinished() {
        Log.d(TAG,"videoRecordingFinished")
    }

    override fun videoRecordingFailed() {
        Log.d(TAG,"videoRecordingStarted")
    }

    override fun videoRecordingPrepared() {
        Log.d(TAG,"videoRecordingPrepared")
    }

    override fun shutdownFinished() {
        Log.d(TAG,"shutdownFinished")
    }

    override fun initialized() {
        deepAR!!.switchEffect("mask", "file:///android_asset/aviators")
    }

    override fun faceVisibilityChanged(b: Boolean) {
    }

    override fun imageVisibilityChanged(s: String, b: Boolean) {
    }

    override fun frameAvailable(image: Image) {
        Log.d(TAG,"frameAvailable")
    }

    override fun error(arErrorType: ARErrorType, s: String) {
    }

    override fun effectSwitched(s: String) {
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val NUMBER_OF_BUFFERS = 2
    }
}