package com.example.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.CompassMarker
import com.example.data.ContractCatalog
import com.example.data.GameMode
import com.example.data.GameSettings
import com.example.data.HarvestEvent
import com.example.data.HitZone
import com.example.data.HudState
import com.example.data.Stance
import com.example.data.TrophyBanner
import com.example.data.TrophyRating
import com.example.data.WeaponType
import com.example.data.WeatherPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Central 60Hz First-Person Hunting Simulation Engine.
 * Coordinates terrain physics, stealth visibility/noise, wind/scent vectors, weapons/ballistics,
 * articulated wildlife AI, Hunter Sense, spatial audio cues, tactile haptics, and throttled HUD snapshots.
 */
class GameEngine(context: Context) {
    val terrain = Terrain()
    val foliage = FoliageSystem(terrain)
    val wildlife = WildlifeSystem(terrain)
    val weapons = WeaponSystem()
    val audio = AudioEngine()
    val assetLoader = AssetLoader(context.applicationContext)

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    @Volatile var settings = GameSettings()
    @Volatile var gameMode = GameMode.TITLE_ORBIT
    @Volatile var activeContractIndex = 0

    // Player transform & physics
    val playerPos = Vec3(42f, 0f, 64f)
    val camPos = Vec3(42f, 10f, 64f)
    val camForward = Vec3(0f, 0f, -1f)
    val camRight = Vec3(1f, 0f, 0f)

    @Volatile var yawRad: Float = -2.35f // facing scenic alpine lake & clearing
    @Volatile var pitchRad: Float = -0.05f
    private var verticalVel = 0f
    private var grounded = true
    private var currentEyeHeight = Stance.STAND.eyeHeight
    @Volatile var stance = Stance.STAND

    // Movement & input state (updated from Compose touch/keyboard & sensors)
    @Volatile private var joyX = 0f
    @Volatile private var joyY = 0f
    @Volatile private var sprintLocked = false
    @Volatile private var keyForward = 0f
    @Volatile private var keyRight = 0f
    @Volatile private var keySprint = false
    private var lookDeltaYaw = 0f
    private var lookDeltaPitch = 0f
    private val lookLock = Any()

    // Scoped ADS & Breath Hold
    @Volatile var scoped = false
    @Volatile var scopeZoom = 6.0f
    @Volatile var holdingBreath = false
        private set
    private var breathStamina = 1.0f
    private var breathExhausted = false

    // Hunter Sense (6s pulse, 14s cooldown)
    private var hunterSenseRemaining = 0f
    private var hunterSenseCooldown = 0f
    val hunterSenseFactor: Float
        get() = Math3D.clamp(hunterSenseRemaining / 0.8f, 0f, 1f)

    // Tactical Flashlight
    @Volatile var flashlightOn = false

    // Wind & Weather dynamics
    var windDirectionDeg = 42f
        private set
    var windSpeedMps = 3.4f
        private set
    var windDirX = 0.67f
        private set
    var windDirZ = 0.74f
        private set

    var lightningFlash = 0f
        private set
    private var nextLightningTimer = 11f
    private val weatherRng = Random(5150)

    // Cinematic hit-stop, footsteps, predator flash, banner
    private var hitStopTimer = 0f
    private var stepAccum = 0f
    var woundedFlash = 0f
        private set
    private var statusAlert: String? = null
    private var statusAlertTimer = 0f
    private var activeBanner: TrophyBanner? = null
    private var bannerTimer = 0f

    // Title screen orbit timer & texture readiness
    var simTime = 0f
        private set
    private var titleOrbitAngle = 0.45f
    @Volatile var textureProgress = 0f
    @Volatile var texturesReady = false
    @Volatile var currentFps = 60

    // Callback to ViewModel/Firestore when an animal is harvested
    @Volatile var onHarvest: ((HarvestEvent, Boolean) -> Unit)? = null

    @Volatile var worldReady = false
        private set
    @Volatile private var pendingStartHunt = false

    private val _hudState = MutableStateFlow(HudState())
    val hudState: StateFlow<HudState> = _hudState.asStateFlow()
    private var hudPublishAccum = 0f

