package com.example.tcc_hawk.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tcc_hawk.ui.components.DatePickerField
import com.example.tcc_hawk.ui.components.TimePickerField
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager

private enum class Mobility { INDEPENDENTE, COM_AUXILIO, ACAMADO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    // ------------- Step 1: Paciente -------------
    var nome by remember { mutableStateOf("") }
    var nascimento by remember { mutableStateOf("") }

    var sexo by remember { mutableStateOf("Prefiro não informar") }
    val sexos = listOf("Masculino", "Feminino", "Prefiro não informar")
    var sexoExpanded by remember { mutableStateOf(false) }

    var peso by remember { mutableStateOf("") }
    var altura by remember { mutableStateOf("") }

    // ------------- Step 2: Rotina -------------
    var dormir by remember { mutableStateOf("22:00") }
    var acordar by remember { mutableStateOf("06:00") }
    var mobilidade by remember { mutableStateOf(Mobility.INDEPENDENTE) }
    var mobExpanded by remember { mutableStateOf(false) }

    // Risco (simples e útil)
    var historicoQuedas by remember { mutableStateOf(false) }
    var medicacaoCardiaca by remember { mutableStateOf(false) }
    var hipertensao by remember { mutableStateOf(false) }

    // ------------- Step 3: Parâmetros do sistema -------------
    var limiteTaqui by remember { mutableStateOf("110") }
    var limiteBradi by remember { mutableStateOf("50") }
    var tempoRepousoMin by remember { mutableStateOf("60") }
    var perguntarOkAntesEscalonar by remember { mutableStateOf(true) }

