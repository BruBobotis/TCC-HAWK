package com.example.tcc_hawk.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AlertUiStyle(
    val header: String,
    val title: String,
    val emoji: String,
    val backgroundColor: Color,
    val cardColor: Color,
    val mainColor: Color,
    val textColor: Color,
    val defaultMessage: String
)

@Composable
fun AlertFullScreen(
    type: String,
    messageOverride: String = "",
    onDismiss: () -> Unit
) {
    val style = when (type) {

        "FALL" -> AlertUiStyle(
            header = "ALERTA CRÍTICO",
            title = "Possível Queda",
            emoji = "🚨",
            backgroundColor = Color(0xFFFFF1F0),
            cardColor = Color(0xFFFFDAD6),
            mainColor = Color(0xFFB3261E),
            textColor = Color(0xFF3B0A07),
            defaultMessage = "Possível queda detectada pelo relógio"
        )

        "BPM_HIGH" -> AlertUiStyle(
            header = "ALERTA",
            title = "Batimento Alto",
            emoji = "❤️",
            backgroundColor = Color(0xFFFFF4F2),
            cardColor = Color(0xFFFFDAD6),
            mainColor = Color(0xFFB3261E),
            textColor = Color(0xFF3B0A07),
            defaultMessage = "Frequência cardíaca acima do limite"
        )

        "BPM_LOW" -> AlertUiStyle(
            header = "ALERTA",
            title = "Batimento Baixo",
            emoji = "💙",
            backgroundColor = Color(0xFFF1F7FF),
            cardColor = Color(0xFFDCEBFF),
            mainColor = Color(0xFF2457A5),
            textColor = Color(0xFF10233F),
            defaultMessage = "Frequência cardíaca abaixo do limite"
        )

        "SPO2_LOW" -> AlertUiStyle(
            header = "ALERTA",
            title = "Oxigenação Baixa",
            emoji = "🫁",
            backgroundColor = Color(0xFFFFF7ED),
            cardColor = Color(0xFFFFE0B2),
            mainColor = Color(0xFFB25A00),
            textColor = Color(0xFF4A2A00),
            defaultMessage = "Saturação de oxigênio abaixo do limite"
        )

        "SPO2_HIGH" -> AlertUiStyle(
            header = "ALERTA",
            title = "Oxigenação Alta",
            emoji = "🫁",
            backgroundColor = Color(0xFFF0FFFA),
            cardColor = Color(0xFFCCF3E6),
            mainColor = Color(0xFF00695C),
            textColor = Color(0xFF083B35),
            defaultMessage = "Saturação de oxigênio acima do limite"
        )

        "WATER" -> AlertUiStyle(
            header = "LEMBRETE",
            title = "Hora da Água",
            emoji = "🥤",
            backgroundColor = Color(0xFFF1F8FF),
            cardColor = Color(0xFFD7ECFF),
            mainColor = Color(0xFF1565C0),
            textColor = Color(0xFF0D2D4F),
            defaultMessage = "Hora de beber água"
        )

        "MEDICINE" -> AlertUiStyle(
            header = "LEMBRETE",
            title = "Hora do Remédio",
            emoji = "💊",
            backgroundColor = Color(0xFFF1FFF5),
            cardColor = Color(0xFFD7F5DF),
            mainColor = Color(0xFF2E7D32),
            textColor = Color(0xFF123817),
            defaultMessage = "Hora do remédio"
        )

        "SLEEP" -> AlertUiStyle(
            header = "LEMBRETE",
            title = "Hora de Dormir",
            emoji = "😴",
            backgroundColor = Color(0xFFF7F1FF),
            cardColor = Color(0xFFE8D7FF),
            mainColor = Color(0xFF6A1B9A),
            textColor = Color(0xFF2E0D45),
            defaultMessage = "Hora de dormir"
        )

        else -> AlertUiStyle(
            header = "ALERTA",
            title = "Alerta Geral",
            emoji = "⚠️",
            backgroundColor = Color(0xFFFFFAEB),
            cardColor = Color(0xFFFFF0C2),
            mainColor = Color(0xFF9A6B00),
            textColor = Color(0xFF3F2C00),
            defaultMessage = "Alerta recebido"
        )
    }

    val message = messageOverride.ifBlank { style.defaultMessage }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(style.backgroundColor)
            .clickable { onDismiss() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = style.cardColor
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = style.header,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = style.mainColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = style.emoji,
                    fontSize = 72.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = style.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = style.mainColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 46.sp
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = style.textColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = style.mainColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Dispensar alerta",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}