    init {
        Thread({
            terrain.prepareData()
            foliage.prepareData()
            resetValley(keepTitle = !pendingStartHunt)
            worldReady = true
            if (pendingStartHunt) {
                pendingStartHunt = false
                if (settings.weatherPreset == WeatherPreset.MOONLIT_NIGHT) {
                    flashlightOn = true
                }
                audio.start()
                publishHudImmediate()
            }
        }, "WildwoodWorldGen").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    fun resetValley(keepTitle: Boolean = false) {
        val sx = 42f
        val sz = 64f
        playerPos.set(sx, terrain.getHeightAt(sx, sz), sz)
        yawRad = -2.38f
        pitchRad = -0.06f
        verticalVel = 0f
        grounded = true
        stance = Stance.STAND
        currentEyeHeight = stance.eyeHeight
        scoped = false
        scopeZoom = 6.0f
        holdingBreath = false
        breathStamina = 1.0f
        breathExhausted = false
        sprintLocked = false
        hunterSenseRemaining = 0f
        hunterSenseCooldown = 0f
        woundedFlash = 0f
        activeBanner = null
        statusAlert = null
        weapons.reset()
        wildlife.reset()
        if (!keepTitle) {
            gameMode = GameMode.PLAYING
        }
        publishHudImmediate()
    }

    fun startHunt() {
        if (!worldReady) {
            pendingStartHunt = true
            return
        }
        if (gameMode == GameMode.TITLE_ORBIT) {
            resetValley(keepTitle = false)
        } else {
            gameMode = GameMode.PLAYING
        }
        if (settings.weatherPreset == WeatherPreset.MOONLIT_NIGHT) {
            flashlightOn = true
        }
        audio.start()
        publishHudImmediate()
    }

    fun pauseHunt() {
        if (gameMode == GameMode.PLAYING) {
            gameMode = GameMode.PAUSED
            joyX = 0f; joyY = 0f
            weapons.cancelBowDraw()
            holdingBreath = false
            publishHudImmediate()
        }
    }

    fun resumeHunt() {
        if (gameMode == GameMode.PAUSED) {
            gameMode = GameMode.PLAYING
            publishHudImmediate()
        }
    }

    fun returnToTitle() {
        gameMode = GameMode.TITLE_ORBIT
        scoped = false
        holdingBreath = false
        weapons.cancelBowDraw()
        joyX = 0f; joyY = 0f
        sprintLocked = false
        publishHudImmediate()
    }

    fun applySettings(newSettings: GameSettings) {
        val weatherChanged = settings.weatherPreset != newSettings.weatherPreset
        settings = newSettings
        audio.setMasterVolume(newSettings.masterVolume)
        if (weatherChanged && newSettings.weatherPreset == WeatherPreset.MOONLIT_NIGHT) {
            flashlightOn = true
        }
    }

    // --- Input hooks from MobileControls, Gyroscope & Keyboard ---
    fun setJoystick(x: Float, y: Float, lockedSprint: Boolean) {
        joyX = Math3D.clamp(x, -1f, 1f)
        joyY = Math3D.clamp(y, -1f, 1f)
        sprintLocked = lockedSprint
    }

    fun setKeyboardMove(forward: Float, right: Float, sprint: Boolean) {
        keyForward = forward; keyRight = right; keySprint = sprint
    }

    fun addTouchLookDelta(dxPx: Float, dyPx: Float) {
        if (gameMode != GameMode.PLAYING) return
        // Velocity-curved precision look: slow drags give crisp sniper control; fast swipes turn briskly
        val mag = sqrt(dxPx * dxPx + dyPx * dyPx)
        val curve = 0.65f + Math3D.clamp(mag / 26f, 0f, 1.45f)
        val sens = if (scoped) {
            settings.adsSensitivity * (4.0f / scopeZoom) * 0.0024f
        } else {
            settings.normalSensitivity * 0.0036f
        }
        synchronized(lookLock) {
            lookDeltaYaw -= dxPx * sens * curve
            lookDeltaPitch -= dyPx * sens * curve
        }
    }

    fun addGyroDelta(dYawRad: Float, dPitchRad: Float) {
        if (gameMode != GameMode.PLAYING) return
        val enabled = when (settings.gyroMode) {
            com.example.data.GyroMode.OFF -> false
            com.example.data.GyroMode.SCOPE_ONLY -> scoped
            com.example.data.GyroMode.ALWAYS_ON -> true
        }
        if (!enabled) return
        val mult = if (scoped) (4.2f / scopeZoom) * 0.72f else 0.85f
        synchronized(lookLock) {
            lookDeltaYaw += dYawRad * mult
            lookDeltaPitch += dPitchRad * mult
        }
    }

    fun onFirePressed() {
        if (gameMode != GameMode.PLAYING) return
        if (weapons.activeWeapon == WeaponType.RIFLE_308) {
            val aim = currentAimDirection()
            val fired = weapons.fireRifle(
                eyePos = camPos,
                aimDir = aim,
                camRight = camRight,
                onShotAudio = { audio.playRifleShot() },
                onSpookWildlife = { x, z, r -> wildlife.spookRadius(x, z, r) },
                onRecoilHaptic = { triggerHaptic(42L, 220) }
            )
            if (fired) {
                synchronized(lookLock) {
                    lookDeltaPitch += if (scoped) 0.022f else 0.045f
                    lookDeltaYaw += (weatherRng.nextFloat() - 0.5f) * 0.014f
                }
            }
        } else {
            weapons.bowDrawing = true
            weapons.bowFullPulseSent = false
            audio.playBowCreak()
        }
    }

    fun onFireReleased() {
        if (gameMode != GameMode.PLAYING) return
        if (weapons.activeWeapon == WeaponType.RECURVE_BOW && weapons.bowDrawing) {
            val aim = currentAimDirection()
            weapons.releaseBowArrow(
                eyePos = camPos,
                aimDir = aim,
                onReleaseAudio = { pwr -> audio.playBowRelease(pwr) },
                onSpookWildlife = { x, z, r -> wildlife.spookRadius(x, z, r) },
                onReleaseHaptic = { triggerHaptic(22L, 140) }
            )
        }
    }

    fun cancelBowDraw() {
        weapons.cancelBowDraw()
    }

    fun toggleScope() {
        if (gameMode != GameMode.PLAYING) return
        if (weapons.activeWeapon == WeaponType.RECURVE_BOW) {
            // Bow uses subtle zoomed focus rather than full 12x sniper optic
            scoped = !scoped
            scopeZoom = if (scoped) 2.2f else 6.0f
        } else {
            scoped = !scoped
            if (scoped && scopeZoom < 4f) scopeZoom = 6.0f
        }
        if (!scoped) holdingBreath = false
        publishHudImmediate()
    }

    fun setScopeZoomLevel(zoom: Float) {
        scopeZoom = Math3D.clamp(zoom, 4.0f, 12.0f)
    }

    fun setHoldingBreath(active: Boolean) {
        holdingBreath = active && scoped && !breathExhausted
    }

    fun cycleStance() {
        if (gameMode != GameMode.PLAYING) return
        stance = when (stance) {
            Stance.STAND -> Stance.CROUCH
            Stance.CROUCH -> Stance.PRONE
            Stance.PRONE -> Stance.STAND
        }
        sprintLocked = false
        publishHudImmediate()
    }

    fun jump() {
        if (gameMode != GameMode.PLAYING) return
        if (stance != Stance.STAND) {
            stance = Stance.STAND
            return
        }
        if (grounded) {
            verticalVel = 5.4f
            grounded = false
        }
    }

    fun reloadWeapon() {
        if (gameMode != GameMode.PLAYING) return
        weapons.startReload { audio.playBoltCycle() }
    }

    fun switchWeapon() {
        if (gameMode != GameMode.PLAYING) return
        scoped = false
        holdingBreath = false
        weapons.switchWeapon()
        publishHudImmediate()
    }

    fun toggleFlashlight() {
        flashlightOn = !flashlightOn
        publishHudImmediate()
    }

    fun activateHunterSense() {
        if (gameMode != GameMode.PLAYING) return
        if (hunterSenseRemaining > 0f || hunterSenseCooldown > 0f) return
        hunterSenseRemaining = 6.0f
        hunterSenseCooldown = 14.0f
        triggerHaptic(30L, 120)
        publishHudImmediate()
    }

    private fun triggerHaptic(durationMs: Long, amplitude: Int) {
        if (!settings.hapticsEnabled) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private var swayOffsetX = 0f
    private var swayOffsetY = 0f

    private fun currentAimDirection(): Vec3 {
        val effYaw = yawRad + swayOffsetX
        val effPitch = Math3D.clamp(pitchRad + swayOffsetY, -1.35f, 1.35f)
        val cp = cos(effPitch)
        return Vec3(-sin(effYaw) * cp, sin(effPitch), -cos(effYaw) * cp).normalize()
    }

    /** Called every GL frame on the render thread with clamped delta time. */
    fun step(rawDt: Float) {
        if (!worldReady) return
        val dt = Math3D.clamp(rawDt, 0.001f, 0.05f)

        // Subtle cinematic hit-stop on vital shots
        val effDt = if (hitStopTimer > 0f) {
            hitStopTimer -= dt
            dt * 0.22f
        } else dt

        simTime += effDt

        // Slowly drift alpine valley wind
        windDirectionDeg = Math3D.wrapDegrees(windDirectionDeg + sin(simTime * 0.07f) * 2.5f * effDt)
        val wRad = windDirectionDeg * Math3D.DEG_TO_RAD
        windDirX = sin(wRad)
        windDirZ = cos(wRad)
        windSpeedMps = when (settings.weatherPreset) {
            WeatherPreset.RAINY_STORM -> 6.8f + sin(simTime * 0.4f) * 1.6f
            WeatherPreset.GOLDEN_HOUR -> 3.2f + sin(simTime * 0.25f) * 0.9f
            WeatherPreset.MISTY_DAWN -> 2.1f + sin(simTime * 0.2f) * 0.7f
            WeatherPreset.MOONLIT_NIGHT -> 1.8f + sin(simTime * 0.18f) * 0.6f
        }

        // Storm lightning flash & rolling thunder
        if (settings.weatherPreset == WeatherPreset.RAINY_STORM) {
            nextLightningTimer -= effDt
            if (nextLightningTimer <= 0f) {
                lightningFlash = 1.0f
                nextLightningTimer = 14f + weatherRng.nextFloat() * 18f
                audio.playThunder()
            }
        }
        if (lightningFlash > 0f) {
            lightningFlash = (lightningFlash - effDt * 3.8f).coerceAtLeast(0f)
        }

        if (woundedFlash > 0f) woundedFlash = (woundedFlash - effDt * 1.8f).coerceAtLeast(0f)
        if (statusAlertTimer > 0f) {
            statusAlertTimer -= dt
            if (statusAlertTimer <= 0f) statusAlert = null
        }
        if (bannerTimer > 0f) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) activeBanner = null
        }

        if (gameMode == GameMode.TITLE_ORBIT) {
            updateTitleCamera(effDt)
            wildlife.update(
                dt = effDt,
                playerPos = camPos,
                playerNoise = 0f,
                playerVisibility = 0f,
                windDirX = windDirX,
                windDirZ = windDirZ,
                weather = settings.weatherPreset,
                isPlaying = false,
                onAnimalCall = { _, _ -> },
                onPredatorThreat = {}
            )
            val lakeProx = 1f - Math3D.smoothstep(8f, 62f, terrain.distanceToWaterEdge(camPos.x, camPos.z))
            audio.setEnvironment(settings.weatherPreset, windSpeedMps / 6f, lakeProx, false)
            publishHudThrottled(dt, 0f, 0f, false)
            return
        }

        if (gameMode == GameMode.PAUSED) {
            publishHudThrottled(dt, 0f, 0f, false)
            return
        }

        // --- ACTIVE HUNT SIMULATION ---
        // Apply accumulated touch + gyro look deltas
        synchronized(lookLock) {
            yawRad += lookDeltaYaw
            pitchRad = Math3D.clamp(pitchRad + lookDeltaPitch, -1.32f, 1.32f)
            lookDeltaYaw = 0f
            lookDeltaPitch = 0f
        }

        // Breathing & Scope sway
        if (holdingBreath && scoped && !breathExhausted) {
            breathStamina = (breathStamina - effDt * 0.22f).coerceAtLeast(0f)
            if (breathStamina <= 0.01f) {
                breathExhausted = true
                holdingBreath = false
            }
        } else {
            breathStamina = (breathStamina + effDt * 0.16f).coerceAtMost(1f)
            if (breathStamina > 0.35f) breathExhausted = false
        }

        val stanceStability = when (stance) {
            Stance.STAND -> 1.0f
            Stance.CROUCH -> 0.58f
            Stance.PRONE -> 0.28f
        }
        val swayAmp = (if (holdingBreath) 0.0012f else 0.0095f) * stanceStability * (if (scoped) 1.0f else 0.35f)
        swayOffsetX = sin(simTime * 1.15f) * swayAmp
        swayOffsetY = cos(simTime * 1.65f) * swayAmp * 0.72f

        val aimDir = currentAimDirection()
        camForward.copyFrom(aimDir)
        camRight.set(cos(yawRad), 0f, -sin(yawRad)).normalize()

        // Analog movement vector from floating joystick + keyboard fallback
        val inForward = Math3D.clamp(joyY + keyForward, -1f, 1f)
        val inRight = Math3D.clamp(joyX + keyRight, -1f, 1f)
        val rawMag = sqrt(inForward * inForward + inRight * inRight).coerceAtMost(1f)
        val wantsSprint = (sprintLocked || keySprint || (inForward > 0.85f && abs(inRight) < 0.45f)) && !scoped && !weapons.bowDrawing && stance == Stance.STAND
        if (inForward < 0.2f && !keySprint) sprintLocked = false

        val baseWalkSpeed = 4.5f
        val moveSpeed = if (wantsSprint) 7.6f else baseWalkSpeed * stance.speedMultiplier * (if (scoped) 0.48f else 1.0f)
        val fwdX = -sin(yawRad)
        val fwdZ = -cos(yawRad)
        val moveX = (fwdX * inForward + camRight.x * inRight) * moveSpeed
        val moveZ = (fwdZ * inForward + camRight.z * inRight) * moveSpeed

        var nextX = Math3D.clamp(playerPos.x + moveX * effDt, -188f, 188f)
        var nextZ = Math3D.clamp(playerPos.z + moveZ * effDt, -188f, 188f)

        // Prevent walking into deep lake water
        val nextTerrainH = terrain.getHeightAt(nextX, nextZ)
        if (nextTerrainH < Terrain.WATER_LEVEL - 0.55f) {
            nextX = playerPos.x
            nextZ = playerPos.z
        }

        // Smooth pushout against tree trunks
        val colliders = foliage.treeColliders
        for (i in 0 until colliders.size) {
            val c = colliders[i]
            val dx = nextX - c.x
            val dz = nextZ - c.y
            val minD = c.z + 0.45f
            val dSq = dx * dx + dz * dz
            if (dSq < minD * minD && dSq > 1e-5f) {
                val d = sqrt(dSq)
                val push = (minD - d) / d
                nextX += dx * push
                nextZ += dz * push
            }
        }

        val groundH = terrain.getHeightAt(nextX, nextZ)
        verticalVel -= 16.5f * effDt
        var nextY = playerPos.y + verticalVel * effDt
        if (nextY <= groundH) {
            nextY = groundH
            verticalVel = 0f
            grounded = true
        }
        playerPos.set(nextX, nextY, nextZ)

        currentEyeHeight = Math3D.lerp(currentEyeHeight, stance.eyeHeight, Math3D.clamp(effDt * 9f, 0f, 1f))
        val bobY = if (grounded) sin(weapons.walkBobPhase * 2f) * 0.045f * weapons.walkBobAmount * (if (scoped) 0.15f else 1f) else 0f
        camPos.set(playerPos.x, playerPos.y + currentEyeHeight + bobY, playerPos.z)

        // Surface-aware footsteps
        val actualMoveSpeed = rawMag * moveSpeed
        if (grounded && actualMoveSpeed > 0.35f) {
            stepAccum += actualMoveSpeed * effDt
            val strideLen = if (wantsSprint) 2.25f else 1.75f
            if (stepAccum >= strideLen) {
                stepAccum = 0f
                val surface = terrain.getSurfaceType(playerPos.x, playerPos.z)
                val footIntensity = Math3D.clamp((actualMoveSpeed / 7.5f) * stance.noiseMultiplier + 0.15f, 0.12f, 1.0f)
                audio.playFootstep(surface, footIntensity)
            }
        }

        // Stealth calculation (visibility & acoustic noise)
        val inBrush = foliage.isInBrushCover(playerPos.x, playerPos.z)
        val bushCoverMult = if (inBrush && stance != Stance.STAND) 0.35f else if (inBrush) 0.65f else 1.0f
        val visibility = Math3D.clamp(stance.visibilityMultiplier * bushCoverMult * (if (flashlightOn) 1.35f else 1.0f), 0.08f, 1.0f)
        val noise = Math3D.clamp((actualMoveSpeed / 7.2f) * stance.noiseMultiplier + (if (wantsSprint) 0.25f else 0f), 0.04f, 1.0f)

        // Hunter Sense timers
        if (hunterSenseRemaining > 0f) {
            hunterSenseRemaining = (hunterSenseRemaining - effDt).coerceAtLeast(0f)
        } else if (hunterSenseCooldown > 0f) {
            hunterSenseCooldown = (hunterSenseCooldown - effDt).coerceAtLeast(0f)
        }

        // Step Weapons & Ballistics
        weapons.update(
            dt = effDt,
            isScoped = scoped,
            moveSpeedRatio = Math3D.clamp(actualMoveSpeed / 6.5f, 0f, 1f),
            eyePos = camPos,
            camRight = camRight,
            camForward = camForward,
            windDirX = windDirX,
            windDirZ = windDirZ,
            windSpeedMps = windSpeedMps,
            terrain = terrain,
            wildlife = wildlife,
            onBoltCycleAudio = { audio.playBoltCycle() },
            onBoltHaptic = { triggerHaptic(18L, 110) },
            onFullDrawHaptic = { triggerHaptic(16L, 125) },
            onHitAnimal = { hit, usedWeapon -> handleAnimalHit(hit, usedWeapon) }
        )

        // Step Wildlife AI
        wildlife.update(
            dt = effDt,
            playerPos = playerPos,
            playerNoise = noise,
            playerVisibility = visibility,
            windDirX = windDirX,
            windDirZ = windDirZ,
            weather = settings.weatherPreset,
            isPlaying = true,
            onAnimalCall = { sp, dist -> audio.playWildlifeCall(sp, dist) },
            onPredatorThreat = {
                woundedFlash = 1.0f
                statusAlert = "Predator lunged nearby! Back away and steady aim."
                statusAlertTimer = 3.2f
                triggerHaptic(75L, 255)
            }
        )

        val lakeProx = 1f - Math3D.smoothstep(6f, 58f, terrain.distanceToWaterEdge(playerPos.x, playerPos.z))
        audio.setEnvironment(settings.weatherPreset, windSpeedMps / 6f, lakeProx, holdingBreath)

        publishHudThrottled(dt, visibility, noise, wantsSprint)
    }

