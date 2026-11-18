package com.example.engine

import com.example.data.HitZone
import com.example.data.Species
import com.example.data.WeatherPreset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class AnimalState {
    GRAZING,
    WALKING,
    ALERT,
    FLEEING,
    CHARGING,
    DOWNED
}

data class TrackDecal(
    val x: Float,
    val y: Float,
    val z: Float,
    val isBlood: Boolean,
    var ageSeconds: Float = 0f
)

data class AnimalHitResult(
    val animal: WildlifeEntity,
    val hitZone: HitZone,
    val killed: Boolean,
    val distanceMeters: Int
)

class WildlifeEntity(
    val id: Int,
    val species: Species,
    val pos: Vec3,
    var yawRad: Float,
    var state: AnimalState = AnimalState.GRAZING
) {
    var health: Float = when (species) {
        Species.ROYAL_STAG -> 100f
        Species.WILD_BOAR -> 90f
        Species.TIMBER_WOLF -> 80f
    }
    var wounded: Boolean = false
    var stateTimer: Float = 3f
    var animPhase: Float = 0f
    var speed: Float = 0f
    var headPitch: Float = 0f
    var targetX: Float = pos.x
    var targetZ: Float = pos.z
    var trackTimer: Float = 0f
    var callTimer: Float = 8f + (id * 2.7f)
    var fallProgress: Float = 0f // 0 upright -> 1 lying downed
    var respawnTimer: Float = 0f

    val scale: Float = when (species) {
        Species.ROYAL_STAG -> 1.18f
        Species.WILD_BOAR -> 0.88f
        Species.TIMBER_WOLF -> 0.92f
    }

    fun getBodyCenter(out: Vec3 = Vec3()): Vec3 {
        val h = when (species) {
            Species.ROYAL_STAG -> 1.32f
            Species.WILD_BOAR -> 0.68f
            Species.TIMBER_WOLF -> 0.78f
        } * scale
        return out.set(pos.x, pos.y + h, pos.z)
    }

    fun getVitalHeartCenter(out: Vec3 = Vec3()): Vec3 {
        val fx = sin(yawRad)
        val fz = cos(yawRad)
        val forwardOff = when (species) {
            Species.ROYAL_STAG -> 0.42f
            Species.WILD_BOAR -> 0.28f
            Species.TIMBER_WOLF -> 0.30f
        } * scale
        val h = when (species) {
            Species.ROYAL_STAG -> 1.30f
            Species.WILD_BOAR -> 0.66f
            Species.TIMBER_WOLF -> 0.76f
        } * scale
        return out.set(pos.x + fx * forwardOff, pos.y + h, pos.z + fz * forwardOff)
    }

    fun getHeadCenter(out: Vec3 = Vec3()): Vec3 {
        val fx = sin(yawRad)
        val fz = cos(yawRad)
        val forwardOff = when (species) {
            Species.ROYAL_STAG -> 1.05f
            Species.WILD_BOAR -> 0.80f
            Species.TIMBER_WOLF -> 0.88f
        } * scale
        val grazeDrop = if (state == AnimalState.GRAZING) -0.45f else 0f
        val h = (when (species) {
            Species.ROYAL_STAG -> 1.88f
            Species.WILD_BOAR -> 0.76f
            Species.TIMBER_WOLF -> 0.96f
        } + grazeDrop) * scale
        return out.set(pos.x + fx * forwardOff, pos.y + h, pos.z + fz * forwardOff)
    }
}

/**
 * Manages the living alpine wildlife ecosystem:
 * - Royal Red Stags (cautious ridge/clearing grazers)
 * - Wild Forest Boars (lakeshore sounders, charge if wounded at close range)
 * - Timber Wolves (deep pine/night apex predators)
 * Includes upwind scent cone, noise detection, blood/footprint trails, and vital-organ hitboxes.
 */
class WildlifeSystem(private val terrain: Terrain) {
    val animals = ArrayList<WildlifeEntity>()
    val trackDecals = ArrayList<TrackDecal>()
    private val rng = Random(42077)
    private val maxTracks = 140

    init {
        spawnInitialPopulation()
    }

    fun reset() {
        animals.clear()
        trackDecals.clear()
        spawnInitialPopulation()
    }

