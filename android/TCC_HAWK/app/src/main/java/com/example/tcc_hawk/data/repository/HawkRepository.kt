package com.example.tcc_hawk.data.repository

import com.example.tcc_hawk.data.model.AlertEvent
import com.example.tcc_hawk.data.model.Caregiver
import com.example.tcc_hawk.data.model.Reminder
import com.example.tcc_hawk.data.model.VitalsReading
import kotlinx.coroutines.flow.Flow

interface HawkRepository {
    fun vitalsFlow(): Flow<VitalsReading>
    fun alertsFlow(): Flow<List<AlertEvent>>
    fun remindersFlow(): Flow<List<Reminder>>
    fun caregiversFlow(): Flow<List<Caregiver>>
    fun sendAlert(type: String, duration: Int, message: String)
    fun sendRawCommand(payload: String)

    suspend fun markAlertRead(id: String)

    suspend fun addReminder(reminder: Reminder)
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(id: String)

    suspend fun addCaregiver(c: Caregiver)
    suspend fun deleteCaregiver(id: String)
    suspend fun setPrincipalCaregiver(id: String)
}