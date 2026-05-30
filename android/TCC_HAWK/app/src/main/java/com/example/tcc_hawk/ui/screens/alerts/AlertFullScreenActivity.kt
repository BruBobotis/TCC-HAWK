package com.example.tcc_hawk.ui.screens.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class AlertFullScreenActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private fun isCriticalAlert(type: String): Boolean {
        return type == "FALL" ||
                type == "BPM_HIGH" ||
                type == "BPM_LOW" ||
                type == "SPO2_LOW" ||
                type == "SPO2_HIGH"
    }

    private fun startAlertEffects() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(applicationContext, alarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            prepare()
            start()
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val pattern = longArrayOf(
            0,
            800,
            300,
            800,
            300,
            1200
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlertEffects() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val type = intent.getStringExtra("type") ?: "GENERAL"
        val message = intent.getStringExtra("message") ?: ""

        if (isCriticalAlert(type)) {
            startAlertEffects()
        }

        setContent {
            AlertFullScreen(
                type = type,
                messageOverride = message,
                onDismiss = { finish() }
            )
        }
    }

    override fun onDestroy() {
        stopAlertEffects()
        super.onDestroy()
    }
}