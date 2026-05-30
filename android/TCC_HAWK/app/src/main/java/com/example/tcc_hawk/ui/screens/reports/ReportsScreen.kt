package com.example.tcc_hawk.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tcc_hawk.data.model.VitalsReading
import kotlin.math.roundToInt

private fun averageBpm(samples: List<VitalsReading>): Int {
    val values = samples.map { it.bpm }.filter { it > 0 }
    return if (values.isEmpty()) 0 else values.average().roundToInt()
}

private fun maxBpm(samples: List<VitalsReading>): Int {
    return samples.map { it.bpm }.filter { it > 0 }.maxOrNull() ?: 0
}

private fun filterByRange(samples: List<VitalsReading>, days: Int): List<VitalsReading> {
    val now = System.currentTimeMillis()
    val start = now - days * 24L * 60L * 60L * 1000L
    return samples.filter { it.timestampMillis >= start }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("24h", "7 dias", "30 dias")

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Relatórios") }) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(selected = tab == idx, onClick = { tab = idx }, text = { Text(label) })
                }
            }

            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.QueryStats, null)
                        Text("Resumo", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Nesta tela vamos consolidar BPM médio, picos, tempo em movimento/repouso e quantidade de alertas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.weight(1f), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Assessment, null)
                        Text("BPM (média)", fontWeight = FontWeight.SemiBold)
                        Text("—", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Card(Modifier.weight(1f), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CalendarMonth, null)
                        Text("Alertas", fontWeight = FontWeight.SemiBold)
                        Text("—", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}