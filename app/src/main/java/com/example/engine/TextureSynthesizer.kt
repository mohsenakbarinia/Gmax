package com.example.engine

import android.opengl.GLES30
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

enum class PbrMaterialId {
    PINE_BARK,
    BIRCH_BARK,
    FOREST_FLOOR,
    ALPINE_ROCK,
    WALNUT_WOOD,
    GUNMETAL,
    ANIMAL_FUR,
    ANTLER_BONE
}

data class SynthesizedTextureData(
    val width: Int,
    val height: Int,
    val albedoRgba: ByteArray,
    val normalRgba: ByteArray,
    val roughnessR: ByteArray
)

data class GlPbrMaterial(
    val albedoTex: Int,
    val normalTex: Int,
    val roughnessTex: Int
)

/**
 * High-Resolution Procedural PBR Texture Synthesizer.
 * Generates 512x512 tileable Albedo, Sobel NormalMap, and RoughnessMap sets on a background
 * thread using multi-octave FBM noise and reports synthesis progress to the Start Screen.
 */
object TextureSynthesizer {
    const val TEX_SIZE = 512

    fun synthesizeAll(onProgress: (Float) -> Unit): Map<PbrMaterialId, SynthesizedTextureData> {
        val result = LinkedHashMap<PbrMaterialId, SynthesizedTextureData>()
        val materials = PbrMaterialId.entries
        materials.forEachIndexed { idx, mat ->
            result[mat] = synthesizeMaterial(mat)
            onProgress((idx + 1).toFloat() / materials.size.toFloat())
            Thread.yield()
        }
        return result
    }

