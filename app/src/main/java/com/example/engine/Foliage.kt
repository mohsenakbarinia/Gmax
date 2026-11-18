package com.example.engine

import android.opengl.GLES30
import com.example.data.WeatherPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Instanced foliage mesh batch using OpenGL ES 3.0 glDrawElementsInstanced
 * with per-instance (x, y, z, scale) and (yawRad, windFactor, tint, 0) attributes.
 */
class InstancedBatch(
    meshData: AssetLoader.MeshData,
    instanceData: FloatArray // 8 floats per instance
) {
    val vao: Int
    private val vbo: Int
    private val ebo: Int
    private val instVbo: Int
    val indexCount = meshData.indices.size
    val maxInstances = instanceData.size / 8

    init {
        val vaoArr = IntArray(1)
        val bufs = IntArray(3)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(3, bufs, 0)
        vao = vaoArr[0]; vbo = bufs[0]; ebo = bufs[1]; instVbo = bufs[2]

        GLES30.glBindVertexArray(vao)

        val vBuf = ByteBuffer.allocateDirect(meshData.vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(meshData.vertices); position(0)
            }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, meshData.vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        val iBuf = ByteBuffer.allocateDirect(meshData.indices.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer().apply {
                put(meshData.indices); position(0)
            }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, meshData.indices.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)

        val instBuf = ByteBuffer.allocateDirect(instanceData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(instanceData); position(0)
            }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instanceData.size * 4, instBuf, GLES30.GL_STATIC_DRAW)

        val instStride = 8 * 4
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, instStride, 0)
        GLES30.glVertexAttribDivisor(3, 1)

        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, instStride, 4 * 4)
        GLES30.glVertexAttribDivisor(4, 1)

        GLES30.glBindVertexArray(0)
    }

    fun draw(densityScale: Float = 1f) {
        val count = (maxInstances * densityScale).toInt().coerceIn(0, maxInstances)
        if (count <= 0) return
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0, count)
        GLES30.glBindVertexArray(0)
    }

    fun dispose() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(3, intArrayOf(vbo, ebo, instVbo), 0)
    }
}

/**
 * Dense living alpine forest, boulders, moss logs, ferns, grass tufts, volumetric god rays,
 * and dynamic weather particles (pollen, fireflies, storm rain).
 */
class FoliageSystem(private val terrain: Terrain) {
    lateinit var pineTrunks: InstancedBatch
    lateinit var pineCanopies: InstancedBatch
    lateinit var birchTrunks: InstancedBatch
    lateinit var birchCanopies: InstancedBatch
    lateinit var boulders: InstancedBatch
    lateinit var mossLogs: InstancedBatch
    lateinit var ferns: InstancedBatch
    lateinit var grassTufts: InstancedBatch

    // Keep lightweight tree trunk colliders & bush cover coordinates for stealth detection
    val treeColliders = ArrayList<Vec3>() // x, z, radius
    val bushPositions = ArrayList<Vec3>()

