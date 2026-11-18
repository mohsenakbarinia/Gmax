package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.data.HudState
import com.example.ui.theme.AlpineEmerald
import com.example.ui.theme.BoneWhite
import com.example.ui.theme.CrimsonEmber
import com.example.ui.theme.HunterCyan
import com.example.ui.theme.SunlitAmber
import kotlin.math.min

/**
 * Photorealistic Hunting Optical Scope Overlay.
 * Renders a 3D parallax dark ocular ring, fine mil-dot ballistic reticle,
 * live laser rangefinder readout (meters), magnification level (4x-12x),
 * windage indicator, and breath-hold lung stamina gauge.
 */
@Composable
fun OpticalScopeOverlay(hud: HudState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            val radius = min(w, h) * 0.44f

            // Subtle parallax ring offset driven by breathing sway
            val px = cx + hud.scopeSwayX * 260f
            val py = cy + hud.scopeSwayY * 260f

            // Opaque matte black scope body with transparent optical aperture punch-out
            drawRect(color = Color(0xFA040605))
            drawCircle(
                color = Color.Transparent,
                radius = radius,
                center = Offset(px, py),
                blendMode = BlendMode.Clear
            )

            // Realistic optical glass edge shadow & subtle lens flare ring
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.72f to Color.Transparent,
                        0.93f to Color(0x66020504),
                        1.0f to Color(0xF5020403)
                    ),
                    center = Offset(px, py),
                    radius = radius
                ),
                radius = radius,
                center = Offset(px, py)
            )

            // Machined anodized scope bezel ring
            drawCircle(
                color = Color(0xFF1A221F),
                radius = radius + 4.dp.toPx(),
                center = Offset(px, py),
                style = Stroke(width = 8.dp.toPx())
            )
            drawCircle(
                color = Color(0x40A8C5B8),
                radius = radius,
                center = Offset(px, py),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Fine Mil-Dot Ballistic Reticle
            val heavyStart = radius * 0.52f
            val lineColor = Color(0xDD0B0E0D)
            val glowAccent = if (hud.holdingBreath) HunterCyan else SunlitAmber

            // Thin hairlines across center
            drawLine(lineColor, Offset(cx - radius, cy), Offset(cx + radius, cy), strokeWidth = 1.4f)
            drawLine(lineColor, Offset(cx, cy - radius), Offset(cx, cy + radius), strokeWidth = 1.4f)

            // Heavy outer stadia duplex posts
            drawLine(Color.Black, Offset(cx - radius, cy), Offset(cx - heavyStart, cy), strokeWidth = 5.5f)
            drawLine(Color.Black, Offset(cx + heavyStart, cy), Offset(cx + radius, cy), strokeWidth = 5.5f)
            drawLine(Color.Black, Offset(cx, cy + heavyStart), Offset(cx, cy + radius), strokeWidth = 5.5f)

            // Mil-dot elevation & windage calibration dots (bullet drop markers below center)
            val step = radius * 0.11f
            for (i in 1..4) {
                val off = i * step
                val dotR = if (i == 2) 3.4f else 2.5f
                drawCircle(Color.Black, dotR, Offset(cx - off, cy))
                drawCircle(Color.Black, dotR, Offset(cx + off, cy))
                drawCircle(Color.Black, dotR, Offset(cx, cy - off))
                drawCircle(Color.Black, dotR, Offset(cx, cy + off))
                // Windage drop holdover hash ticks below center
                val hashW = 8f + i * 3.5f
                drawLine(Color.Black, Offset(cx - hashW, cy + off), Offset(cx + hashW, cy + off), strokeWidth = 1.6f)
            }

            // Illuminated fiber-optic center aiming dot
            drawCircle(glowAccent.copy(alpha = 0.85f), 3.6f, Offset(cx, cy))

            // Breath-hold lung stamina arc on inner-left bezel
            val staminaColor = when {
                hud.breathStamina < 0.25f -> CrimsonEmber
                hud.holdingBreath -> HunterCyan
                else -> AlpineEmerald
            }
            val arcMargin = radius * 0.90f
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = 152f,
                sweepAngle = 56f,
                useCenter = false,
                topLeft = Offset(cx - arcMargin, cy - arcMargin),
                size = Size(arcMargin * 2f, arcMargin * 2f),
                style = Stroke(width = 4.dp.toPx())
            )
            drawArc(
                color = staminaColor,
                startAngle = 152f + 56f * (1f - hud.breathStamina),
                sweepAngle = 56f * hud.breathStamina,
                useCenter = false,
                topLeft = Offset(cx - arcMargin, cy - arcMargin),
                size = Size(arcMargin * 2f, arcMargin * 2f),
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // Digital Laser Rangefinder & Optical Telemetry inside lower-center aperture
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format("%.1fx", hud.scopeZoom),
                    style = MaterialTheme.typography.labelMedium,
                    color = SunlitAmber
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "LRF ${hud.rangefinderMeters?.let { "${it}m" } ?: "---"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = HunterCyan
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = String.format("WIND %.1fm/s", hud.windSpeedMps),
                    style = MaterialTheme.typography.labelMedium,
                    color = BoneWhite.copy(alpha = 0.85f)
                )
            }
            if (hud.holdingBreath) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "HOLDING BREATH • STEADY",
                    style = MaterialTheme.typography.labelSmall,
                    color = HunterCyan
                )
            }
        }
    }
}
