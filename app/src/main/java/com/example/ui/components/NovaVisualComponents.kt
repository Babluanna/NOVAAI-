package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NovaCosmosDeep
import com.example.ui.theme.NovaCosmosIndigo
import com.example.ui.theme.NovaElectricBlue
import com.example.ui.theme.NovaNeonCyan
import com.example.ui.theme.NovaNeonPink
import com.example.ui.theme.NovaNeonViolet
import com.example.ui.theme.NovaOrangeBright
import com.example.ui.theme.NovaOrangeCore
import com.example.ui.theme.NovaOrangeDeep
import com.example.ui.theme.NovaOrangeHighlight
import com.example.ui.theme.VoiceLavender
import com.example.ui.theme.VoicePearlWarm
import com.example.ui.theme.VoicePeriwinkle
import com.example.ui.theme.VoicePinkAccent
import com.example.ui.theme.VoiceSoftPurple
import com.example.ui.theme.VoiceTextPrimary
import com.example.voice.VoiceSessionStatus
import kotlin.math.PI
import kotlin.math.sin

/**
 * Reference Image 2: Deep Midnight Cosmos Background with glowing electric-blue & violet silk waves.
 */
@Composable
fun CosmosWaveBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cosmos_waves")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_drift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    NovaCosmosDeep,
                    NovaCosmosIndigo,
                    Color(0xFF050B29),
                    NovaCosmosDeep
                )
            )
        )

        // Ambient glow behind top center logo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NovaElectricBlue.copy(alpha = 0.34f),
                    NovaNeonViolet.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.24f),
                radius = w * 0.62f
            ),
            center = Offset(w * 0.5f, h * 0.24f),
            radius = w * 0.62f
        )

        // Upper-left sweeping translucent sapphire ribbon
        val topWave = Path().apply {
            moveTo(0f, h * 0.13f)
            cubicTo(
                w * 0.22f,
                h * (0.20f + 0.015f * sin(drift)),
                w * 0.18f,
                h * 0.36f,
                w * 0.52f,
                h * 0.43f
            )
            lineTo(0f, h * 0.40f)
            close()
        }
        drawPath(
            path = topWave,
            brush = Brush.linearGradient(
                colors = listOf(
                    NovaNeonCyan.copy(alpha = 0.24f),
                    NovaElectricBlue.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                start = Offset(0f, h * 0.14f),
                end = Offset(w * 0.52f, h * 0.43f)
            )
        )

        // Lower sweeping neon electric-blue & violet aurora ribbon (matching Ref Image 2)
        val waveShift = 0.018f * sin(drift)
        val lowerRibbon1 = Path().apply {
            moveTo(0f, h * (0.73f + waveShift))
            cubicTo(
                w * 0.28f,
                h * (0.69f - waveShift),
                w * 0.48f,
                h * (0.81f + waveShift),
                w,
                h * (0.61f - waveShift)
            )
            lineTo(w, h * 0.76f)
            cubicTo(
                w * 0.62f,
                h * (0.68f + waveShift),
                w * 0.35f,
                h * 0.84f,
                0f,
                h * 0.80f
            )
            close()
        }
        drawPath(
            path = lowerRibbon1,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    NovaNeonViolet.copy(alpha = 0.42f),
                    NovaElectricBlue.copy(alpha = 0.52f),
                    NovaNeonCyan.copy(alpha = 0.44f)
                )
            )
        )

        // Crisp luminous rim curve along the lower wave
        val rimCurve = Path().apply {
            moveTo(0f, h * (0.725f + waveShift))
            cubicTo(
                w * 0.26f,
                h * (0.705f - waveShift),
                w * 0.48f,
                h * (0.80f + waveShift),
                w,
                h * (0.65f - waveShift)
            )
        }
        drawPath(
            path = rimCurve,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    NovaNeonPink.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.78f),
                    NovaNeonCyan.copy(alpha = 0.90f),
                    NovaElectricBlue.copy(alpha = 0.65f)
                )
            ),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Reference Image 2: Glowing Violet-Cyan Sphere with 4-looped geometric Nova swirl knot.
 */
