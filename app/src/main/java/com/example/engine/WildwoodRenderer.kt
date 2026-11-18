package com.example.engine

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.data.GameMode
import com.example.data.Species
import com.example.data.WeaponType
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-Fidelity OpenGL ES 3.0 Renderer for Wildwood: Reality.
 * Render Pipeline:
 *  1. Frustum-fitted Directional Sun PCF Shadow Map FBO
 *  2. Offscreen HDR Scene FBO:
 *     - Physical Atmospheric Sky Dome (Rayleigh/Mie, Clouds, Stars, Lightning)
 *     - Multi-Layer PBR Alpine Valley Terrain
 *     - Instanced Wind-Sway Foliage (Douglas Firs, Birches, Boulders, Logs, Ferns, Grass)
 *     - Articulated Multi-Part 3D Wildlife (Stag, Boar, Wolf) + Track/Blood Decals
 *     - Reflective Mountain Lake Water Shader (Fresnel, Depth Absorption, Ripples, Foam)
 *     - Volumetric God Rays & Atmospheric Weather Particles
 *     - Depth-Cleared First-Person PBR Weapons (.308 Bolt Rifle & Carbon Recurve Bow)
 *  3. Selective HDR Bright-Pass Bloom + ACES Filmic / Scope Chromatic Composite Pass
 */
class WildwoodRenderer(private val engine: GameEngine) : GLSurfaceView.Renderer {

    // Programs
    private var skyProg = 0
    private var shadowProg = 0
    private var shadowInstProg = 0
    private var terrainProg = 0
    private var instancedProg = 0
    private var worldPbrProg = 0
    private var waterProg = 0
    private var effectsProg = 0
    private var brightProg = 0
    private var blurProg = 0
    private var compositeProg = 0

    // Core geometry
    private var skyCubeVao = 0
    private var quadVao = 0
    private lateinit var sphereMesh: GlMesh
    private lateinit var cylinderMesh: GlMesh
    private lateinit var boxMesh: GlMesh
    private lateinit var antlerMesh: GlMesh
    private lateinit var tuskMesh: GlMesh
    private lateinit var rifleStockMesh: GlMesh
    private lateinit var rifleMetalMesh: GlMesh
    private lateinit var bowFrameMesh: GlMesh
    private lateinit var arrowMesh: GlMesh
    private lateinit var decalMesh: GlMesh

    // Textures (fallback white/flat normal until 512px synthesis finishes)
    private var fallbackWhiteTex = 0
    private var fallbackNormalTex = 0
    private val materials = HashMap<PbrMaterialId, GlPbrMaterial>()
    private val cachedTextureData = java.util.concurrent.ConcurrentHashMap<PbrMaterialId, SynthesizedTextureData>()
    private val pendingTextureQueue = ConcurrentLinkedQueue<Pair<PbrMaterialId, SynthesizedTextureData>>()
    @Volatile private var synthesisStarted = false
    @Volatile private var synthesisCompleted = false

    // Progressive zero-stall initialization stages (0 -> 5 = READY)
    private var initStage = 0
    private var viewportDirty = true

    // FBOs
    private var shadowFbo = 0
    private var shadowDepthTex = 0
    private var currentShadowSize = 1536

    private var sceneFbo = 0
    private var sceneColorTex = 0
    private var sceneDepthRb = 0
    private var bloomFboA = 0
    private var bloomTexA = 0
    private var bloomFboB = 0
    private var bloomTexB = 0
    private var viewW = 1280
    private var viewH = 720
    private var fboW = 0
    private var fboH = 0
    private var currentRenderScale = 0.92f

    // Reusable matrices (zero per-frame heap churn)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val viewProjMatrix = FloatArray(16)
    private val skyViewRotProj = FloatArray(16)
    private val lightViewMatrix = FloatArray(16)
    private val lightProjMatrix = FloatArray(16)
    private val lightSpaceMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tmpMvp = FloatArray(16)
    private val fpProjMatrix = FloatArray(16)

    // Frame timing & FPS governor
    private var lastNano = 0L
    private var fpsFrames = 0
    private var fpsTimer = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glClearColor(0.04f, 0.07f, 0.06f, 1f)

