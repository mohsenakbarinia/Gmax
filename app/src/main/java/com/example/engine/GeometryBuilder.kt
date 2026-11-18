package com.example.engine

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GlMesh(
    val vao: Int,
    private val vbo: Int,
    private val ebo: Int,
    val indexCount: Int
) {
    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun dispose() {
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (vbo != 0) GLES30.glDeleteBuffers(2, intArrayOf(vbo, ebo), 0)
    }
}

/**
 * Procedural 3D mesh generator producing interleaved (pos3, norm3, uv2) meshes
 * for terrain props, multi-tiered conifers, articulated wildlife parts, and first-person weapons.
 */
object GeometryBuilder {

    class Builder {
        val verts = ArrayList<Float>()
        val indices = ArrayList<Int>()

        fun addVert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float): Int {
            val idx = verts.size / 8
            verts.add(x); verts.add(y); verts.add(z)
            verts.add(nx); verts.add(ny); verts.add(nz)
            verts.add(u); verts.add(v)
            return idx
        }

        fun addTri(a: Int, b: Int, c: Int) {
            indices.add(a); indices.add(b); indices.add(c)
        }

        fun addBox(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float) {
            val hx = sx * 0.5f; val hy = sy * 0.5f; val hz = sz * 0.5f
            fun face(
                x0: Float, y0: Float, z0: Float,
                x1: Float, y1: Float, z1: Float,
                x2: Float, y2: Float, z2: Float,
                x3: Float, y3: Float, z3: Float,
                nx: Float, ny: Float, nz: Float
            ) {
                val i0 = addVert(cx + x0, cy + y0, cz + z0, nx, ny, nz, 0f, 0f)
                val i1 = addVert(cx + x1, cy + y1, cz + z1, nx, ny, nz, 1f, 0f)
                val i2 = addVert(cx + x2, cy + y2, cz + z2, nx, ny, nz, 1f, 1f)
                val i3 = addVert(cx + x3, cy + y3, cz + z3, nx, ny, nz, 0f, 1f)
                addTri(i0, i1, i2); addTri(i0, i2, i3)
            }
            face(-hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz, 0f, 0f, 1f)
            face(hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, 0f, 0f, -1f)
            face(hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, 1f, 0f, 0f)
            face(-hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz, -1f, 0f, 0f)
            face(-hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz, 0f, 1f, 0f)
            face(-hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz, 0f, -1f, 0f)
        }

        fun addCylinder(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            r0: Float, r1: Float,
            segments: Int = 10,
            capEnds: Boolean = true
        ) {
            val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
            val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
            val ux = dx / len; val uy = dy / len; val uz = dz / len

            // Orthonormal basis perpendicular to axis
            val refX = if (kotlin.math.abs(uy) < 0.9f) 0f else 1f
            val refY = if (kotlin.math.abs(uy) < 0.9f) 1f else 0f
            val refZ = 0f
            var tx = uy * refZ - uz * refY
            var ty = uz * refX - ux * refZ
            var tz = ux * refY - uy * refX
            val tLen = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-5f)
            tx /= tLen; ty /= tLen; tz /= tLen

            val bx = uy * tz - uz * ty
            val by = uz * tx - ux * tz
            val bz = ux * ty - uy * tx

