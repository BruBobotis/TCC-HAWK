package com.example.tcc_hawk.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tcc_hawk.data.repository.HawkRepository
import com.example.tcc_hawk.ui.state.DashboardUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.tcc_hawk.data.repository.ble.BleHawkRepository
import com.example.tcc_hawk.data.ble.HawkBleUuids
import android.content.ContentValues
import kotlin.math.roundToInt
import com.example.tcc_hawk.data.model.VitalsReading
data class ChartPoint(
    val label: String,
    val value: Int
)

private fun buildDailyBpmSeries(samples: List<VitalsReading>): List<ChartPoint> {
    val now = System.currentTimeMillis()
    val start = now - 24L * 60L * 60L * 1000L

    val filtered = samples.filter {
        it.timestampMillis in start..now && it.bpm > 0
    }

    // 6 blocos de 4h: 00h, 04h, 08h, 12h, 16h, 20h, 24h
    val buckets = listOf(
        0L to 4L,
        4L to 8L,
        8L to 12L,
        12L to 16L,
        16L to 20L,
        20L to 24L
    )

    return buckets.map { (fromHour, toHour) ->
        val from = start + fromHour * 60L * 60L * 1000L
        val to = start + toHour * 60L * 60L * 1000L

        val values = filtered.filter { it.timestampMillis in from until to }.map { it.bpm }
        val avg = if (values.isNotEmpty()) values.average().roundToInt() else 0

        ChartPoint(label = "${toHour.toString().padStart(2, '0')}h", value = avg)
    }
}

private fun computeMovementAndRest(samples: List<VitalsReading>): Pair<Long, Long> {
    if (samples.size < 2) return 0L to 0L

    var movingMillis = 0L
    var restingMillis = 0L

    for (i in 1 until samples.size) {
        val prev = samples[i - 1]
        val curr = samples[i]

        val delta = (curr.timestampMillis - prev.timestampMillis)
            .coerceIn(0L, 5L * 60L * 1000L) // no máximo 5 min por salto

        if (prev.isMoving) {
            movingMillis += delta
        } else {
            restingMillis += delta
        }
    }

    return movingMillis to restingMillis
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes.toString().padStart(2, '0')}min"
}

private val _movingText = MutableStateFlow("0h 00min")
val movingText: StateFlow<String> = _movingText.asStateFlow()

private val _restingText = MutableStateFlow("0h 00min")
val restingText: StateFlow<String> = _restingText.asStateFlow()

class DashboardViewModel(private val repo: HawkRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    private val _movingText = MutableStateFlow("0h 00min")
    val movingText: StateFlow<String> = _movingText.asStateFlow()

    private val _restingText = MutableStateFlow("0h 00min")
    val restingText: StateFlow<String> = _restingText.asStateFlow()

    private var lastSampleMillis = 0L
    private var movingMillis = 0L
    private var restingMillis = 0L
    private var lastWasMoving = false
    private val _chartPoints = MutableStateFlow<List<ChartPoint>>(emptyList())
    val chartPoints: StateFlow<List<ChartPoint>> = _chartPoints.asStateFlow()
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes.toString().padStart(2, '0')}min"
    }
    init {
        viewModelScope.launch {
            repo.vitalsFlow().collect { vitals ->
                Log.d("VM_DEBUG", "Recebido no ViewModel: $vitals")

                val now = vitals.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

                if (lastSampleMillis > 0L) {
                    val delta = (now - lastSampleMillis).coerceIn(0L, 10_000L)

                    if (lastWasMoving) {
                        movingMillis += delta
                    } else {
                        restingMillis += delta
                    }

                    _movingText.value = formatDuration(movingMillis)
                    _restingText.value = formatDuration(restingMillis)
                }

                lastSampleMillis = now
                lastWasMoving = vitals.isMoving

                _uiState.value = _uiState.value.copy(
                    connected = vitals.timestampMillis > 0L,
                    latest = vitals
                )
            }
        }
    }

    fun sendWaterAlert() {
        repo.sendAlert(
            type = "WATER",
            duration = 5000,
            message = "Hora de beber agua"
        )
    }

    fun sendMedicineAlert() {
        repo.sendAlert(
            type = "MEDICINE",
            duration = 8000,
            message = "Tomar remedio"
        )
    }

    /** Chame isso APÓS o usuário conceder BLUETOOTH_SCAN/CONNECT */
    fun startBle() {
        try {
            Log.d("HAWK_BLE", "startBle() chamado")
            (repo as? BleHawkRepository)?.startScan()
        } catch (e: SecurityException) {
            Log.e("HAWK_BLE", "Sem permissão BLE ainda: ${e.message}")
        }
    }

    companion object {
        fun factory(repo: HawkRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(repo) as T
            }
        }
    }
}