package com.example.tcc_hawk.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.example.tcc_hawk.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {

        val progress = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            progress.animateTo(1f, tween(durationMillis = 1000))
            delay(200)
            onDone()
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ✅ Use um recurso raster em drawable (PNG/WEBP)
                Image(
                    painter = painterResource(id = R.drawable.hawk_logo),
                    contentDescription = "HAWK",
                    modifier = Modifier.size(300.dp).padding(15.dp),
                    contentScale = ContentScale.Fit
                )

                EcgDraw(
                    progress = progress.value,
                    modifier = Modifier
                        .height(70.dp)
                        .fillMaxWidth(0.82f)
                )
            }
        }
    }
}

@Composable
private fun EcgDraw(progress: Float, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val pts = listOf(
            Offset(0.00f, 0.55f),
            Offset(0.10f, 0.55f),
            Offset(0.14f, 0.45f),
            Offset(0.18f, 0.70f),
            Offset(0.22f, 0.35f),
            Offset(0.26f, 0.55f),
            Offset(0.36f, 0.55f),
            Offset(0.40f, 0.15f),
            Offset(0.44f, 0.90f),
            Offset(0.50f, 0.55f),
            Offset(0.62f, 0.55f),
            Offset(0.66f, 0.45f),
            Offset(0.70f, 0.62f),
            Offset(0.74f, 0.40f),
            Offset(0.78f, 0.55f),
            Offset(1.00f, 0.55f),
        )

        val segCount = ((pts.size - 1) * progress).coerceIn(0f, (pts.size - 1).toFloat())
        val fullSeg = segCount.toInt()
        val frac = segCount - fullSeg

        fun scale(p: Offset) = Offset(p.x * size.width, p.y * size.height)

        val path = Path()
        val p0 = scale(pts[0])
        path.moveTo(p0.x, p0.y)

        for (i in 1..fullSeg.coerceAtMost(pts.size - 1)) {
            val p = scale(pts[i])
            path.lineTo(p.x, p.y)
        }

        if (fullSeg + 1 < pts.size) {
            val a = scale(pts[fullSeg])
            val b = scale(pts[fullSeg + 1])
            val x = a.x + (b.x - a.x) * frac
            val y = a.y + (b.y - a.y) * frac
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
    }
}