package com.example.tcc_hawk.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.tcc_hawk.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val ui by vm.uiState.collectAsState()
    val heartRateNow = ui.latest.bpm
    val stepsToday = ui.latest.steps
    val movingText by vm.movingText.collectAsState()
    val restingText by vm.restingText.collectAsState()
    val spo2Now = ui.latest.spo2

    val spo2Status = when {
        spo2Now <= 0 -> "Sem leitura"
        spo2Now < 92 -> "Oxigenação baixa"
        spo2Now < 95 -> "Atenção"
        else -> "Saturação normal"
    }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        show = true
    }
    // Série com horário (24h)
    val series = remember {
        listOf(
            ChartPoint(0,  82f),
            ChartPoint(4,  76f),
            ChartPoint(8,  60f),
            ChartPoint(12, 55f),
            ChartPoint(16, 63f),
            ChartPoint(20, 70f),
            ChartPoint(24, 92f)
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Olá, Bruno!", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Resumo de hoje",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 8 }
                ) { TimeRangeChips() }
            }

            item {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 10 }
                ) {
                    MetricsGrid(
                        heartRateNow = heartRateNow,
                        stepsToday = stepsToday,
                        moveTime = movingText,
                        restTime = restingText,
                        spo2Now = spo2Now,
                        spo2Status = spo2Status,
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 12 }
                ) {
                    HealthReportCard(series = series)
                }
            }

            // ✅ Agora: Monitor abaixo do gráfico (card inteiro)
            item {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 14 }
                ) {
                    ActivityCard(
                        restTime = restingText,
                        moveTime = movingText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AccelCard(latest = ui.latest)

            }

            // ✅ Agora: Status abaixo do monitor (card inteiro)
            item {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 16 }
                ) {
                    WatchStatusCard(
                        connected = ui.latest.timestampMillis > 0L, // ou use um flag real se tiver
                        battery = ui.latest.battery,
                        lastRxMillis = ui.latest.timestampMillis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/* -------------------- Chips -------------------- */

@Composable
private fun TimeRangeChips(
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit = {}
) {
    var selected by remember { mutableIntStateOf(0) } // 0=Hoje, 1=7d, 2=30d
    val labels = listOf("Hoje", "7 dias", "30 dias")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        labels.forEachIndexed { idx, label ->
            FilterChip(
                selected = selected == idx,
                onClick = { selected = idx; onSelected(idx) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/* -------------------- Cards topo (sem SpO2) -------------------- */
@Composable
private fun MetricsGrid(
    heartRateNow: Int,
    stepsToday: Int,
    spo2Now: Int,
    spo2Status: String,
    moveTime: String,
    restTime: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Frequência Cardíaca",
                big = "$heartRateNow BPM",
                small = "Ritmo normal",
                icon = Icons.Filled.Favorite,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Passos",
                big = stepsToday.toString(),
                small = "Hoje",
                icon = Icons.Filled.DirectionsWalk,
                modifier = Modifier.weight(1f)
            )
        }

        Spo2WideCard(
            spo2Now = spo2Now,
            spo2Status = spo2Status,
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Tempo em movimento",
                big = moveTime,
                small = "Atividade do dia",
                icon = Icons.Filled.DirectionsRun,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Tempo em repouso",
                big = restTime,
                small = "Inatividade",
                icon = Icons.Filled.SelfImprovement,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
@Composable
private fun CleanCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
@Composable
private fun MetricCard(
    title: String,
    big: String,
    small: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    CleanCard(
        modifier = modifier.heightIn(min = 118.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = icon)

            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            big,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            small,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
private fun Spo2WideCard(
    spo2Now: Int,
    spo2Status: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = 104.dp)
            .animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🫁",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Saturação de Oxigênio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (spo2Now > 0) "$spo2Now%" else "--%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        spo2Now <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                        spo2Now < 92 -> MaterialTheme.colorScheme.error
                        spo2Now < 95 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Text(
                    text = spo2Status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = when {
                    spo2Now <= 0 -> Icons.Filled.SensorsOff
                    spo2Now < 92 -> Icons.Filled.Warning
                    else -> Icons.Filled.CheckCircle
                },
                contentDescription = null,
                tint = when {
                    spo2Now <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    spo2Now < 92 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/* -------------------- Gráfico com horário + tooltip no toque -------------------- */

private data class ChartPoint(val hour: Int, val value: Float)

@Composable
private fun HealthReportCard(series: List<ChartPoint>) {
    CleanCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Filled.Timeline)

            Column {
                Text(
                    "Relatório Diário de Saúde",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Frequência cardíaca nas últimas 24 horas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        InteractiveLineChart(
            series = series,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        )
    }
}

@Composable
private fun InteractiveLineChart(series: List<ChartPoint>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val line = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipText = MaterialTheme.colorScheme.onSurface

    var selectedIndex by remember { mutableIntStateOf(-1) }

    val minH = series.minOfOrNull { it.hour } ?: 0
    val maxH = series.maxOfOrNull { it.hour } ?: 24
    val minV = series.minOfOrNull { it.value } ?: 0f
    val maxV = series.maxOfOrNull { it.value } ?: 1f
    val spanV = max(0.0001f, maxV - minV)
    val spanH = max(1, maxH - minH)

    fun normV(v: Float) = (v - minV) / spanV
    fun xForHour(width: Float, hour: Int): Float =
        ((hour - minH).toFloat() / spanH.toFloat()) * width

    Box(modifier = modifier) {

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(series) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue

                            val widthF: Float = size.width.toFloat()
                            val x: Float = pos.x.coerceIn(0f, widthF)

                            var best = 0
                            var bestDist = Float.MAX_VALUE
                            series.forEachIndexed { idx, p ->
                                val px = xForHour(widthF, p.hour)
                                val d = abs(px - x)
                                if (d < bestDist) {
                                    bestDist = d
                                    best = idx
                                }
                            }
                            selectedIndex = best
                        }
                    }
                }
        ) {
            // grid horizontal
            val rows = 3
            for (i in 1..rows) {
                val y = size.height * i / (rows + 1)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }

            // ticks verticais (00/06/12/18/24)
            val tickHours = listOf(0, 6, 12, 18, 24)
            tickHours.forEach { h ->
                val x = xForHour(size.width, h)
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }

            // linha
            val path = Path()
            series.forEachIndexed { idx, p ->
                val x = xForHour(size.width, p.hour)
                val y = size.height * (1f - normV(p.value)).coerceIn(0f, 1f)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 7f))

            // ponto selecionado
            if (selectedIndex in series.indices) {
                val p = series[selectedIndex]
                val x = xForHour(size.width, p.hour)
                val y = size.height * (1f - normV(p.value)).coerceIn(0f, 1f)

                drawCircle(color = line, radius = 10f, center = Offset(x, y))
                drawCircle(color = tooltipBg, radius = 5f, center = Offset(x, y))
            }
        }

        // Labels de horário (em baixo)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00h", color = text, style = MaterialTheme.typography.labelSmall)
            Text("06h", color = text, style = MaterialTheme.typography.labelSmall)
            Text("12h", color = text, style = MaterialTheme.typography.labelSmall)
            Text("18h", color = text, style = MaterialTheme.typography.labelSmall)
            Text("24h", color = text, style = MaterialTheme.typography.labelSmall)
        }

        // Tooltip
        if (selectedIndex in series.indices) {
            val p = series[selectedIndex]
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp,
                color = tooltipBg
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Timeline, null, tint = line)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${p.hour.toString().padStart(2, '0')}h  •  ${p.value.toInt()} BPM",
                        color = tooltipText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/* -------------------- Atividade simplificada (Movimento/Repouso) -------------------- */

@Composable
private fun ActivityCard(
    restTime: String,
    moveTime: String,
    modifier: Modifier = Modifier
) {
    CleanCard(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Filled.DirectionsRun)

            Column {
                Text(
                    "Monitor de Atividade",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Movimento vs repouso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        SimpleRow("Em movimento", moveTime, Icons.Filled.DirectionsRun)
        SimpleRow("Em repouso", restTime, Icons.Filled.SelfImprovement)
    }
}

@Composable
private fun SimpleRow(label: String, value: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Box(Modifier.padding(7.dp)) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
/* -------------------- Status do relógio -------------------- */

@Composable
private fun WatchStatusCard(
    connected: Boolean,
    battery: Int,
    lastRxMillis: Long,
    modifier: Modifier = Modifier
) {
    val lastSyncText = remember(lastRxMillis) {
        if (lastRxMillis <= 0L) "—"
        else {
            val deltaSec = ((System.currentTimeMillis() - lastRxMillis) / 1000).toInt()
            when {
                deltaSec < 10 -> "agora"
                deltaSec < 60 -> "há ${deltaSec}s"
                else -> "há ${deltaSec / 60} min"
            }
        }
    }

    CleanCard(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = if (connected) Icons.Filled.BluetoothConnected else Icons.Filled.BluetoothDisabled
            )

            Column {
                Text(
                    "Status do Relógio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Conectividade",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        SimpleRow(
            "Conexão",
            if (connected) "Conectado" else "Desconectado",
            if (connected) Icons.Filled.BluetoothConnected else Icons.Filled.BluetoothDisabled
        )

        SimpleRow(
            "Último dado",
            lastSyncText,
            Icons.Filled.Sync
        )
    }
}
@Composable
private fun AccelCard(latest: com.example.tcc_hawk.data.model.VitalsReading) {
    val ax = latest.ax
    val ay = latest.ay
    val az = latest.az
    val fall = latest.fallDetected

    Spacer(modifier = Modifier.height(16.dp))

    CleanCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = if (fall) Icons.Filled.Warning else Icons.Filled.Timeline
            )

            Column {
                Text(
                    text = "Acelerômetro / Queda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (fall) "Alerta de queda detectado" else "Monitoramento de movimento",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AxisValue("AX", ax ?: 0f)
            AxisValue("AY", ay ?: 0f)
            AxisValue("AZ", az ?: 0f)
        }

        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = if (fall) "Queda detectada" else "Sem queda",
                    fontWeight = FontWeight.SemiBold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (fall) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (fall) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                labelColor = if (fall) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                leadingIconContentColor = if (fall) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        )
    }
}
@Composable
private fun AxisValue(label: String, value: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = String.format("%.2f", value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}