            val ring0 = IntArray(segments + 1)
            val ring1 = IntArray(segments + 1)
            for (s in 0..segments) {
                val frac = s.toFloat() / segments.toFloat()
                val ang = frac * (2.0 * PI).toFloat()
                val ca = cos(ang); val sa = sin(ang)
                val nx = tx * ca + bx * sa
                val ny = ty * ca + by * sa
                val nz = tz * ca + bz * sa
                ring0[s] = addVert(x0 + nx * r0, y0 + ny * r0, z0 + nz * r0, nx, ny, nz, frac, 0f)
                ring1[s] = addVert(x1 + nx * r1, y1 + ny * r1, z1 + nz * r1, nx, ny, nz, frac, 1f)
            }
            for (s in 0 until segments) {
                addTri(ring0[s], ring0[s + 1], ring1[s + 1])
                addTri(ring0[s], ring1[s + 1], ring1[s])
            }
            if (capEnds && r1 > 0.001f) {
                val topCenter = addVert(x1, y1, z1, ux, uy, uz, 0.5f, 0.5f)
                for (s in 0 until segments) {
                    addTri(ring1[s], ring1[s + 1], topCenter)
                }
            }
            if (capEnds && r0 > 0.001f) {
                val botCenter = addVert(x0, y0, z0, -ux, -uy, -uz, 0.5f, 0.5f)
                for (s in 0 until segments) {
                    addTri(ring0[s + 1], ring0[s], botCenter)
                }
            }
        }

        fun addEllipsoid(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, stacks: Int = 8, slices: Int = 12, craggy: Float = 0f) {
            val grid = Array(stacks + 1) { IntArray(slices + 1) }
            for (i in 0..stacks) {
                val v = i.toFloat() / stacks.toFloat()
                val phi = v * PI.toFloat()
                val sp = sin(phi); val cp = cos(phi)
                for (j in 0..slices) {
                    val u = j.toFloat() / slices.toFloat()
                    val theta = u * (2.0 * PI).toFloat()
                    val nx = sp * cos(theta)
                    val ny = cp
                    val nz = sp * sin(theta)
                    val bump = if (craggy > 0f) 1f + (Noise2D.valueNoise(nx * 3.1f + 5f, nz * 3.1f + ny * 2.3f, 77) - 0.5f) * craggy else 1f
                    grid[i][j] = addVert(
                        cx + nx * rx * bump,
                        cy + ny * ry * bump,
                        cz + nz * rz * bump,
                        nx, ny, nz, u, v
                    )
                }
            }
            for (i in 0 until stacks) {
                for (j in 0 until slices) {
                    addTri(grid[i][j], grid[i + 1][j], grid[i + 1][j + 1])
                    addTri(grid[i][j], grid[i + 1][j + 1], grid[i][j + 1])
                }
            }
        }

        fun toMeshData(): AssetLoader.MeshData =
            AssetLoader.MeshData(verts.toFloatArray(), indices.toIntArray())
    }

    fun uploadMesh(data: AssetLoader.MeshData): GlMesh {
        val vaoArr = IntArray(1)
        val bufs = IntArray(2)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(2, bufs, 0)
        val vao = vaoArr[0]; val vbo = bufs[0]; val ebo = bufs[1]

        GLES30.glBindVertexArray(vao)
        val vBuf = ByteBuffer.allocateDirect(data.vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(data.vertices); position(0)
            }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        val iBuf = ByteBuffer.allocateDirect(data.indices.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer().apply {
                put(data.indices); position(0)
            }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.indices.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glBindVertexArray(0)
        return GlMesh(vao, vbo, ebo, data.indices.size)
    }

    fun createPineTrunk(): AssetLoader.MeshData = Builder().apply {
        addCylinder(0f, -0.4f, 0f, 0f, 8.8f, 0f, 0.36f, 0.08f, 8, true)
    }.toMeshData()

    fun createPineCanopy(): AssetLoader.MeshData = Builder().apply {
        // Multi-tiered Douglas Fir needle boughs
        val tiers = listOf(
            Triple(2.4f, 3.0f, 2.75f),
            Triple(4.3f, 2.7f, 2.35f),
            Triple(6.1f, 2.4f, 1.90f),
            Triple(7.8f, 2.1f, 1.40f),
            Triple(9.3f, 2.0f, 0.85f)
        )
        tiers.forEachIndexed { idx, (yBase, height, radius) ->
            val segs = 9
            val tipIdx = addVert(0f, yBase + height, 0f, 0f, 1f, 0f, 0.5f, 1f)
            val ring = IntArray(segs + 1)
            for (s in 0..segs) {
                val frac = s.toFloat() / segs.toFloat()
                val ang = frac * (2.0 * PI).toFloat() + idx * 0.45f
                val droop = if (s % 2 == 0) 0.92f else 1.08f
                val x = cos(ang) * radius * droop
                val z = sin(ang) * radius * droop
                val y = yBase - (if (s % 2 == 0) 0.25f else 0f)
                ring[s] = addVert(x, y, z, cos(ang) * 0.78f, 0.55f, sin(ang) * 0.78f, frac, 0f)
            }
            for (s in 0 until segs) {
                addTri(ring[s], ring[s + 1], tipIdx)
            }
        }
    }.toMeshData()

    fun createBirchTrunk(): AssetLoader.MeshData = Builder().apply {
        addCylinder(0f, -0.3f, 0f, 0.12f, 6.8f, -0.08f, 0.20f, 0.07f, 7, true)
        addCylinder(0.08f, 4.2f, -0.05f, 0.95f, 6.2f, 0.35f, 0.09f, 0.03f, 5, true)
        addCylinder(0.06f, 4.8f, -0.04f, -0.82f, 6.6f, -0.45f, 0.08f, 0.03f, 5, true)
    }.toMeshData()

    fun createBirchCanopy(): AssetLoader.MeshData = Builder().apply {
        addEllipsoid(0f, 7.2f, 0f, 1.95f, 1.65f, 1.85f, 6, 8, 0.35f)
        addEllipsoid(1.0f, 6.4f, 0.4f, 1.35f, 1.15f, 1.25f, 5, 7, 0.3f)
        addEllipsoid(-0.9f, 6.7f, -0.45f, 1.30f, 1.10f, 1.20f, 5, 7, 0.3f)
    }.toMeshData()

    fun createBoulder(): AssetLoader.MeshData = Builder().apply {
        addEllipsoid(0f, 0.35f, 0f, 1.35f, 0.92f, 1.15f, 6, 9, 0.42f)
    }.toMeshData()

    fun createMossLog(): AssetLoader.MeshData = Builder().apply {
        addCylinder(-2.2f, 0.28f, 0f, 2.2f, 0.24f, 0.18f, 0.34f, 0.24f, 8, true)
    }.toMeshData()

    fun createFernCluster(): AssetLoader.MeshData = Builder().apply {
        val fronds = 7
        for (f in 0 until fronds) {
            val ang = f * (2.0 * PI / fronds).toFloat()
            val ca = cos(ang); val sa = sin(ang)
            val i0 = addVert(0f, 0.02f, 0f, 0f, 1f, 0f, 0.5f, 0f)
            val i1 = addVert(ca * 0.55f - sa * 0.18f, 0.38f, sa * 0.55f + ca * 0.18f, ca * 0.3f, 0.92f, sa * 0.3f, 0f, 0.5f)
            val i2 = addVert(ca * 0.55f + sa * 0.18f, 0.38f, sa * 0.55f - ca * 0.18f, ca * 0.3f, 0.92f, sa * 0.3f, 1f, 0.5f)
            val i3 = addVert(ca * 1.10f, 0.18f, sa * 1.10f, ca * 0.45f, 0.85f, sa * 0.45f, 0.5f, 1f)
            addTri(i0, i1, i2); addTri(i1, i3, i2)
        }
    }.toMeshData()

    fun createGrassTuft(): AssetLoader.MeshData = Builder().apply {
        val blades = 5
        for (b in 0 until blades) {
            val ang = b * (PI.toFloat() / blades) + 0.2f
            val ca = cos(ang); val sa = sin(ang)
            val ox = (b - 2) * 0.08f
            val oz = ((b % 3) - 1) * 0.08f
            val w = 0.09f
            val h = 0.48f + (b % 2) * 0.14f
            val i0 = addVert(ox - ca * w, 0f, oz - sa * w, -sa, 0.4f, ca, 0f, 0f)
            val i1 = addVert(ox + ca * w, 0f, oz + sa * w, -sa, 0.4f, ca, 1f, 0f)
            val i2 = addVert(ox + ca * 0.04f, h, oz + sa * 0.04f, 0f, 1f, 0f, 0.5f, 1f)
            addTri(i0, i1, i2)
            addTri(i1, i0, i2)
        }
    }.toMeshData()

    fun createUnitSphere(): AssetLoader.MeshData = Builder().apply {
        addEllipsoid(0f, 0f, 0f, 1f, 1f, 1f, 8, 12, 0f)
    }.toMeshData()

    fun createUnitCylinder(): AssetLoader.MeshData = Builder().apply {
        addCylinder(0f, 0f, 0f, 0f, 1f, 0f, 1f, 0.78f, 8, true)
    }.toMeshData()

    fun createUnitBox(): AssetLoader.MeshData = Builder().apply {
        addBox(0f, 0f, 0f, 1f, 1f, 1f)
    }.toMeshData()

    fun createStagAntlerRack(): AssetLoader.MeshData = Builder().apply {
        for (side in listOf(-1f, 1f)) {
            // Main sweeping antler beam
            addCylinder(side * 0.08f, 0f, 0f, side * 0.34f, 0.36f, -0.14f, 0.032f, 0.022f, 6, true)
            addCylinder(side * 0.34f, 0.36f, -0.14f, side * 0.54f, 0.72f, -0.02f, 0.022f, 0.012f, 6, true)
            // Brow tine (forward)
            addCylinder(side * 0.17f, 0.12f, -0.04f, side * 0.24f, 0.28f, 0.20f, 0.018f, 0.007f, 5, true)
            // Bez & Trez tines
            addCylinder(side * 0.32f, 0.34f, -0.13f, side * 0.42f, 0.56f, 0.16f, 0.016f, 0.007f, 5, true)
            addCylinder(side * 0.46f, 0.56f, -0.07f, side * 0.64f, 0.80f, -0.18f, 0.014f, 0.006f, 5, true)
            addCylinder(side * 0.50f, 0.65f, -0.04f, side * 0.42f, 0.88f, 0.08f, 0.012f, 0.005f, 5, true)
        }
    }.toMeshData()

    fun createBoarTusks(): AssetLoader.MeshData = Builder().apply {
        for (side in listOf(-1f, 1f)) {
            addCylinder(side * 0.10f, -0.04f, 0.05f, side * 0.18f, 0.11f, 0.14f, 0.024f, 0.006f, 6, true)
        }
    }.toMeshData()

    fun createRifleWoodStock(): AssetLoader.MeshData = Builder().apply {
        // Buttstock, ergonomic pistol grip, and contoured walnut forend
        addBox(0f, -0.055f, 0.34f, 0.048f, 0.115f, 0.36f)
        addBox(0f, -0.050f, 0.10f, 0.045f, 0.072f, 0.20f)
        addBox(0f, -0.022f, -0.18f, 0.044f, 0.052f, 0.42f)
    }.toMeshData()

    fun createRifleGunmetal(): AssetLoader.MeshData = Builder().apply {
        // Receiver block
        addBox(0f, 0.018f, -0.02f, 0.040f, 0.046f, 0.22f)
        // Precision free-floating .308 barrel (extends forward along -Z)
        addCylinder(0f, 0.022f, -0.12f, 0f, 0.022f, -0.82f, 0.017f, 0.013f, 10, true)
        // Scope rings & 4x-12x hunting optic tube
        addCylinder(0f, 0.078f, 0.08f, 0f, 0.078f, -0.22f, 0.024f, 0.029f, 12, true)
        addBox(0f, 0.050f, 0.02f, 0.026f, 0.032f, 0.025f)
        addBox(0f, 0.050f, -0.12f, 0.026f, 0.032f, 0.025f)
        // Bolt handle
        addCylinder(0.020f, 0.022f, 0.05f, 0.068f, -0.005f, 0.07f, 0.007f, 0.011f, 6, true)
    }.toMeshData()

    fun createBowFrame(): AssetLoader.MeshData = Builder().apply {
        // Ergonomic carbon recurve riser & sweeping curved upper/lower limbs
        addBox(0f, 0f, 0f, 0.032f, 0.34f, 0.045f)
        // Upper limb
        addCylinder(0f, 0.16f, 0f, 0f, 0.44f, 0.06f, 0.016f, 0.012f, 6, true)
        addCylinder(0f, 0.44f, 0.06f, 0f, 0.68f, -0.02f, 0.012f, 0.008f, 6, true)
        // Lower limb
        addCylinder(0f, -0.16f, 0f, 0f, -0.44f, 0.06f, 0.016f, 0.012f, 6, true)
        addCylinder(0f, -0.44f, 0.06f, 0f, -0.68f, -0.02f, 0.012f, 0.008f, 6, true)
        // Sight ring
        addBox(0.028f, 0.06f, -0.06f, 0.016f, 0.036f, 0.036f)
    }.toMeshData()

    fun createArrowMesh(): AssetLoader.MeshData = Builder().apply {
        addCylinder(0f, 0f, 0.36f, 0f, 0f, -0.42f, 0.006f, 0.005f, 6, true)
        // Broadhead tip
        addCylinder(0f, 0f, -0.42f, 0f, 0f, -0.50f, 0.014f, 0.001f, 4, true)
        // Fletching vanes
        addBox(0f, 0.014f, 0.30f, 0.004f, 0.028f, 0.09f)
        addBox(0.012f, -0.008f, 0.30f, 0.026f, 0.004f, 0.09f)
    }.toMeshData()

    fun createDecalDisc(): AssetLoader.MeshData = Builder().apply {
        val segs = 10
        val center = addVert(0f, 0.04f, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
        val ring = IntArray(segs + 1)
        for (s in 0..segs) {
            val ang = (s.toFloat() / segs) * (2.0 * PI).toFloat()
            val c = cos(ang); val sn = sin(ang)
            ring[s] = addVert(c * 0.42f, 0.04f, sn * 0.42f, 0f, 1f, 0f, c * 0.5f + 0.5f, sn * 0.5f + 0.5f)
        }
        for (s in 0 until segs) {
            addTri(center, ring[s + 1], ring[s])
        }
    }.toMeshData()

    fun createSkyCubeVao(): Int {
        val v = floatArrayOf(
            -1f, 1f, -1f, -1f, -1f, -1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f, -1f,
            -1f, -1f, 1f, -1f, -1f, -1f, -1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f,
            1f, -1f, -1f, 1f, -1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, 1f, -1f, -1f,
            -1f, -1f, 1f, -1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, 1f, -1f, -1f, 1f,
            -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f,
            -1f, -1f, -1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f, -1f, -1f, -1f, -1f, 1f, 1f, -1f, 1f
        )
        val vaoArr = IntArray(1); val vboArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)
        GLES30.glBindVertexArray(vaoArr[0])
        val buf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(v); position(0)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboArr[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, v.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)
        GLES30.glBindVertexArray(0)
        return vaoArr[0]
    }

    fun createFullscreenQuadVao(): Int {
        val v = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f)
        val vaoArr = IntArray(1); val vboArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)
        GLES30.glBindVertexArray(vaoArr[0])
        val buf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(v); position(0)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboArr[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, v.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glBindVertexArray(0)
        return vaoArr[0]
    }
}
