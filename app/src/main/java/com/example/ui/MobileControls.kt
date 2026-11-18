package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameSettings
import com.example.data.HudState
import com.example.data.Stance
import com.example.data.WeaponType
import com.example.engine.GameEngine
import com.example.ui.theme.AlpineEmerald
import com.example.ui.theme.BoneWhite
import com.example.ui.theme.CrimsonEmber
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.HunterCyan
import com.example.ui.theme.SlateMist
import com.example.ui.theme.SunlitAmber
import kotlin.math.sqrt

/**
 * Mobile-First Two-Thumb Landscape Touch Controls with strict multi-touch PointerId isolation:
 * - Left half: Dynamic floating analog joystick (creep -> walk -> jog -> >85% Sprint + Sprint Lock)
 * - Right half: Velocity-curved precision look pad
 * - Ergonomic translucent glassmorphism action buttons (Fire + Bow Tension Ring + Cancel Draw,
 *   Optional Left-Side Fire, Scope/ADS, Vertical 4x-12x Zoom Slider, Hold Breath, Stance,
 *   Jump, Reload, Hunter Sense with cooldown ring, Flashlight, Weapon Switch).
 */
@Composable
fun MobileControlsOverlay(
    hud: HudState,
    settings: GameSettings,
    engine: GameEngine
) {
    // Floating joystick visual state
    var joyActive by remember { mutableStateOf(false) }
    var joyOrigin by remember { mutableStateOf(Offset.Zero) }
    var joyThumb by remember { mutableStateOf(Offset.Zero) }
    var joySprintReady by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // =====================================================================
        // LAYER 1: MULTI-TOUCH ISOLATED JOYSTICK (LEFT) & LOOK PAD (RIGHT)
        // =====================================================================
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Touch Zone: Dynamic Floating Joystick
            Box(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        val maxRadiusPx = 68.dp.toPx()
                        val sprintLockOffsetPx = 96.dp.toPx()
                        awaitPointerEventScope {
                            var activePointer: PointerId? = null
                            var origin = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (activePointer == null && change.pressed && !change.isConsumed) {
                                        activePointer = change.id
                                        origin = change.position
                                        joyActive = true
                                        joyOrigin = origin
                                        joyThumb = origin
                                        joySprintReady = false
                                        change.consume()
                                    } else if (change.id == activePointer) {
                                        if (!change.pressed) {
                                            val lockSprint = joySprintReady
                                            activePointer = null
                                            joyActive = false
                                            joySprintReady = false
                                            if (lockSprint) {
                                                engine.setJoystick(0f, 1.0f, true)
                                            } else {
                                                engine.setJoystick(0f, 0f, false)
                                            }
                                            change.consume()
                                        } else {
                                            val dx = change.position.x - origin.x
                                            val dy = change.position.y - origin.y
                                            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                                            val clamped = dist.coerceAtMost(maxRadiusPx)
                                            val nx = (dx / dist) * (clamped / maxRadiusPx)
                                            val ny = -(dy / dist) * (clamped / maxRadiusPx) // screen up = forward (+1)
                                            joyThumb = Offset(
                                                origin.x + (dx / dist) * clamped,
                                                origin.y + (dy / dist) * clamped
                                            )
                                            joySprintReady = (-dy > sprintLockOffsetPx) && (kotlin.math.abs(nx) < 0.45f)
                                            engine.setJoystick(nx, ny, joySprintReady)
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                if (joyActive) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = 64.dp.toPx()
                        drawCircle(
                            color = GlassSurfaceDark,
                            radius = r,
                            center = joyOrigin
                        )
                        drawCircle(
                            color = if (joySprintReady) SunlitAmber else GlassBorder,
                            radius = r,
                            center = joyOrigin,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Slide-up Sprint Lock guide ring
                        val lockCenter = Offset(joyOrigin.x, joyOrigin.y - 96.dp.toPx())
                        drawCircle(
                            color = if (joySprintReady) SunlitAmber.copy(alpha = 0.35f) else GlassSurfaceDark,
                            radius = 18.dp.toPx(),
                            center = lockCenter
                        )
                        drawCircle(
                            color = if (joySprintReady) SunlitAmber else GlassBorder,
                            radius = 18.dp.toPx(),
                            center = lockCenter,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        // Thumb puck
                        drawCircle(
                            color = if (joySprintReady) SunlitAmber else BoneWhite.copy(alpha = 0.85f),
                            radius = 22.dp.toPx(),
                            center = joyThumb
                        )
                    }
                }
            }

            // Right Touch Zone: Precision Velocity-Curved Look Pad
            Box(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            var lookPointer: PointerId? = null
                            var lastPos = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (lookPointer == null && change.pressed && !change.isConsumed) {
                                        lookPointer = change.id
                                        lastPos = change.position
                                        change.consume()
                                    } else if (change.id == lookPointer) {
                                        if (!change.pressed) {
                                            lookPointer = null
                                            change.consume()
                                        } else {
                                            val delta = change.position - lastPos
                                            lastPos = change.position
                                            if (delta.x != 0f || delta.y != 0f) {
                                                engine.addTouchLookDelta(delta.x, delta.y)
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // =====================================================================
        // LAYER 2: ERGONOMIC TRANSLUCENT GLASSMORPHISM ACTION BUTTONS
        // =====================================================================
        if (settings.forceTouchUi) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Optional Left-Side Fire Button (top-left thumb arc for two-thumb aim+shoot)
                if (settings.leftFireButtonEnabled) {
                    FireActionButton(
                        hud = hud,
                        size = 64.dp,
                        tag = "left_fire_button",
                        onPress = { engine.onFirePressed() },
                        onRelease = { engine.onFireReleased() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 64.dp)
                    )
                }

                // Sprint Lock indicator pill (tap to cancel Sprint Lock)
                if (hud.sprintLocked) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SunlitAmber.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, SunlitAmber),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 116.dp, start = 12.dp)
                            .clickable { engine.setJoystick(0f, 0f, false) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = SunlitAmber, modifier = Modifier.size(16.dp))
                            Text("SPRINT LOCK", style = MaterialTheme.typography.labelSmall, color = BoneWhite)
                        }
                    }
                }

                // Vertical 4x-12x Optical Zoom Slider (appears on right side when scoped on Rifle)
                if (hud.scoped && hud.activeWeapon == WeaponType.RIFLE_308) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 6.dp, bottom = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("12x", style = MaterialTheme.typography.labelSmall, color = SunlitAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(136.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(GlassSurfaceDark)
                                .pointerInput(hud.scopeZoom) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        val deltaZoom = -dragAmount * 0.045f
                                        engine.setScopeZoomLevel(engine.scopeZoom + deltaZoom)
                                    }
                                }
                                .testTag("scope_zoom_slider"),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val trackTop = 16.dp.toPx()
                                val trackBot = size.height - 16.dp.toPx()
                                drawLine(
                                    color = GlassBorder,
                                    start = Offset(size.width / 2f, trackTop),
                                    end = Offset(size.width / 2f, trackBot),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                val frac = ((hud.scopeZoom - 4f) / 8f).coerceIn(0f, 1f)
                                val knobY = trackBot - frac * (trackBot - trackTop)
                                drawCircle(SunlitAmber, 10.dp.toPx(), Offset(size.width / 2f, knobY))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("4x", style = MaterialTheme.typography.labelSmall, color = SlateMist)
                    }
                }

                // Left-edge compact utility stack: Hunter Sense (with cooldown ring) + Flashlight
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HunterSenseButton(
                        hud = hud,
                        onClick = { engine.activateHunterSense() }
                    )
                    GlassCircleButton(
                        icon = if (hud.flashlightOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = stringResource(id = R.string.cd_flashlight),
                        active = hud.flashlightOn,
                        accent = SunlitAmber,
                        tag = "flashlight_button",
                        onClick = { engine.toggleFlashlight() }
                    )
                }

                // Bottom-Right Ergonomic Combat Cluster
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Upper row: Weapon Switch, Reload, Stance, Jump
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Bow Draw pill (appears only while drawing bowstring)
                        if (hud.bowDrawing) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = CrimsonEmber.copy(alpha = 0.82f),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable { engine.cancelBowDraw() }
                                    .testTag("cancel_bow_draw_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.cd_cancel_draw), tint = BoneWhite, modifier = Modifier.size(18.dp))
                                    Text("CANCEL", style = MaterialTheme.typography.labelSmall, color = BoneWhite)
                                }
                            }
                        }

                        // Hold Breath button (when scoped)
                        if (hud.scoped) {
                            HoldActionCircleButton(
                                icon = Icons.Filled.Air,
                                contentDescription = stringResource(id = R.string.cd_hold_breath),
                                active = hud.holdingBreath,
                                accent = HunterCyan,
                                label = "BREATH",
                                tag = "hold_breath_button",
                                onHoldChange = { engine.setHoldingBreath(it) }
                            )
                        }

                        GlassCircleButton(
                            icon = Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(id = R.string.cd_switch_weapon),
                            label = if (hud.activeWeapon == WeaponType.RIFLE_308) "BOW" else ".308",
                            tag = "weapon_switch_button",
                            onClick = { engine.switchWeapon() }
                        )

                        if (hud.activeWeapon == WeaponType.RIFLE_308) {
                            GlassCircleButton(
                                icon = Icons.Filled.Refresh,
                                contentDescription = stringResource(id = R.string.cd_reload),
                                active = hud.reloading,
                                accent = SunlitAmber,
                                tag = "reload_button",
                                onClick = { engine.reloadWeapon() }
                            )
                        }

                        GlassCircleButton(
                            icon = Icons.Filled.Height,
                            contentDescription = stringResource(id = R.string.cd_stance),
                            active = hud.stance != Stance.STAND,
                            accent = AlpineEmerald,
                            label = when (hud.stance) {
                                Stance.STAND -> "STD"
                                Stance.CROUCH -> "CRC"
                                Stance.PRONE -> "PRN"
                            },
                            tag = "stance_button",
                            onClick = { engine.cycleStance() }
                        )

                        GlassCircleButton(
                            icon = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(id = R.string.cd_jump),
                            tag = "jump_button",
                            onClick = { engine.jump() }
                        )
                    }

                    // Lower row: Optical Scope Toggle + Primary Fire Button (with Bow Tension Ring)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassCircleButton(
                            icon = Icons.Filled.GpsFixed,
                            contentDescription = stringResource(id = R.string.cd_scope),
                            active = hud.scoped,
                            accent = SunlitAmber,
                            size = 62.dp,
                            label = if (hud.scoped) "ADS" else "SCOPE",
                            tag = "scope_toggle_button",
                            onClick = { engine.toggleScope() }
                        )

                        FireActionButton(
                            hud = hud,
                            size = 80.dp,
                            tag = "primary_fire_button",
                            onPress = { engine.onFirePressed() },
                            onRelease = { engine.onFireReleased() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FireActionButton(
    hud: HudState,
    size: Dp,
    tag: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBow = hud.activeWeapon == WeaponType.RECURVE_BOW
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (hud.bowDrawing) SunlitAmber.copy(alpha = 0.34f)
                else CrimsonEmber.copy(alpha = 0.28f)
            )
            .pointerInput(isBow) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = if (hud.bowDrawing) SunlitAmber else CrimsonEmber.copy(alpha = 0.85f),
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Live circular bow draw tension progress ring
            if (isBow && hud.bowDrawTension > 0.01f) {
                val inset = 5.dp.toPx()
                drawArc(
                    color = SunlitAmber,
                    startAngle = -90f,
                    sweepAngle = 360f * hud.bowDrawTension,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - inset * 2f, this.size.height - inset * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isBow) (if (hud.bowDrawing) "${(hud.bowDrawTension * 100).toInt()}%" else "DRAW") else "FIRE",
                style = MaterialTheme.typography.labelLarge,
                color = BoneWhite
            )
        }
    }
}

