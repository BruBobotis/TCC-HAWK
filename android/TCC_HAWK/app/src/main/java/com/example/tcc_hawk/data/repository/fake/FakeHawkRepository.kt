package com.example.tcc_hawk.data.repository.fake

import com.example.tcc_hawk.data.model.*
import com.example.tcc_hawk.data.repository.HawkRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.random.Random

class FakeHawkRepository : HawkRepository {

    private val _vitals = MutableStateFlow(
        VitalsReading(bpm = 78, steps = 1200, isMoving = true, battery = 82)
    )

    private val _alerts = MutableStateFlow(
        listOf(
            AlertEvent(
                type = AlertType.INACTIVITY,
                title = "Inatividade prolongada",
                subtitle = "Sem movimento por 60 min",
                severity = Severity.LOW,
                read = false
            )
        )
    )

    private val _reminders = MutableStateFlow(
        listOf(
            Reminder(type = ReminderType.WATER, title = "Beber água", time = "10:00", repeatText = "Semana", days = setOf(1,2,3,4,5,6,7)),
            Reminder(type = ReminderType.MEDICINE, title = "Remédio", time = "20:30", repeatText = "Seg–Sex", days = setOf(1,2,3,4,5)),
        )
    )

    private val _caregivers = MutableStateFlow(
        listOf(
            Caregiver(name="Maria Silva", relation="Filha", phone="(11) 9xxxx-xxxx", priority = 1),
            Caregiver(name="João Santos", relation="Neto", phone="(11) 9xxxx-xxxx", priority = 2),
        )
    )

    /**
     * Chame isso uma vez (ex: em ViewModel) pra simular dados “vivos”.
     */
    suspend fun startSimulation() {
        while (true) {
            val current = _vitals.value
            val moving = Random.nextBoolean()

            val bpmDelta = if (moving) Random.nextInt(0, 6) else Random.nextInt(-4, 3)
            val newBpm = (current.bpm + bpmDelta).coerceIn(48, 130)

            val stepDelta = if (moving) Random.nextInt(5, 18) else 0
            val newSteps = current.steps + stepDelta

            val newBattery = current.battery.coerceIn(0, 100)

            _vitals.value = current.copy(
                bpm = newBpm,
                steps = newSteps,
                isMoving = moving,
                battery = newBattery,
                timestampMillis = System.currentTimeMillis()
            )

            // solta um alerta ocasional pra demo
            if (Random.nextInt(0, 25) == 0) {
                val ev = AlertEvent(
                    type = AlertType.TACHY,
                    title = "Taquicardia",
                    subtitle = "BPM acima do limite por 2 min",
                    severity = Severity.MEDIUM,
                    read = false
                )
                _alerts.value = listOf(ev) + _alerts.value
            }

            delay(1500)
        }
    }

    override fun vitalsFlow(): Flow<VitalsReading> = _vitals
    override fun alertsFlow(): Flow<List<AlertEvent>> = _alerts
    override fun remindersFlow(): Flow<List<Reminder>> = _reminders
    override fun caregiversFlow(): Flow<List<Caregiver>> = _caregivers

    override suspend fun markAlertRead(id: String) {
        _alerts.value = _alerts.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    override suspend fun addReminder(reminder: Reminder) {
        _reminders.value = listOf(reminder) + _reminders.value
    }

    override suspend fun updateReminder(reminder: Reminder) {
        _reminders.value = _reminders.value.map { if (it.id == reminder.id) reminder else it }
    }

    override suspend fun deleteReminder(id: String) {
        _reminders.value = _reminders.value.filterNot { it.id == id }
    }

    override suspend fun addCaregiver(c: Caregiver) {
        val maxPriority = _caregivers.value.maxOfOrNull { it.priority } ?: 0
        _caregivers.value = _caregivers.value + c.copy(priority = maxPriority + 1)
    }

    override suspend fun deleteCaregiver(id: String) {
        _caregivers.value = normalize(_caregivers.value.filterNot { it.id == id })
    }

    override suspend fun setPrincipalCaregiver(id: String) {
        _caregivers.value = normalize(
            _caregivers.value.map { c ->
                if (c.id == id) c.copy(priority = 1) else c.copy(priority = c.priority + 1)
            }
        )
    }
    override fun sendAlert(type: String, duration: Int, message: String) {}

    override fun sendRawCommand(payload: String) {}
    private fun normalize(list: List<Caregiver>): List<Caregiver> =
        list.sortedBy { it.priority }.mapIndexed { idx, c -> c.copy(priority = idx + 1) }
}