package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisSurface

/**
 * Fullscreen holographic blueprint grid + radial Stark HUD crosshair backdrop.
 */
@Composable
fun JarvisHudBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF07233B),
                    Color(0xFF030E1C),
                    JarvisObsidian
                ),
                center = Offset(size.width * 0.5f, size.height * 0.34f),
                radius = size.maxDimension * 0.78f
            )
        )

        val gridStep = 36.dp.toPx()
        val gridColor = JarvisCyan.copy(alpha = 0.045f)
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.8.dp.toPx()
            )
            x += gridStep
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.8.dp.toPx()
            )
            y += gridStep
        }

        // Subtle outer HUD targeting frame corners
        val inset = 14.dp.toPx()
        val bracketLen = 22.dp.toPx()
        val bracketColor = JarvisCyan.copy(alpha = 0.28f)
        val strokeW = 1.5.dp.toPx()

        // Top-left
        drawLine(bracketColor, Offset(inset, inset), Offset(inset + bracketLen, inset), strokeW)
        drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + bracketLen), strokeW)
        // Top-right
        drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - bracketLen, inset), strokeW)
        drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + bracketLen), strokeW)
        // Bottom-left
        drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + bracketLen, size.height - inset), strokeW)
        drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - bracketLen), strokeW)
        // Bottom-right
        drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - bracketLen, size.height - inset), strokeW)
        drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - bracketLen), strokeW)

        // Subtle polar crosshair behind core
        val coreCenter = Offset(size.width * 0.5f, size.height * 0.34f)
        drawCircle(
            color = JarvisCyan.copy(alpha = 0.05f),
            radius = size.minDimension * 0.45f,
            center = coreCenter,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * Chamfered holographic HUD panel with glowing corner tick accents.
 */
@Composable
fun HudBracketPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = JarvisCyan,
    backgroundColor: Color = JarvisSurface.copy(alpha = 0.78f),
    cornerCut: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = CutCornerShape(cornerCut)
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, accentColor.copy(alpha = 0.32f), shape)
            .drawBehind {
                val tick = 10.dp.toPx()
                val stroke = 1.8.dp.toPx()
                val glow = accentColor.copy(alpha = 0.85f)
                // Top-left accent L
                drawLine(glow, Offset(0f, tick), Offset(tick, 0f), stroke)
                // Bottom-right accent L
                drawLine(glow, Offset(size.width - tick, size.height), Offset(size.width, size.height - tick), stroke)
            },
        content = content
    )
}