@Composable
private fun HunterSenseButton(
    hud: HudState,
    onClick: () -> Unit
) {
    val ready = !hud.hunterSenseActive && hud.hunterSenseCooldownRatio <= 0.01f
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (hud.hunterSenseActive) HunterCyan.copy(alpha = 0.30f) else GlassSurfaceDark
            )
            .clickable(enabled = ready, onClick = onClick)
            .testTag("hunter_sense_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = if (hud.hunterSenseActive) HunterCyan else GlassBorder,
                style = Stroke(width = 2.dp.toPx())
            )
            val ratio = if (hud.hunterSenseActive) (hud.hunterSenseRemaining / 6f) else (1f - hud.hunterSenseCooldownRatio)
            if (hud.hunterSenseActive || !ready) {
                val inset = 4.dp.toPx()
                drawArc(
                    color = if (hud.hunterSenseActive) HunterCyan else SlateMist,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = stringResource(id = R.string.cd_hunter_sense),
                tint = if (hud.hunterSenseActive || ready) HunterCyan else SlateMist,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (hud.hunterSenseActive) "${hud.hunterSenseRemaining.toInt()}s" else "SENSE",
                style = MaterialTheme.typography.labelSmall,
                color = if (hud.hunterSenseActive || ready) BoneWhite else SlateMist
            )
        }
    }
}

@Composable
private fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    accent: Color = SunlitAmber,
    size: Dp = 50.dp,
    label: String? = null,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) accent.copy(alpha = 0.28f) else GlassSurfaceDark,
        border = BorderStroke(1.2.dp, if (active) accent else GlassBorder),
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) accent else BoneWhite,
                modifier = Modifier.size(20.dp)
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) BoneWhite else SlateMist
                )
            }
        }
    }
}

@Composable
private fun HoldActionCircleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accent: Color,
    label: String,
    tag: String,
    onHoldChange: (Boolean) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) accent.copy(alpha = 0.34f) else GlassSurfaceDark,
        border = BorderStroke(1.2.dp, if (active) accent else GlassBorder),
        modifier = Modifier
            .size(56.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onHoldChange(true)
                    waitForUpOrCancellation()
                    onHoldChange(false)
                }
            }
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = if (active) accent else BoneWhite, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = BoneWhite)
        }
    }
}
