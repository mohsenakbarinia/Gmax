package com.example.engine

import android.opengl.GLES30
import com.example.data.SurfaceType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 420m x 420m undulating alpine valley terrain with ridges, clearings, sunken reflective lake,
 * dirt hunting trails, exact bilinear height/normal queries, and surface classification.
 */
class Terrain {
    companion object {
        const val WORLD_SIZE = 420f
        const val HALF_SIZE = WORLD_SIZE * 0.5f
        const val GRID_RES = 120
        const val WATER_LEVEL = 0.0f
        const val LAKE_CENTER_X = -18f
        const val LAKE_CENTER_Z = -22f
        const val LAKE_RADIUS_X = 56f
        const val LAKE_RADIUS_Z = 42f
    }

    private val stride = GRID_RES + 1
    private val heights = FloatArray(stride * stride)
    private val step = WORLD_SIZE / GRID_RES.toFloat()

    @Volatile private var prepared = false
    private var preparedTerrainVerts: FloatArray? = null
    private var preparedTerrainIndices: IntArray? = null
    private var preparedWaterVerts: FloatArray? = null
    private var preparedWaterIndices: IntArray? = null

    var vao = 0
        private set
    private var vbo = 0
    private var ebo = 0
    var indexCount = 0
        private set

    var waterVao = 0
        private set
    private var waterVbo = 0
    private var waterEbo = 0
    var waterIndexCount = 0
        private set

    @Synchronized
    fun prepareData() {
        if (prepared) return
        generateHeightfield()
        prepareTerrainMeshData()
        prepareWaterMeshData()
        prepared = true
    }

    private fun generateHeightfield() {
        for (gz in 0..GRID_RES) {
            val z = -HALF_SIZE + gz * step
            for (gx in 0..GRID_RES) {
                val x = -HALF_SIZE + gx * step
                heights[gz * stride + gx] = computeRawHeight(x, z)
            }
        }
    }

    private fun computeRawHeight(x: Float, z: Float): Float {
        // Multi-scale alpine rolling hills + dramatic valley rim ridges
        val baseHills = (Noise2D.fbm(x * 0.0085f + 12.3f, z * 0.0085f - 7.1f, 5, 42) - 0.42f) * 24f
        val ridges = Noise2D.ridgedFbm(x * 0.0065f - 3.4f, z * 0.0065f + 9.2f, 4, 88) * 18f

        // Bowl-like valley enclosure near world borders so player stays framed by alpine peaks
        val edgeDist = max(abs(x), abs(z)) / HALF_SIZE
        val rimBoost = Math3D.smoothstep(0.55f, 0.98f, edgeDist) * 34f

        // Sunken alpine lake basin centered around (-18, -22)
        val lx = (x - LAKE_CENTER_X) / LAKE_RADIUS_X
        val lz = (z - LAKE_CENTER_Z) / LAKE_RADIUS_Z
        val lakeNoise = (Noise2D.fbm(x * 0.025f, z * 0.025f, 3, 19) - 0.5f) * 0.28f
        val lakeDist = sqrt(lx * lx + lz * lz) + lakeNoise
        val lakeCarve = (1f - Math3D.smoothstep(0.32f, 1.18f, lakeDist)) * 16.5f

        // Gentle meadow clearing on eastern side for scenic vista
        val cx = x - 52f
        val cz = z - 35f
        val clearing = exp(-(cx * cx + cz * cz) / (46f * 46f)) * 4.5f

        var h = 5.4f + baseHills + ridges * (0.28f + 0.72f * Math3D.smoothstep(0.28f, 0.85f, edgeDist)) + rimBoost - lakeCarve - clearing * 0.45f
        // Flatten hunting trail slightly for natural pathing
        val trail = trailFactor(x, z)
        if (trail > 0.01f && h > 0.9f) {
            h -= trail * 0.32f
        }
        return h
    }

    fun trailFactor(x: Float, z: Float): Float {
        // Winding hunting trail looping from south overlook past the east lake shore to north ridge
        val centerLineX = 44f + sin(z * 0.022f) * 28f + cos(z * 0.011f) * 14f
        val dist = abs(x - centerLineX)
        return 1f - Math3D.smoothstep(2.2f, 6.2f, dist)
    }