    // Dynamic effects buffers (god rays & particles)
    private var effectsVao = 0
    private var effectsVbo = 0
    private val maxParticles = 420
    private val particlePos = FloatArray(maxParticles * 3)
    private val particlePhase = FloatArray(maxParticles)
    private val dynamicEffectVerts = FloatArray(maxParticles * 8)
    private val dynamicEffectBuf = ByteBuffer.allocateDirect(dynamicEffectVerts.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    // Volumetric sun shafts (18 angled light shafts through tall pines)
    private val shaftCount = 18
    private val shaftVerts = FloatArray(shaftCount * 6 * 8)
    private val shaftBuf = ByteBuffer.allocateDirect(shaftVerts.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val shaftCenters = ArrayList<Vec3>()

    @Volatile private var dataPrepared = false
    private var preparedPineData: FloatArray? = null
    private var preparedBirchData: FloatArray? = null
    private var preparedBoulderData: FloatArray? = null
    private var preparedLogData: FloatArray? = null
    private var preparedFernData: FloatArray? = null
    private var preparedGrassData: FloatArray? = null

    private var meshPineTrunk: AssetLoader.MeshData? = null
    private var meshPineCanopy: AssetLoader.MeshData? = null
    private var meshBirchTrunk: AssetLoader.MeshData? = null
    private var meshBirchCanopy: AssetLoader.MeshData? = null
    private var meshBoulder: AssetLoader.MeshData? = null
    private var meshMossLog: AssetLoader.MeshData? = null
    private var meshFern: AssetLoader.MeshData? = null
    private var meshGrass: AssetLoader.MeshData? = null

    private var glReady = false

    @Synchronized
    fun prepareData() {
        if (dataPrepared) return
        val rng = Random(202611)
        treeColliders.clear()
        bushPositions.clear()
        shaftCenters.clear()

        val pineData = ArrayList<Float>()
        val birchData = ArrayList<Float>()
        val boulderData = ArrayList<Float>()
        val logData = ArrayList<Float>()
        val fernData = ArrayList<Float>()
        val grassData = ArrayList<Float>()

        // 1. Tall Douglas Firs / Pines (~420)
        var attempts = 0
        while (pineData.size / 8 < 420 && attempts < 3200) {
            attempts++
            val x = rng.nextFloat() * 365f - 182.5f
            val z = rng.nextFloat() * 365f - 182.5f
            val y = terrain.getHeightAt(x, z)
            if (y < 1.2f || y > 32f) continue
            if (terrain.trailFactor(x, z) > 0.35f) continue
            val clearingDx = x - 52f; val clearingDz = z - 35f
            if (clearingDx * clearingDx + clearingDz * clearingDz < 28f * 28f) continue
            if ((x - 42f) * (x - 42f) + (z - 64f) * (z - 64f) < 12f * 12f) continue

            val scale = 0.85f + rng.nextFloat() * 0.95f
            val yaw = rng.nextFloat() * 6.283185f
            val tint = rng.nextFloat()
            pineData.add(x); pineData.add(y - 0.15f); pineData.add(z); pineData.add(scale)
            pineData.add(yaw); pineData.add(0.24f); pineData.add(tint); pineData.add(0f)
            treeColliders.add(Vec3(x, z, 0.42f * scale))
            if (shaftCenters.size < shaftCount && x in -95f..95f && z in -95f..95f && rng.nextFloat() < 0.08f) {
                shaftCenters.add(Vec3(x, y + 0.5f, z))
            }
        }

        // 2. Alpine Birch Trees near meadow edges & lakeshore (~120)
        attempts = 0
        while (birchData.size / 8 < 120 && attempts < 1300) {
            attempts++
            val x = rng.nextFloat() * 320f - 160f
            val z = rng.nextFloat() * 320f - 160f
            val y = terrain.getHeightAt(x, z)
            if (y < 0.9f || y > 13.5f || terrain.trailFactor(x, z) > 0.4f) continue
            val scale = 0.82f + rng.nextFloat() * 0.65f
            birchData.add(x); birchData.add(y - 0.1f); birchData.add(z); birchData.add(scale)
            birchData.add(rng.nextFloat() * 6.283185f); birchData.add(0.32f); birchData.add(rng.nextFloat()); birchData.add(0f)
            treeColliders.add(Vec3(x, z, 0.28f * scale))
        }

        // 3. Craggy Alpine Boulders (~90)
        attempts = 0
        while (boulderData.size / 8 < 90 && attempts < 850) {
            attempts++
            val x = rng.nextFloat() * 340f - 170f
            val z = rng.nextFloat() * 340f - 170f
            val y = terrain.getHeightAt(x, z)
            if (y < -0.6f || terrain.trailFactor(x, z) > 0.45f) continue
            val scale = 0.65f + rng.nextFloat() * 1.45f
            boulderData.add(x); boulderData.add(y - 0.18f); boulderData.add(z); boulderData.add(scale)
            boulderData.add(rng.nextFloat() * 6.283185f); boulderData.add(0f); boulderData.add(rng.nextFloat()); boulderData.add(0f)
        }

        // 4. Fallen Moss-Covered Logs (~55)
        attempts = 0
        while (logData.size / 8 < 55 && attempts < 550) {
            attempts++
            val x = rng.nextFloat() * 310f - 155f
            val z = rng.nextFloat() * 310f - 155f
            val y = terrain.getHeightAt(x, z)
            if (y < 1.2f || y > 16f || terrain.trailFactor(x, z) > 0.35f) continue
            val scale = 0.85f + rng.nextFloat() * 0.75f
            logData.add(x); logData.add(y); logData.add(z); logData.add(scale)
            logData.add(rng.nextFloat() * 6.283185f); logData.add(0f); logData.add(rng.nextFloat()); logData.add(0f)
        }

        // 5. Dense Fern Clusters / Stealth Brush (~320)
        attempts = 0
        while (fernData.size / 8 < 320 && attempts < 2000) {
            attempts++
            val x = rng.nextFloat() * 310f - 155f
            val z = rng.nextFloat() * 310f - 155f
            val y = terrain.getHeightAt(x, z)
            if (y < 0.7f || y > 17f || terrain.trailFactor(x, z) > 0.45f) continue
            val scale = 0.85f + rng.nextFloat() * 0.95f
            fernData.add(x); fernData.add(y); fernData.add(z); fernData.add(scale)
            fernData.add(rng.nextFloat() * 6.283185f); fernData.add(0.95f); fernData.add(rng.nextFloat()); fernData.add(0f)
            bushPositions.add(Vec3(x, y, z))
        }

        // 6. Wind-Swaying Grass Blades (~1800)
        attempts = 0
        while (grassData.size / 8 < 1800 && attempts < 6000) {
            attempts++
            val x = rng.nextFloat() * 290f - 145f
            val z = rng.nextFloat() * 290f - 145f
            val y = terrain.getHeightAt(x, z)
            if (y < 0.8f || y > 16f || terrain.trailFactor(x, z) > 0.50f) continue
            val scale = 0.75f + rng.nextFloat() * 0.90f
            grassData.add(x); grassData.add(y); grassData.add(z); grassData.add(scale)
            grassData.add(rng.nextFloat() * 6.283185f); grassData.add(1.65f); grassData.add(rng.nextFloat()); grassData.add(0f)
        }

        preparedPineData = pineData.toFloatArray()
        preparedBirchData = birchData.toFloatArray()
        preparedBoulderData = boulderData.toFloatArray()
        preparedLogData = logData.toFloatArray()
        preparedFernData = fernData.toFloatArray()
        preparedGrassData = grassData.toFloatArray()

        meshPineTrunk = GeometryBuilder.createPineTrunk()
        meshPineCanopy = GeometryBuilder.createPineCanopy()
        meshBirchTrunk = GeometryBuilder.createBirchTrunk()
        meshBirchCanopy = GeometryBuilder.createBirchCanopy()
        meshBoulder = GeometryBuilder.createBoulder()
        meshMossLog = GeometryBuilder.createMossLog()
        meshFern = GeometryBuilder.createFernCluster()
        meshGrass = GeometryBuilder.createGrassTuft()

        for (i in 0 until maxParticles) {
            particlePos[i * 3] = (rng.nextFloat() - 0.5f) * 44f
            particlePos[i * 3 + 1] = rng.nextFloat() * 14f
            particlePos[i * 3 + 2] = (rng.nextFloat() - 0.5f) * 44f
            particlePhase[i] = rng.nextFloat() * 6.283185f
        }
        dataPrepared = true
    }

    fun uploadToGl() {
        if (!dataPrepared) prepareData()

        pineTrunks = InstancedBatch(meshPineTrunk!!, preparedPineData!!)
        pineCanopies = InstancedBatch(meshPineCanopy!!, preparedPineData!!)
        birchTrunks = InstancedBatch(meshBirchTrunk!!, preparedBirchData!!)
        birchCanopies = InstancedBatch(meshBirchCanopy!!, preparedBirchData!!)
        boulders = InstancedBatch(meshBoulder!!, preparedBoulderData!!)
        mossLogs = InstancedBatch(meshMossLog!!, preparedLogData!!)
        ferns = InstancedBatch(meshFern!!, preparedFernData!!)
        grassTufts = InstancedBatch(meshGrass!!, preparedGrassData!!)

        val vaoArr = IntArray(1); val vboArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)
        effectsVao = vaoArr[0]; effectsVbo = vboArr[0]
        GLES30.glBindVertexArray(effectsVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, effectsVbo)
        val byteCapacity = maxOf(dynamicEffectVerts.size, shaftVerts.size) * 4
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, byteCapacity, null, GLES30.GL_DYNAMIC_DRAW)
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 7 * 4)
        GLES30.glBindVertexArray(0)

