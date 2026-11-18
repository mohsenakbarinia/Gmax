package com.example.engine

import com.example.data.WeatherPreset

data class AtmosphereProfile(
    val sunDir: Vec3,
    val sunColor: Vec3,
    val skyZenith: Vec3,
    val skyHorizon: Vec3,
    val fogColor: Vec3,
    val fogDensity: Float,
    val wetness: Float,
    val nightFactor: Float,
    val cloudCoverage: Float,
    val exposure: Float,
    val colorGradeTint: Vec3
)

object AtmosphereCatalog {
    fun profileFor(weather: WeatherPreset): AtmosphereProfile = when (weather) {
        WeatherPreset.GOLDEN_HOUR -> AtmosphereProfile(
            sunDir = Vec3(0.68f, 0.28f, -0.68f).normalize(),
            sunColor = Vec3(1.55f, 1.06f, 0.62f),
            skyZenith = Vec3(0.18f, 0.36f, 0.64f),
            skyHorizon = Vec3(0.95f, 0.64f, 0.38f),
            fogColor = Vec3(0.74f, 0.56f, 0.40f),
            fogDensity = 0.0052f,
            wetness = 0.12f,
            nightFactor = 0.0f,
            cloudCoverage = 0.28f,
            exposure = 1.08f,
            colorGradeTint = Vec3(1.04f, 1.00f, 0.94f)
        )

        WeatherPreset.MISTY_DAWN -> AtmosphereProfile(
            sunDir = Vec3(-0.62f, 0.32f, -0.71f).normalize(),
            sunColor = Vec3(1.18f, 1.16f, 1.12f),
            skyZenith = Vec3(0.26f, 0.40f, 0.56f),
            skyHorizon = Vec3(0.72f, 0.80f, 0.84f),
            fogColor = Vec3(0.60f, 0.69f, 0.74f),
            fogDensity = 0.0088f,
            wetness = 0.52f,
            nightFactor = 0.0f,
            cloudCoverage = 0.46f,
            exposure = 1.04f,
            colorGradeTint = Vec3(0.95f, 1.01f, 1.06f)
        )

        WeatherPreset.RAINY_STORM -> AtmosphereProfile(
            sunDir = Vec3(0.32f, 0.68f, -0.65f).normalize(),
            sunColor = Vec3(0.55f, 0.62f, 0.72f),
            skyZenith = Vec3(0.12f, 0.16f, 0.22f),
            skyHorizon = Vec3(0.28f, 0.33f, 0.39f),
            fogColor = Vec3(0.24f, 0.29f, 0.34f),
            fogDensity = 0.0096f,
            wetness = 0.94f,
            nightFactor = 0.08f,
            cloudCoverage = 0.88f,
            exposure = 1.12f,
            colorGradeTint = Vec3(0.92f, 0.97f, 1.05f)
        )

        WeatherPreset.MOONLIT_NIGHT -> AtmosphereProfile(
            sunDir = Vec3(-0.45f, 0.62f, -0.64f).normalize(),
            sunColor = Vec3(0.36f, 0.48f, 0.74f),
            skyZenith = Vec3(0.02f, 0.04f, 0.09f),
            skyHorizon = Vec3(0.06f, 0.10f, 0.18f),
            fogColor = Vec3(0.05f, 0.08f, 0.14f),
            fogDensity = 0.0072f,
            wetness = 0.24f,
            nightFactor = 1.0f,
            cloudCoverage = 0.22f,
            exposure = 1.22f,
            colorGradeTint = Vec3(0.90f, 0.95f, 1.10f)
        )
    }
}