    private fun handleAnimalHit(hit: AnimalHitResult, weapon: WeaponType) {
        if (hit.killed) {
            val rating = when {
                hit.hitZone.vital -> TrophyRating.GOLD
                hit.distanceMeters >= 60 -> TrophyRating.SILVER
                else -> TrophyRating.BRONZE
            }
            val vitalMult = if (hit.hitZone.vital) 2.0f else 1.0f
            val distBonus = (hit.distanceMeters * 2).coerceAtMost(450)
            val bowBonus = if (weapon == WeaponType.RECURVE_BOW) 150 else 0
            val score = ((hit.animal.species.basePoints * vitalMult).toInt() + distBonus + bowBonus).coerceIn(10, 10000)

            val activeContract = ContractCatalog.activeForIndex(activeContractIndex)
            val contractDone = activeContract.matches(hit.animal.species, weapon, hit.hitZone, hit.distanceMeters)

            if (hit.hitZone.vital) {
                hitStopTimer = 0.24f
                audio.playVitalConfirm()
                triggerHaptic(55L, 240)
            } else {
                triggerHaptic(28L, 160)
            }

            activeBanner = TrophyBanner(
                headline = "${hit.distanceMeters}m ${hit.hitZone.displayName} • ${rating.displayName}",
                subline = "${hit.animal.species.displayName} • +$score PTS" + if (contractDone) " • CONTRACT COMPLETE" else "",
                rating = rating,
                contractCompleted = contractDone,
                timestampMs = System.currentTimeMillis()
            )
            bannerTimer = 5.2f

            onHarvest?.invoke(
                HarvestEvent(
                    species = hit.animal.species,
                    weapon = weapon,
                    hitZone = hit.hitZone,
                    rating = rating,
                    distanceMeters = hit.distanceMeters,
                    scorePoints = score,
                    weatherPreset = settings.weatherPreset
                ),
                contractDone
            )
        } else {
            triggerHaptic(24L, 145)
            statusAlert = "Wounded ${hit.animal.species.displayName} (${hit.distanceMeters}m) — Track blood trail with Hunter Sense"
            statusAlertTimer = 4.2f
        }
        publishHudImmediate()
    }

