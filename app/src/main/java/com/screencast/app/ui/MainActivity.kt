package com.screencast.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.screencast.app.databinding.ActivityMainBinding
import com.screencast.app.streaming.ScreenCaptureService
import com.screencast.app.streaming.StreamState

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager
    private var currentState = StreamState.IDLE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) requestScreenCapture()
        else showSnackbar("Microphone permission is required to stream audio")
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            launchService(result.resultCode, result.data!!)
        } else {
            showSnackbar("Screen capture permission denied")
            updateUI(StreamState.IDLE)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stateName = intent.getStringExtra(ScreenCaptureService.EXTRA_STATE) ?: return
            updateUI(StreamState.valueOf(stateName))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectionManager = getSystemService(MediaProjectionManager::class.java)

        loadSavedPrefs()
        setupButtons()

        registerReceiver(
            stateReceiver,
            IntentFilter(ScreenCaptureService.ACTION_STATE_UPDATE),
            RECEIVER_NOT_EXPORTED
        )
    }

    private fun loadSavedPrefs() {
        val prefs = getSharedPreferences("screencast_prefs", MODE_PRIVATE)
        binding.etRtmpsUrl.setText(prefs.getString("rtmps_url", ""))
        binding.etStreamKey.setText(prefs.getString("stream_key", ""))
    }

    private fun savePrefs() {
        getSharedPreferences("screencast_prefs", MODE_PRIVATE).edit()
            .putString("rtmps_url", binding.etRtmpsUrl.text.toString().trim())
            .putString("stream_key", binding.etStreamKey.text.toString().trim())
            .apply()
    }

    private fun setupButtons() {
        binding.btnStartStop.setOnClickListener {
            when (currentState) {
                StreamState.IDLE,
                StreamState.ERROR,
                StreamState.STOPPED -> startStream()
                StreamState.LIVE,
                StreamState.CONNECTING -> stopStream()
            }
        }
    }

    private fun startStream() {
        val url = binding.etRtmpsUrl.text.toString().trim()
        val key = binding.etStreamKey.text.toString().trim()

        if (url.isEmpty()) {
            showSnackbar("Please enter the RTMPS URL")
            return
        }
        if (!url.startsWith("rtmps://") && !url.startsWith("rtmp://")) {
            showSnackbar("URL must start with rtmps:// or rtmp://")
            return
        }

        savePrefs()
        checkPermissionsAndStart()
    }

    private fun stopStream() {
        startService(Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        })
    }

    private fun checkPermissionsAndStart() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) requestScreenCapture()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun requestScreenCapture() {
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun launchService(resultCode: Int, data: Intent) {
        val url = binding.etRtmpsUrl.text.toString().trim()
        val key = binding.etStreamKey.text.toString().trim()

        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
            putExtra(ScreenCaptureService.EXTRA_RTMPS_URL, url)
            putExtra(ScreenCaptureService.EXTRA_STREAM_KEY, key)
        }
        ContextCompat.startForegroundService(this, intent)
        updateUI(StreamState.CONNECTING)
    }

    private fun updateUI(state: StreamState) {
        currentState = state
        runOnUiThread {
            when (state) {
                StreamState.IDLE, StreamState.STOPPED -> {
                    binding.btnStartStop.text = "GO LIVE"
                    binding.btnStartStop.isEnabled = true
                    binding.statusDot.setBackgroundResource(com.screencast.app.R.drawable.dot_idle)
                    binding.tvStatus.text = "Ready"
                    binding.inputGroup.isEnabled = true
                    binding.etRtmpsUrl.isEnabled = true
                    binding.etStreamKey.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
                StreamState.CONNECTING -> {
                    binding.btnStartStop.text = "STOP"
                    binding.btnStartStop.isEnabled = true
                    binding.statusDot.setBackgroundResource(com.screencast.app.R.drawable.dot_connecting)
                    binding.tvStatus.text = "Connecting..."
                    binding.etRtmpsUrl.isEnabled = false
                    binding.etStreamKey.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }
                StreamState.LIVE -> {
                    binding.btnStartStop.text = "STOP"
                    binding.btnStartStop.isEnabled = true
                    binding.statusDot.setBackgroundResource(com.screencast.app.R.drawable.dot_live)
                    binding.tvStatus.text = "🔴  LIVE"
                    binding.etRtmpsUrl.isEnabled = false
                    binding.etStreamKey.isEnabled = false
                    binding.progressBar.visibility = View.GONE
                }
                StreamState.ERROR -> {
                    binding.btnStartStop.text = "RETRY"
                    binding.btnStartStop.isEnabled = true
                    binding.statusDot.setBackgroundResource(com.screencast.app.R.drawable.dot_idle)
                    binding.tvStatus.text = "Connection failed"
                    binding.etRtmpsUrl.isEnabled = true
                    binding.etStreamKey.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    showSnackbar("Failed to connect — check your URL & stream key")
                }
            }
        }
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        unregisterReceiver(stateReceiver)
        super.onDestroy()
    }
}
