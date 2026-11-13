package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NovaBlack
import com.example.ui.theme.NovaNeonViolet
import com.example.ui.theme.NovaOrangeBright
import com.example.ui.theme.NovaOrangeCore
import com.example.ui.theme.NovaOrangeHighlight
import com.example.ui.theme.NovaTextPrimaryDark
import com.example.ui.theme.NovaTextSecondaryDark
import kotlinx.coroutines.delay

@Composable
fun NovaSplashIntroScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orbScale = remember { Animatable(0.28f) }
    val orbAlpha = remember { Animatable(0f) }
    val progressAnim = remember { Animatable(0f) }

    var showTitle by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_orbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_spin"
    )
    val rippleWave by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_wave"
    )

    LaunchedEffect(Unit) {
        NovaSoundEffects.playStartupChime()
        orbAlpha.animateTo(1f, animationSpec = tween(durationMillis = 360))
    }

    LaunchedEffect(Unit) {
        orbScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(240L)
        showTitle = true
        delay(220L)
        showSubtitle = true
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1350, easing = FastOutSlowInEasing)
        )
        delay(120L)
        onFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(NovaBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished
            )
            .testTag("nova_splash_intro_screen")
    ) {
        // Atmospheric cosmic-amber radial backdrop & shockwave rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val baseRadius = size.minDimension * 0.24f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NovaOrangeCore.copy(alpha = 0.16f),
                        NovaNeonViolet.copy(alpha = 0.10f),
                        NovaBlack
                    ),
                    center = center,
                    radius = size.minDimension * 0.85f
                )
            )

            // Expanding luminous shockwave ring
            val rippleAlpha = ((1.48f - rippleWave) / 0.93f).coerceIn(0f, 1f) * 0.42f
            drawCircle(
                color = NovaOrangeBright.copy(alpha = rippleAlpha),
                center = center,
                radius = baseRadius * rippleWave,
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Orb + Counter-rotating Orbital Arcs
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(184.dp)
                    .scale(orbScale.value)
                    .alpha(orbAlpha.value)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val outerOrbit = size.minDimension * 0.45f
                    val innerOrbit = size.minDimension * 0.38f

                    rotate(degrees = orbitAngle, pivot = c) {
                        val rect = Rect(
                            c.x - outerOrbit,
                            c.y - outerOrbit,
                            c.x + outerOrbit,
                            c.y + outerOrbit
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NovaOrangeBright.copy(alpha = 0.78f),
                                    NovaOrangeHighlight,
                                    Color.Transparent
                                ),
                                center = c
                            ),
                            startAngle = 0f,
                            sweepAngle = 135f,
                            useCenter = false,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    rotate(degrees = -orbitAngle * 1.25f, pivot = c) {
                        val rect = Rect(
                            c.x - innerOrbit,
                            c.y - innerOrbit,
                            c.x + innerOrbit,
                            c.y + innerOrbit
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NovaNeonViolet.copy(alpha = 0.72f),
                                    Color(0xFFD8B4FE),
                                    Color.Transparent
                                ),
                                center = c
                            ),
                            startAngle = 180f,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                GlowingOrangeNovaOrb(
                    size = 114.dp,
                    isPulsing = true
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 2 }
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.0).sp
                    ),
                    color = NovaTextPrimaryDark,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = showSubtitle,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 2 }
            ) {
                Text(
                    text = stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NovaTextSecondaryDark,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Sleek glowing progress filament at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 44.dp)
                .width(148.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .width(148.dp * progressAnim.value)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(NovaNeonViolet, NovaOrangeCore, NovaOrangeHighlight)
                        )
                    )
            )
        }
    }
}
