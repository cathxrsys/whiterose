package com.example.whiterose

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class WhiteRoseService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var interval: Long = 60 * 1000L // Интервал в миллисекундах (1 минута)
    private var handler: Handler? = null
    private var lastSoundTime: Long = 0L // Время последнего звука
    private val prefs by lazy { getSharedPreferences("whiterose_prefs", MODE_PRIVATE) }

    companion object {
        const val EXTRA_INTERVAL = "interval"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WhiteRoseService", "Service created at ${System.currentTimeMillis()}")
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.beep)
            mediaPlayer?.isLooping = false
            Log.d("WhiteRoseService", "MediaPlayer created")
        } catch (e: Exception) {
            Log.e("WhiteRoseService", "Error creating MediaPlayer: ${e.message}")
        }

        // Восстанавливаем время последнего звука
        lastSoundTime = prefs.getLong("lastSoundTime", System.currentTimeMillis())
        Log.d("WhiteRoseService", "Restored lastSoundTime: $lastSoundTime")
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WhiteRoseService", "Service started with flags=$flags, startId=$startId, intent=${intent?.action}")
        try {
            // Получаем интервал из Intent или SharedPreferences
            interval = intent?.getLongExtra(EXTRA_INTERVAL, prefs.getLong("interval", 60 * 1000L))
                ?: prefs.getLong("interval", 60 * 1000L)
            Log.d("WhiteRoseService", "Using interval: $interval ms")

            // Сохраняем интервал и состояние сервиса
            prefs.edit().putLong("interval", interval).putBoolean("isServiceRunning", true).apply()

            // Запускаем foreground-сервис
            startForeground(1, createNotification())
            Log.d("WhiteRoseService", "Foreground service started")

            // Останавливаем предыдущий таймер, если он есть
            handler?.removeCallbacksAndMessages(null)
            Log.d("WhiteRoseService", "Previous timer cancelled")

            // Если intent == null (перезапуск из-за START_STICKY), не проигрываем начальный звук
            if (intent == null) {
                Log.d("WhiteRoseService", "Service restarted by system, skipping initial sound")
            } else {
                // Проигрываем начальный звук для явного запуска
                playSound()
                lastSoundTime = System.currentTimeMillis()
                prefs.edit().putLong("lastSoundTime", lastSoundTime).apply()
            }

            // Запускаем таймер
            startTimer()
        } catch (e: Exception) {
            Log.e("WhiteRoseService", "Error in onStartCommand: ${e.message}")
            stopSelf()
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "whiterose_channel"
        val channel = NotificationChannel(
            channelId,
            "White Rose Reminder",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "White Rose Reminder Service"
            enableVibration(false)
            setSound(null, null)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        Log.d("WhiteRoseService", "Notification channel created")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Белая Роза")
            .setContentText("Режим активен (интервал: ${interval / 1000 / 60} мин)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .build()
    }

    private fun startTimer() {
        handler?.postDelayed(object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                Log.d("WhiteRoseService", "Timer tick at $currentTime, lastSoundTime: $lastSoundTime")

                // Проверяем, прошло ли достаточно времени с последнего звука
                if (currentTime >= lastSoundTime + interval) {
                    playSound()
                }

                // Планируем следующий тик с корректировкой
                val nextTick = 1000L - (currentTime % 1000)
                handler?.postDelayed(this, nextTick)
                Log.d("WhiteRoseService", "Next tick scheduled in $nextTick ms")
            }
        }, 1000L)
        Log.d("WhiteRoseService", "Timer started")
    }

    private fun playSound() {
        val startTime = System.currentTimeMillis()
        var wakeLock: PowerManager.WakeLock? = null
        try {
            // Пробуем использовать WakeLock
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhiteRoseService::SoundWakeLock")
            wakeLock.acquire(2000L) // Держим WakeLock на 2 секунды
            Log.d("WhiteRoseService", "WakeLock acquired")
        } catch (e: Exception) {
            Log.w("WhiteRoseService", "Failed to acquire WakeLock: ${e.message}, proceeding without it")
        }

        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                    player.reset()
                    mediaPlayer = MediaPlayer.create(this, R.raw.beep)
                    Log.d("WhiteRoseService", "MediaPlayer reset and recreated")
                }
                mediaPlayer?.start()
                Log.d("WhiteRoseService", "Sound played at ${System.currentTimeMillis()}")
            } ?: run {
                Log.e("WhiteRoseService", "MediaPlayer is null")
                mediaPlayer = MediaPlayer.create(this, R.raw.beep)
                mediaPlayer?.start()
                Log.d("WhiteRoseService", "MediaPlayer recreated and sound played")
            }
        } catch (e: Exception) {
            Log.e("WhiteRoseService", "Error playing sound: ${e.message}")
        } finally {
            try {
                wakeLock?.release()
                Log.d("WhiteRoseService", "WakeLock released")
            } catch (e: Exception) {
                Log.w("WhiteRoseService", "Error releasing WakeLock: ${e.message}")
            }
            // Устанавливаем lastSoundTime равным началу следующего интервала
            val executionTime = System.currentTimeMillis() - startTime
            lastSoundTime = lastSoundTime + interval // Планируем следующий звук ровно через interval
            prefs.edit().putLong("lastSoundTime", lastSoundTime).apply()
            Log.d("WhiteRoseService", "Sound execution took $executionTime ms, new lastSoundTime: $lastSoundTime")
        }
    }

    override fun onDestroy() {
        Log.d("WhiteRoseService", "Service destroyed at ${System.currentTimeMillis()}")
        prefs.edit().putBoolean("isServiceRunning", false).putLong("lastSoundTime", lastSoundTime).apply()
        handler?.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d("WhiteRoseService", "MediaPlayer released")
        } catch (e: Exception) {
            Log.e("WhiteRoseService", "Error releasing MediaPlayer: ${e.message}")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}