    private fun updateTitleCamera(dt: Float) {
        titleOrbitAngle += dt * 0.085f
        val radius = 78f
        val cx = Terrain.LAKE_CENTER_X + cos(titleOrbitAngle) * radius
        val cz = Terrain.LAKE_CENTER_Z + sin(titleOrbitAngle) * radius
        val ground = terrain.getHeightAt(cx, cz)
        val cy = (ground + 13.5f).coerceAtLeast(15.5f)
        camPos.set(cx, cy, cz)

        val lookX = Terrain.LAKE_CENTER_X + 8f
        val lookY = 2.5f
        val lookZ = Terrain.LAKE_CENTER_Z + 4f
        camForward.set(lookX - cx, lookY - cy, lookZ - cz).normalize()
        camRight.set(-camForward.z, 0f, camForward.x).normalize()
    }

    private fun publishHudThrottled(dt: Float, visibility: Float, noise: Float, sprinting: Boolean) {
        hudPublishAccum += dt
        if (hudPublishAccum >= 0.05f) { // 20Hz smooth Compose updates
            hudPublishAccum = 0f
            publishHud(visibility, noise, sprinting)
        }
    }

    fun publishHudImmediate() {
        publishHud(0.35f, 0.1f, false)
    }

    private fun publishHud(visibility: Float, noise: Float, sprinting: Boolean) {
        val headingDeg = Math3D.wrapDegrees((-yawRad) * Math3D.RAD_TO_DEG)

        // Laser rangefinder while scoped
        val lrfMeters = if (scoped && gameMode == GameMode.PLAYING) {
            val animalDist = wildlife.rangefinderQuery(camPos, camForward)
            if (animalDist != null) {
                animalDist.toInt()
            } else {
                // Raymarch terrain distance for crisp real-world rangefinder readout
                var dist = 250
                var t = 4f
                while (t < 240f) {
                    val rx = camPos.x + camForward.x * t
                    val ry = camPos.y + camForward.y * t
                    val rz = camPos.z + camForward.z * t
                    if (ry <= terrain.getHeightAt(rx, rz)) {
                        dist = t.toInt()
                        break
                    }
                    t += 4.5f
                }
                dist
            }
        } else null

        // Compass track / wildlife acoustic markers (enhanced during Hunter Sense)
        val markers = ArrayList<CompassMarker>(6)
        val senseRange = if (hunterSenseRemaining > 0f) 160f else 68f
        for (a in wildlife.animals) {
            if (a.state == AnimalState.DOWNED) continue
            val dx = a.pos.x - playerPos.x
            val dz = a.pos.z - playerPos.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist < senseRange && (hunterSenseRemaining > 0f || a.wounded || a.state == AnimalState.ALERT || a.state == AnimalState.CHARGING)) {
                val bearing = Math3D.wrapDegrees(atan2(dx, -dz) * Math3D.RAD_TO_DEG)
                markers.add(
                    CompassMarker(
                        bearingDegrees = bearing,
                        distanceMeters = dist.toInt(),
                        kind = if (a.wounded) "BLOOD" else if (hunterSenseRemaining > 0f) "TRACK" else "CALL"
                    )
                )
            }
        }