    private fun spawnInitialPopulation() {
        var nextId = 1
        // 5 Royal Red Stags on scenic clearings & ridges (one visible down-valley from start!)
        val stagZones = listOf(
            Pair(56f, 18f),
            Pair(18f, -72f),
            Pair(-78f, 34f),
            Pair(92f, -40f),
            Pair(-52f, -88f)
        )
        for ((sx, sz) in stagZones) {
            val (px, pz) = findDrySpawn(sx, sz)
            animals.add(WildlifeEntity(nextId++, Species.ROYAL_STAG, Vec3(px, terrain.getHeightAt(px, pz), pz), rng.nextFloat() * 6.28f))
        }

        // 5 Wild Forest Boars near lake shore mud & brush
        val boarZones = listOf(
            Pair(34f, -12f),
            Pair(26f, -34f),
            Pair(-74f, -18f),
            Pair(-22f, 32f),
            Pair(-58f, 12f)
        )
        for ((bx, bz) in boarZones) {
            val (px, pz) = findDrySpawn(bx, bz)
            animals.add(WildlifeEntity(nextId++, Species.WILD_BOAR, Vec3(px, terrain.getHeightAt(px, pz), pz), rng.nextFloat() * 6.28f))
        }

        // 4 Timber Wolves in deep forest edges
        val wolfZones = listOf(
            Pair(-96f, -64f),
            Pair(88f, 68f),
            Pair(-84f, 82f),
            Pair(74f, -92f)
        )
        for ((wx, wz) in wolfZones) {
            val (px, pz) = findDrySpawn(wx, wz)
            animals.add(WildlifeEntity(nextId++, Species.TIMBER_WOLF, Vec3(px, terrain.getHeightAt(px, pz), pz), rng.nextFloat() * 6.28f))
        }
    }

    private fun findDrySpawn(prefX: Float, prefZ: Float): Pair<Float, Float> {
        var x = prefX
        var z = prefZ
        for (i in 0 until 20) {
            val h = terrain.getHeightAt(x, z)
            if (h > 0.9f && h < 22f) return Pair(x, z)
            x += (rng.nextFloat() - 0.5f) * 28f
            z += (rng.nextFloat() - 0.5f) * 28f
            x = Math3D.clamp(x, -165f, 165f)
            z = Math3D.clamp(z, -165f, 165f)
        }
        return Pair(58f, 12f)
    }

    fun spookRadius(originX: Float, originZ: Float, radius: Float) {
        val rSq = radius * radius
        for (a in animals) {
            if (a.state == AnimalState.DOWNED) continue
            val dx = a.pos.x - originX
            val dz = a.pos.z - originZ
            if (dx * dx + dz * dz <= rSq) {
                a.state = AnimalState.FLEEING
                a.stateTimer = 11f + rng.nextFloat() * 5f
                val awayYaw = atan2(dx, dz) + (rng.nextFloat() - 0.5f) * 0.6f
                a.targetX = Math3D.clamp(a.pos.x + sin(awayYaw) * 95f, -172f, 172f)
                a.targetZ = Math3D.clamp(a.pos.z + cos(awayYaw) * 95f, -172f, 172f)
            }
        }
    }

