package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ContractCatalog
import com.example.data.GameMode
import com.example.data.GameSettings
import com.example.data.GraphicsQuality
import com.example.data.GyroMode
import com.example.data.HitZone
import com.example.data.Species
import com.example.data.TrophyRating
import com.example.data.TrophyRecord
import com.example.data.UiState
import com.example.data.UserProfileStats
import com.example.data.WeaponType
import com.example.data.WeatherPreset
import com.example.ui.theme.AlpineEmerald
import com.example.ui.theme.BoneWhite
import com.example.ui.theme.CharcoalMoss
import com.example.ui.theme.CrimsonEmber
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.HunterCyan
import com.example.ui.theme.ObsidianPine
import com.example.ui.theme.OnAmberInk
import com.example.ui.theme.SlateMist
import com.example.ui.theme.SunlitAmber

@Composable
fun SettingsDrawerModal(
    settings: GameSettings,
    gameMode: GameMode,
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onReturnTitle: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianPine.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxHeight()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(22.dp),
            color = GlassSurfaceElevated,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BoneWhite
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("close_settings_button")) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.cd_close), tint = BoneWhite)
                    }
                }

                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Action shortcuts during active hunt
                    if (gameMode != GameMode.TITLE_ORBIT) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onResume,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("resume_hunt_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SunlitAmber, contentColor = OnAmberInk)
                            ) { Text(stringResource(id = R.string.resume_hunt)) }

                            OutlinedButton(
                                onClick = onRestart,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("restart_hunt_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GlassBorder)
                            ) { Text(stringResource(id = R.string.restart_hunt), color = BoneWhite) }
                        }
                        OutlinedButton(
                            onClick = onReturnTitle,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GlassBorder)
                        ) { Text(stringResource(id = R.string.return_title), color = SlateMist) }
                    }

                    // Graphics Preset
                    SettingSectionLabel("Graphics Quality")
                    OptionChipRow(
                        options = GraphicsQuality.entries,
                        selected = settings.graphicsQuality,
                        label = { it.displayName },
                        onSelect = { q -> onUpdateSettings { it.copy(graphicsQuality = q) } }
                    )

                    // Live Weather & Time
                    SettingSectionLabel("Live Weather & Time")
                    OptionChipRow(
                        options = WeatherPreset.entries,
                        selected = settings.weatherPreset,
                        label = { it.displayName },
                        onSelect = { w -> onUpdateSettings { it.copy(weatherPreset = w) } }
                    )

                    // Gyroscope Mode
                    SettingSectionLabel("Gyroscope Precision Aiming")
                    OptionChipRow(
                        options = GyroMode.entries,
                        selected = settings.gyroMode,
                        label = { it.displayName },
                        onSelect = { g -> onUpdateSettings { it.copy(gyroMode = g) } }
                    )

                    // Sensitivity & Audio Sliders
                    SettingSlider(
                        label = "Look Sensitivity",
                        value = settings.normalSensitivity,
                        range = 0.3f..2.4f,
                        valueText = String.format("%.2fx", settings.normalSensitivity),
                        onValueChange = { v -> onUpdateSettings { it.copy(normalSensitivity = v) } }
                    )

                    SettingSlider(
                        label = "Scoped (ADS) Sensitivity",
                        value = settings.adsSensitivity,
                        range = 0.15f..1.4f,
                        valueText = String.format("%.2fx", settings.adsSensitivity),
                        onValueChange = { v -> onUpdateSettings { it.copy(adsSensitivity = v) } }
                    )

                    SettingSlider(
                        label = "Spatial Audio Volume",
                        value = settings.masterVolume,
                        range = 0f..1f,
                        valueText = "${(settings.masterVolume * 100).toInt()}%",
                        onValueChange = { v -> onUpdateSettings { it.copy(masterVolume = v) } }
                    )

                    // Toggles
                    SettingToggle("Left-Side Fire Button", settings.leftFireButtonEnabled) { v ->
                        onUpdateSettings { it.copy(leftFireButtonEnabled = v) }
                    }
                    SettingToggle("Haptic Recoil & Hit Feedback", settings.hapticsEnabled) { v ->
                        onUpdateSettings { it.copy(hapticsEnabled = v) }
                    }
                    SettingToggle("Force Mobile Touch Controls", settings.forceTouchUi) { v ->
                        onUpdateSettings { it.copy(forceTouchUi = v) }
                    }
                    SettingToggle("Show Live FPS Counter", settings.showFpsCounter) { v ->
                        onUpdateSettings { it.copy(showFpsCounter = v) }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CrimsonEmber.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = CrimsonEmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.sign_out), color = CrimsonEmber)
                    }
                }
            }
        }
    }
}

