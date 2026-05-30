package com.example.tcc_hawk.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---------- MINT (claro: fundo verdinho / letras verde escuro) ----------
private val MintLight = lightColorScheme(
    primary = Color(0xFF0F766E),        // verde escuro (botões/destaques)
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF14B8A6),      // verde mais vivo
    onSecondary = Color(0xFF062925),
    background = Color(0xFFE7FFF7),     // fundo verdinho claro
    onBackground = Color(0xFF063B35),   // texto verde escuro
    surface = Color(0xFFF4FFFC),        // cards
    onSurface = Color(0xFF063B35),
    surfaceVariant = Color(0xFFD3F4EA),
    onSurfaceVariant = Color(0xFF0B4F46),
    outline = Color(0xFF86DCCB)
)

private val MintDark = darkColorScheme(
    primary = Color(0xFF34D399),        // mint brilhante
    onPrimary = Color(0xFF062925),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF062925),
    background = Color(0xFF061612),     // quase preto com verde musgo
    onBackground = Color(0xFFE6FFF7),
    surface = Color(0xFF0A201A),        // cards musgo
    onSurface = Color(0xFFE6FFF7),
    surfaceVariant = Color(0xFF0E2B23),
    onSurfaceVariant = Color(0xFFB7F3E2),
    outline = Color(0xFF1E6F5F)
)

// ---------- SKY (claro: fundo azul clarinho / letras azul escuro) ----------
private val SkyLight = lightColorScheme(
    primary = Color(0xFF1D4ED8),        // azul forte (botões)
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF38BDF8),      // sky vivo
    onSecondary = Color(0xFF07243A),
    background = Color(0xFFEAF4FF),     // fundo azul bem claro
    onBackground = Color(0xFF0B2A4A),   // texto azul escuro
    surface = Color(0xFFF6FBFF),
    onSurface = Color(0xFF0B2A4A),
    surfaceVariant = Color(0xFFD9EEFF),
    onSurfaceVariant = Color(0xFF123A66),
    outline = Color(0xFF9DD6FF)
)

private val SkyDark = darkColorScheme(
    primary = Color(0xFF60A5FA),        // azul claro brilhante
    onPrimary = Color(0xFF071A2F),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF071A2F),
    background = Color(0xFF050B16),     // azul marinho bem escuro
    onBackground = Color(0xFFEAF4FF),
    surface = Color(0xFF0A1428),        // cards marinho
    onSurface = Color(0xFFEAF4FF),
    surfaceVariant = Color(0xFF0E1C38),
    onSurfaceVariant = Color(0xFFB9DDFF),
    outline = Color(0xFF23406F)
)

// ---------- DEFAULT (se quiser manter padrão Material) ----------
private val DefaultLight = lightColorScheme()
private val DefaultDark = darkColorScheme()

@Composable
fun TCCHAWKTheme(content: @Composable () -> Unit) {
    val dark = AppThemeState.darkMode
    val palette = AppThemeState.palette

    val scheme = when (palette) {
        AppPalette.MINT -> if (dark) MintDark else MintLight
        AppPalette.SKY -> if (dark) SkyDark else SkyLight
        AppPalette.DEFAULT -> if (dark) DefaultDark else DefaultLight
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}