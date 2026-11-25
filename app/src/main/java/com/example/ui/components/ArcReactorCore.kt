package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.JarvisCoreState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisPlasmaBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SpaceGroteskFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorCore(
    coreState: JarvisCoreState,
    rmsLevel: Float,
    partialTranscript: String,
    onCoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_hologram_anim")

    val cwRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (coreState) {
                    JarvisCoreState.PROCESSING -> 2400
                    JarvisCoreState.LISTENING -> 6000
                    JarvisCoreState.SPEAKING -> 4800
                    else -> 18000
                },
                easing = LinearEasing
            )
        ),
        label = "cw_rot"
    )

    val ccwRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (coreState) {
                    JarvisCoreState.PROCESSING -> 1800
                    JarvisCoreState.SPEAKING -> 3600
                    else -> 12000
                },
                easing = LinearEasing
            )
        ),
        label = "ccw_rot"
    )

    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing)
        ),
        label = "radar_sweep"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val animatedRms by animateFloatAsState(
        targetValue = rmsLevel,
        animationSpec = spring(stiffness = 550f),
        label = "rms_spring"
    )

    val primaryAccent = when (coreState) {
        JarvisCoreState.IDLE -> JarvisCyan
        JarvisCoreState.LISTENING -> JarvisEmerald
        JarvisCoreState.PROCESSING -> JarvisAmber
        JarvisCoreState.SPEAKING -> JarvisPlasmaBlue
        JarvisCoreState.ERROR -> JarvisCrimson
    }
    val secondaryAccent = when (coreState) {
        JarvisCoreState.PROCESSING -> JarvisCyan
        JarvisCoreState.ERROR -> JarvisAmber
        else -> JarvisAmber
    }

    val coreDiameter = if (compact) 292.dp else 332.dp
    val coreDescription = stringResource(R.string.cd_voice_core)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(coreDiameter)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = primaryAccent),
                    role = Role.Button,
                    onClick = onCoreClick
                )
                .semantics { contentDescription = coreDescription }
                .testTag("voice_core_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                val voiceEnergy = when (coreState) {
                    JarvisCoreState.LISTENING -> 0.28f + animatedRms * 0.85f
                    JarvisCoreState.SPEAKING -> 0.58f
                    JarvisCoreState.PROCESSING -> 0.45f
                    else -> 0.14f * breathe
                }

                // 1. Holographic sphere radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.28f * breathe),
                            primaryAccent.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )

                // 2. Sweeping holographic radar beam
                rotate(radarSweep, pivot = center) {
                    val sweepRadius = maxRadius * 0.90f
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.76f to Color.Transparent,
                            0.96f to primaryAccent.copy(alpha = 0.16f),
                            1.0f to primaryAccent.copy(alpha = 0.34f),
                            center = center
                        ),
                        radius = sweepRadius,
                        center = center
                    )
                }

                // 3. Outer 360° compass reticle ticks (72 ticks)
                rotate(cwRotation * 0.45f, pivot = center) {
                    val outerTickR = maxRadius * 0.96f
                    for (i in 0 until 72) {
                        val angleRad = Math.toRadians(i * 5.0)
                        val isMajor = i % 6 == 0
                        val isMed = i % 3 == 0
                        val tickLen = when {
                            isMajor -> 10.dp.toPx()
                            isMed -> 6.dp.toPx()
                            else -> 3.dp.toPx()
                        }
                        val innerR = outerTickR - tickLen
                        val cosA = cos(angleRad).toFloat()
                        val sinA = sin(angleRad).toFloat()
                        drawLine(
                            color = if (isMajor) {
                                primaryAccent.copy(alpha = 0.88f)
                            } else {
                                primaryAccent.copy(alpha = 0.32f)
                            },
                            start = Offset(center.x + innerR * cosA, center.y + innerR * sinA),
                            end = Offset(center.x + outerTickR * cosA, center.y + outerTickR * sinA),
                            strokeWidth = if (isMajor) 1.7.dp.toPx() else 0.9.dp.toPx()
                        )
                    }
                }

                // 4. Outer technical segmented arc ring + orbital nodes
                rotate(cwRotation, pivot = center) {
                    val r = maxRadius * 0.84f
                    val rectSize = Size(r * 2f, r * 2f)
                    val tl = Offset(center.x - r, center.y - r)
                    val segments = listOf(10f to 64f, 95f to 42f, 165f to 78f, 268f to 52f)
                    segments.forEachIndexed { idx, (start, sweep) ->
                        drawArc(
                            color = if (idx == 1) secondaryAccent.copy(alpha = 0.85f) else primaryAccent.copy(alpha = 0.84f),
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = tl,
                            size = rectSize,
                            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Butt)
                        )
                    }
                    listOf(10.0, 165.0).forEach { deg ->
                        val rad = Math.toRadians(deg)
                        drawCircle(
                            color = JarvisCyanGlow,
                            radius = 3.dp.toPx(),
                            center = Offset(
                                center.x + r * cos(rad).toFloat(),
                                center.y + r * sin(rad).toFloat()
                            )
                        )
                    }
                }

                // 5. Middle counter-rotating Stark dual-track arc
                rotate(ccwRotation, pivot = center) {
                    val r = maxRadius * 0.72f
                    val rectSize = Size(r * 2f, r * 2f)
                    val tl = Offset(center.x - r, center.y - r)
                    for (i in 0 until 6) {
                        drawArc(
                            color = if (i % 2 == 0) {
                                primaryAccent.copy(alpha = 0.62f)
                            } else {
                                secondaryAccent.copy(alpha = 0.52f)
                            },
                            startAngle = i * 60f + 8f,
                            sweepAngle = 36f,
                            useCenter = false,
                            topLeft = tl,
                            size = rectSize,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt)
                        )
                    }
                }

                // 6. 48-bar Radial Voice Waveform Equalizer
                val barCount = 48
                val baseWaveR = maxRadius * 0.50f
                val maxBarH = maxRadius * 0.14f
                for (i in 0 until barCount) {
                    val angleRad = (i.toFloat() / barCount) * (2f * PI.toFloat())
                    val harmonic = (sin(angleRad * 5f + wavePhase * 2f) * 0.5f + 0.5f) *
                        (cos(angleRad * 3f - wavePhase) * 0.4f + 0.6f)
                    val barLen = 2.5.dp.toPx() + (maxBarH * voiceEnergy * harmonic)
                    val cosA = cos(angleRad)
                    val sinA = sin(angleRad)
                    drawLine(
                        color = if (i % 4 == 0 && coreState == JarvisCoreState.PROCESSING) {
                            secondaryAccent.copy(alpha = 0.9f)
                        } else {
                            primaryAccent.copy(alpha = 0.88f)
                        },
                        start = Offset(center.x + baseWaveR * cosA, center.y + baseWaveR * sinA),
                        end = Offset(
                            center.x + (baseWaveR + barLen) * cosA,
                            center.y + (baseWaveR + barLen) * sinA
                        ),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 7. Pure glowing holographic inner core (no microphone icon)
                val coreR = maxRadius * 0.44f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.22f * breathe),
                            Color(0xFF041426).copy(alpha = 0.94f)
                        ),
                        center = center,
                        radius = coreR
                    ),
                    radius = coreR,
                    center = center
                )
                drawCircle(
                    color = primaryAccent.copy(alpha = 0.9f),
                    radius = coreR,
                    center = center,
                    style = Stroke(width = 1.8.dp.toPx())
                )
                val hexPath = Path()
                val hexR = coreR * 0.84f
                for (k in 0 until 6) {
                    val a = Math.toRadians((k * 60 - 30).toDouble())
                    val hx = center.x + hexR * cos(a).toFloat()
                    val hy = center.y + hexR * sin(a).toFloat()
                    if (k == 0) hexPath.moveTo(hx, hy) else hexPath.lineTo(hx, hy)
                }
                hexPath.close()
                drawPath(
                    path = hexPath,
                    color = primaryAccent.copy(alpha = 0.34f),
                    style = Stroke(width = 1.1.dp.toPx())
                )
            }

            // Pure J.A.R.V.I.S. holographic core text without microphone icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "J.A.R.V.I.S.",
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 17.sp else 20.sp,
                    letterSpacing = 2.6.sp,
                    color = JarvisCyanGlow
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = when (coreState) {
                        JarvisCoreState.IDLE -> "EN ESPERA"
                        JarvisCoreState.LISTENING -> "ESCUCHANDO"
                        JarvisCoreState.PROCESSING -> "PROCESANDO"
                        JarvisCoreState.SPEAKING -> "HABLANDO"
                        JarvisCoreState.ERROR -> "REINTENTAR"
                    },
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    color = primaryAccent
                )
            }
        }

        if (partialTranscript.isNotBlank() && coreState == JarvisCoreState.LISTENING) {
            Spacer(modifier = Modifier.height(12.dp))
            HudBracketPanel(
                accentColor = JarvisEmerald,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "“$partialTranscript”",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisCyanGlow,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
