package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ContractCatalog
import com.example.data.GameSettings
import com.example.data.HudState
import com.example.data.TrophyRating
import com.example.data.UiState
import com.example.data.UserProfileStats
import com.example.data.WeaponType
import com.example.engine.Math3D
import com.example.ui.theme.AlpineEmerald
import com.example.ui.theme.BoneWhite
import com.example.ui.theme.CrimsonEmber
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.HunterCyan
import com.example.ui.theme.SlateMist
import com.example.ui.theme.SunlitAmber

/**
 * Minimalist Tactical HUD:
 * - Slim top compass tape with rotating Wind Arrow & speed + Hunter Sense track/call pings
 * - Subtle center hip-fire dot (when unscoped)
 * - Discrete stealth visibility & noise meters
 * - Active hunting contract pill, weapon/ammo pill, optional FPS counter
 * - Cinematic Trophy Banner & Wounded/Predator alert toasts
 */
@Composable
fun HudOverlay(
    hud: HudState,
    settings: GameSettings,
    statsState: UiState<UserProfileStats>,
    onOpenSettings: () -> Unit,
    onOpenTrophies: () -> Unit
) {
    val stats = (statsState as? UiState.Success)?.data ?: UserProfileStats()
    val contract = ContractCatalog.activeForIndex(stats.activeContractIndex.toInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Subtle hip-fire crosshair dot when not looking through optical scope
        if (!hud.scoped) {
            Canvas(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
            ) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val col = if (hud.bowDrawing) SunlitAmber else BoneWhite.copy(alpha = 0.78f)
                drawCircle(col, 2.5f, c)
                if (hud.activeWeapon == WeaponType.RECURVE_BOW) {
                    val spread = 12f * (1.15f - hud.bowDrawTension * 0.7f)
                    drawLine(col, Offset(c.x - spread - 6f, c.y), Offset(c.x - spread, c.y), 2f)
                    drawLine(col, Offset(c.x + spread, c.y), Offset(c.x + spread + 6f, c.y), 2f)
                }
            }
        }

        // TOP ROW: Active Contract left | Compass & Wind center | Trophy/Settings right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Compact Active Contract & FPS Badge
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassSurfaceDark,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.clickable(onClick = onOpenTrophies)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(
                            text = "CONTRACT • ${contract.title.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SunlitAmber
                        )
                        Text(
                            text = contract.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = BoneWhite.copy(alpha = 0.9f),
                            maxLines = 1
                        )
                    }
                }
                if (settings.showFpsCounter) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GlassSurfaceDark,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Text(
                            text = "${hud.fps} FPS • ${settings.graphicsQuality.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlpineEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Center: Top Compass Tape + Wind Vector Arrow
            CompassAndWindBar(hud = hud)

            // Right: Trophy Log & Pause/Settings buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassSurfaceDark,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    IconButton(
                        onClick = onOpenTrophies,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hud_trophy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = stringResource(id = R.string.cd_trophies),
                            tint = SunlitAmber
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassSurfaceDark,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hud_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(id = R.string.cd_settings),
                            tint = BoneWhite
                        )
                    }
                }
            }
        }

        // CINEMATIC VITAL SHOT TROPHY BANNER (Upper Center below compass)
        AnimatedVisibility(
            visible = hud.trophyBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 62.dp)
        ) {
            hud.trophyBanner?.let { banner ->
                val accent = when (banner.rating) {
                    TrophyRating.GOLD -> SunlitAmber
                    TrophyRating.SILVER -> BoneWhite
                    TrophyRating.BRONZE -> Color(0xFFCD8B52)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurfaceElevated,
                    border = BorderStroke(1.5.dp, accent)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = banner.headline,
                            style = MaterialTheme.typography.headlineMedium,
                            color = accent
                        )
                        Text(
                            text = banner.subline,
                            style = MaterialTheme.typography.labelMedium,
                            color = BoneWhite
                        )
                    }
                }
            }
        }

        // TRANSIENT TRACKING / PREDATOR ALERT TOAST
        AnimatedVisibility(
            visible = hud.statusAlert != null && hud.trophyBanner == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            hud.statusAlert?.let { alert ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassSurfaceElevated,
                    border = BorderStroke(1.dp, if (hud.woundedWarning) CrimsonEmber else HunterCyan)
                ) {
                    Text(
                        text = alert,
                        style = MaterialTheme.typography.labelMedium,
                        color = BoneWhite,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // BOTTOM-LEFT: Discrete Stealth Visibility/Noise + Weapon/Ammo Pill
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stealth Meters
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GlassSurfaceDark,
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StealthMeterChip(
                        icon = Icons.Filled.Visibility,
                        value = hud.stealthVisibility,
                        color = if (hud.stealthVisibility < 0.4f) AlpineEmerald else SunlitAmber
                    )
                    StealthMeterChip(
                        icon = Icons.Filled.Hearing,
                        value = hud.stealthNoise,
                        color = if (hud.stealthNoise < 0.35f) AlpineEmerald else CrimsonEmber
                    )
                }
            }

            // Weapon & Ammo Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GlassSurfaceDark,
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = hud.activeWeapon.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMist
                    )
                    Text(
                        text = when {
                            hud.activeWeapon == WeaponType.RECURVE_BOW -> "∞ ARROWS"
                            hud.reloading -> "RELOADING ${(hud.reloadProgress * 100).toInt()}%"
                            else -> "${hud.rifleAmmo} / ${hud.rifleMagSize}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SunlitAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassAndWindBar(hud: HudState) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassSurfaceDark,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Rotating Wind Direction Arrow relative to player heading
            val relWindDeg = Math3D.shortestAngleDiffDeg(hud.headingDegrees, hud.windDirectionDegrees)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(HunterCyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = null,
                        tint = HunterCyan,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(relWindDeg)
                    )
                }
                Text(
                    text = String.format("%.1fm/s", hud.windSpeedMps),
                    style = MaterialTheme.typography.labelSmall,
                    color = HunterCyan
                )
            }

            // Live Bearing Tape + Wildlife Markers
            Box(
                modifier = Modifier
                    .width(176.dp)
                    .height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val mid = size.width * 0.5f
                    val pxPerDeg = size.width / 110f

                    // Tick marks every 15 degrees
                    for (deg in 0 until 360 step 15) {
                        val diff = Math3D.shortestAngleDiffDeg(hud.headingDegrees, deg.toFloat())
                        if (kotlin.math.abs(diff) <= 52f) {
                            val x = mid + diff * pxPerDeg
                            val major = deg % 45 == 0
                            drawLine(
                                color = if (major) BoneWhite.copy(alpha = 0.7f) else SlateMist.copy(alpha = 0.35f),
                                start = Offset(x, if (major) 2f else 7f),
                                end = Offset(x, size.height - 4f),
                                strokeWidth = if (major) 2f else 1f
                            )
                        }
                    }

                    // Wildlife tracks / acoustic calls / blood trails on compass
                    for (m in hud.compassMarkers) {
                        val diff = Math3D.shortestAngleDiffDeg(hud.headingDegrees, m.bearingDegrees)
                        if (kotlin.math.abs(diff) <= 54f) {
                            val x = mid + diff * pxPerDeg
                            val markerCol = when (m.kind) {
                                "BLOOD" -> CrimsonEmber
                                "TRACK" -> HunterCyan
                                else -> SunlitAmber
                            }
                            drawCircle(markerCol, 4.5f, Offset(x, size.height * 0.5f))
                        }
                    }

                    // Center heading caret
                    drawLine(SunlitAmber, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 2.2f)
                }
            }

            val cardinal = when (((hud.headingDegrees + 22.5f).toInt() % 360) / 45) {
                0 -> "N"
                1 -> "NE"
                2 -> "E"
                3 -> "SE"
                4 -> "S"
                5 -> "SW"
                6 -> "W"
                else -> "NW"
            }
            Text(
                text = String.format("%03d° %s", hud.headingDegrees.toInt() % 360, cardinal),
                style = MaterialTheme.typography.labelSmall,
                color = BoneWhite
            )
        }
    }
}

@Composable
private fun StealthMeterChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.14f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0.08f, 1f))
                    .height(5.dp)
                    .background(color)
            )
        }
    }
}
