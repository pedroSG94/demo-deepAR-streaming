package com.example.streaming_deepar.streaming

import android.content.Context
import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.library.base.StreamBase
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.rtmp.rtmp.RtmpClient
import java.nio.ByteBuffer

class RtmpDeepAR(context: Context, connectChecker: ConnectChecker, videoSource: VideoSource,
                 audioSource: AudioSource
): StreamBase(context,videoSource,audioSource) {

    val rtmpClient = RtmpClient(connectChecker)
    private val streamClientListener = object: StreamClientListener {
        override fun onRequestKeyframe() {
            requestKeyframe()
        }
    }
    override fun getStreamClient(): RtmpStreamClient = RtmpStreamClient(rtmpClient, streamClientListener)

    constructor(context: Context, connectChecker: ConnectChecker):
            this(context, connectChecker, Camera2Source(context), MicrophoneSource())

    override fun setVideoCodecImp(codec: VideoCodec) {
        rtmpClient.setVideoCodec(codec)
    }

    override fun setAudioCodecImp(codec: AudioCodec) {
        rtmpClient.setAudioCodec(codec)
    }

    override fun onAudioInfoImp(sampleRate: Int, isStereo: Boolean) {
        rtmpClient.setAudioInfo(sampleRate, isStereo)
    }

    override fun startStreamImp(endPoint: String) {
        val resolution = super.getVideoResolution()
        rtmpClient.setVideoResolution(resolution.width, resolution.height)
        rtmpClient.setFps(super.getVideoFps())
        rtmpClient.connect(endPoint)
    }

    override fun stopStreamImp() {
        rtmpClient.disconnect()
    }

    override fun onVideoInfoImp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
        rtmpClient.setVideoInfo(sps, pps, vps)
    }

    override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        rtmpClient.sendVideo(videoBuffer, info)
    }

    override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        rtmpClient.sendAudio(audioBuffer, info)
    }

    fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo){
        rtmpClient.sendVideo(h264Buffer, info);
    }

}