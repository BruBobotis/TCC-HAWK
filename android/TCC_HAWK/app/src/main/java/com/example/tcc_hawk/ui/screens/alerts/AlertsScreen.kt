package com.example.tcc_hawk.ui.screens.alerts

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.tcc_hawk.data.model.AlertEvent
import com.example.tcc_hawk.data.model.AlertType
import com.example.tcc_hawk.data.model.Reminder
import com.example.tcc_hawk.data.model.ReminderType
import com.example.tcc_hawk.data.model.Severity
import com.example.tcc_hawk.ui.viewmodel.AlertsViewModel
import java.text.Normalizer
import java.util.Calendar
import java.util.UUID



private enum class RepeatPreset { DAILY, WEEKDAYS, WEEK, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: AlertsViewModel, navController: NavController) {
    val ui by vm.uiState.collectAsState()
    val events = ui.events
    val badge = ui.unreadCount
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableIntStateOf(0) } // 0=Eventos, 1=Lembretes
    val tabs = listOf("Eventos", "Lembretes")

    // ✅ Lembretes continuam mock por enquanto (depois ligamos no repo.remindersFlow)
    var reminders by remember {
        mutableStateOf(
            listOf(
                Reminder(
                    type = ReminderType.WATER,
                    title = "Beber água",
                    time = "10:00",
                    repeatText = "Semana",
                    days = setOf(1, 2, 3, 4, 5, 6, 7),
                    enabled = true
                ),
                Reminder(
                    type = ReminderType.MEDICINE,
                    title = "Remédio",
                    time = "20:30",
                    repeatText = "Seg–Sex",
                    days = setOf(1, 2, 3, 4, 5),
                    enabled = true
                ),
            )
        )
    }

    var showCreate by remember { mutableStateOf(false) }
    var prefillType by remember { mutableStateOf(ReminderType.GENERAL) }
    var prefillTitle by remember { mutableStateOf("Lembrete") }

    fun openCreate(type: ReminderType, title: String) {
        prefillType = type
        prefillTitle = title
        showCreate = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Alertas") }
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(onClick = { openCreate(ReminderType.GENERAL, "Lembrete") }) {
                    Icon(Icons.Filled.Add, null)
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        text = {
                            if (idx == 0 && badge > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(label)
                                    Badge { Text(badge.toString()) }
                                }
                            } else {
                                Text(label)
                            }
                        }
                    )
                }
            }

            when (tab) {
                0 -> EventsTab(
                    events = events,
                    onMarkRead = { id -> vm.markRead(id) }
                )

                1 -> RemindersTab(
                    reminders = reminders,
                    onQuickWater = { openCreate(ReminderType.WATER, "Beber água") },
                    onToggle = { id, enabled ->
                        reminders = reminders.map { if (it.id == id) it.copy(enabled = enabled) else it }
                        // TODO BLE: enviar toggle
                    },
                    onDelete = { id ->
                        reminders = reminders.filterNot { it.id == id }
                        // TODO BLE: deletar no relógio
                    },
                    onAdd = { openCreate(ReminderType.GENERAL, "Lembrete") }
                )
            }
        }

        if (showCreate) {
            CreateReminderDialog(
                initialType = prefillType,
                initialTitle = prefillTitle,
                onDismiss = { showCreate = false },
                vm = vm,
                onSave = { newReminder ->
                    reminders = listOf(newReminder) + reminders
                    showCreate = false
                    vm.sendReminderToWatch(newReminder)
                    vm.scheduleOnPhone(context, newReminder)
                }
            )
        }
    }
}

/* -------------------- TAB EVENTOS -------------------- */

@Composable
private fun EventsTab(
    events: List<AlertEvent>,
    onMarkRead: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {

        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AssistChip(onClick = { }, label = { Text("Todos") }, leadingIcon = { Icon(Icons.Filled.List, null) })
            AssistChip(onClick = { }, label = { Text("Críticos") }, leadingIcon = { Icon(Icons.Filled.Report, null) })
            AssistChip(onClick = { }, label = { Text("Hoje") }, leadingIcon = { Icon(Icons.Filled.Today, null) })
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = { it.id }) { e ->
                EventCard(e = e, onMarkRead = onMarkRead)
            }
        }
    }
}

