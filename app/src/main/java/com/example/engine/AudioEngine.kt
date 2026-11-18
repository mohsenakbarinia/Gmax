package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.Species
import com.example.data.SurfaceType
import com.example.data.WeatherPreset
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Native Spatial Procedural Audio Engine powered by Android AudioTrack (stereo 44.1kHz PCM).
 * Synthesizes in real time:
 * - Layered stereo pine wind, shore-proximity lake water, rain patter & rolling thunder
 * - Daytime forest bird chirps & nighttime owl hoots / crickets
 * - Surface-aware footsteps (grass, dirt, rock, shallow water)
 * - Punchy .308 rifle gunshot + multi-tap alpine valley echo, metallic bolt cycle
 * - Bowstring draw creak & arrow release whoosh, wildlife bugles/barks
 * - Breath-hold ambient muffling + steady heartbeat in optical scope
 */
class AudioEngine {
    companion object {
        private const val SAMPLE_RATE = 24000
        private const val FRAMES_PER_CHUNK = 512
    }

    private sealed class SoundEvent {
        data object RifleShot : SoundEvent()
        data object BoltCycle : SoundEvent()
        data object BowCreak : SoundEvent()
        data class BowRelease(val power: Float) : SoundEvent()
        data class Footstep(val surface: SurfaceType, val intensity: Float) : SoundEvent()
        data object VitalHitConfirm : SoundEvent()
        data object Thunder : SoundEvent()
        data class WildlifeCall(val species: Species, val distance: Float) : SoundEvent()
    }

    private class Voice(
        val type: Int, // 0=rifle, 1=bolt, 2=bowRelease, 3=footstep, 4=vitalHit, 5=thunder, 6=call, 7=bowCreak
        var time: Float = 0f,
        val duration: Float,
        val param1: Float = 1f,
        val param2: Float = 0f
    )

    @Volatile private var running = false
    @Volatile private var masterVolume = 0.85f
    @Volatile private var weather = WeatherPreset.GOLDEN_HOUR
    @Volatile private var windStrength = 0.45f
    @Volatile private var lakeProximity = 0f // 0 far .. 1 right at shoreline
    @Volatile private var holdingBreath = false

    private val eventQueue = ConcurrentLinkedQueue<SoundEvent>()
    private var audioThread: Thread? = null

    fun setMasterVolume(v: Float) { masterVolume = v.coerceIn(0f, 1f) }
    fun setEnvironment(w: WeatherPreset, wind: Float, lakeProx: Float, breath: Boolean) {
        weather = w
        windStrength = wind.coerceIn(0.1f, 1.5f)
        lakeProximity = lakeProx.coerceIn(0f, 1f)
        holdingBreath = breath
    }

    fun playRifleShot() { eventQueue.offer(SoundEvent.RifleShot) }
    fun playBoltCycle() { eventQueue.offer(SoundEvent.BoltCycle) }
    fun playBowCreak() { eventQueue.offer(SoundEvent.BowCreak) }
    fun playBowRelease(power: Float) { eventQueue.offer(SoundEvent.BowRelease(power)) }
    fun playFootstep(surface: SurfaceType, intensity: Float) { eventQueue.offer(SoundEvent.Footstep(surface, intensity)) }
    fun playVitalConfirm() { eventQueue.offer(SoundEvent.VitalHitConfirm) }
    fun playThunder() { eventQueue.offer(SoundEvent.Thunder) }
    fun playWildlifeCall(species: Species, dist: Float) { eventQueue.offer(SoundEvent.WildlifeCall(species, dist)) }

    fun start() {
        if (running) return
        running = true
        audioThread = Thread({ runSynthLoop() }, "WildwoodAudioSynth").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        try {
            audioThread?.join(300)
        } catch (_: InterruptedException) {}
        audioThread = null
    }