@Composable
fun TrophyLogModal(
    statsState: UiState<UserProfileStats>,
    trophiesState: UiState<List<TrophyRecord>>,
    onDeleteTrophy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val stats = (statsState as? UiState.Success)?.data ?: UserProfileStats()
    val contract = ContractCatalog.activeForIndex(stats.activeContractIndex.toInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianPine.copy(alpha = 0.78f))
            .clickable(onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.92f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(22.dp),
            color = GlassSurfaceElevated,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SunlitAmber, modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(id = R.string.trophy_log_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = BoneWhite
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("close_trophies_button")) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.cd_close), tint = BoneWhite)
                    }
                }

                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Career Stats Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("HARVESTS", "${stats.totalHarvests}", BoneWhite, Modifier.weight(1f))
                    StatTile("GOLD VITAL", "${stats.goldTrophies}", SunlitAmber, Modifier.weight(1f))
                    StatTile("LONGEST VITAL", "${stats.longestVitalShotMeters}m", HunterCyan, Modifier.weight(1f))
                    StatTile("CONTRACTS", "${stats.completedContractsCount}", AlpineEmerald, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Contract Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CharcoalMoss,
                    border = BorderStroke(1.dp, SunlitAmber.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("ACTIVE CONTRACT • ${contract.title.uppercase()}", style = MaterialTheme.typography.labelSmall, color = SunlitAmber)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(contract.description, style = MaterialTheme.typography.bodyMedium, color = BoneWhite)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time Firestore Trophies List
                when (trophiesState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SunlitAmber)
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(trophiesState.message, style = MaterialTheme.typography.bodyMedium, color = CrimsonEmber)
                        }
                    }
                    is UiState.Success -> {
                        val list = trophiesState.data
                        if (list.isEmpty()) {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SlateMist.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(stringResource(id = R.string.empty_trophies_title), style = MaterialTheme.typography.titleMedium, color = BoneWhite)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(id = R.string.empty_trophies_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMist,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(list, key = { it.id.ifBlank { "${it.species}_${it.scorePoints}_${it.distanceMeters}" } }) { rec ->
                                    TrophyRowCard(rec = rec, onDelete = { if (rec.id.isNotBlank()) onDeleteTrophy(rec.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CharcoalMoss,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = SlateMist)
        }
    }
}

@Composable
private fun TrophyRowCard(rec: TrophyRecord, onDelete: () -> Unit) {
    val sp = Species.fromRaw(rec.species)
    val wp = WeaponType.fromRaw(rec.weapon)
    val hz = HitZone.fromRaw(rec.hitZone)
    val rt = TrophyRating.fromRaw(rec.rating)
    val wt = WeatherPreset.fromRaw(rec.weatherPreset)
    val badgeCol = when (rt) {
        TrophyRating.GOLD -> SunlitAmber
        TrophyRating.SILVER -> BoneWhite
        TrophyRating.BRONZE -> Color(0xFFCD8B52)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CharcoalMoss.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sp.displayName, style = MaterialTheme.typography.titleSmall, color = BoneWhite)
                    Text(rt.displayName, style = MaterialTheme.typography.labelSmall, color = badgeCol)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${rec.distanceMeters}m ${hz.displayName} • ${wp.shortLabel} • ${wt.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMist
                )
            }
            Text("+${rec.scorePoints}", style = MaterialTheme.typography.labelMedium, color = SunlitAmber)
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(id = R.string.cd_delete_trophy), tint = SlateMist, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingSectionLabel(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = SunlitAmber)
}

@Composable
private fun <T> OptionChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { item ->
                    val isSel = item == selected
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) SunlitAmber.copy(alpha = 0.22f) else CharcoalMoss,
                        border = BorderStroke(1.dp, if (isSel) SunlitAmber else GlassBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onSelect(item) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                text = label(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSel) BoneWhite else SlateMist,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = BoneWhite)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = SunlitAmber)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = SunlitAmber,
                activeTrackColor = SunlitAmber,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            )
        )
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = BoneWhite)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAmberInk,
                checkedTrackColor = SunlitAmber
            )
        )
    }
}