    fun getHeightAt(x: Float, z: Float): Float {
        val fx = Math3D.clamp((x + HALF_SIZE) / step, 0f, GRID_RES - 0.001f)
        val fz = Math3D.clamp((z + HALF_SIZE) / step, 0f, GRID_RES - 0.001f)
        val ix = floor(fx).toInt()
        val iz = floor(fz).toInt()
        val tx = fx - ix
        val tz = fz - iz

        val h00 = heights[iz * stride + ix]
        val h10 = heights[iz * stride + ix + 1]
        val h01 = heights[(iz + 1) * stride + ix]
        val h11 = heights[(iz + 1) * stride + ix + 1]
        return Math3D.lerp(Math3D.lerp(h00, h10, tx), Math3D.lerp(h01, h11, tx), tz)
    }

    fun getNormalAt(x: Float, z: Float, out: Vec3 = Vec3()): Vec3 {
        val eps = 1.4f
        val hl = getHeightAt(x - eps, z)
        val hr = getHeightAt(x + eps, z)
        val hd = getHeightAt(x, z - eps)
        val hu = getHeightAt(x, z + eps)
        return out.set(hl - hr, 2f * eps, hd - hu).normalize()
    }

    fun getSurfaceType(x: Float, z: Float): SurfaceType {
        val h = getHeightAt(x, z)
        if (h < WATER_LEVEL + 0.35f) return SurfaceType.SHALLOW_WATER
        val n = getNormalAt(x, z)
        if (n.y < 0.78f || h > 22f) return SurfaceType.ROCK
        if (trailFactor(x, z) > 0.38f || h < 1.6f) return SurfaceType.DIRT
        return SurfaceType.GRASS
    }

    fun distanceToWaterEdge(x: Float, z: Float): Float {
        val lx = (x - LAKE_CENTER_X)
        val lz = (z - LAKE_CENTER_Z)
        val approxRad = 0.5f * (LAKE_RADIUS_X + LAKE_RADIUS_Z)
        return max(0f, sqrt(lx * lx + lz * lz) - approxRad)
    }

    fun uploadToGl() {
        if (!prepared) prepareData()
        uploadTerrainMesh()
        uploadWaterMesh()
    }

    private fun prepareTerrainMeshData() {
        val vertCount = stride * stride
        // pos(3), normal(3), uv(2), splat(4) = 12 floats per vertex
        val verts = FloatArray(vertCount * 12)
        val normTmp = Vec3()
        var ptr = 0

        for (gz in 0..GRID_RES) {
            val z = -HALF_SIZE + gz * step
            for (gx in 0..GRID_RES) {
                val x = -HALF_SIZE + gx * step
                val y = heights[gz * stride + gx]
                getNormalAt(x, z, normTmp)

                val slope = 1f - normTmp.y
                val rockWeight = Math3D.smoothstep(0.16f, 0.38f, slope) + Math3D.smoothstep(21f, 34f, y) * 0.85f
                val shoreMud = 1f - Math3D.smoothstep(-0.2f, 2.2f, y)
                val trailDirt = trailFactor(x, z) * Math3D.smoothstep(0.4f, 1.8f, y)
                val mudWeight = Math3D.clamp(shoreMud + trailDirt * 0.85f, 0f, 1f)
                val grassWeight = Math3D.clamp(1f - rockWeight - mudWeight * 0.75f, 0.05f, 1f)
                val ao = Math3D.clamp(0.68f + (y / 28f) * 0.25f - slope * 0.35f, 0.45f, 1.0f)

                verts[ptr++] = x
                verts[ptr++] = y
                verts[ptr++] = z
                verts[ptr++] = normTmp.x
                verts[ptr++] = normTmp.y
                verts[ptr++] = normTmp.z
                verts[ptr++] = gx.toFloat() / GRID_RES.toFloat()
                verts[ptr++] = gz.toFloat() / GRID_RES.toFloat()
                verts[ptr++] = grassWeight
                verts[ptr++] = mudWeight
                verts[ptr++] = Math3D.clamp(rockWeight, 0f, 1.4f)
                verts[ptr++] = ao
            }
        }

        val indices = IntArray(GRID_RES * GRID_RES * 6)
        var iPtr = 0
        for (gz in 0 until GRID_RES) {
            for (gx in 0 until GRID_RES) {
                val i0 = gz * stride + gx
                val i1 = i0 + 1
                val i2 = i0 + stride
                val i3 = i2 + 1
                indices[iPtr++] = i0
                indices[iPtr++] = i2
                indices[iPtr++] = i1
                indices[iPtr++] = i1
                indices[iPtr++] = i2
                indices[iPtr++] = i3
            }
        }
        preparedTerrainVerts = verts
        preparedTerrainIndices = indices
        indexCount = indices.size
    }