    // Validações leves
    val canContinue = when (step) {
        0 -> nome.isNotBlank() && nascimento.isNotBlank()
        1 -> true
        2 -> limiteTaqui.isNotBlank() && limiteBradi.isNotBlank() && tempoRepousoMin.isNotBlank()
        else -> true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configuração inicial") },
                navigationIcon = {
                    if (step > 0) {
                        IconButton(onClick = { step-- }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusManager.clearFocus() }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LinearProgressIndicator(
                progress = (step + 1) / totalSteps.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            when (step) {
                0 -> PremiumCard(
                    title = "Dados do paciente",
                    subtitle = "Essas informações ajudam a personalizar métricas e alertas.",
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) }
                ) {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        label = { Text("Nome completo") },
                        leadingIcon = { Icon(Icons.Filled.Badge, null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DatePickerField(
                        label = "Data de nascimento (dd/MM/aaaa)",
                        value = nascimento,
                        onValueChange = { nascimento = it }
                    )

                    // Sexo (opcional)
                    ExposedDropdownMenuBox(
                        expanded = sexoExpanded,
                        onExpandedChange = { sexoExpanded = !sexoExpanded }
                    ) {
                        OutlinedTextField(
                            value = sexo,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Sexo (opcional)") },
                            leadingIcon = { Icon(Icons.Filled.Wc, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexoExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sexoExpanded,
                            onDismissRequest = { sexoExpanded = false }
                        ) {
                            sexos.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = { sexo = s; sexoExpanded = false }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = peso,
                            onValueChange = { peso = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Peso (kg)") },
                            leadingIcon = { Icon(Icons.Filled.Scale, null) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = altura,
                            onValueChange = { altura = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Altura (cm)") },
                            leadingIcon = { Icon(Icons.Filled.Height, null) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                1 -> PremiumCard(
                    title = "Rotina e perfil",
                    subtitle = "Ajuda a IA a entender períodos normais de inatividade e risco.",
                    icon = { Icon(Icons.Filled.Bedtime, contentDescription = null) }
                ) {
                    TimePickerField(
                        label = "Horário que costuma dormir",
                        value = dormir,
                        onValueChange = { dormir = it }
                    )
                    TimePickerField(
                        label = "Horário que costuma acordar",
                        value = acordar,
                        onValueChange = { acordar = it }
                    )

                    // Mobilidade (dropdown)
                    ExposedDropdownMenuBox(
                        expanded = mobExpanded,
                        onExpandedChange = { mobExpanded = !mobExpanded }
                    ) {
                        OutlinedTextField(
                            value = when (mobilidade) {
                                Mobility.INDEPENDENTE -> "Independente"
                                Mobility.COM_AUXILIO -> "Com auxílio"
                                Mobility.ACAMADO -> "Acamado"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Mobilidade") },
                            leadingIcon = { Icon(Icons.Filled.AccessibilityNew, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mobExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = mobExpanded,
                            onDismissRequest = { mobExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("Independente") }, onClick = {
                                mobilidade = Mobility.INDEPENDENTE; mobExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Com auxílio") }, onClick = {
                                mobilidade = Mobility.COM_AUXILIO; mobExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Acamado") }, onClick = {
                                mobilidade = Mobility.ACAMADO; mobExpanded = false
                            })
                        }
                    }

                    HorizontalDivider()

                    ToggleRow(
                        icon = Icons.Filled.Warning,
                        title = "Histórico de quedas",
                        checked = historicoQuedas,
                        onCheckedChange = { historicoQuedas = it }
                    )
                    ToggleRow(
                        icon = Icons.Filled.Medication,
                        title = "Uso de medicação cardíaca",
                        checked = medicacaoCardiaca,
                        onCheckedChange = { medicacaoCardiaca = it }
                    )
                    ToggleRow(
                        icon = Icons.Filled.HealthAndSafety,
                        title = "Hipertensão (opcional)",
                        checked = hipertensao,
                        onCheckedChange = { hipertensao = it }
                    )
                }

                2 -> PremiumCard(
                    title = "Parâmetros do sistema",
                    subtitle = "Valores iniciais (podem ser ajustados depois em Configurações).",
                    icon = { Icon(Icons.Filled.MonitorHeart, contentDescription = null) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = limiteTaqui,
                            onValueChange = { limiteTaqui = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Taquicardia (BPM)") },
                            leadingIcon = { Icon(Icons.Filled.TrendingUp, null) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = limiteBradi,
                            onValueChange = { limiteBradi = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Bradicardia (BPM)") },
                            leadingIcon = { Icon(Icons.Filled.TrendingDown, null) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = tempoRepousoMin,
                        onValueChange = { tempoRepousoMin = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Tempo em repouso p/ alerta (min)") },
                        leadingIcon = { Icon(Icons.Filled.Timer, null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QuestionAnswer, null)
                            Text("Perguntar “Você está bem?” antes de escalar")
                        }
                        Switch(
                            checked = perguntarOkAntesEscalonar,
                            onCheckedChange = { perguntarOkAntesEscalonar = it }
                        )
                    }
                }

                else -> PremiumCard(
                    title = "Resumo",
                    subtitle = "Confira os dados antes de concluir.",
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                ) {
                    SummaryLine("Paciente", nome)
                    SummaryLine("Nascimento", nascimento)
                    SummaryLine("Sexo", sexo)
                    SummaryLine("Sono", "$dormir → $acordar")
                    SummaryLine("Mobilidade", when (mobilidade) {
                        Mobility.INDEPENDENTE -> "Independente"
                        Mobility.COM_AUXILIO -> "Com auxílio"
                        Mobility.ACAMADO -> "Acamado"
                    })
                    SummaryLine("Quedas", if (historicoQuedas) "Sim" else "Não")
                    SummaryLine("Medicação cardíaca", if (medicacaoCardiaca) "Sim" else "Não")
                    SummaryLine("Taquicardia", "$limiteTaqui BPM")
                    SummaryLine("Bradicardia", "$limiteBradi BPM")
                    SummaryLine("Repouso p/ alerta", "$tempoRepousoMin min")
                    SummaryLine("Confirmação no relógio", if (perguntarOkAntesEscalonar) "Ativa" else "Desativada")

                    Text(
                        "Obs.: esses valores são iniciais e podem ser ajustados depois.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = {
                    if (step < totalSteps - 1) step++ else onFinished()
                },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (step < totalSteps - 1) "Continuar" else "Concluir")
            }

            // ✅ PROVISÓRIO: pular onboarding para testar rápido
            TextButton(
                onClick = { onFinished() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Entrar sem cadastrar (modo teste)")
            }
        }
    }
}

/* -------------------- Componentes auxiliares -------------------- */

@Composable
private fun PremiumCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.padding(10.dp)) { icon() }
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Text(title)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}