@Composable
private fun EventCard(
    e: AlertEvent,
    onMarkRead: (String) -> Unit
) {
    val (chipLabel, chipColor) = when (e.severity) {
        Severity.HIGH -> "Alto" to MaterialTheme.colorScheme.error
        Severity.MEDIUM -> "Médio" to MaterialTheme.colorScheme.tertiary
        Severity.LOW -> "Baixo" to MaterialTheme.colorScheme.secondary
    }

    val icon = iconForAlert(e.type)

    Card(shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.padding(10.dp)) { Icon(icon, null) }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.title, fontWeight = FontWeight.SemiBold)
                    AssistChip(
                        onClick = { },
                        label = { Text(chipLabel) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = chipColor.copy(alpha = 0.12f),
                            labelColor = chipColor
                        )
                    )
                }
                Text(e.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // timestamp bonito depois (Etapa 5.2)
                    Text(
                        if (e.read) "Lido" else "Não lido",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = { onMarkRead(e.id) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Marcar como lido")
                    }
                }
            }
        }
    }
}

private fun iconForAlert(type: AlertType): ImageVector = when (type) {
    AlertType.FALL -> Icons.Filled.Warning
    AlertType.TACHY -> Icons.Filled.MonitorHeart
    AlertType.BRADY -> Icons.Filled.MonitorHeart
    AlertType.INACTIVITY -> Icons.Filled.Timer
    AlertType.DISCONNECTED -> Icons.Filled.BluetoothDisabled
    AlertType.LOW_BATTERY -> Icons.Filled.BatteryAlert
}

/* -------------------- TAB LEMBRETES -------------------- */

