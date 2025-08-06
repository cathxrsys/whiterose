package com.example.whiterose

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var intervalGroup: RadioGroup
    private var isWhiteRoseActive = false
    private val prefs by lazy { getSharedPreferences("whiterose_prefs", MODE_PRIVATE) }
    private var mediaPlayer: MediaPlayer? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("MainActivity", "Notification permission granted: $granted")
        if (granted && isWhiteRoseActive) {
            startWhiteRoseService()
            playImmediateSound()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        Log.d("MainActivity", "Activity created")

        // Инициализация MediaPlayer
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.beep)
            Log.d("MainActivity", "MediaPlayer created")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating MediaPlayer: ${e.message}")
        }

        // Запрос исключения из оптимизации батареи
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("MainActivity", "Requesting battery optimization exemption")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error requesting battery optimization exemption: ${e.message}")
                }
            }
        }

        // Восстановление состояния
        isWhiteRoseActive = prefs.getBoolean("isWhiteRoseActive", false)
        val savedInterval = prefs.getLong("interval", 60 * 1000L)
        imageView = findViewById(R.id.imageView3)
        intervalGroup = findViewById(R.id.interval_group)
        imageView.alpha = if (isWhiteRoseActive) 1.0f else 0.5f

        // Установка выбранного интервала
        if (savedInterval == 60 * 1000L) {
            intervalGroup.check(R.id.interval_1min)
        } else {
            intervalGroup.check(R.id.interval_5min)
        }

        if (isWhiteRoseActive) {
            checkAndStartService()
        }

        // Обработчик нажатия на ImageView
        imageView.setOnClickListener {
            isWhiteRoseActive = !isWhiteRoseActive
            imageView.alpha = if (isWhiteRoseActive) 1.0f else 0.5f
            Log.d("MainActivity", "ImageView clicked, isWhiteRoseActive=$isWhiteRoseActive")
            if (isWhiteRoseActive) {
                checkAndStartService()
            } else {
                stopWhiteRoseService()
            }
            prefs.edit().putBoolean("isWhiteRoseActive", isWhiteRoseActive).apply()
        }

        // Обработчик выбора интервала
        intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val interval = when (checkedId) {
                R.id.interval_1min -> 60 * 1000L
                R.id.interval_5min -> 300 * 1000L
                else -> 60 * 1000L
            }
            prefs.edit().putLong("interval", interval).apply()
            if (isWhiteRoseActive) {
                stopWhiteRoseService()
                checkAndStartService()
            }
        }
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d("MainActivity", "Requesting POST_NOTIFICATIONS permission")
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startWhiteRoseService()
            playImmediateSound()
        }
    }

    private fun startWhiteRoseService() {
        val interval = prefs.getLong("interval", 60 * 1000L)
        val intent = Intent(this, WhiteRoseService::class.java).apply {
            putExtra(WhiteRoseService.EXTRA_INTERVAL, interval)
        }
        try {
            startForegroundService(intent)
            Log.d("MainActivity", "Service started with interval=$interval")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting service: ${e.message}")
        }
    }

    private fun stopWhiteRoseService() {
        try {
            stopService(Intent(this, WhiteRoseService::class.java))
            Log.d("MainActivity", "Service stopped")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping service: ${e.message}")
        }
    }

    private fun playImmediateSound() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                    player.reset()
                    mediaPlayer = MediaPlayer.create(this, R.raw.beep)
                }
                mediaPlayer?.start()
                Log.d("MainActivity", "Immediate sound played")
            } ?: run {
                Log.e("MainActivity", "MediaPlayer is null")
                mediaPlayer = MediaPlayer.create(this, R.raw.beep)
                mediaPlayer?.start()
                Log.d("MainActivity", "MediaPlayer recreated and sound played")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing immediate sound: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d("MainActivity", "MediaPlayer released")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error releasing MediaPlayer: ${e.message}")
        }
        super.onDestroy()
    }
}