package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class WeatherPreset(val displayName: String, val subtitle: String) {
    GOLDEN_HOUR("Golden Hour", "Low amber sun & god rays"),
    MISTY_DAWN("Misty Dawn", "Cool alpine mist & wet sheen"),
    RAINY_STORM("Rainy Storm", "Overcast rain & distant thunder"),
    MOONLIT_NIGHT("Moonlit Night", "Indigo night, fireflies & flashlight");

    companion object {
        fun fromRaw(raw: String?): WeatherPreset =
            entries.firstOrNull { it.name == raw } ?: GOLDEN_HOUR
    }
}

enum class GraphicsQuality(
    val displayName: String,
    val renderScale: Float,
    val shadowSize: Int,
    val grassDensity: Float,
    val bloomEnabled: Boolean,
    val godRaysEnabled: Boolean
) {
    BALANCED("Balanced (60 FPS)", 0.78f, 1024, 0.55f, false, false),
    ULTRA_REALISTIC("Ultra Realistic", 0.92f, 1536, 0.85f, true, true),
    PHOTO_MAX("Photo Mode Max", 1.0f, 2048, 1.0f, true, true);

    companion object {
        fun fromRaw(raw: String?): GraphicsQuality =
            entries.firstOrNull { it.name == raw } ?: ULTRA_REALISTIC
    }
}

enum class GyroMode(val displayName: String) {
    OFF("Off"),
    SCOPE_ONLY("Scope Only"),
    ALWAYS_ON("Always On");

    companion object {
        fun fromRaw(raw: String?): GyroMode =
            entries.firstOrNull { it.name == raw } ?: SCOPE_ONLY
    }
}

enum class WeaponType(val displayName: String, val shortLabel: String) {
    RIFLE_308(".308 Bolt Rifle", ".308"),
    RECURVE_BOW("Carbon Recurve Bow", "BOW");

    companion object {
        fun fromRaw(raw: String?): WeaponType =
            entries.firstOrNull { it.name == raw } ?: RIFLE_308
    }
}

enum class Stance(val displayName: String, val eyeHeight: Float, val speedMultiplier: Float, val noiseMultiplier: Float, val visibilityMultiplier: Float) {
    STAND("Stand", 1.72f, 1.0f, 1.0f, 1.0f),
    CROUCH("Crouch", 1.12f, 0.56f, 0.42f, 0.52f),
    PRONE("Prone", 0.52f, 0.30f, 0.16f, 0.22f)
}

enum class Species(val displayName: String, val basePoints: Int) {
    ROYAL_STAG("Royal Red Stag", 450),
    WILD_BOAR("Wild Forest Boar", 300),
    TIMBER_WOLF("Timber Wolf", 400);

    companion object {
        fun fromRaw(raw: String?): Species =
            entries.firstOrNull { it.name == raw } ?: ROYAL_STAG
    }
}

enum class HitZone(val displayName: String, val vital: Boolean) {
    HEART("HEART SHOT", true),
    LUNGS("LUNG SHOT", true),
    HEAD("HEAD SHOT", true),
    BODY("BODY SHOT", false);

    companion object {
        fun fromRaw(raw: String?): HitZone =
            entries.firstOrNull { it.name == raw } ?: BODY
    }
}

enum class TrophyRating(val displayName: String) {
    GOLD("GOLD TROPHY"),
    SILVER("SILVER TROPHY"),
    BRONZE("BRONZE TROPHY");

    companion object {
        fun fromRaw(raw: String?): TrophyRating =
            entries.firstOrNull { it.name == raw } ?: BRONZE
    }
}

enum class SurfaceType {
    GRASS,
    DIRT,
    ROCK,
    SHALLOW_WATER
}

enum class GameMode {
    TITLE_ORBIT,
    PLAYING,
    PAUSED
}

data class HuntingContract(
    val id: Int,
    val title: String,
    val description: String,
    val requiredSpecies: Species?,
    val requiredWeapon: WeaponType?,
    val requireVital: Boolean,
    val minDistanceMeters: Int,
    val maxDistanceMeters: Int
) {
    fun matches(species: Species, weapon: WeaponType, hitZone: HitZone, distanceMeters: Int): Boolean {
        if (requiredSpecies != null && requiredSpecies != species) return false
        if (requiredWeapon != null && requiredWeapon != weapon) return false
        if (requireVital && !hitZone.vital) return false
        if (distanceMeters < minDistanceMeters) return false
        if (distanceMeters > maxDistanceMeters) return false
        return true
    }
}

