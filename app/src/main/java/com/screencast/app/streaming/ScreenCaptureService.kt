package com.screencast.app.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.screencast.app.ui.MainActivity

class ScreenCaptureService : Service() {

    private val TAG = "ScreenCaptureService"
    private val CHANNEL_ID = "screencast_channel"
    private val NOTIFICATION_ID = 1

    private var streamingManager: StreamingManager? = null
    private var mediaProjection: MediaProjection? = null

    inner class LocalBinder : Binder() {
        fun getService() = this@ScreenCaptureService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                val rtmpsUrl = intent.getStringExtra(EXTRA_RTMPS_URL) ?: ""
                val streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""

                if (data == null || rtmpsUrl.isEmpty()) {
                    stopSelf(); return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                startCapture(resultCode, data, rtmpsUrl, streamKey)
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, rtmpsUrl: String, streamKey: String) {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null")
            broadcastState(StreamState.ERROR)
            stopSelf()
            return
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)

        // Cap resolution to 720p to save bandwidth & battery
        val (width, height) = capResolution(metrics.widthPixels, metrics.heightPixels)

        streamingManager = StreamingManager(
            context = this,
            onStateChanged = { state ->
                val label = when (state) {
                    StreamState.LIVE -> "🔴 LIVE"
                    StreamState.CONNECTING -> "Connecting..."
                    StreamState.ERROR -> "Connection error"
                    StreamState.STOPPED -> "Stopped"
                    else -> "Idle"
                }
                updateNotification(label)
                broadcastState(state)
            }
        )

        streamingManager?.startStreaming(
            mediaProjection = mediaProjection!!,
            rtmpsUrl = rtmpsUrl,
            streamKey = streamKey,
            width = width,
            height = height,
            dpi = metrics.densityDpi
        )
    }

    private fun capResolution(w: Int, h: Int): Pair<Int, Int> {
        val maxDim = 1280
        return if (w > h) {
            if (w > maxDim) Pair(maxDim, (h * maxDim / w).roundToEven())
            else Pair(w, h)
        } else {
            if (h > maxDim) Pair((w * maxDim / h).roundToEven(), maxDim)
            else Pair(w, h)
        }
    }

    private fun Int.roundToEven() = if (this % 2 == 0) this else this + 1

    private fun stopCapture() {
        streamingManager?.stopStreaming()
        streamingManager = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun broadcastState(state: StreamState) {
        sendBroadcast(Intent(ACTION_STATE_UPDATE).apply {
            putExtra(EXTRA_STATE, state.name)
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "ScreenCast Live",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Screen streaming notification"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenCast")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START       = "com.screencast.app.START"
        const val ACTION_STOP        = "com.screencast.app.STOP"
        const val ACTION_STATE_UPDATE = "com.screencast.app.STATE_UPDATE"
        const val EXTRA_RESULT_CODE  = "result_code"
        const val EXTRA_DATA         = "data"
        const val EXTRA_RTMPS_URL    = "rtmps_url"
        const val EXTRA_STREAM_KEY   = "stream_key"
        const val EXTRA_STATE        = "state"
    }
}
