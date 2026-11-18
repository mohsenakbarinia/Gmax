package com.example.engine

import com.example.data.WeaponType

data class Projectile(
    val weapon: WeaponType,
    val pos: Vec3,
    val prevPos: Vec3,
    val vel: Vec3,
    val shooterOrigin: Vec3,
    val damage: Float,
    var age: Float = 0f,
    var active: Boolean = true
)

data class EmbeddedArrow(
    val pos: Vec3,
    val dir: Vec3,
    var age: Float = 0f
)

data class EjectedShell(
    val pos: Vec3,
    val vel: Vec3,
    var spin: Float = 0f,
    var age: Float = 0f
)

/**
 * Manages first-person weapon animations, ballistic projectiles (.308 rifle + recurve bow),
 * brass casing ejection, muzzle flash light, and embedded arrow visuals.
 */
class WeaponSystem {
    val projectiles = ArrayList<Projectile>()
    val embeddedArrows = ArrayList<EmbeddedArrow>()
    val ejectedShells = ArrayList<EjectedShell>()

    var activeWeapon: WeaponType = WeaponType.RIFLE_308
    var rifleAmmo: Int = 5
    val rifleMagSize: Int = 5

    var reloading: Boolean = false
    var reloadTimer: Float = 0f
    val reloadDuration: Float = 2.15f

    var boltTimer: Float = 0f
    val boltDuration: Float = 0.85f
    var boltShellEjected: Boolean = true

    var recoilKick: Float = 0f
    var muzzleFlashTimer: Float = 0f

    var bowDrawing: Boolean = false
    var bowDrawTension: Float = 0f // 0..1
    var bowFullPulseSent: Boolean = false

    var walkBobPhase: Float = 0f
    var walkBobAmount: Float = 0f
    var adsBlend: Float = 0f // 0 hip -> 1 scoped

    fun reset() {
        projectiles.clear()
        embeddedArrows.clear()
        ejectedShells.clear()
        rifleAmmo = rifleMagSize
        reloading = false
        reloadTimer = 0f
        boltTimer = 0f
        boltShellEjected = true
        recoilKick = 0f
        muzzleFlashTimer = 0f
        bowDrawing = false
        bowDrawTension = 0f
        bowFullPulseSent = false
        adsBlend = 0f
    }

    fun switchWeapon() {
        cancelBowDraw()
        reloading = false
        activeWeapon = if (activeWeapon == WeaponType.RIFLE_308) {
            WeaponType.RECURVE_BOW
        } else {
            WeaponType.RIFLE_308
        }
    }

    fun startReload(onBoltSound: () -> Unit) {
        if (activeWeapon != WeaponType.RIFLE_308) return
        if (reloading || rifleAmmo >= rifleMagSize) return
        reloading = true
        reloadTimer = 0f
        onBoltSound()
    }

    fun cancelBowDraw() {
        bowDrawing = false
        bowDrawTension = 0f
        bowFullPulseSent = false
    }

    fun fireRifle(
        eyePos: Vec3,
        aimDir: Vec3,
        camRight: Vec3,
        onShotAudio: () -> Unit,
        onSpookWildlife: (Float, Float, Float) -> Unit,
        onRecoilHaptic: () -> Unit
    ): Boolean {
        if (activeWeapon != WeaponType.RIFLE_308) return false
        if (reloading || boltTimer > 0f) return false
        if (rifleAmmo <= 0) {
            startReload {}
            return false
        }
        rifleAmmo--
        boltTimer = boltDuration
        boltShellEjected = false
        recoilKick = 1.0f
        muzzleFlashTimer = 0.085f

        val startPos = Vec3(
            eyePos.x + aimDir.x * 0.65f,
            eyePos.y + aimDir.y * 0.65f,
            eyePos.z + aimDir.z * 0.65f
        )
        // Authentic .308 muzzle velocity (~240 m/s scaled for thrilling visible alpine valley drop)
        val muzzleSpeed = 245f
        projectiles.add(
            Projectile(
                weapon = WeaponType.RIFLE_308,
                pos = startPos.copy(),
                prevPos = startPos.copy(),
                vel = Vec3(aimDir.x * muzzleSpeed, aimDir.y * muzzleSpeed, aimDir.z * muzzleSpeed),
                shooterOrigin = eyePos.copy(),
                damage = 110f
            )
        )
        onShotAudio()
        onSpookWildlife(eyePos.x, eyePos.z, 180f)
        onRecoilHaptic()

        if (rifleAmmo == 0) {
            reloading = true
            reloadTimer = 0f
        }
        return true
    }