    fun update(
        dt: Float,
        playerPos: Vec3,
        playerNoise: Float,
        playerVisibility: Float,
        windDirX: Float,
        windDirZ: Float,
        weather: WeatherPreset,
        isPlaying: Boolean,
        onAnimalCall: (Species, Float) -> Unit,
        onPredatorThreat: () -> Unit
    ) {
        // Age & prune track decals
        var i = trackDecals.size - 1
        while (i >= 0) {
            val tr = trackDecals[i]
            tr.ageSeconds += dt
            if (tr.ageSeconds > (if (tr.isBlood) 95f else 52f)) {
                trackDecals.removeAt(i)
            }
            i--
        }

        for (a in animals) {
            if (a.state == AnimalState.DOWNED) {
                a.fallProgress = Math3D.clamp(a.fallProgress + dt * 2.4f, 0f, 1f)
                a.respawnTimer += dt
                if (a.respawnTimer > 42f) {
                    val (rx, rz) = findDrySpawn((rng.nextFloat() - 0.5f) * 280f, (rng.nextFloat() - 0.5f) * 280f)
                    a.pos.set(rx, terrain.getHeightAt(rx, rz), rz)
                    a.health = 100f
                    a.wounded = false
                    a.fallProgress = 0f
                    a.respawnTimer = 0f
                    a.state = AnimalState.GRAZING
                }
                continue
            }

            // Periodic distant animal calls (bugles, grunts, howls)
            a.callTimer -= dt
            if (a.callTimer <= 0f) {
                a.callTimer = 18f + rng.nextFloat() * 24f
                val dist = a.pos.distanceXZ(playerPos.x, playerPos.z)
                if (dist < 145f) {
                    onAnimalCall(a.species, dist)
                }
            }

            // Sensory checks during gameplay
            val dxP = a.pos.x - playerPos.x
            val dzP = a.pos.z - playerPos.z
            val distToPlayer = sqrt(dxP * dxP + dzP * dzP).coerceAtLeast(0.01f)
            val dirFromPlayerX = dxP / distToPlayer
            val dirFromPlayerZ = dzP / distToPlayer

            if (isPlaying) {
                // Upwind scent cone: if wind blows from player toward animal
                val windAlignment = dirFromPlayerX * windDirX + dirFromPlayerZ * windDirZ
                val scentDetected = windAlignment > 0.62f && distToPlayer < (if (a.species == Species.ROYAL_STAG) 72f else 52f)
                val noiseDetected = distToPlayer < (16f + playerNoise * 68f)
                val sightRange = (22f + playerVisibility * 58f) * (if (weather == WeatherPreset.MOONLIT_NIGHT) 0.72f else 1.0f)
                val sightDetected = distToPlayer < sightRange

                if (a.species == Species.TIMBER_WOLF && distToPlayer < (if (weather == WeatherPreset.MOONLIT_NIGHT) 38f else 22f) && a.state != AnimalState.FLEEING) {
                    a.state = AnimalState.CHARGING
                    a.stateTimer = 7f
                } else if (a.species == Species.WILD_BOAR && a.wounded && distToPlayer < 26f) {
                    a.state = AnimalState.CHARGING
                    a.stateTimer = 6f
                } else if ((scentDetected || noiseDetected || sightDetected) && a.state != AnimalState.FLEEING && a.state != AnimalState.CHARGING) {
                    if (a.state == AnimalState.ALERT && a.stateTimer < 1.2f) {
                        a.state = AnimalState.FLEEING
                        a.stateTimer = 10f
                        a.targetX = Math3D.clamp(a.pos.x + dirFromPlayerX * 92f, -172f, 172f)
                        a.targetZ = Math3D.clamp(a.pos.z + dirFromPlayerZ * 92f, -172f, 172f)
                    } else if (a.state != AnimalState.ALERT) {
                        a.state = AnimalState.ALERT
                        a.stateTimer = 2.6f
                    }
                }
            }

            // State execution
            a.stateTimer -= dt
            when (a.state) {
                AnimalState.GRAZING -> {
                    a.speed = Math3D.lerp(a.speed, 0f, dt * 4f)
                    a.headPitch = Math3D.lerp(a.headPitch, -0.58f, dt * 2.5f)
                    if (a.stateTimer <= 0f) {
                        a.state = AnimalState.WALKING
                        a.stateTimer = 6f + rng.nextFloat() * 8f
                        val ang = rng.nextFloat() * 6.283185f
                        val (tx, tz) = findDrySpawn(a.pos.x + sin(ang) * 34f, a.pos.z + cos(ang) * 34f)
                        a.targetX = tx; a.targetZ = tz
                    }
                }
                AnimalState.WALKING -> {
                    a.speed = Math3D.lerp(a.speed, 2.3f, dt * 3f)
                    a.headPitch = Math3D.lerp(a.headPitch, 0f, dt * 3f)
                    steerToward(a, a.targetX, a.targetZ, dt, 2.1f)
                    if (a.stateTimer <= 0f || a.pos.distanceXZ(a.targetX, a.targetZ) < 3.5f) {
                        a.state = AnimalState.GRAZING
                        a.stateTimer = 4f + rng.nextFloat() * 6f
                    }
                }
                AnimalState.ALERT -> {
                    a.speed = Math3D.lerp(a.speed, 0f, dt * 6f)
                    a.headPitch = Math3D.lerp(a.headPitch, 0.25f, dt * 4f)
                    // Look toward player
                    val lookYaw = atan2(-dxP, -dzP)
                    a.yawRad = lerpAngleRad(a.yawRad, lookYaw, dt * 3.8f)
                    if (a.stateTimer <= 0f) {
                        a.state = AnimalState.WALKING
                        a.stateTimer = 6f
                    }
                }
                AnimalState.FLEEING -> {
                    val maxSpd = when (a.species) {
                        Species.ROYAL_STAG -> 10.6f
                        Species.TIMBER_WOLF -> 9.8f
                        Species.WILD_BOAR -> 8.2f
                    } * (if (a.wounded) 0.72f else 1.0f)
                    a.speed = Math3D.lerp(a.speed, maxSpd, dt * 4.5f)
                    a.headPitch = Math3D.lerp(a.headPitch, 0.12f, dt * 4f)
                    steerToward(a, a.targetX, a.targetZ, dt, 4.5f)
                    if (a.stateTimer <= 0f || a.pos.distanceXZ(a.targetX, a.targetZ) < 5f) {
                        a.state = AnimalState.WALKING
                        a.stateTimer = 5f
                    }
                }
                AnimalState.CHARGING -> {
                    a.speed = Math3D.lerp(a.speed, 8.8f, dt * 4.5f)
                    a.headPitch = Math3D.lerp(a.headPitch, -0.15f, dt * 5f)
                    steerToward(a, playerPos.x, playerPos.z, dt, 5.2f)
                    if (distToPlayer < 4.5f) {
                        onPredatorThreat()
                        a.state = AnimalState.FLEEING
                        a.stateTimer = 7f
                        a.targetX = Math3D.clamp(a.pos.x + dirFromPlayerX * 65f, -172f, 172f)
                        a.targetZ = Math3D.clamp(a.pos.z + dirFromPlayerZ * 65f, -172f, 172f)
                    } else if (a.stateTimer <= 0f) {
                        a.state = AnimalState.WALKING
                        a.stateTimer = 5f
                    }
                }
                AnimalState.DOWNED -> Unit
            }

            // Integrate locomotion & keep out of deep lake water
            if (a.speed > 0.05f) {
                val nx = Math3D.clamp(a.pos.x + sin(a.yawRad) * a.speed * dt, -176f, 176f)
                val nz = Math3D.clamp(a.pos.z + cos(a.yawRad) * a.speed * dt, -176f, 176f)
                val nextH = terrain.getHeightAt(nx, nz)
                if (nextH > 0.15f) {
                    a.pos.set(nx, nextH, nz)
                } else {
                    a.yawRad += 1.8f * dt
                }
                a.animPhase += dt * (2.6f + a.speed * 1.15f)

                // Drop footprints or blood trails when moving
                a.trackTimer -= dt
                if (a.trackTimer <= 0f) {
                    a.trackTimer = if (a.wounded) 0.55f else 1.85f
                    addTrackDecal(a.pos.x, a.pos.y, a.pos.z, a.wounded)
                }
            }
        }
    }

