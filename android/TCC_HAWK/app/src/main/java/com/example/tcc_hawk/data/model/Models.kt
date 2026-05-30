package com.example.tcc_hawk.data.model

import java.util.UUID

data class PatientProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val birthDate: String,     // dd/MM/yyyy
    val sex: String,
    val weightKg: Int?,
    val heightCm: Int?,
    val sleepTime: String,     // HH:mm
    val wakeTime: String,      // HH:mm
    val mobility: String,
    val historyFalls: Boolean,
    val cardiacMeds: Boolean,
    val hypertension: Boolean,
    val bradycardiaLimit: Int? = null,
    val tachycardiaLimit: Int? = null,
    val spo2LowLimit: Int? = null,
    val spo2HighLimit: Int? = null,
    val restAlertMin: Int,
    val askOkBeforeEscalate: Boolean,
)

data class VitalsReading(
    val bpm: Int = 0,
    val spo2: Int = 0,
    val steps: Int = 0,
    val battery: Int = 0,

    val watchHour: String = "",
    val watchDate: String = "",
    val ax: Float? = null,
    val ay: Float? = null,
    val az: Float? = null,

    val fallDetected: Boolean = false,
    val fallsCount: Int = 0,
    val isMoving: Boolean = false,
    val mpuOk: Boolean = false,
    val fingerDetected: Boolean = false,

    val timestampMillis: Long = 0L
)

data class VitalsPayload(
    val passos: Int,
    val bpm: Int,
    val spo2: Float,
    val quedasTotal: Int,
    val quedaAtual: Boolean,
    val timestamp: String
)

data class VitalLimits(
    val bpmLow: Int = 60,
    val bpmHigh: Int = 130,
    val spo2Low: Int = 80,
    val spo2High: Int = 100
)

enum class AlertType { FALL, TACHY, BRADY, INACTIVITY, DISCONNECTED, LOW_BATTERY }
enum class Severity { LOW, MEDIUM, HIGH }

data class AlertEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: AlertType,
    val title: String,
    val subtitle: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val severity: Severity,
    val read: Boolean = false
)

enum class ReminderType { FALL, MEDICINE, WATER, SLEEP, GENERAL }

enum class VitalAlertType {
    BPM_LOW,
    BPM_HIGH,
    SPO2_LOW,
    SPO2_HIGH
}

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val type: ReminderType,
    val title: String,
    val time: String,           // HH:mm
    val repeatText: String,     // Diário/Seg–Sex/Semana/Personalizado
    val days: Set<Int>,         // 1..7
    val enabled: Boolean = true
)

data class Caregiver(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relation: String,
    val phone: String,
    val priority: Int,          // 1 principal
    val active: Boolean = true
)