    private fun runSynthLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAMES_PER_CHUNK * 4)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            running = false
            return
        }

        val pcm = ShortArray(FRAMES_PER_CHUNK * 2)
        val voices = ArrayList<Voice>(16)
        val rng = Random(9911)
        val dt = 1f / SAMPLE_RATE.toFloat()

        // Filter state for stereo wind, water, rain, and crickets
        var windLpL = 0f; var windLpR = 0f
        var waterLp = 0f
        var rainLp = 0f
        var masterLpL = 0f; var masterLpR = 0f
        var globalTime = 0f
        var ambientBirdTimer = 4.5f
        var birdPhase = 0f
        var birdActive = 0f
        var birdBaseFreq = 2200f
        var heartbeatTimer = 0f

        try {
            track.play()
            while (running) {
                while (true) {
                    val ev = eventQueue.poll() ?: break
                    when (ev) {
                        is SoundEvent.RifleShot -> voices.add(Voice(0, 0f, 1.9f, 1f))
                        is SoundEvent.BoltCycle -> voices.add(Voice(1, 0f, 0.45f, 1f))
                        is SoundEvent.BowRelease -> voices.add(Voice(2, 0f, 0.42f, ev.power))
                        is SoundEvent.Footstep -> voices.add(Voice(3, 0f, 0.16f, ev.surface.ordinal.toFloat(), ev.intensity))
                        is SoundEvent.VitalHitConfirm -> voices.add(Voice(4, 0f, 0.35f, 1f))
                        is SoundEvent.Thunder -> voices.add(Voice(5, 0f, 3.2f, 1f))
                        is SoundEvent.WildlifeCall -> voices.add(Voice(6, 0f, 1.15f, ev.species.ordinal.toFloat(), ev.distance))
                        is SoundEvent.BowCreak -> voices.add(Voice(7, 0f, 0.25f, 1f))
                    }
                }

                val vol = masterVolume
                val curWeather = weather
                val wind = windStrength
                val lake = lakeProximity
                val breath = holdingBreath

                for (f in 0 until FRAMES_PER_CHUNK) {
                    globalTime += dt
                    val noiseL = rng.nextFloat() * 2f - 1f
                    val noiseR = rng.nextFloat() * 2f - 1f

                    // 1. Stereo sweeping alpine pine wind
                    val gustMod = 0.65f + 0.35f * sin(globalTime * 0.42f) * sin(globalTime * 0.19f + 1.2f)
                    val windCut = 0.032f + wind * 0.035f
                    windLpL += (noiseL - windLpL) * windCut
                    windLpR += (noiseR - windLpR) * windCut
                    var outL = windLpL * 0.24f * wind * gustMod
                    var outR = windLpR * 0.24f * wind * gustMod

                    // 2. Proximity-based rushing mountain lake shore water
                    if (lake > 0.02f) {
                        waterLp += (noiseL - waterLp) * 0.14f
                        val lap = 0.72f + 0.28f * sin(globalTime * 1.15f)
                        val wSig = waterLp * lake * 0.22f * lap
                        outL += wSig
                        outR += wSig * 0.92f
                    }

                    // 3. Weather layer: rain patter OR night crickets OR day birds
                    if (curWeather == WeatherPreset.RAINY_STORM) {
                        rainLp += (noiseR - rainLp) * 0.42f
                        val rSig = (noiseL * 0.22f + rainLp * 0.78f) * 0.16f
                        outL += rSig; outR += rSig
                    } else if (curWeather == WeatherPreset.MOONLIT_NIGHT) {
                        val chirpEnv = (0.5f + 0.5f * sin(globalTime * 14f)) * (0.5f + 0.5f * sin(globalTime * 2.1f))
                        val cricket = sin(globalTime * 2f * PI.toFloat() * 4150f) * chirpEnv * 0.018f
                        outL += cricket * 0.7f; outR += cricket
                    } else {
                        ambientBirdTimer -= dt
                        if (ambientBirdTimer <= 0f) {
                            ambientBirdTimer = 5f + rng.nextFloat() * 8f
                            birdActive = 0.38f
                            birdPhase = 0f
                            birdBaseFreq = 2100f + rng.nextFloat() * 1400f
                        }
                        if (birdActive > 0f) {
                            birdActive -= dt
                            birdPhase += dt
                            val env = sin((birdPhase / 0.38f).coerceIn(0f, 1f) * PI.toFloat())
                            val freq = birdBaseFreq + sin(birdPhase * 34f) * 420f
                            val chirp = sin(birdPhase * 2f * PI.toFloat() * freq) * env * 0.032f
                            outL += chirp * 0.6f; outR += chirp
                        }
                    }

                    // 4. Hold Breath rhythmic heartbeat & ambient muffling
                    if (breath) {
                        outL *= 0.38f; outR *= 0.38f
                        heartbeatTimer += dt
                        val beatCycle = heartbeatTimer % 0.95f
                        val lub = exp(-((beatCycle - 0.08f) * 28f) * ((beatCycle - 0.08f) * 28f))
                        val dub = exp(-((beatCycle - 0.24f) * 32f) * ((beatCycle - 0.24f) * 32f)) * 0.65f
                        val hb = sin(globalTime * 2f * PI.toFloat() * 54f) * (lub + dub) * 0.26f
                        outL += hb; outR += hb
                    } else {
                        heartbeatTimer = 0f
                    }

                    // 5. Active one-shot procedural voices
                    var vi = voices.size - 1
                    while (vi >= 0) {
                        val v = voices[vi]
                        val t = v.time
                        v.time += dt
                        if (v.time >= v.duration) {
                            voices.removeAt(vi)
                            vi--
                            continue
                        }
                        val sig = when (v.type) {
                            0 -> { // .308 Rifle Crack + Multi-Tap Alpine Valley Echo
                                val punch = sin(t * 2f * PI.toFloat() * (145f * exp(-t * 14f))) * exp(-t * 22f) * 0.95f
                                val crack = noiseL * exp(-t * 28f) * 0.85f
                                val echo1 = if (t > 0.26f) noiseR * exp(-(t - 0.26f) * 5.2f) * 0.28f else 0f
                                val echo2 = if (t > 0.62f) noiseL * exp(-(t - 0.62f) * 3.1f) * 0.16f else 0f
                                punch + crack + echo1 + echo2
                            }
                            1 -> { // Metallic Bolt-Action Click-Clack
                                val c1 = exp(-((t - 0.07f) * 55f) * ((t - 0.07f) * 55f)) * sin(t * 2400f) * 0.25f
                                val c2 = exp(-((t - 0.24f) * 50f) * ((t - 0.24f) * 50f)) * (sin(t * 3100f) * 0.22f + noiseL * 0.12f)
                                c1 + c2
                            }
                            2 -> { // Bowstring Twang + Arrow Whoosh
                                val twang = sin(t * 2f * PI.toFloat() * (135f + v.param1 * 45f)) * exp(-t * 18f) * 0.30f
                                val whoosh = noiseL * exp(-t * 12f) * 0.26f * v.param1
                                twang + whoosh
                            }
                            3 -> { // Surface-Aware Footstep
                                val env = exp(-t * 28f) * v.param2 * 0.34f
                                when (v.param1.toInt()) {
                                    SurfaceType.SHALLOW_WATER.ordinal -> (noiseL * 0.7f + sin(t * 950f) * 0.3f) * env * 1.15f
                                    SurfaceType.ROCK.ordinal -> (sin(t * 680f) * 0.55f + noiseR * 0.45f) * env * 1.05f
                                    SurfaceType.DIRT.ordinal -> (noiseL * 0.85f + sin(t * 220f) * 0.15f) * env * 0.9f
                                    else -> (noiseL * 0.55f + sin(t * 130f) * 0.45f) * env * 0.7f
                                }
                            }
                            4 -> { // Vital Organ Hit Confirmation Chime + Thump
                                val thump = sin(t * 2f * PI.toFloat() * 92f) * exp(-t * 16f) * 0.42f
                                val chime = sin(t * 2f * PI.toFloat() * 880f) * exp(-t * 9f) * 0.16f
                                thump + chime
                            }
                            5 -> { // Rolling Storm Thunder
                                val env = exp(-t * 1.15f) * (0.65f + 0.35f * sin(t * 7.5f))
                                val rumble = sin(t * 2f * PI.toFloat() * (44f + 16f * sin(t * 3f))) * 0.45f + noiseL * 0.35f
                                rumble * env * 0.55f
                            }
                            6 -> { // Distant Wildlife Bugle / Grunt / Wolf Howl
                                val atten = (1f - (v.param2 / 150f)).coerceIn(0.08f, 0.7f)
                                val env = sin((t / v.duration).coerceIn(0f, 1f) * PI.toFloat())
                                val freq = when (v.param1.toInt()) {
                                    Species.ROYAL_STAG.ordinal -> 520f + sin(t * 4.5f) * 190f
                                    Species.WILD_BOAR.ordinal -> 145f + sin(t * 22f) * 35f
                                    else -> 360f + sin(t * 2.2f) * 110f
                                }
                                sin(t * 2f * PI.toFloat() * freq) * env * atten * 0.22f
                            }
                            else -> { // Bow draw creak
                                sin(t * 2f * PI.toFloat() * (280f + t * 240f)) * exp(-t * 10f) * 0.12f
                            }
                        }
                        outL += sig
                        outR += sig
                        vi--
                    }

                    // Gentle master smoothing when holding breath
                    val filt = if (breath) 0.18f else 0.85f
                    masterLpL += (outL - masterLpL) * filt
                    masterLpR += (outR - masterLpR) * filt

                    val sL = (masterLpL * vol * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                    val sR = (masterLpR * vol * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                    pcm[f * 2] = sL
                    pcm[f * 2 + 1] = sR
                }
                track.write(pcm, 0, pcm.size)
            }
        } catch (_: Exception) {
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }
}
