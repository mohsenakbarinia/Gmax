package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.wildwoodDataStore: DataStore<Preferences> by preferencesDataStore(name = "wildwood_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val GRAPHICS = stringPreferencesKey("graphics_quality")
        val WEATHER = stringPreferencesKey("weather_preset")
        val NORMAL_SENS = floatPreferencesKey("normal_sensitivity")
        val ADS_SENS = floatPreferencesKey("ads_sensitivity")
        val GYRO_MODE = stringPreferencesKey("gyro_mode")
        val LEFT_FIRE = booleanPreferencesKey("left_fire_button")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val FORCE_TOUCH = booleanPreferencesKey("force_touch_ui")
        val VOLUME = floatPreferencesKey("master_volume")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
    }

    val settingsFlow: Flow<GameSettings> = context.wildwoodDataStore.data
        .map { prefs ->
            GameSettings(
                graphicsQuality = GraphicsQuality.fromRaw(prefs[Keys.GRAPHICS]),
                weatherPreset = WeatherPreset.fromRaw(prefs[Keys.WEATHER]),
                normalSensitivity = (prefs[Keys.NORMAL_SENS] ?: 1.0f).coerceIn(0.3f, 2.5f),
                adsSensitivity = (prefs[Keys.ADS_SENS] ?: 0.42f).coerceIn(0.15f, 1.5f),
                gyroMode = GyroMode.fromRaw(prefs[Keys.GYRO_MODE]),
                leftFireButtonEnabled = prefs[Keys.LEFT_FIRE] ?: false,
                hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
                forceTouchUi = prefs[Keys.FORCE_TOUCH] ?: true,
                masterVolume = (prefs[Keys.VOLUME] ?: 0.85f).coerceIn(0f, 1f),
                showFpsCounter = prefs[Keys.SHOW_FPS] ?: false
            )
        }
        .catch { emit(GameSettings()) }

    suspend fun update(transform: (GameSettings) -> GameSettings) {
        context.wildwoodDataStore.edit { prefs ->
            val current = GameSettings(
                graphicsQuality = GraphicsQuality.fromRaw(prefs[Keys.GRAPHICS]),
                weatherPreset = WeatherPreset.fromRaw(prefs[Keys.WEATHER]),
                normalSensitivity = prefs[Keys.NORMAL_SENS] ?: 1.0f,
                adsSensitivity = prefs[Keys.ADS_SENS] ?: 0.42f,
                gyroMode = GyroMode.fromRaw(prefs[Keys.GYRO_MODE]),
                leftFireButtonEnabled = prefs[Keys.LEFT_FIRE] ?: false,
                hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
                forceTouchUi = prefs[Keys.FORCE_TOUCH] ?: true,
                masterVolume = prefs[Keys.VOLUME] ?: 0.85f,
                showFpsCounter = prefs[Keys.SHOW_FPS] ?: false
            )
            val next = transform(current)
            prefs[Keys.GRAPHICS] = next.graphicsQuality.name
            prefs[Keys.WEATHER] = next.weatherPreset.name
            prefs[Keys.NORMAL_SENS] = next.normalSensitivity
            prefs[Keys.ADS_SENS] = next.adsSensitivity
            prefs[Keys.GYRO_MODE] = next.gyroMode.name
            prefs[Keys.LEFT_FIRE] = next.leftFireButtonEnabled
            prefs[Keys.HAPTICS] = next.hapticsEnabled
            prefs[Keys.FORCE_TOUCH] = next.forceTouchUi
            prefs[Keys.VOLUME] = next.masterVolume
            prefs[Keys.SHOW_FPS] = next.showFpsCounter
        }
    }
}