    fun releaseBowArrow(
        eyePos: Vec3,
        aimDir: Vec3,
        onReleaseAudio: (Float) -> Unit,
        onSpookWildlife: (Float, Float, Float) -> Unit,
        onReleaseHaptic: () -> Unit
    ): Boolean {
        if (activeWeapon != WeaponType.RECURVE_BOW || !bowDrawing) return false
        val power = bowDrawTension
        cancelBowDraw()
        if (power < 0.20f) return false

        val speed = Math3D.lerp(36f, 88f, power)
        val startPos = Vec3(
            eyePos.x + aimDir.x * 0.55f,
            eyePos.y + aimDir.y * 0.55f,
            eyePos.z + aimDir.z * 0.55f
        )
        projectiles.add(
            Projectile(
                weapon = WeaponType.RECURVE_BOW,
                pos = startPos.copy(),
                prevPos = startPos.copy(),
                vel = Vec3(aimDir.x * speed, aimDir.y * speed + 1.4f, aimDir.z * speed),
                shooterOrigin = eyePos.copy(),
                damage = Math3D.lerp(55f, 105f, power)
            )
        )
        recoilKick = 0.45f * power
        onReleaseAudio(power)
        // Bow is near-silent (14m spook radius only!)
        onSpookWildlife(eyePos.x, eyePos.z, 14f)
        onReleaseHaptic()
        return true
    }

