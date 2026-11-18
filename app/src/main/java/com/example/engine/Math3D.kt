package com.example.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(nx: Float, ny: Float, nz: Float): Vec3 {
        x = nx; y = ny; z = nz
        return this
    }

    fun copyFrom(o: Vec3): Vec3 = set(o.x, o.y, o.z)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun lengthSq(): Float = x * x + y * y + z * z

    fun normalize(): Vec3 {
        val len = length()
        if (len > 1e-6f) {
            x /= len; y /= len; z /= len
        }
        return this
    }

    fun distanceTo(o: Vec3): Float {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun distanceXZ(ox: Float, oz: Float): Float {
        val dx = x - ox
        val dz = z - oz
        return sqrt(dx * dx + dz * dz)
    }

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
}

object Math3D {
    const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
    const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    fun clamp(v: Float, min: Float, max: Float): Float =
        if (v < min) min else if (v > max) max else v

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun wrapDegrees(deg: Float): Float {
        var r = deg % 360f
        if (r < 0f) r += 360f
        return r
    }

    fun shortestAngleDiffDeg(from: Float, to: Float): Float {
        var diff = (to - from) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    fun dot(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float =
        ax * bx + ay * by + az * bz

    /**
     * Tests whether the line segment [p0 -> p1] intersects a sphere centered at (cx, cy, cz) with radius r.
     * Returns hit fraction t in [0, 1] or -1f if no hit.
     */
    fun segmentSphereHit(
        p0x: Float, p0y: Float, p0z: Float,
        p1x: Float, p1y: Float, p1z: Float,
        cx: Float, cy: Float, cz: Float,
        r: Float
    ): Float {
        val dx = p1x - p0x
        val dy = p1y - p0y
        val dz = p1z - p0z
        val fx = p0x - cx
        val fy = p0y - cy
        val fz = p0z - cz

        val a = dx * dx + dy * dy + dz * dz
        if (a < 1e-8f) {
            return if (fx * fx + fy * fy + fz * fz <= r * r) 0f else -1f
        }
        val b = 2f * (fx * dx + fy * dy + fz * dz)
        val c = fx * fx + fy * fy + fz * fz - r * r
        val disc = b * b - 4f * a * c
        if (disc < 0f) return -1f
        val sqrtDisc = sqrt(disc)
        val t1 = (-b - sqrtDisc) / (2f * a)
        val t2 = (-b + sqrtDisc) / (2f * a)
        if (t1 in 0f..1f) return t1
        if (t2 in 0f..1f) return t2
        return -1f
    }

    /**
     * Ray-sphere intersection along ray origin + dir * t (t >= 0, dir normalized).
     * Returns distance t or -1f if miss.
     */
    fun raySphereHit(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        cx: Float, cy: Float, cz: Float,
        r: Float
    ): Float {
        val fx = ox - cx
        val fy = oy - cy
        val fz = oz - cz
        val b = fx * dx + fy * dy + fz * dz
        val c = fx * fx + fy * fy + fz * fz - r * r
        val disc = b * b - c
        if (disc < 0f) return -1f
        val t = -b - sqrt(disc)
        return if (t >= 0f) t else -1f
    }
}

/**
 * Deterministic fast value/gradient noise & multi-octave FBM for terrain and 512px PBR textures.
 */
object Noise2D {
    private fun hash(ix: Int, iy: Int, seed: Int): Float {
        var n = ix * 374761393 + iy * 668265263 + seed * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7FFFFFFF).toFloat() / 2147483647.0f
    }

    fun valueNoise(x: Float, y: Float, seed: Int = 42): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3f - 2f * xf)
        val v = yf * yf * (3f - 2f * yf)
        val a = hash(xi, yi, seed)
        val b = hash(xi + 1, yi, seed)
        val c = hash(xi, yi + 1, seed)
        val d = hash(xi + 1, yi + 1, seed)
        return Math3D.lerp(Math3D.lerp(a, b, u), Math3D.lerp(c, d, u), v)
    }

    /** Tileable value noise with integer period for seamless PBR texture synthesis. */
    fun tileableNoise(x: Float, y: Float, periodX: Int, periodY: Int, seed: Int = 17): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3f - 2f * xf)
        val v = yf * yf * (3f - 2f * yf)

        val x0 = Math.floorMod(xi, periodX)
        val x1 = Math.floorMod(xi + 1, periodX)
        val y0 = Math.floorMod(yi, periodY)
        val y1 = Math.floorMod(yi + 1, periodY)

        val a = hash(x0, y0, seed)
        val b = hash(x1, y0, seed)
        val c = hash(x0, y1, seed)
        val d = hash(x1, y1, seed)
        return Math3D.lerp(Math3D.lerp(a, b, u), Math3D.lerp(c, d, u), v)
    }

    fun fbm(x: Float, y: Float, octaves: Int = 5, seed: Int = 42): Float {
        var total = 0f
        var amp = 0.5f
        var freq = 1f
        var norm = 0f
        for (i in 0 until octaves) {
            total += valueNoise(x * freq, y * freq, seed + i * 31) * amp
            norm += amp
            amp *= 0.5f
            freq *= 2.02f
        }
        return total / norm
    }

    fun ridgedFbm(x: Float, y: Float, octaves: Int = 4, seed: Int = 99): Float {
        var total = 0f
        var amp = 0.5f
        var freq = 1f
        var norm = 0f
        for (i in 0 until octaves) {
            val n = 1f - abs(valueNoise(x * freq, y * freq, seed + i * 29) * 2f - 1f)
            total += n * n * amp
            norm += amp
            amp *= 0.5f
            freq *= 2.05f
        }
        return total / norm
    }

    fun tileableFbm(u: Float, v: Float, baseFreqX: Int, baseFreqY: Int, octaves: Int, seed: Int): Float {
        var total = 0f
        var amp = 0.5f
        var fx = baseFreqX
        var fy = baseFreqY
        var norm = 0f
        for (i in 0 until octaves) {
            total += tileableNoise(u * fx, v * fy, fx, fy, seed + i * 19) * amp
            norm += amp
            amp *= 0.5f
            fx *= 2
            fy *= 2
        }
        return total / norm
    }

    fun sinCosDir(yawRad: Float): Pair<Float, Float> = Pair(sin(yawRad), cos(yawRad))
}
