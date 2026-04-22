package com.screencast.app.streaming

import android.content.Context
import android.media.MediaCodecInfo
import android.media.projection.MediaProjection
import android.util.Log
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.utils.CodecUtil
import com.pedro.library.rtmps.RtmpsDisplay
import com.pedro.library.util.streamclient.RtmpStreamClient

/**
 * Manages RTMPS screen streaming using RootEncoder (Pedro Gallardo).
 * Handles: screen capture, audio with noise suppression, RTMPS push.
 */
class StreamingManager(
    private val context: Context,
    private val onStateChanged: (StreamState) -> Unit
) {

    private val TAG = "StreamingManager"
    private var rtmpsDisplay: RtmpsDisplay? = null

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

            rtmpsDisplay = RtmpsDisplay(context, true, connectCheckerRtmps)

            // Video config: H.264, 2.5 Mbps, 30fps, hardware encoder
            val videoReady = rtmpsDisplay!!.prepareVideo(
                width, height,
                30,           // fps
                2_500_000,    // bitrate 2.5 Mbps
                0,            // rotation
                dpi
            )

            // Audio config: 128kbps, 44100Hz, stereo, with noise suppressor
            val audioReady = rtmpsDisplay!!.prepareAudio(
                128_000,  // bitrate
                44100,    // sample rate
                true,     // stereo
                true,     // echo canceler
                true      // noise suppressor
            )

            if (!videoReady || !audioReady) {
                Log.e(TAG, "Prepare failed — video=$videoReady audio=$audioReady")
                onStateChanged(StreamState.ERROR)
                return
            }

            // Start screen capture with MediaProjection
            rtmpsDisplay!!.startDisplay(mediaProjection)

            // Build full RTMPS URL with stream key
            val fullUrl = buildUrl(rtmpsUrl, streamKey)
            Log.d(TAG, "Connecting to: $fullUrl")
            rtmpsDisplay!!.startStream(fullUrl)

        } catch (e: Exception) {
            Log.e(TAG, "startStreaming error: ${e.message}", e)
            onStateChanged(StreamState.ERROR)
        }
    }

    fun stopStreaming() {
        try {
            rtmpsDisplay?.stopStream()
            rtmpsDisplay?.stopDisplay()
            rtmpsDisplay = null
            onStateChanged(StreamState.STOPPED)
        } catch (e: Exception) {
            Log.e(TAG, "stopStreaming error: ${e.message}", e)
        }
    }

    fun isStreaming() = rtmpsDisplay?.isStreaming == true

    private fun buildUrl(base: String, key: String): String {
        // If user already included stream key in URL, use as-is
        if (key.isBlank()) return base
        val cleanBase = base.trimEnd('/')
        return "$cleanBase/$key"
    }

    private val connectCheckerRtmps = object : com.pedro.common.ConnectChecker {
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
            Log.d(TAG, "Bitrate: $bitrate bps")
        }
        override fun onDisconnect() {
            Log.w(TAG, "Disconnected")
            onStateChanged(StreamState.STOPPED)
        }
        override fun onAuthError() {
            Log.e(TAG, "Auth error — check stream key")
            onStateChanged(StreamState.ERROR)
        }
        override fun onAuthSuccess() {
            Log.d(TAG, "Auth success")
        }
    }
}
