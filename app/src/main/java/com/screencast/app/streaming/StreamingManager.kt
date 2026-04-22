package com.screencast.app.streaming

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmps.RtmpsDisplay

/**
 * Manages RTMPS screen streaming using RootEncoder 2.7.2
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

            rtmpsDisplay = RtmpsDisplay(context, true, connectChecker)

            val videoReady = rtmpsDisplay!!.prepareVideo(
                width, height,
                30,         // fps
                2_500_000,  // 2.5 Mbps bitrate
                0,          // rotation
                dpi
            )

            val audioReady = rtmpsDisplay!!.prepareAudio(
                128_000,    // bitrate
                44100,      // sample rate
                true,       // stereo
                true,       // echo canceler
                true        // noise suppressor
            )

            if (!videoReady || !audioReady) {
                Log.e(TAG, "Prepare failed — video=$videoReady audio=$audioReady")
                onStateChanged(StreamState.ERROR)
                return
            }

            rtmpsDisplay!!.startDisplay(mediaProjection)

            val fullUrl = if (streamKey.isBlank()) rtmpsUrl
                         else "${rtmpsUrl.trimEnd('/')}/$streamKey"

            Log.d(TAG, "Connecting: $fullUrl")
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
            Log.e(TAG, "Auth error — check stream key")
            onStateChanged(StreamState.ERROR)
        }
        override fun onAuthSuccess() {
            Log.d(TAG, "Auth success")
        }
    }
}
