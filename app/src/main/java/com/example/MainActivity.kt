package com.example

import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AuthManager
import com.example.data.GameMode
import com.example.data.WeaponType
import com.example.engine.GameEngine
import com.example.engine.WildwoodRenderer
import com.example.ui.AuthScreen
import com.example.ui.HudOverlay
import com.example.ui.MobileControlsOverlay
import com.example.ui.OpticalScopeOverlay
import com.example.ui.SettingsDrawerModal
import com.example.ui.StartScreen
import com.example.ui.TrophyLogModal
import com.example.ui.WildwoodViewModel
import com.example.ui.theme.WildwoodTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var engine: GameEngine
    private var glView: GLSurfaceView? = null
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var lastGyroTs = 0L

    // Hardware keyboard state (WASD + Shift testing fallback)
    private val pressedKeys = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        engine = GameEngine(applicationContext)
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            WildwoodTheme {
                WildwoodRoot(
                    engine = engine,
                    onAttachGlView = { view -> glView = view },
                    onRequestLandscape = {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        applyImmersiveFullscreen()
                    }
                )
            }
        }
        window.decorView.post { applyImmersiveFullscreen() }
    }

    private fun applyImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullscreen()
        glView?.onResume()
        gyroSensor?.let {
            lastGyroTs = 0L
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (engine.gameMode == GameMode.PLAYING) {
            engine.audio.start()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        engine.audio.stop()
        glView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.audio.stop()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_GYROSCOPE) return
        if (lastGyroTs != 0L) {
            val dt = ((event.timestamp - lastGyroTs) * 1e-9).toFloat().coerceIn(0f, 0.05f)
            // Landscape phone orientation maps gyro X/Y axes to yaw & pitch smoothly
            engine.addGyroDelta(event.values[0] * dt, event.values[1] * dt)
        }
        lastGyroTs = event.timestamp
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (engine.gameMode != GameMode.PLAYING) return super.onKeyDown(keyCode, event)
        pressedKeys.add(keyCode)
        syncKeyboardMovement()
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> { engine.jump(); return true }
            KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_CTRL_LEFT -> { engine.cycleStance(); return true }
            KeyEvent.KEYCODE_R -> { engine.reloadWeapon(); return true }
            KeyEvent.KEYCODE_Q -> { engine.activateHunterSense(); return true }
            KeyEvent.KEYCODE_F -> { engine.toggleFlashlight(); return true }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2 -> { engine.switchWeapon(); return true }
            KeyEvent.KEYCODE_ESCAPE -> { engine.pauseHunt(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        syncKeyboardMovement()
        return super.onKeyUp(keyCode, event)
    }

    private fun syncKeyboardMovement() {
        var fwd = 0f
        var right = 0f
        if (pressedKeys.contains(KeyEvent.KEYCODE_W)) fwd += 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_S)) fwd -= 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_D)) right += 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_A)) right -= 1f
        val sprint = pressedKeys.contains(KeyEvent.KEYCODE_SHIFT_LEFT)
        engine.setKeyboardMove(fwd, right, sprint)
    }
}

@Composable
private fun WildwoodRoot(
    engine: GameEngine,
    onAttachGlView: (GLSurfaceView) -> Unit,
    onRequestLandscape: () -> Unit
) {
    val hud by engine.hudState.collectAsStateWithLifecycle()
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Persistent live OpenGL ES 3.0 3D Alpine Valley Canvas behind both Auth & Gameplay
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    preserveEGLContextOnPause = true
                    setRenderer(WildwoodRenderer(engine))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    onAttachGlView(this)
                }
            }
        )

        if (currentUser == null) {
            AuthScreen(
                textureProgress = hud.textureProgress,
                texturesReady = hud.texturesReady,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        } else {
            val uid = currentUser!!.uid
            key(uid) {
                AuthenticatedWildwoodScreen(
                    uid = uid,
                    engine = engine,
                    onRequestLandscape = onRequestLandscape,
                    onSignedOut = {
                        engine.returnToTitle()
                        engine.audio.stop()
                        currentUser = null
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedWildwoodScreen(
    uid: String,
    engine: GameEngine,
    onRequestLandscape: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: WildwoodViewModel = viewModel(
        key = uid,
        factory = WildwoodViewModel.Factory(context, engine)
    )

    val hud by engine.hudState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val statsState by vm.statsState.collectAsStateWithLifecycle()
    val trophiesState by vm.trophiesState.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showTrophies by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettings || showTrophies || hud.gameMode == GameMode.PLAYING) {
        when {
            showSettings -> {
                showSettings = false
                if (hud.gameMode == GameMode.PAUSED) engine.resumeHunt()
            }
            showTrophies -> {
                showTrophies = false
                if (hud.gameMode == GameMode.PAUSED) engine.resumeHunt()
            }
            hud.gameMode == GameMode.PLAYING -> {
                engine.pauseHunt()
                showSettings = true
            }
        }
    }

    LaunchedEffect(syncMessage) {
        if (syncMessage != null) {
            delay(3500)
            vm.clearSyncMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (hud.gameMode) {
            GameMode.TITLE_ORBIT -> {
                StartScreen(
                    hud = hud,
                    settings = settings,
                    statsState = statsState,
                    onPlayFullscreen = {
                        onRequestLandscape()
                        engine.startHunt()
                    },
                    onSelectWeather = { preset -> vm.updateSettings { it.copy(weatherPreset = preset) } },
                    onSelectQuality = { quality -> vm.updateSettings { it.copy(graphicsQuality = quality) } },
                    onOpenTrophies = { showTrophies = true },
                    onOpenSettings = { showSettings = true }
                )
            }

            GameMode.PLAYING, GameMode.PAUSED -> {
                if (hud.scoped && hud.activeWeapon == WeaponType.RIFLE_308) {
                    OpticalScopeOverlay(hud = hud)
                }

                if (hud.gameMode == GameMode.PLAYING) {
                    MobileControlsOverlay(
                        hud = hud,
                        settings = settings,
                        engine = engine
                    )
                }

                HudOverlay(
                    hud = hud,
                    settings = settings,
                    statsState = statsState,
                    onOpenSettings = {
                        engine.pauseHunt()
                        showSettings = true
                    },
                    onOpenTrophies = {
                        engine.pauseHunt()
                        showTrophies = true
                    }
                )
            }
        }

        if (showSettings) {
            SettingsDrawerModal(
                settings = settings,
                gameMode = hud.gameMode,
                onUpdateSettings = { vm.updateSettings(it) },
                onResume = {
                    showSettings = false
                    engine.resumeHunt()
                },
                onRestart = {
                    showSettings = false
                    engine.resetValley(keepTitle = false)
                },
                onReturnTitle = {
                    showSettings = false
                    engine.returnToTitle()
                },
                onSignOut = {
                    showSettings = false
                    AuthManager.signOut(context, scope, onSignOutComplete = onSignedOut)
                },
                onDismiss = {
                    showSettings = false
                    if (hud.gameMode == GameMode.PAUSED) engine.resumeHunt()
                }
            )
        }

        if (showTrophies) {
            TrophyLogModal(
                statsState = statsState,
                trophiesState = trophiesState,
                onDeleteTrophy = { id -> vm.deleteTrophy(id) },
                onDismiss = {
                    showTrophies = false
                    if (hud.gameMode == GameMode.PAUSED) engine.resumeHunt()
                }
            )
        }

        if (syncMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(text = syncMessage!!)
            }
        }
    }
}
