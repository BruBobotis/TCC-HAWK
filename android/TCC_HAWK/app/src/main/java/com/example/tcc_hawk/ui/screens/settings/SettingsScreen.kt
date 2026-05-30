package com.example.tcc_hawk.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tcc_hawk.ui.theme.AppPalette
import com.example.tcc_hawk.ui.theme.AppThemeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Configurações") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.ColorLens, contentDescription = null)
                        Text("Aparência", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (AppThemeState.darkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = null
                            )
                            Text(if (AppThemeState.darkMode) "Tema escuro" else "Tema claro")
                        }
                        Switch(
                            checked = AppThemeState.darkMode,
                            onCheckedChange = { vm.setDarkMode(it) }
                        )
                    }

                    Text("Paleta de cores", color = MaterialTheme.colorScheme.onSurfaceVariant)

                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = AppThemeState.palette == AppPalette.DEFAULT,
                            onClick = { vm.setPalette(AppPalette.DEFAULT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) { Text("Padrão") }

                        SegmentedButton(
                            selected = AppThemeState.palette == AppPalette.MINT,
                            onClick = { vm.setPalette(AppPalette.MINT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) { Text("Mint") }

                        SegmentedButton(
                            selected = AppThemeState.palette == AppPalette.SKY,
                            onClick = { vm.setPalette(AppPalette.SKY) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) { Text("Sky") }
                    }
                }
            }
        }
    }
}