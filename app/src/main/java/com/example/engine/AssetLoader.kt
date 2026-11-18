package com.example.engine

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Hybrid Drop-In Asset Pipeline.
 * Silently checks Android app assets (`assets/models/` for `.obj` files and `assets/hdri/forest.hdr`)
 * so custom photogrammetry models dropped into `app/src/main/assets/` automatically upgrade
 * procedural meshes without producing noisy errors when absent.
 */
class AssetLoader(private val context: Context) {

    data class MeshData(
        val vertices: FloatArray, // interleaved: pos(3), normal(3), uv(2) = 8 floats/vert
        val indices: IntArray
    )

    private val availableModelAssets: Set<String> by lazy {
        try {
            context.assets.list("models")?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private val availableHdriAssets: Set<String> by lazy {
        try {
            context.assets.list("hdri")?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun hasDropInHdri(fileName: String = "forest.hdr"): Boolean =
        availableHdriAssets.contains(fileName)

    /**
     * Attempts to load an optional drop-in Wavefront .obj mesh from `assets/models/<name>.obj`.
     * Returns null silently if not bundled, letting the engine seamlessly render procedural assets.
     */
    fun tryLoadObjModel(baseName: String): MeshData? {
        val fileName = "$baseName.obj"
        if (!availableModelAssets.contains(fileName)) return null
        return try {
            context.assets.open("models/$fileName").use { stream ->
                parseObj(BufferedReader(InputStreamReader(stream)))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseObj(reader: BufferedReader): MeshData? {
        val positions = ArrayList<FloatArray>()
        val normals = ArrayList<FloatArray>()
        val uvs = ArrayList<FloatArray>()
        val outVerts = ArrayList<Float>()
        val outIndices = ArrayList<Int>()
        val vertMap = HashMap<String, Int>()

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("v ") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) {
                        positions.add(floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat()))
                    }
                }
                line.startsWith("vn ") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) {
                        normals.add(floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat()))
                    }
                }
                line.startsWith("vt ") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 3) {
                        uvs.add(floatArrayOf(p[1].toFloat(), p[2].toFloat()))
                    }
                }
                line.startsWith("f ") -> {
                    val tokens = line.split(Regex("\\s+")).drop(1)
                    if (tokens.size >= 3) {
                        val faceIds = IntArray(tokens.size) { idx ->
                            val key = tokens[idx]
                            vertMap.getOrPut(key) {
                                val parts = key.split("/")
                                val vi = (parts[0].toIntOrNull() ?: 1) - 1
                                val ti = (parts.getOrNull(1)?.toIntOrNull() ?: 0) - 1
                                val ni = (parts.getOrNull(2)?.toIntOrNull() ?: 0) - 1
                                val pos = positions.getOrNull(vi) ?: floatArrayOf(0f, 0f, 0f)
                                val nrm = normals.getOrNull(ni) ?: floatArrayOf(0f, 1f, 0f)
                                val uv = uvs.getOrNull(ti) ?: floatArrayOf(0f, 0f)
                                val newIndex = outVerts.size / 8
                                outVerts.add(pos[0]); outVerts.add(pos[1]); outVerts.add(pos[2])
                                outVerts.add(nrm[0]); outVerts.add(nrm[1]); outVerts.add(nrm[2])
                                outVerts.add(uv[0]); outVerts.add(uv[1])
                                newIndex
                            }
                        }
                        for (i in 1 until faceIds.size - 1) {
                            outIndices.add(faceIds[0])
                            outIndices.add(faceIds[i])
                            outIndices.add(faceIds[i + 1])
                        }
                    }
                }
            }
        }
        if (outIndices.isEmpty()) return null
        return MeshData(outVerts.toFloatArray(), outIndices.toIntArray())
    }
}