@Composable
private fun RemindersTab(
    reminders: List<Reminder>,
    onQuickWater: () -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
    onAdd: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {

        Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Lembretes no relógio", fontWeight = FontWeight.SemiBold)
                Text(
                    "Crie alarmes de remédio, água e rotina. O relógio alerta mesmo sem internet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onQuickWater) {
                        Icon(Icons.Filled.LocalDrink, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Beber água")
                    }
                    OutlinedButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Novo")
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(reminders, key = { it.id }) { r ->
                ReminderCard(reminder = r, onToggle = onToggle, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onDelete: (id: String) -> Unit
) {
    val (icon, label) = reminderIcon(reminder.type)

    Card(shape = MaterialTheme.shapes.extraLarge) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.padding(10.dp)) { Icon(icon, null) }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(reminder.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${reminder.time} • ${reminder.repeatText} • $label",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(checked = reminder.enabled, onCheckedChange = { onToggle(reminder.id, it) })
            IconButton(onClick = { onDelete(reminder.id) }) { Icon(Icons.Filled.Delete, null) }
        }
    }
}

private fun reminderIcon(type: ReminderType): Pair<ImageVector, String> = when (type) {
    ReminderType.FALL -> Icons.Filled.Notifications to "Geral"
    ReminderType.MEDICINE -> Icons.Filled.Medication to "Remédio"
    ReminderType.WATER -> Icons.Filled.LocalDrink to "Água"
    ReminderType.SLEEP -> Icons.Filled.Bedtime to "Sono"
    ReminderType.GENERAL -> Icons.Filled.Notifications to "Geral"
}

/* -------------------- Dialog Criar (layout premium) -------------------- */

@Composable
private fun CreateReminderDialog(
    initialType: ReminderType,
    vm: AlertsViewModel,
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val context = LocalContext.current
    val cal = remember { Calendar.getInstance() }
    fun prettyTextFor(type: ReminderType): String = when (type) {
        ReminderType.FALL -> "🚨 Possível Queda"
        ReminderType.WATER -> "🥤 Hora de beber água"
        ReminderType.MEDICINE -> "💊 Tomar remédio"
        ReminderType.SLEEP -> "😴 Hora de dormir"
        ReminderType.GENERAL -> "⚠️ Alerta"
    }
    var type by remember { mutableStateOf(initialType) }
    var title by remember { mutableStateOf(initialTitle) }
    var hour by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }

    var preset by remember { mutableStateOf(RepeatPreset.WEEK) }
    var customDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }

    val timeStr = String.format("%02d:%02d:%02d", hour, minute, 0) // ✅ sempre com :00

    // perto do dialog (antes do TextField), algo assim:
    val defaultDesc = when (type) {
        ReminderType.FALL -> "🚨 Possível Queda"
        ReminderType.WATER -> "🥤 Hora de beber água"
        ReminderType.MEDICINE -> "💊 Tomar remédio"
        ReminderType.SLEEP -> "😴 Hora de dormir"
        ReminderType.GENERAL -> ""
    }

    LaunchedEffect(type) {
        // sempre que mudar o tipo:
        if (type != ReminderType.GENERAL) {
            title = defaultDesc
        } else {
            if (title == "🥤 Hora de beber água" ||
                title == "💊 Tomar remédio" ||
                title == "😴 Hora de dormir"
            ) title = ""
        }
    }

    TextField(
        value = title,
        onValueChange = { if (type == ReminderType.GENERAL) title = it },
        label = { Text("Descrição") },
        enabled = (type == ReminderType.GENERAL),  // trava
        readOnly = (type != ReminderType.GENERAL)
    )

    val timeDialog = remember {
        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo lembrete") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tipo", fontWeight = FontWeight.SemiBold)
                ReminderTypeGrid(selected = type, onSelect = { type = it })

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    leadingIcon = { Icon(reminderIcon(type).first, null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Horário") },
                    leadingIcon = { Icon(Icons.Filled.AccessTime, null) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { timeDialog.show() }) { Icon(Icons.Filled.Schedule, null) }
                    }
                )

                Text("Repetição", fontWeight = FontWeight.SemiBold)
                RepeatDropdown(value = preset, onChange = { preset = it })

                if (preset == RepeatPreset.CUSTOM) {
                    DaysPicker(selected = customDays, onToggle = { day ->
                        customDays = if (customDays.contains(day)) customDays - day else customDays + day
                    })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (repeatText, days) = when (preset) {
                        RepeatPreset.DAILY -> "Diário" to setOf(1, 2, 3, 4, 5, 6, 7)
                        RepeatPreset.WEEKDAYS -> "Seg–Sex" to setOf(1, 2, 3, 4, 5)
                        RepeatPreset.WEEK -> "Semana" to setOf(1, 2, 3, 4, 5, 6, 7)
                        RepeatPreset.CUSTOM -> "Personalizado" to customDays
                    }
                    val titleFinal = title.trim().ifBlank { prettyTextFor(type) }
                    val reminder = Reminder(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        title = titleFinal,
                        time = timeStr,
                        repeatText = repeatText,
                        days = days,
                        enabled = true
                    )
                    vm.sendReminderToWatch(reminder)      // manda pro relógio (BLE)
                    vm.scheduleOnPhone(context, reminder) // agenda no celular (AlarmManager)
                    onSave(reminder)
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatDropdown(value: RepeatPreset, onChange: (RepeatPreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val label = when (value) {
        RepeatPreset.DAILY -> "Diário"
        RepeatPreset.WEEKDAYS -> "Seg–Sex"
        RepeatPreset.WEEK -> "Semana"
        RepeatPreset.CUSTOM -> "Personalizado"
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label,
            onValueChange = { },
            readOnly = true,
            label = { Text("Repetição") },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Diário") }, onClick = { onChange(RepeatPreset.DAILY); expanded = false })
            DropdownMenuItem(text = { Text("Seg–Sex") }, onClick = { onChange(RepeatPreset.WEEKDAYS); expanded = false })
            DropdownMenuItem(text = { Text("Semana") }, onClick = { onChange(RepeatPreset.WEEK); expanded = false })
            DropdownMenuItem(text = { Text("Personalizado") }, onClick = { onChange(RepeatPreset.CUSTOM); expanded = false })
        }
    }
}

@Composable
private fun ReminderTypeGrid(selected: ReminderType, onSelect: (ReminderType) -> Unit) {
    val opts = listOf(ReminderType.MEDICINE, ReminderType.WATER, ReminderType.SLEEP, ReminderType.GENERAL)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypeTile(type = opts[0], selected = selected == opts[0], onClick = { onSelect(opts[0]) }, modifier = Modifier.weight(1f))
            TypeTile(type = opts[1], selected = selected == opts[1], onClick = { onSelect(opts[1]) }, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypeTile(type = opts[2], selected = selected == opts[2], onClick = { onSelect(opts[2]) }, modifier = Modifier.weight(1f))
            TypeTile(type = opts[3], selected = selected == opts[3], onClick = { onSelect(opts[3]) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TypeTile(
    type: ReminderType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (ic, label) = reminderIcon(type)
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        border = border,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(ic, null)
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DaysPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val labels = listOf(
        1 to "Seg", 2 to "Ter", 3 to "Qua", 4 to "Qui", 5 to "Sex", 6 to "Sáb", 7 to "Dom"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Dias da semana", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels) { (day, txt) ->
                FilterChip(
                    selected = selected.contains(day),
                    onClick = { onToggle(day) },
                    label = { Text(txt) }
                )
            }
        }
    }
}