    private fun uploadTerrainMesh() {
        val verts = preparedTerrainVerts ?: return
        val indices = preparedTerrainIndices ?: return

        val vaoArr = IntArray(1)
        val bufs = IntArray(2)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(2, bufs, 0)
        vao = vaoArr[0]; vbo = bufs[0]; ebo = bufs[1]

        GLES30.glBindVertexArray(vao)
        val vBuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(verts); position(0)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        val iBuf = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
            put(indices); position(0)
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

        val strideBytes = 12 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, strideBytes, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, strideBytes, 6 * 4)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, strideBytes, 8 * 4)
        GLES30.glBindVertexArray(0)
    }

    private fun prepareWaterMeshData() {
        val res = 56
        val minX = LAKE_CENTER_X - LAKE_RADIUS_X * 1.42f
        val maxX = LAKE_CENTER_X + LAKE_RADIUS_X * 1.42f
        val minZ = LAKE_CENTER_Z - LAKE_RADIUS_Z * 1.42f
        val maxZ = LAKE_CENTER_Z + LAKE_RADIUS_Z * 1.42f
        val wStride = res + 1
        // pos(3), uv(2), depth(1) = 6 floats per vert
        val verts = FloatArray(wStride * wStride * 6)
        var ptr = 0
        for (gz in 0..res) {
            val vz = gz.toFloat() / res.toFloat()
            val z = Math3D.lerp(minZ, maxZ, vz)
            for (gx in 0..res) {
                val vx = gx.toFloat() / res.toFloat()
                val x = Math3D.lerp(minX, maxX, vx)
                val bedH = getHeightAt(x, z)
                val depth = max(0f, WATER_LEVEL - bedH)
                verts[ptr++] = x
                verts[ptr++] = WATER_LEVEL
                verts[ptr++] = z
                verts[ptr++] = vx
                verts[ptr++] = vz
                verts[ptr++] = depth
            }
        }

        val indices = IntArray(res * res * 6)
        var iPtr = 0
        for (gz in 0 until res) {
            for (gx in 0 until res) {
                val i0 = gz * wStride + gx
                val i1 = i0 + 1
                val i2 = i0 + wStride
                val i3 = i2 + 1
                indices[iPtr++] = i0; indices[iPtr++] = i2; indices[iPtr++] = i1
                indices[iPtr++] = i1; indices[iPtr++] = i2; indices[iPtr++] = i3
            }
        }
        preparedWaterVerts = verts
        preparedWaterIndices = indices
        waterIndexCount = indices.size
    }

    private fun uploadWaterMesh() {
        val verts = preparedWaterVerts ?: return
        val indices = preparedWaterIndices ?: return

        val vaoArr = IntArray(1)
        val bufs = IntArray(2)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(2, bufs, 0)
        waterVao = vaoArr[0]; waterVbo = bufs[0]; waterEbo = bufs[1]

        GLES30.glBindVertexArray(waterVao)
        val vBuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(verts); position(0)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, waterVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        val iBuf = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
            put(indices); position(0)
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, waterEbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

        val strideBytes = 6 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, strideBytes, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, strideBytes, 5 * 4)
        GLES30.glBindVertexArray(0)
    }

    fun dispose() {
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (vbo != 0) GLES30.glDeleteBuffers(2, intArrayOf(vbo, ebo), 0)
        if (waterVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(waterVao), 0)
        if (waterVbo != 0) GLES30.glDeleteBuffers(2, intArrayOf(waterVbo, waterEbo), 0)
        vao = 0; vbo = 0; ebo = 0; waterVao = 0; waterVbo = 0; waterEbo = 0
    }
}