@Composable
fun NovaCosmosVortexLogo(
    size: Dp = 132.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "vortex_logo")
    val slowSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vortex_spin"
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vortex_pulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val orbRadius = (this.size.minDimension / 2f) * 0.76f

        // Outer neon halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NovaNeonViolet.copy(alpha = 0.50f * glowPulse.coerceAtMost(1f)),
                    NovaElectricBlue.copy(alpha = 0.30f),
                    Color.Transparent
                ),
                center = center,
                radius = orbRadius * 1.32f * glowPulse
            ),
            center = center,
            radius = orbRadius * 1.32f * glowPulse
        )

        // 3D Cosmic Sphere Body
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFA855F7),
                    Color(0xFF4F46E5),
                    Color(0xFF1D4ED8),
                    Color(0xFF0EA5E9)
                ),
                start = Offset(center.x - orbRadius, center.y - orbRadius),
                end = Offset(center.x + orbRadius, center.y + orbRadius)
            ),
            center = center,
            radius = orbRadius
        )

        // Inner dark depth core so white swirl pops with 3D contrast
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0F174A).copy(alpha = 0.72f),
                    Color.Transparent
                ),
                center = center,
                radius = orbRadius * 0.85f
            ),
            center = center,
            radius = orbRadius * 0.85f
        )

        // Luminous sphere rim
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    NovaNeonCyan,
                    NovaNeonViolet,
                    NovaNeonPink,
                    NovaNeonViolet,
                    NovaNeonCyan
                ),
                center = center
            ),
            center = center,
            radius = orbRadius,
            style = Stroke(width = 2.4.dp.toPx())
        )

        // 4 interlocking smooth C-arcs forming the Nova vortex knot
        val strokePx = orbRadius * 0.145f
        val loopRadius = orbRadius * 0.42f
        val offsetDist = orbRadius * 0.20f

        rotate(degrees = slowSpin * 0.15f, pivot = center) {
            for (i in 0 until 4) {
                rotate(degrees = i * 90f, pivot = center) {
                    val arcCenter = Offset(center.x, center.y - offsetDist)
                    val rect = Rect(
                        left = arcCenter.x - loopRadius,
                        top = arcCenter.y - loopRadius,
                        right = arcCenter.x + loopRadius,
                        bottom = arcCenter.y + loopRadius
                    )
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFE9D5FF),
                                Color(0xFF93C5FD)
                            ),
                            start = Offset(rect.left, rect.top),
                            end = Offset(rect.right, rect.bottom)
                        ),
                        startAngle = 155f,
                        sweepAngle = 215f,
                        useCenter = false,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/**
 * Reference Image 3: Glowing 3D Orange AI Orb for Chat Mode empty greeting & assistant avatar.
 */
@Composable
fun GlowingOrangeNovaOrb(
    size: Dp = 116.dp,
    isPulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orange_orb")
    val breath by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (isPulsing) 1.09f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isPulsing) 900 else 2800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breath"
    )
    val highlightShift by transition.animateFloat(
        initialValue = -0.03f,
        targetValue = 0.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_highlight"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val sphereRadius = (this.size.minDimension / 2f) * 0.72f * breath

        // Warm outer ambient orange bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NovaOrangeCore.copy(alpha = if (isPulsing) 0.56f else 0.42f),
                    NovaOrangeDeep.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = center,
                radius = sphereRadius * 1.42f
            ),
            center = center,
            radius = sphereRadius * 1.42f
        )

        // Core 3D tangerine-amber sphere with upper-right warm highlight (matching Ref Image 3)
        val highlightCenter = Offset(
            x = center.x + sphereRadius * (0.34f + highlightShift),
            y = center.y - sphereRadius * (0.36f - highlightShift)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NovaOrangeHighlight,
                    NovaOrangeBright,
                    NovaOrangeCore,
                    NovaOrangeDeep,
                    Color(0xFFC94400)
                ),
                center = highlightCenter,
                radius = sphereRadius * 1.58f
            ),
            center = center,
            radius = sphereRadius
        )

        // Soft warm peach-rose subsurface glow in lower-left quadrant (seen in Ref Image 3)
        val subGlowCenter = Offset(
            x = center.x - sphereRadius * 0.28f,
            y = center.y + sphereRadius * 0.34f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFB58A).copy(alpha = 0.62f),
                    Color.Transparent
                ),
                center = subGlowCenter,
                radius = sphereRadius * 0.62f
            ),
            center = subGlowCenter,
            radius = sphereRadius * 0.62f
        )

        // Glossy specular pearl highlight dot at top-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.90f),
                    NovaOrangeHighlight.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = highlightCenter,
                radius = sphereRadius * 0.34f
            ),
            center = highlightCenter,
            radius = sphereRadius * 0.34f
        )
    }
}
