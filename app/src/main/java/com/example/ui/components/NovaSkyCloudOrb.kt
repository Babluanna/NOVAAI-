package com.example.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voice.VoiceSessionStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SKY_CLOUD_ORB_AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float uEnergy;
uniform float uMode;

float hash21(float2 p) {
    p = fract(p * float2(127.1, 311.7));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float valueNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float amp = 0.52;
    float2 shift = float2(100.0, 100.0);
    for (int i = 0; i < 5; i++) {
        v += amp * valueNoise(p);
        p = float2(p.x * 1.62 - p.y * 1.18, p.x * 1.18 + p.y * 1.62) + shift;
        amp *= 0.50;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float minDim = min(uResolution.x, uResolution.y);
    float2 uv = (fragCoord - 0.5 * uResolution) / (minDim * 0.5);
    float r = length(uv);

    // Crisp anti-aliased sphere edge
    float edgeAlpha = 1.0 - smoothstep(0.982, 1.0, r);
    if (edgeAlpha <= 0.001) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    // Subtle 3D spherical projection so clouds curve naturally near the rim
    float z = sqrt(max(0.0, 1.0 - r * r * 0.82));
    float2 sphereUv = uv / (0.68 + 0.32 * z);

    // Dynamic voice-reactive wind & turbulence
    float speakingBoost = (uMode > 2.5) ? (0.35 + uEnergy * 0.85) : 0.0;
    float thinkingSwirl = (uMode > 1.5 && uMode < 2.5) ? 0.45 : 0.0;
    float drift = uTime * (0.20 + uEnergy * 0.22 + thinkingSwirl * 0.18);

    // Optional gentle rotational eddy during thinking & speaking
    float angle = thinkingSwirl * sin(uTime * 0.9) * 0.35 + speakingBoost * 0.14 * sin(uTime * 1.7 + r * 2.4);
    float ca = cos(angle);
    float sa = sin(angle);
    float2 warpedUv = float2(sphereUv.x * ca - sphereUv.y * sa, sphereUv.x * sa + sphereUv.y * ca);

    // Domain-warped billowing cumulus & wispy cirrus clouds
    float2 q = float2(
        fbm(warpedUv * 1.55 + float2(drift * 0.75, -drift * 0.32)),
        fbm(warpedUv * 1.55 + float2(4.2 - drift * 0.40, 2.7 + drift * 0.55))
    );

    float2 w = float2(
        fbm(warpedUv * 2.15 + 2.1 * q + float2(1.7 + drift * 0.95, 9.2 - drift * 0.45)),
        fbm(warpedUv * 2.15 + 2.1 * q + float2(8.3 - drift * 0.60, 2.8 + drift * 0.80))
    );

    float cloudField = fbm(warpedUv * 1.85 + 1.85 * w);

    // Sky palette matching reference video: rich cobalt top -> clear sky azure -> soft periwinkle horizon
    float skyGrad = clamp(uv.y * 0.52 + 0.50, 0.0, 1.0);
    float3 deepCobalt = float3(0.11, 0.38, 0.93);   // #1C61ED
    float3 clearAzure = float3(0.30, 0.58, 0.98);   // #4D94FA
    float3 horizonMist = float3(0.66, 0.81, 1.00);  // #A8CFFF

    float3 skyColor = mix(deepCobalt, clearAzure, smoothstep(0.0, 0.58, skyGrad));
    skyColor = mix(skyColor, horizonMist, smoothstep(0.48, 1.0, skyGrad));

    // Sculpt billowing cloud density (richer clouds in lower-middle & drifting wisps across top)
    float verticalCloudBias = 0.08 * uv.y + 0.10 * uEnergy * sin(uTime * 2.8 - r * 3.1);
    float cloudDensity = smoothstep(0.34, 0.76, cloudField + verticalCloudBias);
    float brightCrest = smoothstep(0.56, 0.88, cloudField + q.x * 0.18 + uEnergy * 0.12);

    // Soft lavender-periwinkle cloud shadow underside + crisp sunlit cumulus white
    float3 cloudShadow = float3(0.76, 0.85, 0.99);  // #C2D9FC
    float3 cloudSunlit = float3(0.995, 0.997, 1.0); // Pure sunlit cloud white

    // Subtle muted/paused desaturation when mic is muted
    float3 col = mix(skyColor, cloudShadow, cloudDensity * 0.88);
    col = mix(col, cloudSunlit, brightCrest * 0.96);

    // Soft inner atmospheric rim & sunlit top sheen so sphere feels weightless and alive
    float rim = smoothstep(0.62, 0.99, r);
    float3 rimTint = mix(float3(0.52, 0.75, 1.0), float3(0.94, 0.97, 1.0), skyGrad);
    col = mix(col, rimTint, rim * 0.24);

    if (uMode < 0.5) {
        // Calm serene sky when muted/paused
        float luma = dot(col, float3(0.299, 0.587, 0.114));
        col = mix(col, float3(luma, luma, luma) * float3(0.94, 0.97, 1.02), 0.22);
    }

    return half4(half3(col * edgeAlpha), half(edgeAlpha));
}
"""

/**
 * Living Sky & Cloud Fluid Orb matching the user's reference video (`Screenrecording_20261104_174206`):
 * - Opens with a smooth spring expansion from a centered blue dot into the full circular sky-cloud sphere.
 * - Uses hardware-accelerated AGSL `RuntimeShader` on Android 13+ (API 33+) for organic billowing
 *   azure sky & sunlit cumulus clouds reactive at 60fps to real-time user and AI voice energy.
 * - Includes a multi-layer procedural `Canvas` fallback for API 24–32 and unit test environments.
 */
@Composable
fun NovaSkyCloudOrb(
    status: VoiceSessionStatus,
    isMuted: Boolean,
    energy: Float,
    contentDesc: String,
    onClick: () -> Unit,
    orbSize: Dp = 252.dp,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Signature video entry: starts as a small centered cobalt dot (0.13f) and blossoms into the full orb
    val entryScale = remember { Animatable(0.13f) }
    val dotOverlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        entryScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    LaunchedEffect(Unit) {
        dotOverlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 460, delayMillis = 90, easing = FastOutSlowInEasing)
        )
    }

    val smoothedEnergy by animateFloatAsState(
        targetValue = when {
            isMuted && status != VoiceSessionStatus.AI_SPEAKING -> 0.08f
            status == VoiceSessionStatus.AI_SPEAKING ||
                status == VoiceSessionStatus.LISTENING ||
                status == VoiceSessionStatus.PROCESSING -> energy.coerceIn(0.14f, 1.0f)
            else -> 0.12f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "orb_voice_energy"
    )

    val voicePulseScale = 1f + (smoothedEnergy - 0.14f).coerceAtLeast(0f) * 0.065f
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "orb_press_scale"
    )

    var elapsedTimeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var firstFrameNs = -1L
        while (true) {
            withFrameNanos { nowNs ->
                if (firstFrameNs < 0L) firstFrameNs = nowNs
                elapsedTimeSec = ((nowNs - firstFrameNs) / 1_000_000_000.0).toFloat()
            }
        }
    }

    val modeValue = when {
        status == VoiceSessionStatus.AI_SPEAKING || status == VoiceSessionStatus.PLAYING_RECORDING -> 3f
        status == VoiceSessionStatus.PROCESSING -> 2f
        status == VoiceSessionStatus.LISTENING && !isMuted -> 1f
        else -> 0f
    }

    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        ) {
            runCatching { RuntimeShader(SKY_CLOUD_ORB_AGSL) }.getOrNull()
        } else {
            null
        }
    }

    val combinedScale = entryScale.value * voicePulseScale * pressScale

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(orbSize + 28.dp)
            .testTag("voice_sky_orb_container")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(orbSize)
                .scale(combinedScale)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .semantics { contentDescription = contentDesc }
                .testTag("voice_mic_button")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val radius = size.minDimension / 2f
                val center = Offset(w / 2f, h / 2f)

                if (agslShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    agslShader.setFloatUniform("uResolution", w, h)
                    agslShader.setFloatUniform("uTime", elapsedTimeSec)
                    agslShader.setFloatUniform("uEnergy", smoothedEnergy)
                    agslShader.setFloatUniform("uMode", modeValue)
                    drawRect(brush = ShaderBrush(agslShader))
                } else {
                    // Multi-layer procedural Sky & Cumulus Cloud Sphere fallback (API 24-32 & tests)
                    val circlePath = Path().apply {
                        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                    }
                    clipPath(circlePath) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1D62ED),
                                    Color(0xFF4F95FA),
                                    Color(0xFF9AC5FF),
                                    Color(0xFFDCEBFF)
                                )
                            )
                        )

                        val puffCount = 12
                        for (i in 0 until puffCount) {
                            val seed = i * 1.37f
                            val speed = 0.24f + (i % 4) * 0.08f + smoothedEnergy * 0.25f
                            val driftX = sin(elapsedTimeSec * speed + seed) * radius * 0.56f
                            val driftY = cos(elapsedTimeSec * (speed * 0.72f) + seed * 1.4f) * radius * 0.28f +
                                radius * (0.14f + (i % 3) * 0.14f)
                            val puffCenter = Offset(center.x + driftX, center.y + driftY)
                            val puffRadius = radius * (0.42f + (i % 4) * 0.13f + smoothedEnergy * 0.08f)

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.88f),
                                        Color(0xFFD6E6FF).copy(alpha = 0.52f),
                                        Color.Transparent
                                    ),
                                    center = puffCenter,
                                    radius = puffRadius
                                ),
                                center = puffCenter,
                                radius = puffRadius
                            )
                        }
                    }
                }

                // Initial cobalt-blue entry dot cross-fading smoothly into the clouds during expansion
                if (dotOverlayAlpha.value > 0.01f) {
                    drawCircle(
                        color = Color(0xFF1D63ED).copy(alpha = dotOverlayAlpha.value),
                        center = center,
                        radius = radius
                    )
                }
            }
        }
    }
}
