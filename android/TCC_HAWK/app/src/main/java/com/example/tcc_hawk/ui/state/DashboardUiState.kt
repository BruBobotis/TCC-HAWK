package com.example.tcc_hawk.ui.state

import com.example.tcc_hawk.data.model.VitalsReading

data class DashboardUiState(
    val connected: Boolean = false,
    val latest: VitalsReading = VitalsReading(
        bpm = 0, steps = 0, isMoving = false, battery = 0
    )
)