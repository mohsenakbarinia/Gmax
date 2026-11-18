package com.example.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ContractCatalog
import com.example.data.GameSettings
import com.example.data.GraphicsQuality
import com.example.data.HudState
import com.example.data.UiState
import com.example.data.UserProfileStats
import com.example.data.WeatherPreset
import com.example.ui.theme.AlpineEmerald
import com.example.ui.theme.BoneWhite
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.ObsidianPine
import com.example.ui.theme.OnAmberInk
import com.example.ui.theme.SlateMist
import com.example.ui.theme.SunlitAmber

@Composable
fun StartScreen(
    hud: HudState,
    settings: GameSettings,
    statsState: UiState<UserProfileStats>,
    onPlayFullscreen: () -> Unit,
    onSelectWeather: (WeatherPreset) -> Unit,
    onSelectQuality: (GraphicsQuality) -> Unit,
    onOpenTrophies: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val config = LocalConfiguration.current
    val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT
    var rotateHintDismissed by remember { mutableStateOf(false) }

    val stats = (statsState as? UiState.Success)?.data ?: UserProfileStats()
    val contract = ContractCatalog.activeForIndex(stats.activeContractIndex.toInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ObsidianPine.copy(alpha = 0.62f),
                        Color.Transparent,
                        ObsidianPine.copy(alpha = 0.88f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        // Top Bar: Brand mark left, Trophy Log & Settings right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "WILDWOOD: REALITY",
                    style = MaterialTheme.typography.displayLarge,
                    color = BoneWhite
                )
                Text(
                    text = stringResource(id = R.string.subtitle_alpine_hunt),
                    style = MaterialTheme.typography.labelSmall,
                    color = SunlitAmber
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GlassSurfaceDark,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(onClick = onOpenTrophies)
                        .testTag("start_trophy_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = stringResource(id = R.string.cd_trophies),
                            tint = SunlitAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${stats.totalHarvests} HARVESTS • ${stats.goldTrophies} GOLD",
                            style = MaterialTheme.typography.labelMedium,
                            color = BoneWhite
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GlassSurfaceDark,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("start_settings_button")
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

        // Non-blocking Portrait rotate hint
        AnimatedVisibility(
            visible = isPortrait && !rotateHintDismissed,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = GlassSurfaceElevated,
                border = BorderStroke(1.dp, SunlitAmber.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ScreenRotation,
                        contentDescription = null,
                        tint = SunlitAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.rotate_hint_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoneWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { rotateHintDismissed = true }) {
                        Text(
                            text = stringResource(id = R.string.continue_anyway),
                            style = MaterialTheme.typography.labelSmall,
                            color = SunlitAmber
                        )
                    }
                }
            }
        }

        // Bottom Controls Deck: Weather & Quality Pickers + Active Contract + Play Fullscreen CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hud.texturesReady) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassSurfaceDark,
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${stringResource(id = R.string.synthesizing_textures)} ${(hud.textureProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMist
                        )
                        LinearProgressIndicator(
                            progress = { hud.textureProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SunlitAmber,
                            trackColor = Color.White.copy(alpha = 0.14f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left: Weather & Quality Selectors
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Weather & Time Presets
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WeatherPreset.entries.forEach { preset ->
                            val selected = preset == settings.weatherPreset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) SunlitAmber.copy(alpha = 0.22f) else GlassSurfaceDark,
                                border = BorderStroke(1.dp, if (selected) SunlitAmber else GlassBorder),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable { onSelectWeather(preset) }
                                    .testTag("weather_chip_${preset.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WbSunny,
                                        contentDescription = null,
                                        tint = if (selected) SunlitAmber else SlateMist,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = preset.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) BoneWhite else SlateMist
                                    )
                                }
                            }
                        }
                    }

                    // Graphics Presets + Active Contract pill
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GraphicsQuality.entries.forEach { q ->
                            val selected = q == settings.graphicsQuality
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) AlpineEmerald.copy(alpha = 0.28f) else GlassSurfaceDark,
                                border = BorderStroke(1.dp, if (selected) AlpineEmerald else GlassBorder),
                                modifier = Modifier
                                    .height(40.dp)
                                    .clickable { onSelectQuality(q) }
                                    .testTag("quality_chip_${q.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = q.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) BoneWhite else SlateMist
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GlassSurfaceDark,
                            border = BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier
                                .height(40.dp)
                                .clickable(onClick = onOpenTrophies)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CONTRACT: ${contract.title.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SunlitAmber
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right: Primary Play Fullscreen CTA
                Button(
                    onClick = onPlayFullscreen,
                    modifier = Modifier
                        .widthIn(min = 210.dp)
                        .height(58.dp)
                        .testTag("play_fullscreen_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunlitAmber,
                        contentColor = OnAmberInk
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.play_fullscreen),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