object ContractCatalog {
    val contracts: List<HuntingContract> = listOf(
        HuntingContract(
            id = 0,
            title = "Alpine Marksman",
            description = "Harvest a Royal Red Stag from >85m with a clean Vital Shot",
            requiredSpecies = Species.ROYAL_STAG,
            requiredWeapon = null,
            requireVital = true,
            minDistanceMeters = 85,
            maxDistanceMeters = 1500
        ),
        HuntingContract(
            id = 1,
            title = "Silent Bow Stalk",
            description = "Silent Bow Hunt: Harvest a Wild Boar within 55m",
            requiredSpecies = Species.WILD_BOAR,
            requiredWeapon = WeaponType.RECURVE_BOW,
            requireVital = false,
            minDistanceMeters = 1,
            maxDistanceMeters = 55
        ),
        HuntingContract(
            id = 2,
            title = "Apex Predator",
            description = "Harvest a Timber Wolf with a clean Vital Shot",
            requiredSpecies = Species.TIMBER_WOLF,
            requiredWeapon = null,
            requireVital = true,
            minDistanceMeters = 1,
            maxDistanceMeters = 1500
        ),
        HuntingContract(
            id = 3,
            title = "Long Valley Shot",
            description = "Harvest any game from >110m using the .308 Bolt Rifle",
            requiredSpecies = null,
            requiredWeapon = WeaponType.RIFLE_308,
            requireVital = false,
            minDistanceMeters = 110,
            maxDistanceMeters = 1500
        ),
        HuntingContract(
            id = 4,
            title = "Ghost of the Pines",
            description = "Harvest a Royal Red Stag with the Recurve Bow",
            requiredSpecies = Species.ROYAL_STAG,
            requiredWeapon = WeaponType.RECURVE_BOW,
            requireVital = false,
            minDistanceMeters = 1,
            maxDistanceMeters = 1500
        ),
        HuntingContract(
            id = 5,
            title = "Ethical Precision",
            description = "Land any Vital Shot from >60m in the alpine valley",
            requiredSpecies = null,
            requiredWeapon = null,
            requireVital = true,
            minDistanceMeters = 60,
            maxDistanceMeters = 1500
        )
    )

    fun activeForIndex(index: Int): HuntingContract =
        contracts[Math.floorMod(index, contracts.size)]
}

data class GameSettings(
    val graphicsQuality: GraphicsQuality = GraphicsQuality.ULTRA_REALISTIC,
    val weatherPreset: WeatherPreset = WeatherPreset.GOLDEN_HOUR,
    val normalSensitivity: Float = 1.0f,
    val adsSensitivity: Float = 0.42f,
    val gyroMode: GyroMode = GyroMode.SCOPE_ONLY,
    val leftFireButtonEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val forceTouchUi: Boolean = true,
    val masterVolume: Float = 0.85f,
    val showFpsCounter: Boolean = false
)

data class CompassMarker(
    val bearingDegrees: Float,
    val distanceMeters: Int,
    val kind: String // "TRACK", "CALL", "BLOOD"
)

data class TrophyBanner(
    val headline: String,
    val subline: String,
    val rating: TrophyRating,
    val contractCompleted: Boolean,
    val timestampMs: Long
)

data class HudState(
    val gameMode: GameMode = GameMode.TITLE_ORBIT,
    val textureProgress: Float = 0f,
    val texturesReady: Boolean = false,
    val headingDegrees: Float = 0f,
    val windDirectionDegrees: Float = 45f,
    val windSpeedMps: Float = 3.4f,
    val stance: Stance = Stance.STAND,
    val sprintLocked: Boolean = false,
    val sprinting: Boolean = false,
    val stealthVisibility: Float = 0.35f,
    val stealthNoise: Float = 0.1f,
    val activeWeapon: WeaponType = WeaponType.RIFLE_308,
    val rifleAmmo: Int = 5,
    val rifleMagSize: Int = 5,
    val reloading: Boolean = false,
    val reloadProgress: Float = 0f,
    val bowDrawing: Boolean = false,
    val bowDrawTension: Float = 0f,
    val scoped: Boolean = false,
    val scopeZoom: Float = 6.0f,
    val rangefinderMeters: Int? = null,
    val holdingBreath: Boolean = false,
    val breathStamina: Float = 1.0f,
    val hunterSenseActive: Boolean = false,
    val hunterSenseRemaining: Float = 0f,
    val hunterSenseCooldownRatio: Float = 0f,
    val flashlightOn: Boolean = false,
    val fps: Int = 60,
    val compassMarkers: List<CompassMarker> = emptyList(),
    val statusAlert: String? = null,
    val trophyBanner: TrophyBanner? = null,
    val woundedWarning: Boolean = false,
    val scopeSwayX: Float = 0f,
    val scopeSwayY: Float = 0f
)

data class HarvestEvent(
    val species: Species,
    val weapon: WeaponType,
    val hitZone: HitZone,
    val rating: TrophyRating,
    val distanceMeters: Int,
    val scorePoints: Int,
    val weatherPreset: WeatherPreset
)

@Keep
@IgnoreExtraProperties
data class UserProfileStats(
    @DocumentId val id: String = "",
    val userId: String = "",
    val totalHarvests: Long = 0L,
    val goldTrophies: Long = 0L,
    val longestVitalShotMeters: Long = 0L,
    val completedContractsCount: Long = 0L,
    val activeContractIndex: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TrophyRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val species: String = Species.ROYAL_STAG.name,
    val weapon: String = WeaponType.RIFLE_308.name,
    val hitZone: String = HitZone.HEART.name,
    val rating: String = TrophyRating.GOLD.name,
    val distanceMeters: Long = 0L,
    val scorePoints: Long = 0L,
    val weatherPreset: String = WeatherPreset.GOLDEN_HOUR.name,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