    fun synthesizeStreaming(onMaterialReady: (PbrMaterialId, SynthesizedTextureData, Float) -> Unit) {
        val materials = PbrMaterialId.entries
        materials.forEachIndexed { idx, mat ->
            val data = synthesizeMaterial(mat)
            val progress = (idx + 1).toFloat() / materials.size.toFloat()
            onMaterialReady(mat, data, progress)
            try {
                Thread.sleep(12L)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun synthesizeMaterial(id: PbrMaterialId): SynthesizedTextureData {
        val w = TEX_SIZE
        val h = TEX_SIZE
        val count = w * h
        val heightField = FloatArray(count)
        val albedo = ByteArray(count * 4)
        val roughness = ByteArray(count)

        val invW = 1f / w.toFloat()
        val invH = 1f / h.toFloat()

        for (y in 0 until h) {
            val v = y * invH
            val rowOffset = y * w
            for (x in 0 until w) {
                val u = x * invW
                val i = rowOffset + x

                var r = 0.5f
                var g = 0.5f
                var b = 0.5f
                var ht = 0.5f
                var rough = 0.75f

                when (id) {
                    PbrMaterialId.PINE_BARK -> {
                        val ridge = Noise2D.tileableFbm(u, v, 14, 4, 4, 11)
                        val fine = Noise2D.tileableFbm(u, v, 32, 16, 3, 23)
                        val crack = Math3D.smoothstep(0.28f, 0.56f, ridge)
                        ht = crack * 0.78f + fine * 0.22f
                        r = Math3D.lerp(0.14f, 0.34f, ht)
                        g = Math3D.lerp(0.09f, 0.23f, ht)
                        b = Math3D.lerp(0.06f, 0.15f, ht)
                        val lichen = Math3D.smoothstep(0.72f, 0.88f, fine)
                        r = Math3D.lerp(r, 0.28f, lichen * 0.35f)
                        g = Math3D.lerp(g, 0.34f, lichen * 0.35f)
                        b = Math3D.lerp(b, 0.22f, lichen * 0.35f)
                        rough = Math3D.clamp(0.92f - ht * 0.12f, 0.72f, 0.96f)
                    }

                    PbrMaterialId.BIRCH_BARK -> {
                        val paper = Noise2D.tileableFbm(u, v, 6, 12, 4, 41)
                        val lenticel = Noise2D.tileableFbm(u, v, 4, 36, 3, 53)
                        val darkBand = Math3D.smoothstep(0.66f, 0.80f, lenticel)
                        ht = 0.7f + paper * 0.2f - darkBand * 0.45f
                        r = Math3D.lerp(0.84f, 0.16f, darkBand) * (0.90f + paper * 0.12f)
                        g = Math3D.lerp(0.82f, 0.14f, darkBand) * (0.90f + paper * 0.12f)
                        b = Math3D.lerp(0.76f, 0.12f, darkBand) * (0.90f + paper * 0.12f)
                        rough = Math3D.lerp(0.68f, 0.90f, darkBand)
                    }

                    PbrMaterialId.FOREST_FLOOR -> {
                        val moss = Noise2D.tileableFbm(u, v, 6, 6, 4, 67)
                        val needles = Noise2D.tileableFbm(u, v, 28, 28, 3, 79)
                        val soil = Noise2D.tileableFbm(u, v, 12, 12, 3, 83)
                        val mossMask = Math3D.smoothstep(0.42f, 0.68f, moss)
                        ht = soil * 0.5f + needles * 0.35f + mossMask * 0.15f
                        val soilR = 0.21f * (0.75f + needles * 0.5f)
                        val soilG = 0.16f * (0.75f + needles * 0.5f)
                        val soilB = 0.10f * (0.75f + needles * 0.45f)
                        val mossR = 0.15f + needles * 0.10f
                        val mossG = 0.27f + needles * 0.16f
                        val mossB = 0.10f + needles * 0.06f
                        r = Math3D.lerp(soilR, mossR, mossMask)
                        g = Math3D.lerp(soilG, mossG, mossMask)
                        b = Math3D.lerp(soilB, mossB, mossMask)
                        rough = Math3D.lerp(0.88f, 0.76f, mossMask)
                    }

                    PbrMaterialId.ALPINE_ROCK -> {
                        val macro = Noise2D.tileableFbm(u, v, 5, 5, 4, 101)
                        val strata = abs(sin((v * 10f + macro * 2.4f) * Math.PI.toFloat()))
                        val grit = Noise2D.tileableFbm(u, v, 24, 24, 3, 113)
                        ht = macro * 0.55f + strata * 0.25f + grit * 0.20f
                        val shade = 0.22f + ht * 0.36f
                        r = shade * 0.96f
                        g = shade * 0.98f
                        b = shade * 1.03f
                        val frost = Math3D.smoothstep(0.76f, 0.92f, ht)
                        r = Math3D.lerp(r, 0.62f, frost * 0.3f)
                        g = Math3D.lerp(g, 0.64f, frost * 0.3f)
                        b = Math3D.lerp(b, 0.66f, frost * 0.3f)
                        rough = Math3D.clamp(0.86f - strata * 0.20f, 0.58f, 0.92f)
                    }

                    PbrMaterialId.WALNUT_WOOD -> {
                        val warp = Noise2D.tileableFbm(u, v, 4, 12, 4, 131)
                        val rings = 0.5f + 0.5f * sin((u * 18f + warp * 3.8f) * 6.283185f)
                        val pore = Noise2D.tileableFbm(u, v, 32, 8, 2, 139)
                        ht = rings * 0.65f + pore * 0.35f
                        r = Math3D.lerp(0.18f, 0.42f, rings) * (0.9f + pore * 0.16f)
                        g = Math3D.lerp(0.09f, 0.23f, rings) * (0.9f + pore * 0.16f)
                        b = Math3D.lerp(0.04f, 0.11f, rings) * (0.9f + pore * 0.16f)
                        rough = Math3D.lerp(0.32f, 0.48f, 1f - rings)
                    }

                    PbrMaterialId.GUNMETAL -> {
                        val brush = Noise2D.tileableFbm(u, v, 4, 48, 3, 151)
                        val micro = Noise2D.tileableFbm(u, v, 24, 24, 2, 163)
                        ht = brush * 0.7f + micro * 0.3f
                        val base = 0.14f + ht * 0.10f
                        r = base * 0.92f
                        g = base * 0.97f
                        b = base * 1.12f
                        rough = Math3D.clamp(0.24f + (1f - brush) * 0.16f, 0.20f, 0.42f)
                    }

                    PbrMaterialId.ANIMAL_FUR -> {
                        val strand = Noise2D.tileableFbm(u, v, 24, 8, 4, 173)
                        val patch = Noise2D.tileableFbm(u, v, 5, 5, 3, 181)
                        ht = strand * 0.75f + patch * 0.25f
                        val shade = 0.58f + ht * 0.42f
                        r = shade
                        g = shade * 0.96f
                        b = shade * 0.90f
                        rough = Math3D.clamp(0.72f + (1f - strand) * 0.20f, 0.65f, 0.94f)
                    }

                    PbrMaterialId.ANTLER_BONE -> {
                        val flutes = Noise2D.tileableFbm(u, v, 18, 4, 3, 193)
                        val grain = Noise2D.tileableFbm(u, v, 10, 10, 3, 199)
                        ht = flutes * 0.65f + grain * 0.35f
                        r = Math3D.lerp(0.56f, 0.86f, ht)
                        g = Math3D.lerp(0.48f, 0.80f, ht)
                        b = Math3D.lerp(0.36f, 0.67f, ht)
                        rough = Math3D.lerp(0.62f, 0.38f, ht)
                    }
                }

                heightField[i] = Math3D.clamp(ht, 0f, 1f)
                val bi = i * 4
                albedo[bi] = (Math3D.clamp(r, 0f, 1f) * 255f).toInt().toByte()
                albedo[bi + 1] = (Math3D.clamp(g, 0f, 1f) * 255f).toInt().toByte()
                albedo[bi + 2] = (Math3D.clamp(b, 0f, 1f) * 255f).toInt().toByte()
                albedo[bi + 3] = 255.toByte()
                roughness[i] = (Math3D.clamp(rough, 0.05f, 0.98f) * 255f).toInt().toByte()
            }
        }

        // Sobel 3x3 height-to-normal filter (tileable wrap)
        val normalStrength = when (id) {
            PbrMaterialId.PINE_BARK -> 3.4f
            PbrMaterialId.ALPINE_ROCK -> 3.2f
            PbrMaterialId.FOREST_FLOOR -> 2.4f
            PbrMaterialId.GUNMETAL -> 0.8f
            PbrMaterialId.WALNUT_WOOD -> 1.1f
            else -> 2.0f
        }
        val normals = ByteArray(count * 4)
        for (y in 0 until h) {
            val ym = (y - 1 + h) % h
            val yp = (y + 1) % h
            for (x in 0 until w) {
                val xm = (x - 1 + w) % w
                val xp = (x + 1) % w

                val tl = heightField[ym * w + xm]
                val tc = heightField[ym * w + x]
                val tr = heightField[ym * w + xp]
                val ml = heightField[y * w + xm]
                val mr = heightField[y * w + xp]
                val bl = heightField[yp * w + xm]
                val bc = heightField[yp * w + x]
                val br = heightField[yp * w + xp]

                val dx = (tr + 2f * mr + br) - (tl + 2f * ml + bl)
                val dy = (bl + 2f * bc + br) - (tl + 2f * tc + tr)

                var nx = -dx * normalStrength
                var ny = -dy * normalStrength
                var nz = 1f
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= len; ny /= len; nz /= len

                val bi = (y * w + x) * 4
                normals[bi] = ((nx * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                normals[bi + 1] = ((ny * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                normals[bi + 2] = ((nz * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                normals[bi + 3] = 255.toByte()
            }
        }

        return SynthesizedTextureData(w, h, albedo, normals, roughness)
    }

    fun uploadToGl(data: SynthesizedTextureData): GlPbrMaterial {
        val albedoId = uploadRgba(data.width, data.height, data.albedoRgba, GLES30.GL_RGBA8, GLES30.GL_RGBA)
        val normalId = uploadRgba(data.width, data.height, data.normalRgba, GLES30.GL_RGBA8, GLES30.GL_RGBA)
        val roughId = uploadSingleChannel(data.width, data.height, data.roughnessR)
        return GlPbrMaterial(albedoId, normalId, roughId)
    }

    private fun uploadRgba(w: Int, h: Int, bytes: ByteArray, internalFmt: Int, fmt: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        val buf = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            position(0)
        }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFmt, w, h, 0, fmt, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return id
    }

    private fun uploadSingleChannel(w: Int, h: Int, bytes: ByteArray): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val buf = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            position(0)
        }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, w, h, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return id
    }
}