        // Reset GL state cleanly and defer heavy shader/mesh setup into frame-sliced stages
        // so Frame 0 completes eglSwapBuffers in <5ms and never stalls SurfaceSyncGroup.
        materials.clear()
        sceneFbo = 0; shadowFbo = 0; fboW = 0; fboH = 0
        initStage = 0
        viewportDirty = true
        lastNano = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width.coerceAtLeast(1)
        viewH = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewW, viewH)
        viewportDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNano) * 1e-9).toFloat().coerceIn(0.001f, 0.05f)
        lastNano = now

        // Frame-sliced progressive initialization so every frame stays fast and responsive
        if (initStage < 5) {
            runInitStage()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, viewW, viewH)
            GLES30.glClearColor(0.04f, 0.07f, 0.06f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            return
        }

        if (viewportDirty) {
            viewportDirty = false
            recreateSceneFbos()
        }

        fpsFrames++
        fpsTimer += dt
        if (fpsTimer >= 0.5f) {
            engine.currentFps = (fpsFrames / fpsTimer).toInt().coerceIn(1, 120)
            fpsFrames = 0
            fpsTimer = 0f
        }

        // Upload at most ONE synthesized 512px PBR material per frame to avoid any frame hitch
        val nextMat = pendingTextureQueue.poll()
        if (nextMat != null) {
            materials[nextMat.first] = TextureSynthesizer.uploadToGl(nextMat.second)
        }
        if (synthesisCompleted && pendingTextureQueue.isEmpty() && !engine.texturesReady) {
            engine.texturesReady = true
            engine.textureProgress = 1f
            engine.publishHudImmediate()
        }

        // React dynamically to Graphics Quality preset changes
        val quality = engine.settings.graphicsQuality
        if (quality.shadowSize != currentShadowSize) {
            currentShadowSize = quality.shadowSize
            createShadowFbo(currentShadowSize)
        }
        if (kotlin.math.abs(quality.renderScale - currentRenderScale) > 0.01f) {
            currentRenderScale = quality.renderScale
            recreateSceneFbos()
        }

        // Advance simulation
        engine.step(dt)

        val atmo = AtmosphereCatalog.profileFor(engine.settings.weatherPreset)
        val cam = engine.camPos
        val fwd = engine.camForward

        // Camera projection (dynamic FOV for 4x-12x Optical Scope)
        val aspect = viewW.toFloat() / viewH.toFloat()
        val fovDeg = if (engine.scoped && engine.gameMode == GameMode.PLAYING) {
            (62f / engine.scopeZoom).coerceIn(5.2f, 34f)
        } else {
            65f
        }
        Matrix.perspectiveM(projMatrix, 0, fovDeg, aspect, 0.25f, 440f)
        Matrix.setLookAtM(
            viewMatrix, 0,
            cam.x, cam.y, cam.z,
            cam.x + fwd.x, cam.y + fwd.y, cam.z + fwd.z,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProjMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Tight directional Sun Shadow Map centered on player frustum
        val shadowHalf = 68f
        val snap = 2.0f
        val scx = kotlin.math.floor(cam.x / snap) * snap
        val scz = kotlin.math.floor(cam.z / snap) * snap
        val scy = engine.terrain.getHeightAt(scx, scz)
        Matrix.setLookAtM(
            lightViewMatrix, 0,
            scx + atmo.sunDir.x * 95f, scy + atmo.sunDir.y * 95f, scz + atmo.sunDir.z * 95f,
            scx, scy, scz,
            0f, 1f, 0f
        )
        Matrix.orthoM(lightProjMatrix, 0, -shadowHalf, shadowHalf, -shadowHalf, shadowHalf, 15f, 220f)
        Matrix.multiplyMM(lightSpaceMatrix, 0, lightProjMatrix, 0, lightViewMatrix, 0)

        // =====================================================================
        // PASS 1: DIRECTIONAL SUN SHADOW MAP
        // =====================================================================
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, currentShadowSize, currentShadowSize)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glCullFace(GLES30.GL_FRONT)

        GLES30.glUseProgram(shadowProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowProg, "uLightSpaceMvp"), 1, false, lightSpaceMatrix, 0)
        GLES30.glBindVertexArray(engine.terrain.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, engine.terrain.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)

        GLES30.glUseProgram(shadowInstProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowInstProg, "uLightSpaceMatrix"), 1, false, lightSpaceMatrix, 0)
        engine.foliage.pineTrunks.draw(1f)
        engine.foliage.pineCanopies.draw(1f)
        engine.foliage.birchTrunks.draw(1f)
        engine.foliage.birchCanopies.draw(1f)
        engine.foliage.boulders.draw(1f)
        GLES30.glCullFace(GLES30.GL_BACK)

        // =====================================================================
        // PASS 2: OFFSCREEN HDR SCENE RENDER
        // =====================================================================
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
        GLES30.glViewport(0, 0, fboW, fboH)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 2A. Multi-Layer Alpine Terrain
        drawTerrain(atmo)

        // 2B. Instanced Wind-Sway Forest, Boulders, Moss Logs, Ferns & Grass
        drawInstancedFoliage(atmo, quality.grassDensity)

        // 2C. Articulated 3D Wildlife, Track/Blood Decals, Embedded Arrows & Brass Shells
        drawWildlifeAndDynamicWorld(atmo)

        // 2D. Reflective Mountain Lake Water Shader
        drawWater(atmo)

        // 2E. Physical Atmospheric Sky Dome (depth LEQUAL at z=1.0)
        drawSkyDome(atmo)

        // 2F. Volumetric Sun Shafts (God Rays) & Weather Particles
        val effVpLoc = GLES30.glGetUniformLocation(effectsProg, "uViewProj")
        val effPtLoc = GLES30.glGetUniformLocation(effectsProg, "uIsPoint")
        if (quality.godRaysEnabled) {
            engine.foliage.drawVolumetricGodRays(
                atmo.sunDir, engine.settings.weatherPreset, cam, effectsProg, effVpLoc, effPtLoc, viewProjMatrix
            )
        }
        engine.foliage.drawAtmosphericParticles(
            dt, engine.simTime, cam, engine.settings.weatherPreset,
            engine.windDirX, engine.windDirZ, effectsProg, effVpLoc, effPtLoc, viewProjMatrix
        )

        // 2G. First-Person 3D Weapons (rendered cleanly over scene when playing & not full-screen scoped)
        if (engine.gameMode != GameMode.TITLE_ORBIT && !(engine.scoped && engine.weapons.activeWeapon == WeaponType.RIFLE_308)) {
            drawFirstPersonWeapon(atmo, aspect)
        }

        // =====================================================================
        // PASS 3: SELECTIVE BLOOM + ACES FILMIC COMPOSITE TO SCREEN
        // =====================================================================
        val bloomStrength = if (quality.bloomEnabled) 0.42f else 0.0f
        if (quality.bloomEnabled) {
            val bw = (fboW / 2).coerceAtLeast(1)
            val bh = (fboH / 2).coerceAtLeast(1)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)

            // Bright pass (threshold 0.90 so only sun glints, water highlights, fireflies & muzzle flash bloom)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
            GLES30.glViewport(0, 0, bw, bh)
            GLES30.glUseProgram(brightProg)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(brightProg, "uSceneTex"), 0)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(brightProg, "uThreshold"), 0.90f)
            drawQuad()

            // Horizontal Gaussian blur
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboB)
            GLES30.glUseProgram(blurProg)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexA)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(blurProg, "uTex"), 0)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(blurProg, "uDir"), 1.6f, 0f)
            drawQuad()

            // Vertical Gaussian blur
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexB)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(blurProg, "uDir"), 0f, 1.6f)
            drawQuad()
        }

        // Final Composite to Default Backbuffer
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(compositeProg)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProg, "uSceneTex"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexA)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProg, "uBloomTex"), 1)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProg, "uBloomStrength"), bloomStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProg, "uExposure"), atmo.exposure + engine.lightningFlash * 0.65f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProg, "uHunterSense"), engine.hunterSenseFactor)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProg, "uScoped"), if (engine.scoped && engine.weapons.activeWeapon == WeaponType.RIFLE_308) 1f else 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProg, "uWoundedFlash"), engine.woundedFlash)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(compositeProg, "uColorGradeTint"), atmo.colorGradeTint.x, atmo.colorGradeTint.y, atmo.colorGradeTint.z)
        drawQuad()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun runInitStage() {
        when (initStage) {
            0 -> {
                // Stage 0: Present clean dark forest buffer immediately so SurfaceSyncGroup succeeds in <5ms
                initStage = 1
            }
            1 -> {
                // Stage 1: Compile core world shaders + fallback textures + basic VAOs
                skyProg = ShaderLibrary.compileProgram(ShaderLibrary.SKY_VS, ShaderLibrary.SKY_FS, "Sky")
                terrainProg = ShaderLibrary.compileProgram(ShaderLibrary.TERRAIN_VS, ShaderLibrary.TERRAIN_FS, "Terrain")
                instancedProg = ShaderLibrary.compileProgram(ShaderLibrary.INSTANCED_VS, ShaderLibrary.INSTANCED_FS, "Instanced")
                worldPbrProg = ShaderLibrary.compileProgram(ShaderLibrary.WORLD_PBR_VS, ShaderLibrary.WORLD_PBR_FS, "WorldPBR")
                waterProg = ShaderLibrary.compileProgram(ShaderLibrary.WATER_VS, ShaderLibrary.WATER_FS, "Water")
                skyCubeVao = GeometryBuilder.createSkyCubeVao()
                quadVao = GeometryBuilder.createFullscreenQuadVao()
                fallbackWhiteTex = createSolidTex(200, 200, 200, 255)
                fallbackNormalTex = createSolidTex(128, 128, 255, 255)
                initStage = 2
            }
            2 -> {
                // Stage 2: Compile shadow/effects/post shaders + create FBOs
                shadowProg = ShaderLibrary.compileProgram(ShaderLibrary.SHADOW_VS, ShaderLibrary.SHADOW_FS, "Shadow")
                shadowInstProg = ShaderLibrary.compileProgram(ShaderLibrary.SHADOW_INSTANCED_VS, ShaderLibrary.SHADOW_FS, "ShadowInst")
                effectsProg = ShaderLibrary.compileProgram(ShaderLibrary.EFFECTS_VS, ShaderLibrary.EFFECTS_FS, "Effects")
                brightProg = ShaderLibrary.compileProgram(ShaderLibrary.FULLSCREEN_VS, ShaderLibrary.POST_BRIGHT_FS, "Bright")
                blurProg = ShaderLibrary.compileProgram(ShaderLibrary.FULLSCREEN_VS, ShaderLibrary.POST_BLUR_FS, "Blur")
                compositeProg = ShaderLibrary.compileProgram(ShaderLibrary.FULLSCREEN_VS, ShaderLibrary.POST_COMPOSITE_FS, "Composite")
                currentShadowSize = engine.settings.graphicsQuality.shadowSize
                currentRenderScale = engine.settings.graphicsQuality.renderScale
                createShadowFbo(currentShadowSize)
                recreateSceneFbos()
                viewportDirty = false
                initStage = 3
            }
            3 -> {
                // Stage 3: Wait for background terrain/foliage prep, then upload terrain + wildlife/weapon meshes
                if (!engine.worldReady) return
                engine.terrain.uploadToGl()
                sphereMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createUnitSphere())
                cylinderMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createUnitCylinder())
                boxMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createUnitBox())
                antlerMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createStagAntlerRack())
                tuskMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createBoarTusks())
                rifleStockMesh = GeometryBuilder.uploadMesh(engine.assetLoader.tryLoadObjModel("rifle") ?: GeometryBuilder.createRifleWoodStock())
                rifleMetalMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createRifleGunmetal())
                bowFrameMesh = GeometryBuilder.uploadMesh(engine.assetLoader.tryLoadObjModel("bow") ?: GeometryBuilder.createBowFrame())
                arrowMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createArrowMesh())
                decalMesh = GeometryBuilder.uploadMesh(GeometryBuilder.createDecalDisc())
                initStage = 4
            }
            4 -> {
                // Stage 4: Upload pre-built foliage batches & kick off low-priority streaming PBR synthesis
                engine.foliage.uploadToGl()
                if (!synthesisStarted) {
                    synthesisStarted = true
                    Thread({
                        TextureSynthesizer.synthesizeStreaming { matId, data, progress ->
                            cachedTextureData[matId] = data
                            pendingTextureQueue.offer(matId to data)
                            engine.textureProgress = progress
                            engine.publishHudImmediate()
                        }
                        synthesisCompleted = true
                    }, "WildwoodPbrSynth").apply {
                        isDaemon = true
                        priority = Thread.MIN_PRIORITY
                        start()
                    }
                } else {
                    // Re-queue already synthesized textures if EGL context was recreated
                    pendingTextureQueue.clear()
                    for ((matId, data) in cachedTextureData) {
                        pendingTextureQueue.offer(matId to data)
                    }
                }
                initStage = 5
            }
        }
    }

    private fun drawSkyDome(atmo: AtmosphereProfile) {
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glUseProgram(skyProg)

        // Strip translation from viewMatrix for infinite skybox
        System.arraycopy(viewMatrix, 0, tmpMvp, 0, 16)
        tmpMvp[12] = 0f; tmpMvp[13] = 0f; tmpMvp[14] = 0f
        Matrix.multiplyMM(skyViewRotProj, 0, projMatrix, 0, tmpMvp, 0)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(skyProg, "uViewRotProj"), 1, false, skyViewRotProj, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProg, "uSunDir"), atmo.sunDir.x, atmo.sunDir.y, atmo.sunDir.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProg, "uSkyZenith"), atmo.skyZenith.x, atmo.skyZenith.y, atmo.skyZenith.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProg, "uSkyHorizon"), atmo.skyHorizon.x, atmo.skyHorizon.y, atmo.skyHorizon.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProg, "uSunColor"), atmo.sunColor.x, atmo.sunColor.y, atmo.sunColor.z)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProg, "uTime"), engine.simTime)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProg, "uNightFactor"), atmo.nightFactor)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProg, "uCloudCoverage"), atmo.cloudCoverage)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProg, "uLightningFlash"), engine.lightningFlash)

        GLES30.glBindVertexArray(skyCubeVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 36)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDepthFunc(GLES30.GL_LESS)
    }

    private fun drawTerrain(atmo: AtmosphereProfile) {
        val grassMat = materials[PbrMaterialId.FOREST_FLOOR]
        val rockMat = materials[PbrMaterialId.ALPINE_ROCK]

        GLES30.glUseProgram(terrainProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(terrainProg, "uViewProj"), 1, false, viewProjMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(terrainProg, "uLightSpaceMatrix"), 1, false, lightSpaceMatrix, 0)
        bindCommonLighting(terrainProg, atmo)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(terrainProg, "uHunterSense"), engine.hunterSenseFactor)

        bindTex(0, grassMat?.albedoTex ?: fallbackWhiteTex, terrainProg, "uGrassAlbedo")
        bindTex(1, grassMat?.normalTex ?: fallbackNormalTex, terrainProg, "uGrassNormal")
        bindTex(2, grassMat?.roughnessTex ?: fallbackWhiteTex, terrainProg, "uGrassRough")
        bindTex(3, rockMat?.albedoTex ?: fallbackWhiteTex, terrainProg, "uRockAlbedo")
        bindTex(4, rockMat?.normalTex ?: fallbackNormalTex, terrainProg, "uRockNormal")
        bindTex(5, rockMat?.roughnessTex ?: fallbackWhiteTex, terrainProg, "uRockRough")
        bindTex(6, shadowDepthTex, terrainProg, "uShadowMap")

        GLES30.glBindVertexArray(engine.terrain.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, engine.terrain.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun drawInstancedFoliage(atmo: AtmosphereProfile, grassDensity: Float) {
        GLES30.glUseProgram(instancedProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(instancedProg, "uViewProj"), 1, false, viewProjMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(instancedProg, "uLightSpaceMatrix"), 1, false, lightSpaceMatrix, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(instancedProg, "uTime"), engine.simTime)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(instancedProg, "uWindDir"), engine.windDirX, engine.windDirZ)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(instancedProg, "uWindStrength"), engine.windSpeedMps * 0.45f)
        bindCommonLighting(instancedProg, atmo)
        bindTex(2, shadowDepthTex, instancedProg, "uShadowMap")

        val baseColLoc = GLES30.glGetUniformLocation(instancedProg, "uBaseColor")
        val useTexLoc = GLES30.glGetUniformLocation(instancedProg, "uUseTex")

        fun renderBatch(batch: InstancedBatch, matId: PbrMaterialId?, r: Float, g: Float, b: Float, useTex: Float, density: Float = 1f) {
            val mat = matId?.let { materials[it] }
            bindTex(0, mat?.albedoTex ?: fallbackWhiteTex, instancedProg, "uAlbedoTex")
            bindTex(1, mat?.roughnessTex ?: fallbackWhiteTex, instancedProg, "uRoughTex")
            GLES30.glUniform3f(baseColLoc, r, g, b)
            GLES30.glUniform1f(useTexLoc, if (mat != null) useTex else 0f)
            batch.draw(density)
        }

        renderBatch(engine.foliage.pineTrunks, PbrMaterialId.PINE_BARK, 1.0f, 0.96f, 0.92f, 1.0f)
        renderBatch(engine.foliage.pineCanopies, PbrMaterialId.FOREST_FLOOR, 0.34f, 0.56f, 0.34f, 0.55f)
        renderBatch(engine.foliage.birchTrunks, PbrMaterialId.BIRCH_BARK, 1.0f, 1.0f, 0.98f, 1.0f)
        renderBatch(engine.foliage.birchCanopies, PbrMaterialId.FOREST_FLOOR, 0.72f, 0.84f, 0.42f, 0.45f)
        renderBatch(engine.foliage.boulders, PbrMaterialId.ALPINE_ROCK, 0.96f, 0.96f, 0.98f, 1.0f)
        renderBatch(engine.foliage.mossLogs, PbrMaterialId.PINE_BARK, 0.75f, 0.85f, 0.65f, 1.0f)

        GLES30.glDisable(GLES30.GL_CULL_FACE)
        renderBatch(engine.foliage.ferns, PbrMaterialId.FOREST_FLOOR, 0.38f, 0.72f, 0.32f, 0.45f, grassDensity)
        renderBatch(engine.foliage.grassTufts, null, 0.32f, 0.48f, 0.22f, 0.0f, grassDensity)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun drawWildlifeAndDynamicWorld(atmo: AtmosphereProfile) {
        GLES30.glUseProgram(worldPbrProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldPbrProg, "uViewProj"), 1, false, viewProjMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldPbrProg, "uLightSpaceMatrix"), 1, false, lightSpaceMatrix, 0)
        bindCommonLighting(worldPbrProg, atmo)
        bindTex(2, shadowDepthTex, worldPbrProg, "uShadowMap")
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldPbrProg, "uUseFog"), 1f)

        val modelLoc = GLES30.glGetUniformLocation(worldPbrProg, "uModel")
        val colLoc = GLES30.glGetUniformLocation(worldPbrProg, "uBaseColor")
        val useTexLoc = GLES30.glGetUniformLocation(worldPbrProg, "uUseTex")
        val metalLoc = GLES30.glGetUniformLocation(worldPbrProg, "uMetallic")
        val roughLoc = GLES30.glGetUniformLocation(worldPbrProg, "uRoughnessOverride")
        val emisLoc = GLES30.glGetUniformLocation(worldPbrProg, "uEmissive")
        val senseLoc = GLES30.glGetUniformLocation(worldPbrProg, "uHunterHighlight")

        val furMat = materials[PbrMaterialId.ANIMAL_FUR]
        val boneMat = materials[PbrMaterialId.ANTLER_BONE]
        val metalMat = materials[PbrMaterialId.GUNMETAL]

        val senseVal = engine.hunterSenseFactor

        // 1. Articulated Wildlife (Stag, Boar, Wolf)
        for (a in engine.wildlife.animals) {
            val s = a.scale
            val yawDeg = a.yawRad * Math3D.RAD_TO_DEG
            val legSwing = sin(a.animPhase) * (if (a.speed > 0.2f) 28f * (a.speed / 8f).coerceIn(0.35f, 1.3f) else 0f)
            val rollDown = a.fallProgress * 86f
            val bodyDrop = a.fallProgress * 0.55f * s

            val furR: Float; val furG: Float; val furB: Float
            when (a.species) {
                Species.ROYAL_STAG -> { furR = 0.54f; furG = 0.36f; furB = 0.22f }
                Species.WILD_BOAR -> { furR = 0.24f; furG = 0.19f; furB = 0.16f }
                Species.TIMBER_WOLF -> { furR = 0.42f; furG = 0.43f; furB = 0.46f }
            }

            GLES30.glUniform1f(senseLoc, if (a.state != AnimalState.DOWNED) senseVal else senseVal * 0.45f)
            GLES30.glUniform1f(metalLoc, 0.04f)
            GLES30.glUniform1f(roughLoc, -1f)
            GLES30.glUniform3f(emisLoc, 0f, 0f, 0f)
            bindTex(0, furMat?.albedoTex ?: fallbackWhiteTex, worldPbrProg, "uAlbedoTex")
            bindTex(1, furMat?.roughnessTex ?: fallbackWhiteTex, worldPbrProg, "uRoughTex")
            GLES30.glUniform1f(useTexLoc, if (furMat != null) 1f else 0f)

            fun drawPart(
                mesh: GlMesh,
                lx: Float, ly: Float, lz: Float,
                sx: Float, sy: Float, sz: Float,
                pitchDeg: Float = 0f,
                r: Float = furR, g: Float = furG, b: Float = furB
            ) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, a.pos.x, a.pos.y - bodyDrop, a.pos.z)
                Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
                if (rollDown > 0.1f) Matrix.rotateM(modelMatrix, 0, rollDown, 0f, 0f, 1f)
                Matrix.translateM(modelMatrix, 0, lx * s, ly * s, lz * s)
                if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
                Matrix.scaleM(modelMatrix, 0, sx * s, sy * s, sz * s)
                GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
                GLES30.glUniform3f(colLoc, r, g, b)
                mesh.draw()
            }

            when (a.species) {
                Species.ROYAL_STAG -> {
                    // Torso & Chest
                    drawPart(sphereMesh, 0f, 1.28f, 0f, 0.42f, 0.42f, 0.92f)
                    // 4 Articulated Legs
                    drawPart(cylinderMesh, -0.22f, 0.0f, 0.54f, 0.085f, 1.12f, 0.085f, legSwing, furR * 0.78f, furG * 0.78f, furB * 0.78f)
                    drawPart(cylinderMesh, 0.22f, 0.0f, 0.54f, 0.085f, 1.12f, 0.085f, -legSwing, furR * 0.78f, furG * 0.78f, furB * 0.78f)
                    drawPart(cylinderMesh, -0.22f, 0.0f, -0.54f, 0.09f, 1.14f, 0.09f, -legSwing, furR * 0.76f, furG * 0.76f, furB * 0.76f)
                    drawPart(cylinderMesh, 0.22f, 0.0f, -0.54f, 0.09f, 1.14f, 0.09f, legSwing, furR * 0.76f, furG * 0.76f, furB * 0.76f)
                    // Neck & Head (dips when grazing)
                    val headDip = a.headPitch * 38f
                    val neckY = 1.44f + a.headPitch * 0.35f
                    val neckZ = 0.76f - a.headPitch * 0.18f
                    drawPart(cylinderMesh, 0f, 1.32f, 0.62f, 0.18f, 0.64f, 0.20f, 24f - headDip)
                    drawPart(sphereMesh, 0f, neckY + 0.44f, neckZ + 0.24f, 0.17f, 0.16f, 0.34f, -headDip * 0.4f, furR * 0.92f, furG * 0.90f, furB * 0.86f)
                    // White rump tail
                    drawPart(sphereMesh, 0f, 1.34f, -0.92f, 0.12f, 0.14f, 0.16f, 0f, 0.88f, 0.84f, 0.76f)
                    // Branching Royal Antler Rack
                    bindTex(0, boneMat?.albedoTex ?: fallbackWhiteTex, worldPbrProg, "uAlbedoTex")
                    drawPart(antlerMesh, 0f, neckY + 0.54f, neckZ + 0.14f, 1.0f, 1.0f, 1.0f, -headDip * 0.35f, 0.92f, 0.86f, 0.74f)
                }

                Species.WILD_BOAR -> {
                    // Barrel torso
                    drawPart(sphereMesh, 0f, 0.68f, 0f, 0.44f, 0.40f, 0.74f)
                    // 4 stout legs
                    drawPart(cylinderMesh, -0.22f, 0f, 0.42f, 0.08f, 0.52f, 0.08f, legSwing)
                    drawPart(cylinderMesh, 0.22f, 0f, 0.42f, 0.08f, 0.52f, 0.08f, -legSwing)
                    drawPart(cylinderMesh, -0.22f, 0f, -0.42f, 0.08f, 0.52f, 0.08f, -legSwing)
                    drawPart(cylinderMesh, 0.22f, 0f, -0.42f, 0.08f, 0.52f, 0.08f, legSwing)
                    // Heavy head & snout
                    drawPart(sphereMesh, 0f, 0.74f + a.headPitch * 0.2f, 0.68f, 0.24f, 0.24f, 0.34f)
                    drawPart(cylinderMesh, 0f, 0.66f + a.headPitch * 0.2f, 0.86f, 0.11f, 0.16f, 0.11f, 88f, 0.35f, 0.25f, 0.22f)
                    // Curved ivory tusks
                    bindTex(0, boneMat?.albedoTex ?: fallbackWhiteTex, worldPbrProg, "uAlbedoTex")
                    drawPart(tuskMesh, 0f, 0.66f + a.headPitch * 0.2f, 0.82f, 1.0f, 1.0f, 1.0f, 0f, 0.94f, 0.90f, 0.78f)
                }

                Species.TIMBER_WOLF -> {
                    // Lithe wolf torso
                    drawPart(sphereMesh, 0f, 0.76f, 0f, 0.28f, 0.28f, 0.72f)
                    // 4 legs
                    drawPart(cylinderMesh, -0.16f, 0f, 0.44f, 0.065f, 0.68f, 0.065f, legSwing)
                    drawPart(cylinderMesh, 0.16f, 0f, 0.44f, 0.065f, 0.68f, 0.065f, -legSwing)
                    drawPart(cylinderMesh, -0.16f, 0f, -0.44f, 0.065f, 0.68f, 0.065f, -legSwing)
                    drawPart(cylinderMesh, 0.16f, 0f, -0.44f, 0.065f, 0.68f, 0.065f, legSwing)
                    // Head, snout, bushy tail
                    drawPart(sphereMesh, 0f, 0.94f + a.headPitch * 0.18f, 0.74f, 0.18f, 0.17f, 0.28f)
                    drawPart(cylinderMesh, 0f, 0.76f, -0.68f, 0.08f, 0.56f, 0.08f, -125f, 0.32f, 0.34f, 0.38f)
                    // Reflective / glowing eyes in Moonlit Night!
                    if (atmo.nightFactor > 0.35f && a.state != AnimalState.DOWNED) {
                        GLES30.glUniform3f(emisLoc, 1.6f, 1.3f, 0.25f)
                        drawPart(sphereMesh, -0.07f, 0.98f, 0.92f, 0.028f, 0.028f, 0.028f, 0f, 1f, 0.9f, 0.3f)
                        drawPart(sphereMesh, 0.07f, 0.98f, 0.92f, 0.028f, 0.028f, 0.028f, 0f, 1f, 0.9f, 0.3f)
                        GLES30.glUniform3f(emisLoc, 0f, 0f, 0f)
                    }
                }
            }
        }

        // 2. Glowing Footprints & Blood Trails (brightly illuminated during Hunter Sense)
        GLES30.glUniform1f(senseLoc, 0f)
        GLES30.glUniform1f(useTexLoc, 0f)
        GLES30.glUniform1f(metalLoc, 0f)
        GLES30.glUniform1f(roughLoc, 0.45f)
        for (tr in engine.wildlife.trackDecals) {
            if (!tr.isBlood && senseVal < 0.05f) continue
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, tr.x, tr.y, tr.z)
            Matrix.scaleM(modelMatrix, 0, if (tr.isBlood) 1.15f else 0.85f, 1f, if (tr.isBlood) 1.15f else 0.85f)
            GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
            if (tr.isBlood) {
                GLES30.glUniform3f(colLoc, 0.55f, 0.04f, 0.03f)
                GLES30.glUniform3f(emisLoc, 0.35f + senseVal * 1.4f, 0.03f, 0.03f)
            } else {
                GLES30.glUniform3f(colLoc, 0.12f, 0.88f, 0.85f)
                GLES30.glUniform3f(emisLoc, 0.15f * senseVal, 1.35f * senseVal, 1.25f * senseVal)
            }
            decalMesh.draw()
        }

        // 3. Embedded Arrows, Active Arrows & Ejected Brass Shells
        GLES30.glUniform3f(emisLoc, 0f, 0f, 0f)
        GLES30.glUniform1f(metalLoc, 0.85f)
        GLES30.glUniform1f(roughLoc, 0.25f)
        bindTex(0, metalMat?.albedoTex ?: fallbackWhiteTex, worldPbrProg, "uAlbedoTex")

        for (arr in engine.weapons.embeddedArrows) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, arr.pos.x, arr.pos.y, arr.pos.z)
            val aYaw = kotlin.math.atan2(-arr.dir.x, -arr.dir.z) * Math3D.RAD_TO_DEG
            val aPitch = kotlin.math.asin(Math3D.clamp(arr.dir.y, -1f, 1f)) * Math3D.RAD_TO_DEG
            Matrix.rotateM(modelMatrix, 0, aYaw, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, aPitch, 1f, 0f, 0f)
            GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
            GLES30.glUniform3f(colLoc, 0.18f, 0.18f, 0.20f)
            arrowMesh.draw()
        }
        for (p in engine.weapons.projectiles) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.pos.x, p.pos.y, p.pos.z)
            if (p.weapon == WeaponType.RECURVE_BOW) {
                val d = Vec3(p.vel.x, p.vel.y, p.vel.z).normalize()
                val aYaw = kotlin.math.atan2(-d.x, -d.z) * Math3D.RAD_TO_DEG
                val aPitch = kotlin.math.asin(Math3D.clamp(d.y, -1f, 1f)) * Math3D.RAD_TO_DEG
                Matrix.rotateM(modelMatrix, 0, aYaw, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, aPitch, 1f, 0f, 0f)
                GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
                GLES30.glUniform3f(colLoc, 0.90f, 0.72f, 0.25f)
                GLES30.glUniform3f(emisLoc, 0.5f, 0.35f, 0.08f)
                arrowMesh.draw()
            } else {
                Matrix.scaleM(modelMatrix, 0, 0.04f, 0.04f, 0.25f)
                GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
                GLES30.glUniform3f(colLoc, 1.0f, 0.85f, 0.45f)
                GLES30.glUniform3f(emisLoc, 2.2f, 1.6f, 0.6f)
                sphereMesh.draw()
            }
        }
        GLES30.glUniform3f(emisLoc, 0f, 0f, 0f)
        for (sh in engine.weapons.ejectedShells) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, sh.pos.x, sh.pos.y, sh.pos.z)
            Matrix.rotateM(modelMatrix, 0, sh.spin * 57f, 1f, 0.7f, 0.2f)
            Matrix.scaleM(modelMatrix, 0, 0.012f, 0.042f, 0.012f)
            GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
            GLES30.glUniform3f(colLoc, 0.92f, 0.74f, 0.28f)
            cylinderMesh.draw()
        }
    }

    private fun drawWater(atmo: AtmosphereProfile) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glUseProgram(waterProg)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(waterProg, "uViewProj"), 1, false, viewProjMatrix, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(waterProg, "uTime"), engine.simTime)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(waterProg, "uWindDir"), engine.windDirX, engine.windDirZ)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProg, "uCameraPos"), engine.camPos.x, engine.camPos.y, engine.camPos.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProg, "uSunDir"), atmo.sunDir.x, atmo.sunDir.y, atmo.sunDir.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProg, "uSunColor"), atmo.sunColor.x, atmo.sunColor.y, atmo.sunColor.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProg, "uSkyZenith"), atmo.skyZenith.x, atmo.skyZenith.y, atmo.skyZenith.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProg, "uSkyHorizon"), atmo.skyHorizon.x, atmo.skyHorizon.y, atmo.skyHorizon.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProg, "uFogColor"), atmo.fogColor.x, atmo.fogColor.y, atmo.fogColor.z)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(waterProg, "uFogDensity"), atmo.fogDensity)

        val waterNormal = materials[PbrMaterialId.ALPINE_ROCK]?.normalTex ?: fallbackNormalTex
        bindTex(0, waterNormal, waterProg, "uNormalMap")

        GLES30.glBindVertexArray(engine.terrain.waterVao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, engine.terrain.waterIndexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawFirstPersonWeapon(atmo: AtmosphereProfile, aspect: Float) {
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        Matrix.perspectiveM(fpProjMatrix, 0, 52f, aspect, 0.05f, 12f)

        GLES30.glUseProgram(worldPbrProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldPbrProg, "uViewProj"), 1, false, fpProjMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldPbrProg, "uLightSpaceMatrix"), 1, false, lightSpaceMatrix, 0)
        bindCommonLighting(worldPbrProg, atmo)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(worldPbrProg, "uCameraPos"), 0f, 0f, 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldPbrProg, "uUseFog"), 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldPbrProg, "uHunterHighlight"), 0f)

        val modelLoc = GLES30.glGetUniformLocation(worldPbrProg, "uModel")
        val colLoc = GLES30.glGetUniformLocation(worldPbrProg, "uBaseColor")
        val useTexLoc = GLES30.glGetUniformLocation(worldPbrProg, "uUseTex")
        val metalLoc = GLES30.glGetUniformLocation(worldPbrProg, "uMetallic")
        val roughLoc = GLES30.glGetUniformLocation(worldPbrProg, "uRoughnessOverride")
        val emisLoc = GLES30.glGetUniformLocation(worldPbrProg, "uEmissive")

        val w = engine.weapons
        val bobX = cos(w.walkBobPhase) * 0.012f * w.walkBobAmount
        val bobY = sin(w.walkBobPhase * 2f) * 0.010f * w.walkBobAmount
        val breathY = sin(engine.simTime * 1.6f) * 0.004f
        val reloadDip = if (w.reloading) sin((w.reloadTimer / w.reloadDuration) * Math.PI.toFloat()) * 0.22f else 0f

        val walnutMat = materials[PbrMaterialId.WALNUT_WOOD]
        val gunmetalMat = materials[PbrMaterialId.GUNMETAL]

        if (w.activeWeapon == WeaponType.RIFLE_308) {
            Matrix.setIdentityM(modelMatrix, 0)
            val hipX = Math3D.lerp(0.24f, 0.0f, w.adsBlend)
            val hipY = Math3D.lerp(-0.22f, -0.10f, w.adsBlend) - reloadDip
            val hipZ = Math3D.lerp(-0.46f, -0.34f, w.adsBlend) + w.recoilKick * 0.09f
            Matrix.translateM(modelMatrix, 0, hipX + bobX, hipY + bobY + breathY, hipZ)
            Matrix.rotateM(modelMatrix, 0, w.recoilKick * 9.5f - reloadDip * 55f, 1f, 0f, 0f)
            if (w.boltTimer > 0f) {
                Matrix.rotateM(modelMatrix, 0, sin((w.boltTimer / w.boltDuration) * Math.PI.toFloat()) * -14f, 0f, 0f, 1f)
            }
            GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)

            // Walnut wood stock
            bindTex(0, walnutMat?.albedoTex ?: fallbackWhiteTex, worldPbrProg, "uAlbedoTex")
            bindTex(1, walnutMat?.roughnessTex ?: fallbackWhiteTex, worldPbrProg, "uRoughTex")
            GLES30.glUniform1f(useTexLoc, if (walnutMat != null) 1f else 0f)
            GLES30.glUniform1f(metalLoc, 0.05f)
            GLES30.glUniform1f(roughLoc, -1f)
            GLES30.glUniform3f(colLoc, 0.96f, 0.90f, 0.84f)
            GLES30.glUniform3f(emisLoc, 0f, 0f, 0f)
            rifleStockMesh.draw()

            // Brushed blued gunmetal barrel & optic scope
            bindTex(0, gunmetalMat?.albedoTex ?: fallbackWhiteTex, worldPbrProg, "uAlbedoTex")
            bindTex(1, gunmetalMat?.roughnessTex ?: fallbackWhiteTex, worldPbrProg, "uRoughTex")
            GLES30.glUniform1f(useTexLoc, if (gunmetalMat != null) 1f else 0f)
            GLES30.glUniform1f(metalLoc, 0.88f)
            GLES30.glUniform1f(roughLoc, -1f)
            GLES30.glUniform3f(colLoc, 0.88f, 0.92f, 0.98f)
            rifleMetalMesh.draw()

            // Bright volumetric muzzle flash burst
            if (w.muzzleFlashTimer > 0f) {
                Matrix.translateM(modelMatrix, 0, 0f, 0.025f, -0.88f)
                val fScale = 0.09f + w.muzzleFlashTimer * 1.4f
                Matrix.scaleM(modelMatrix, 0, fScale, fScale, fScale * 2.2f)
                GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
                GLES30.glUniform1f(useTexLoc, 0f)
                GLES30.glUniform3f(colLoc, 1.0f, 0.88f, 0.55f)
                GLES30.glUniform3f(emisLoc, 4.5f, 2.8f, 0.8f)
                sphereMesh.draw()
            }
        } else {
            // Silent Carbon Recurve Bow + Drawn Arrow
            Matrix.setIdentityM(modelMatrix, 0)
            val bowX = Math3D.lerp(-0.22f, -0.09f, w.bowDrawTension * 0.8f + w.adsBlend * 0.2f)
            val bowY = -0.10f + bobY + breathY
            val bowZ = -0.52f + w.recoilKick * 0.06f
            Matrix.translateM(modelMatrix, 0, bowX + bobX, bowY, bowZ)
            Matrix.rotateM(modelMatrix, 0, Math3D.lerp(-14f, -4f, w.bowDrawTension), 0f, 0f, 1f)
            GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)

            bindTex(0, gunmetalMat?.albedoTex ?: fallbackWhiteTex, worldPbrProg, "uAlbedoTex")
            bindTex(1, gunmetalMat?.roughnessTex ?: fallbackWhiteTex, worldPbrProg, "uRoughTex")
            GLES30.glUniform1f(useTexLoc, if (gunmetalMat != null) 1f else 0f)
            GLES30.glUniform1f(metalLoc, 0.65f)
            GLES30.glUniform1f(roughLoc, 0.34f)
            GLES30.glUniform3f(colLoc, 0.24f, 0.26f, 0.28f)
            GLES30.glUniform3f(emisLoc, 0f, 0f, 0f)
            bowFrameMesh.draw()

            // Nocked Carbon Arrow sliding smoothly back with draw tension
            Matrix.translateM(modelMatrix, 0, 0.03f, 0.03f, -0.04f + w.bowDrawTension * 0.34f)
            GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
            GLES30.glUniform3f(colLoc, 0.82f, 0.65f, 0.28f)
            arrowMesh.draw()
        }
    }

    private fun bindCommonLighting(prog: Int, atmo: AtmosphereProfile) {
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uCameraPos"), engine.camPos.x, engine.camPos.y, engine.camPos.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uSunDir"), atmo.sunDir.x, atmo.sunDir.y, atmo.sunDir.z)
        val flashBoost = 1f + engine.lightningFlash * 2.2f
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uSunColor"), atmo.sunColor.x * flashBoost, atmo.sunColor.y * flashBoost, atmo.sunColor.z * flashBoost)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uSkyZenith"), atmo.skyZenith.x, atmo.skyZenith.y, atmo.skyZenith.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uSkyHorizon"), atmo.skyHorizon.x, atmo.skyHorizon.y, atmo.skyHorizon.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uFogColor"), atmo.fogColor.x, atmo.fogColor.y, atmo.fogColor.z)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uFogDensity"), atmo.fogDensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uWetness"), atmo.wetness)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uFlashlightPos"), engine.camPos.x, engine.camPos.y, engine.camPos.z)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uFlashlightDir"), engine.camForward.x, engine.camForward.y, engine.camForward.z)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uFlashlightOn"), if (engine.flashlightOn && engine.gameMode != GameMode.TITLE_ORBIT) 1f else 0f)
    }

    private fun bindTex(unit: Int, texId: Int, prog: Int, uniformName: String) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, uniformName), unit)
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(quadVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glBindVertexArray(0)
    }

    private fun createShadowFbo(size: Int) {
        if (shadowFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(shadowFbo), 0)
        if (shadowDepthTex != 0) GLES30.glDeleteTextures(1, intArrayOf(shadowDepthTex), 0)

        val fboArr = IntArray(1); val texArr = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArr, 0)
        GLES30.glGenTextures(1, texArr, 0)
        shadowFbo = fboArr[0]; shadowDepthTex = texArr[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowDepthTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, size, size, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowDepthTex, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun recreateSceneFbos() {
        val nw = (viewW * currentRenderScale).toInt().coerceIn(320, 2560)
        val nh = (viewH * currentRenderScale).toInt().coerceIn(240, 2560)
        if (nw == fboW && nh == fboH && sceneFbo != 0) return
        fboW = nw; fboH = nh

        if (sceneFbo != 0) GLES30.glDeleteFramebuffers(3, intArrayOf(sceneFbo, bloomFboA, bloomFboB), 0)
        if (sceneColorTex != 0) GLES30.glDeleteTextures(3, intArrayOf(sceneColorTex, bloomTexA, bloomTexB), 0)
        if (sceneDepthRb != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(sceneDepthRb), 0)

        val fbos = IntArray(3); val texs = IntArray(3); val rbs = IntArray(1)
        GLES30.glGenFramebuffers(3, fbos, 0)
        GLES30.glGenTextures(3, texs, 0)
        GLES30.glGenRenderbuffers(1, rbs, 0)
        sceneFbo = fbos[0]; bloomFboA = fbos[1]; bloomFboB = fbos[2]
        sceneColorTex = texs[0]; bloomTexA = texs[1]; bloomTexB = texs[2]
        sceneDepthRb = rbs[0]

        // Main Scene FBO (RGB16F or RGBA8 fallback)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, fboW, fboH, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, sceneDepthRb)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, fboW, fboH)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, sceneColorTex, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, sceneDepthRb)

        // If GPU doesn't support RGBA16F or DEPTH_COMPONENT24 render target, fall back cleanly
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, fboW, fboH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, sceneDepthRb)
                GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, fboW, fboH)
            }
        }

        val bw = (fboW / 2).coerceAtLeast(1)
        val bh = (fboH / 2).coerceAtLeast(1)
        for (i in 0..1) {
            val fbo = if (i == 0) bloomFboA else bloomFboB
            val tex = if (i == 0) bloomTexA else bloomTexB
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, bw, bh, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun createSolidTex(r: Int, g: Int, b: Int, a: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        val buf = ByteBuffer.allocateDirect(4).apply {
            put(r.toByte()); put(g.toByte()); put(b.toByte()); put(a.toByte()); position(0)
        }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        return ids[0]
    }
}