        _hudState.value = HudState(
            gameMode = gameMode,
            textureProgress = textureProgress,
            texturesReady = texturesReady,
            headingDegrees = headingDeg,
            windDirectionDegrees = windDirectionDeg,
            windSpeedMps = windSpeedMps,
            stance = stance,
            sprintLocked = sprintLocked,
            sprinting = sprinting,
            stealthVisibility = visibility,
            stealthNoise = noise,
            activeWeapon = weapons.activeWeapon,
            rifleAmmo = weapons.rifleAmmo,
            rifleMagSize = weapons.rifleMagSize,
            reloading = weapons.reloading,
            reloadProgress = if (weapons.reloading) (weapons.reloadTimer / weapons.reloadDuration).coerceIn(0f, 1f) else 0f,
            bowDrawing = weapons.bowDrawing,
            bowDrawTension = weapons.bowDrawTension,
            scoped = scoped,
            scopeZoom = scopeZoom,
            rangefinderMeters = lrfMeters,
            holdingBreath = holdingBreath,
            breathStamina = breathStamina,
            hunterSenseActive = hunterSenseRemaining > 0f,
            hunterSenseRemaining = hunterSenseRemaining,
            hunterSenseCooldownRatio = if (hunterSenseRemaining > 0f) 1f else (hunterSenseCooldown / 14f).coerceIn(0f, 1f),
            flashlightOn = flashlightOn,
            fps = currentFps,
            compassMarkers = markers,
            statusAlert = statusAlert,
            trophyBanner = activeBanner,
            woundedWarning = woundedFlash > 0.1f,
            scopeSwayX = swayOffsetX,
            scopeSwayY = swayOffsetY
        )
    }
}