    private fun steerToward(a: WildlifeEntity, tx: Float, tz: Float, dt: Float, turnRate: Float) {
        val desired = atan2(tx - a.pos.x, tz - a.pos.z)
        a.yawRad = lerpAngleRad(a.yawRad, desired, dt * turnRate)
    }

    private fun lerpAngleRad(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % 6.2831855f
        if (diff > 3.1415927f) diff -= 6.2831855f
        if (diff < -3.1415927f) diff += 6.2831855f
        return a + diff * Math3D.clamp(t, 0f, 1f)
    }

    private fun addTrackDecal(x: Float, y: Float, z: Float, isBlood: Boolean) {
        if (trackDecals.size >= maxTracks) {
            trackDecals.removeAt(0)
        }
        trackDecals.add(TrackDecal(x, y + 0.03f, z, isBlood))
    }

    /**
     * Sweeps a ballistic projectile segment [p0 -> p1] against Vital Organ zones (Heart, Head)
     * before Body hitboxes for authentic ethical hunting scoring.
     */
    fun checkProjectileHit(
        p0: Vec3,
        p1: Vec3,
        shooterPos: Vec3,
        damage: Float
    ): AnimalHitResult? {
        var bestT = 2f
        var bestAnimal: WildlifeEntity? = null
        var bestZone = HitZone.BODY

        val heartPos = Vec3()
        val headPos = Vec3()
        val bodyPos = Vec3()

        for (a in animals) {
            if (a.state == AnimalState.DOWNED) continue

            // Quick broad-phase check
            a.getBodyCenter(bodyPos)
            val broadR = 2.4f * a.scale
            if (Math3D.segmentSphereHit(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, bodyPos.x, bodyPos.y, bodyPos.z, broadR) < 0f) {
                continue
            }

            // 1. Heart / Lungs Vital Sphere
            a.getVitalHeartCenter(heartPos)
            val heartR = 0.44f * a.scale
            val tHeart = Math3D.segmentSphereHit(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, heartPos.x, heartPos.y, heartPos.z, heartR)
            if (tHeart >= 0f && tHeart < bestT) {
                bestT = tHeart; bestAnimal = a; bestZone = if (tHeart < 0.55f) HitZone.HEART else HitZone.LUNGS
                continue
            }

            // 2. Head Vital Sphere
            a.getHeadCenter(headPos)
            val headR = 0.36f * a.scale
            val tHead = Math3D.segmentSphereHit(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, headPos.x, headPos.y, headPos.z, headR)
            if (tHead >= 0f && tHead < bestT) {
                bestT = tHead; bestAnimal = a; bestZone = HitZone.HEAD
                continue
            }

            // 3. Torso Body Sphere
            val bodyR = 0.88f * a.scale
            val tBody = Math3D.segmentSphereHit(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, bodyPos.x, bodyPos.y, bodyPos.z, bodyR)
            if (tBody >= 0f && tBody < bestT) {
                bestT = tBody; bestAnimal = a; bestZone = HitZone.BODY
            }
        }

        val hitAnimal = bestAnimal ?: return null
        val distMeters = shooterPos.distanceTo(hitAnimal.pos).toInt().coerceAtLeast(1)

        val killed = if (bestZone.vital) {
            hitAnimal.health = 0f
            hitAnimal.state = AnimalState.DOWNED
            addTrackDecal(hitAnimal.pos.x, hitAnimal.pos.y, hitAnimal.pos.z, true)
            true
        } else {
            hitAnimal.health -= damage * 0.55f
            hitAnimal.wounded = true
            addTrackDecal(hitAnimal.pos.x, hitAnimal.pos.y, hitAnimal.pos.z, true)
            if (hitAnimal.health <= 0f) {
                hitAnimal.state = AnimalState.DOWNED
                true
            } else {
                // Wounded boar may charge at close range; others flee leaving blood trail
                if (hitAnimal.species == Species.WILD_BOAR && distMeters < 35) {
                    hitAnimal.state = AnimalState.CHARGING
                    hitAnimal.stateTimer = 7f
                } else {
                    hitAnimal.state = AnimalState.FLEEING
                    hitAnimal.stateTimer = 14f
                    val away = atan2(hitAnimal.pos.x - shooterPos.x, hitAnimal.pos.z - shooterPos.z)
                    hitAnimal.targetX = Math3D.clamp(hitAnimal.pos.x + sin(away) * 95f, -172f, 172f)
                    hitAnimal.targetZ = Math3D.clamp(hitAnimal.pos.z + cos(away) * 95f, -172f, 172f)
                }
                false
            }
        }

        return AnimalHitResult(hitAnimal, bestZone, killed, distMeters)
    }

    /** Laser rangefinder ray query against active wildlife (returns meters if aiming near an animal). */
    fun rangefinderQuery(origin: Vec3, dir: Vec3): Float? {
        var nearest = Float.MAX_VALUE
        val c = Vec3()
        for (a in animals) {
            if (a.state == AnimalState.DOWNED) continue
            a.getBodyCenter(c)
            val t = Math3D.raySphereHit(origin.x, origin.y, origin.z, dir.x, dir.y, dir.z, c.x, c.y, c.z, 1.35f * a.scale)
            if (t > 1f && t < nearest) {
                nearest = t
            }
        }
        return if (nearest < 320f) nearest else null
    }
}
