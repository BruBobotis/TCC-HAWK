package com.example.tcc_hawk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.tcc_hawk.data.repository.RepositoryProvider
import com.example.tcc_hawk.ui.navigation.AppNav
import com.example.tcc_hawk.ui.theme.TCCHAWKTheme
import com.example.tcc_hawk.ui.viewmodel.AlertsViewModel
import com.example.tcc_hawk.ui.viewmodel.DashboardViewModel
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

class MainActivity : ComponentActivity() {

    private val repo by lazy { RepositoryProvider.provide(applicationContext) }

    private val dashboardVm: DashboardViewModel by viewModels { DashboardViewModel.factory(repo) }
    private val alertsVm: AlertsViewModel by viewModels { AlertsViewModel.factory(repo) }

    private fun ensureAlarmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tcc_hawk_alerts",
                "Alertas (TCC Hawk)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de lembretes e alarmes"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
    private val blePermsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val scanOk = granted[Manifest.permission.BLUETOOTH_SCAN] == true
            val connectOk = granted[Manifest.permission.BLUETOOTH_CONNECT] == true

            if (scanOk && connectOk) {
                dashboardVm.startBle() // ✅ só aqui, com permissão ok
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        ensureAlarmChannel()
        // ✅ pede permissão antes de tentar scan
        requestBlePermissionsIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!ok) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
            }
        }
        setContent {
            TCCHAWKTheme {
                AppNav(
                    dashboardVm = dashboardVm,
                    alertsVm = alertsVm
                )
            }
        }

        // Se já tem permissão, inicia logo
        if (hasBlePermissions()) {
            dashboardVm.startBle()
        }
    }

    private fun hasBlePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val scanOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val connectOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        return scanOk && connectOk
    }

    private fun requestBlePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBlePermissions()) {
            blePermsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }
    }
}