    fun update(
        dt: Float,
        isScoped: Boolean,
        moveSpeedRatio: Float,
        eyePos: Vec3,
        camRight: Vec3,
        camForward: Vec3,
        windDirX: Float,
        windDirZ: Float,
        windSpeedMps: Float,
        terrain: Terrain,
        wildlife: WildlifeSystem,
        onBoltCycleAudio: () -> Unit,
        onBoltHaptic: () -> Unit,
        onFullDrawHaptic: () -> Unit,
        onHitAnimal: (AnimalHitResult, WeaponType) -> Unit
    ) {
        val targetAds = if (isScoped) 1f else 0f
        adsBlend = Math3D.lerp(adsBlend, targetAds, Math3D.clamp(dt * 11f, 0f, 1f))
        recoilKick = Math3D.lerp(recoilKick, 0f, Math3D.clamp(dt * 8.5f, 0f, 1f))
        if (muzzleFlashTimer > 0f) muzzleFlashTimer = (muzzleFlashTimer - dt).coerceAtLeast(0f)

        // Walking weapon bob
        walkBobAmount = Math3D.lerp(walkBobAmount, moveSpeedRatio, Math3D.clamp(dt * 8f, 0f, 1f))
        if (walkBobAmount > 0.02f) {
            walkBobPhase += dt * (6.2f + moveSpeedRatio * 5.2f)
        }

        // Bolt action cycle & spinning brass casing ejection
        if (boltTimer > 0f) {
            boltTimer -= dt
            if (!boltShellEjected && boltTimer < boltDuration * 0.58f) {
                boltShellEjected = true
                onBoltCycleAudio()
                onBoltHaptic()
                val shellPos = Vec3(
                    eyePos.x + camForward.x * 0.42f + camRight.x * 0.16f,
                    eyePos.y - 0.10f,
                    eyePos.z + camForward.z * 0.42f + camRight.z * 0.16f
                )
                val shellVel = Vec3(
                    camRight.x * 2.2f + camForward.x * 0.3f,
                    1.6f,
                    camRight.z * 2.2f + camForward.z * 0.3f
                )
                ejectedShells.add(EjectedShell(shellPos, shellVel))
            }
            if (boltTimer < 0f) boltTimer = 0f
        }

        // Reload timer
        if (reloading) {
            reloadTimer += dt
            if (reloadTimer >= reloadDuration) {
                reloading = false
                reloadTimer = 0f
                rifleAmmo = rifleMagSize
            }
        }

        // Bow draw tension accumulation
        if (bowDrawing && activeWeapon == WeaponType.RECURVE_BOW) {
            bowDrawTension = Math3D.clamp(bowDrawTension + dt * 0.92f, 0f, 1f)
            if (bowDrawTension >= 0.98f && !bowFullPulseSent) {
                bowFullPulseSent = true
                onFullDrawHaptic()
            }
        }

        // Update ejected brass casings
        var si = ejectedShells.size - 1
        while (si >= 0) {
            val s = ejectedShells[si]
            s.age += dt
            s.vel.y -= 9.81f * dt
            s.pos.x += s.vel.x * dt
            s.pos.y += s.vel.y * dt
            s.pos.z += s.vel.z * dt
            s.spin += dt * 16f
            if (s.age > 1.4f) ejectedShells.removeAt(si)
            si--
        }

        // Age embedded arrows
        var ai = embeddedArrows.size - 1
        while (ai >= 0) {
            embeddedArrows[ai].age += dt
            if (embeddedArrows[ai].age > 40f) embeddedArrows.removeAt(ai)
            ai--
        }

        // Step ballistic projectiles with realistic gravity drop & crosswind drift
        var pi = projectiles.size - 1
        while (pi >= 0) {
            val p = projectiles[pi]
            p.age += dt
            p.prevPos.copyFrom(p.pos)

            val gravity = if (p.weapon == WeaponType.RIFLE_308) 9.81f else 11.4f
            val windFactor = if (p.weapon == WeaponType.RIFLE_308) 0.18f else 0.42f
            p.vel.y -= gravity * dt
            p.vel.x += windDirX * windSpeedMps * windFactor * dt
            p.vel.z += windDirZ * windSpeedMps * windFactor * dt

            p.pos.x += p.vel.x * dt
            p.pos.y += p.vel.y * dt
            p.pos.z += p.vel.z * dt

            // Test hit against wildlife vital organs & body
            val hit = wildlife.checkProjectileHit(p.prevPos, p.pos, p.shooterOrigin, p.damage)
            if (hit != null) {
                if (p.weapon == WeaponType.RECURVE_BOW) {
                    val d = Vec3(p.vel.x, p.vel.y, p.vel.z).normalize()
                    if (embeddedArrows.size > 24) embeddedArrows.removeAt(0)
                    embeddedArrows.add(EmbeddedArrow(p.pos.copy(), d))
                }
                onHitAnimal(hit, p.weapon)
                projectiles.removeAt(pi)
                pi--
                continue
            }

            // Test terrain impact
            val groundY = terrain.getHeightAt(p.pos.x, p.pos.z)
            if (p.pos.y <= groundY || p.age > 4.5f) {
                if (p.weapon == WeaponType.RECURVE_BOW && p.pos.y <= groundY) {
                    val d = Vec3(p.vel.x, p.vel.y, p.vel.z).normalize()
                    if (embeddedArrows.size > 24) embeddedArrows.removeAt(0)
                    embeddedArrows.add(EmbeddedArrow(Vec3(p.pos.x, groundY + 0.04f, p.pos.z), d))
                }
                projectiles.removeAt(pi)
            }
            pi--
        }
    }
}
