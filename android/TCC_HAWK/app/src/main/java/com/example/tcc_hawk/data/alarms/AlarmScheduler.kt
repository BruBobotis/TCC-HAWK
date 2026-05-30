package com.example.tcc_hawk.data.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar
import android.util.Log
import com.example.tcc_hawk.data.model.Reminder

class AlarmScheduler(private val context: Context) {

    fun Reminder.toTriggerAtMillis(): Long {
        val parts = time.trim().split(":")
        val hh = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val ss = parts.getOrNull(2)?.toIntOrNull() ?: 0  // ✅ segundos opcionais

        val cal = Calendar.getInstance().apply {
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hh)
            set(Calendar.MINUTE, mm)
            set(Calendar.SECOND, ss) // ✅ respeita segundos
        }

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun schedule(reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerAtMillis = reminder.toTriggerAtMillis()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("type", reminder.type.name)
            putExtra("title", reminder.title)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val canExact =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pi
            )
        } else {
            // ✅ fallback funciona, mas avisa no log (e você pode guiar o user depois)
            Log.w("ALARM", "Sem permissão de exact alarm. Usando setAndAllowWhileIdle (menos preciso).")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }
}