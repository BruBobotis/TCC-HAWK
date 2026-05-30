package com.example.tcc_hawk.data.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tcc_hawk.ui.screens.alerts.AlertFullScreenActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.tcc_hawk.R
import android.util.Log
import android.app.NotificationManager
import android.os.Build
import android.app.NotificationChannel
import android.app.Notification
import android.media.RingtoneManager
import android.media.AudioAttributes


private fun ensureFallChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fall_critical_channel"

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1500)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId,
            "Queda crítica",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas críticos de queda"
            enableVibration(true)
            setVibrationPattern(vibrationPattern)
            setSound(alarmUri, audioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
    }
}

private fun ensureChannel(context: Context, channelId: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Alertas TCC HAWK"
        val desc = "Notificações de alarmes (água/remédio/geral)"
        val importance = android.app.NotificationManager.IMPORTANCE_HIGH

        val channel = android.app.NotificationChannel(channelId, name, importance).apply {
            description = desc
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(channel)
    }
}

private fun isAppInForeground(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val pkg = context.packageName
    val procs = am.runningAppProcesses ?: return false
    return procs.any { it.processName == pkg && it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
}

private fun showFallNotification(context: Context, message: String) {
    ensureFallChannel(context)

    val fullScreenIntent = Intent(context, AlertFullScreenActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("type", "FALL")
        putExtra("message", message)
    }

    val fullScreenPendingIntent = PendingIntent.getActivity(
        context,
        9999,
        fullScreenIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notif = NotificationCompat.Builder(context, "fall_critical_channel")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("ALERTA CRÍTICO")
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setOngoing(true)
        .setAutoCancel(false)
        .build()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(9999, notif)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: "GENERAL"
        val titleExtra = intent.getStringExtra("title")?.takeIf { it.isNotBlank() }
        val channelId = if (type == "FALL") {
            "fall_critical_channel_v2"
        } else {
            "tcc_hawk_alerts"
        }
        val message: String = when (type) {
            "FALL" -> titleExtra ?: "🚨 Possível Queda"
            "WATER" -> titleExtra ?: "🥤 Hora de beber água"
            "MEDICINE" -> titleExtra ?: "💊 Tomar remédio"
            "SLEEP" -> titleExtra ?: "😴 Hora de dormir"
            else -> titleExtra ?: "⚠️ Alerta"
        }
        val color = when (type) {
            "WATER" -> 0xFF2196F3.toInt()    // azul
            "MEDICINE" -> 0xFF4CAF50.toInt() // verde
            "SLEEP" -> 0xFF7E57C2.toInt()    // roxo
            else -> 0xFFFFC107.toInt()       // amarelo
        }
        ensureChannel(context, channelId)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Alarmes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas em tela cheia"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val fullScreenIntent = Intent(context, AlertFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", type)
            putExtra("message", message)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, 1001, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notificação com fullScreenIntent
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ALERTA!")
            .setContentText(message as CharSequence?)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPending, true)
            .setAutoCancel(true)
            .setColor(color)
            .build()

        val canPost =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        if (canPost) {
            nm.notify(1001, notif)
        } else {
            Log.w("ALARM", "POST_NOTIFICATIONS não concedida, não exibiu notificação")
        }
        if (isAppInForeground(context)) {
            context.startActivity(fullScreenIntent)
        }
    }
}