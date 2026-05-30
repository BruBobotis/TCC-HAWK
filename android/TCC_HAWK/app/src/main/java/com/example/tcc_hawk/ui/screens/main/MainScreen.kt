package com.example.tcc_hawk.ui.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tcc_hawk.ui.screens.alerts.AlertsScreen
import com.example.tcc_hawk.ui.screens.caregivers.CaregiversScreen
import com.example.tcc_hawk.ui.screens.dashboard.DashboardScreen
import com.example.tcc_hawk.ui.screens.reports.ReportsScreen
import com.example.tcc_hawk.ui.screens.settings.SettingsScreen
import com.example.tcc_hawk.ui.viewmodel.AlertsViewModel
import com.example.tcc_hawk.ui.viewmodel.DashboardViewModel
import androidx.navigation.NavController

@Composable
fun MainScreen(dashboardVm: DashboardViewModel, alertsVm: AlertsViewModel, navController: NavController) {
    // Aba do meio (Painel) como padrão
    var tab by remember { mutableIntStateOf(2) }

    // Mock (depois vem do estado real/DB)
    val alertCount = 2

    val items = listOf(
        NavItem("Alertas") { androidx.compose.material3.Icon(Icons.Filled.NotificationsActive, null) },
        NavItem("Relatórios") { androidx.compose.material3.Icon(Icons.Filled.Assessment, null) },
        NavItem("Painel") { androidx.compose.material3.Icon(Icons.Filled.Dashboard, null) }, // centro
        NavItem("Cuidadores") { androidx.compose.material3.Icon(Icons.Filled.Group, null) },
        NavItem("Config") { androidx.compose.material3.Icon(Icons.Filled.Settings, null) },
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    val isCenter = index == 2

                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(item.label) },
                        alwaysShowLabel = !isCenter,
                        icon = {
                            // Ícone base (com badge nos alertas)
                            val baseIcon: @Composable () -> Unit = {
                                if (index == 0 && alertCount > 0) {
                                    BadgedBox(badge = { Badge() }) { item.icon() }
                                } else item.icon()
                            }

                            // Destaque do botão do meio
                            if (isCenter) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = if (tab == index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 2.dp
                                ) {
                                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        baseIcon()
                                    }
                                }
                            } else {
                                baseIcon()
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                },
                label = "tabTransition"
            ) { page ->
                when (page) {
                    0 -> AlertsScreen(vm = alertsVm, navController = navController)
                    1 -> ReportsScreen()
                    2 -> DashboardScreen(vm = dashboardVm)
                    3 -> CaregiversScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

private data class NavItem(val label: String, val icon: @Composable () -> Unit)