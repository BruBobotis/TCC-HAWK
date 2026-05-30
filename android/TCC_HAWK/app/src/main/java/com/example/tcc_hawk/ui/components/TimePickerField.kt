package com.example.tcc_hawk.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@Composable
fun TimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // tenta parsear "HH:MM"
    val (hInit, mInit) = runCatching {
        val parts = value.split(":")
        parts[0].toInt() to parts[1].toInt()
    }.getOrElse {
        val c = Calendar.getInstance()
        c.get(Calendar.HOUR_OF_DAY) to c.get(Calendar.MINUTE)
    }

    val dialog = remember {
        TimePickerDialog(context, { _, hour, minute ->
            onValueChange(String.format("%02d:%02d", hour, minute))
        }, hInit, mInit, true)
    }

    OutlinedTextField(
        value = value,
        onValueChange = { /* bloqueia teclado */ },
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.AccessTime, contentDescription = null) },
        readOnly = true,
        modifier = modifier
            .fillMaxWidth()
            .clickable { dialog.show() }
    )
}