        glReady = true
    }

    fun isInBrushCover(px: Float, pz: Float): Boolean {
        for (i in 0 until bushPositions.size) {
            val b = bushPositions[i]
            val dx = b.x - px
            val dz = b.z - pz
            if (dx * dx + dz * dz < 4.5f) return true
        }
        return false
    }

    fun drawAtmosphericParticles(
        dt: Float,
        time: Float,
        camPos: Vec3,
        weather: WeatherPreset,
        windDirX: Float,
        windDirZ: Float,
        effectsProgram: Int,
        viewProjLoc: Int,
        isPointLoc: Int,
        viewProj: FloatArray
    ) {
        if (!glReady) return
        val count = when (weather) {
            WeatherPreset.RAINY_STORM -> 360
            WeatherPreset.MOONLIT_NIGHT -> 180
            WeatherPreset.GOLDEN_HOUR -> 160
            WeatherPreset.MISTY_DAWN -> 140
        }

        val range = if (weather == WeatherPreset.RAINY_STORM) 28f else 36f
        val half = range * 0.5f

        var ptr = 0
        for (i in 0 until count) {
            val base = i * 3
            var px = particlePos[base]
            var py = particlePos[base + 1]
            var pz = particlePos[base + 2]
            val ph = particlePhase[i]

            when (weather) {
                WeatherPreset.RAINY_STORM -> {
                    px += windDirX * 5.2f * dt
                    py -= 19.5f * dt
                    pz += windDirZ * 5.2f * dt
                }
                WeatherPreset.MOONLIT_NIGHT -> {
                    px += sin(time * 0.9f + ph) * 0.55f * dt
                    py += cos(time * 1.1f + ph * 1.3f) * 0.35f * dt
                    pz += cos(time * 0.8f + ph) * 0.55f * dt
                }
                else -> {
                    px += (windDirX * 0.85f + sin(time * 0.5f + ph) * 0.25f) * dt
                    py -= (0.28f + 0.12f * sin(time + ph)) * dt
                    pz += (windDirZ * 0.85f + cos(time * 0.5f + ph) * 0.25f) * dt
                }
            }

            // Wrap around camera volume
            while (px - camPos.x > half) px -= range
            while (px - camPos.x < -half) px += range
            while (pz - camPos.z > half) pz -= range
            while (pz - camPos.z < -half) pz += range
            val groundY = terrain.getHeightAt(px, pz)
            if (py < groundY + 0.3f || py > groundY + 16f) {
                py = groundY + (if (weather == WeatherPreset.RAINY_STORM) 13f else 2f + (ph % 9f))
            }

            particlePos[base] = px; particlePos[base + 1] = py; particlePos[base + 2] = pz

            dynamicEffectVerts[ptr++] = px
            dynamicEffectVerts[ptr++] = py
            dynamicEffectVerts[ptr++] = pz
            when (weather) {
                WeatherPreset.GOLDEN_HOUR -> {
                    dynamicEffectVerts[ptr++] = 1.0f; dynamicEffectVerts[ptr++] = 0.86f; dynamicEffectVerts[ptr++] = 0.52f
                    dynamicEffectVerts[ptr++] = 0.45f + 0.25f * sin(time * 2.2f + ph)
                    dynamicEffectVerts[ptr++] = 38f
                }
                WeatherPreset.MISTY_DAWN -> {
                    dynamicEffectVerts[ptr++] = 0.85f; dynamicEffectVerts[ptr++] = 0.92f; dynamicEffectVerts[ptr++] = 0.96f
                    dynamicEffectVerts[ptr++] = 0.32f
                    dynamicEffectVerts[ptr++] = 42f
                }
                WeatherPreset.RAINY_STORM -> {
                    dynamicEffectVerts[ptr++] = 0.72f; dynamicEffectVerts[ptr++] = 0.80f; dynamicEffectVerts[ptr++] = 0.90f
                    dynamicEffectVerts[ptr++] = 0.52f
                    dynamicEffectVerts[ptr++] = 26f
                }
                WeatherPreset.MOONLIT_NIGHT -> {
                    val pulse = 0.25f + 0.75f * (0.5f + 0.5f * sin(time * 3.1f + ph * 2.0f))
                    dynamicEffectVerts[ptr++] = 0.78f; dynamicEffectVerts[ptr++] = 1.0f; dynamicEffectVerts[ptr++] = 0.45f
                    dynamicEffectVerts[ptr++] = pulse * 0.92f
                    dynamicEffectVerts[ptr++] = 56f
                }
            }
        }

        GLES30.glUseProgram(effectsProgram)
        GLES30.glUniformMatrix4fv(viewProjLoc, 1, false, viewProj, 0)
        GLES30.glUniform1f(isPointLoc, 1.0f)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)

        dynamicEffectBuf.position(0)
        dynamicEffectBuf.put(dynamicEffectVerts, 0, ptr)
        dynamicEffectBuf.position(0)
        GLES30.glBindVertexArray(effectsVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, effectsVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, ptr * 4, dynamicEffectBuf)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
    }

    fun drawVolumetricGodRays(
        sunDir: Vec3,
        weather: WeatherPreset,
        camPos: Vec3,
        effectsProgram: Int,
        viewProjLoc: Int,
        isPointLoc: Int,
        viewProj: FloatArray
    ) {
        if (!glReady) return
        if (weather != WeatherPreset.GOLDEN_HOUR && weather != WeatherPreset.MISTY_DAWN) return
        if (shaftCenters.isEmpty()) return

        val rCol = if (weather == WeatherPreset.GOLDEN_HOUR) 1.0f else 0.76f
        val gCol = if (weather == WeatherPreset.GOLDEN_HOUR) 0.78f else 0.88f
        val bCol = if (weather == WeatherPreset.GOLDEN_HOUR) 0.42f else 0.96f
        val baseAlpha = if (weather == WeatherPreset.GOLDEN_HOUR) 0.11f else 0.08f

        var ptr = 0
        var vertCount = 0
        for (center in shaftCenters) {
            val dx = camPos.x - center.x
            val dz = camPos.z - center.z
            val distSq = dx * dx + dz * dz
            if (distSq > 145f * 145f) continue

            // Billboard ribbon aligned horizontally toward camera and slanted along sunDir
            val lenXZ = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
            val rightX = -dz / lenXZ * 1.9f
            val rightZ = dx / lenXZ * 1.9f

            val bx = center.x; val by = center.y; val bz = center.z
            val tx = bx + sunDir.x * 22f
            val ty = by + sunDir.y * 22f + 12f
            val tz = bz + sunDir.z * 22f

            fun putV(x: Float, y: Float, z: Float, a: Float) {
                shaftVerts[ptr++] = x; shaftVerts[ptr++] = y; shaftVerts[ptr++] = z
                shaftVerts[ptr++] = rCol; shaftVerts[ptr++] = gCol; shaftVerts[ptr++] = bCol; shaftVerts[ptr++] = a
                shaftVerts[ptr++] = 1f
                vertCount++
            }
            putV(bx - rightX, by, bz - rightZ, 0.0f)
            putV(bx + rightX, by, bz + rightZ, 0.0f)
            putV(tx + rightX * 1.6f, ty, tz + rightZ * 1.6f, baseAlpha)

            putV(bx - rightX, by, bz - rightZ, 0.0f)
            putV(tx + rightX * 1.6f, ty, tz + rightZ * 1.6f, baseAlpha)
            putV(tx - rightX * 1.6f, ty, tz - rightZ * 1.6f, baseAlpha)
        }
        if (vertCount == 0) return

        GLES30.glUseProgram(effectsProgram)
        GLES30.glUniformMatrix4fv(viewProjLoc, 1, false, viewProj, 0)
        GLES30.glUniform1f(isPointLoc, 0.0f)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        shaftBuf.position(0)
        shaftBuf.put(shaftVerts, 0, ptr)
        shaftBuf.position(0)
        GLES30.glBindVertexArray(effectsVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, effectsVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, ptr * 4, shaftBuf)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertCount)

        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
    }

    fun dispose() {
        if (!glReady) return
        pineTrunks.dispose(); pineCanopies.dispose()
        birchTrunks.dispose(); birchCanopies.dispose()
        boulders.dispose(); mossLogs.dispose()
        ferns.dispose(); grassTufts.dispose()
        if (effectsVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(effectsVao), 0)
        if (effectsVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(effectsVbo), 0)
        glReady = false
    }
}
