package com.example.tcc_hawk.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cal = remember { Calendar.getInstance() }

    // tenta reaproveitar a data atual do value (dd/MM/yyyy)
    val (dInit, mInit, yInit) = runCatching {
        val parts = value.split("/")
        Triple(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }.getOrElse {
        Triple(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.YEAR) - 60
        )
    }

    fun openDialog() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onValueChange(String.format("%02d/%02d/%04d", day, month + 1, year))
            },
            yInit, mInit, dInit
        ).show()
    }

    // ✅ Box clicável por cima do TextField (TextField não “come” o clique)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { openDialog() }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { },      // não digita
            enabled = false,          // não abre teclado
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}