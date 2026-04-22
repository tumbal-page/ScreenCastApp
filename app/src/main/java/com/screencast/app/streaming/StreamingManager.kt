package com.screencast.app.streaming

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.rtmp.RtmpStream

/**
 * Manages RTMPS screen streaming using RootEncoder 2.7.x StreamBase API.
 */
class StreamingManager(
    private val context: Context,
    private val onStateChanged: (StreamState) -> Unit
) {
    private val TAG = "StreamingManager"
    private var rtmpStream: RtmpStream? = null

    fun startStreaming(
        mediaProjection: MediaProjection,
        rtmpsUrl: String,
        streamKey: String,
        width: Int,
        height: Int,
        dpi: Int
    ) {
        try {
            onStateChanged(StreamState.CONNECTING)

            val screenSource = ScreenSource(context, mediaProjection)
            val stream = RtmpStream(context, connectChecker, screenSource, MicrophoneSource())
            rtmpStream = stream

            val videoOk = stream.prepareVideo(width, height, 2_500_000)
            val audioOk = stream.prepareAudio(44100, true, 128_000)

            if (!videoOk || !audioOk) {
                Log.e(TAG, "Prepare failed video=$videoOk audio=$audioOk")
                onStateChanged(StreamState.ERROR)
                return
            }

            val fullUrl = if (streamKey.isBlank()) rtmpsUrl
                         else "${rtmpsUrl.trimEnd('/')}/$streamKey"

            Log.d(TAG, "Connecting to: $fullUrl")
            stream.startStream(fullUrl)

        } catch (e: Exception) {
            Log.e(TAG, "startStreaming error: ${e.message}", e)
            onStateChanged(StreamState.ERROR)
        }
    }

    fun stopStreaming() {
        try {
            rtmpStream?.stopStream()
            rtmpStream?.release()
            rtmpStream = null
            onStateChanged(StreamState.STOPPED)
        } catch (e: Exception) {
            Log.e(TAG, "stopStreaming error: ${e.message}", e)
        }
    }

    fun isStreaming() = rtmpStream?.isStreaming == true

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            Log.d(TAG, "Connection started: $url")
        }
        override fun onConnectionSuccess() {
            Log.d(TAG, "Connected!")
            onStateChanged(StreamState.LIVE)
        }
        override fun onConnectionFailed(reason: String) {
            Log.e(TAG, "Connection failed: $reason")
            onStateChanged(StreamState.ERROR)
        }
        override fun onNewBitrate(bitrate: Long) {
            Log.d(TAG, "Bitrate: ${bitrate / 1000}kbps")
        }
        override fun onDisconnect() {
            Log.w(TAG, "Disconnected")
            onStateChanged(StreamState.STOPPED)
        }
        override fun onAuthError() {
            Log.e(TAG, "Auth error")
            onStateChanged(StreamState.ERROR)
        }
        override fun onAuthSuccess() {
            Log.d(TAG, "Auth success")
        }
    }
}
