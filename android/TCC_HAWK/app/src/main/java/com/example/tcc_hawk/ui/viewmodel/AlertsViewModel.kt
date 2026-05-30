package com.example.tcc_hawk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tcc_hawk.data.model.AlertEvent
import com.example.tcc_hawk.data.repository.HawkRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.tcc_hawk.data.model.Reminder
import com.example.tcc_hawk.data.model.ReminderType
import com.example.tcc_hawk.data.alarms.AlarmScheduler
import android.content.Context
import java.text.Normalizer
data class AlertsUiState(
    val events: List<AlertEvent> = emptyList(),
    val unreadCount: Int = 0
)

class AlertsViewModel(private val repo: HawkRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.alertsFlow().collect { list ->
                _uiState.value = AlertsUiState(
                    events = list,
                    unreadCount = list.count { !it.read }
                )
            }
        }
    }

    private fun sanitizeForEsp(input: String): String {
        // remove acentos
        val noAccent = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        // remove emojis/símbolos não ASCII
        return noAccent.replace("[^\\x20-\\x7E]".toRegex(), "")
            .trim()
    }

    fun sendReminderToWatch(reminder: Reminder) {
        val type = when (reminder.type) {
            ReminderType.FALL -> "FALL"
            ReminderType.WATER -> "WATER"
            ReminderType.MEDICINE -> "MEDICINE"
            ReminderType.SLEEP -> "SLEEP"
            ReminderType.GENERAL -> "GENERAL"
        }

        val safeTitle = sanitizeForEsp(reminder.title)
        val payload = "ALERT|$type|8000|${reminder.time}|$safeTitle"
        repo.sendRawCommand(payload)
    }

    private fun toEspSafe(text: String): String {
        val noDiacritics = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        // remove tudo que não for ASCII imprimível (tira emoji)
        return noDiacritics.replace("[^\\x20-\\x7E]".toRegex(), "").trim()
    }
    fun scheduleOnPhone(context: Context, reminder: Reminder) {
        AlarmScheduler(context).schedule(reminder)
    }

    fun markRead(id: String) {
        viewModelScope.launch { repo.markAlertRead(id) }
    }

    companion object {
        fun factory(repo: HawkRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AlertsViewModel(repo) as T
            }
        }
    }
}