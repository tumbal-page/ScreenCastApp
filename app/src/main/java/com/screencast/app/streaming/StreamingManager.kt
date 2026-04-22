package com.screencast.app.streaming

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.source.ScreenSource
import com.pedro.library.source.MicrophoneSource

/**
 * Manages RTMPS screen streaming using RootEncoder 2.7.x new StreamBase API.
 * Uses RtmpStream + ScreenSource (supports rtmp:// and rtmps://)
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

            val stream = RtmpStream(context, connectChecker)
            rtmpStream = stream

            // Prepare video: width, height, bitrate (2.5Mbps)
            val videoOk = stream.prepareVideo(width, height, 2_500_000)

            // Prepare audio: sampleRate, stereo, bitrate
            val audioOk = stream.prepareAudio(44100, true, 128_000)

            if (!videoOk || !audioOk) {
                Log.e(TAG, "Prepare failed video=$videoOk audio=$audioOk")
                onStateChanged(StreamState.ERROR)
                return
            }

            // Switch to screen source
            stream.changeVideoSource(ScreenSource(context, mediaProjection))
            stream.changeAudioSource(MicrophoneSource())

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
            Log.d(TAG, "Connected! Streaming live.")
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
