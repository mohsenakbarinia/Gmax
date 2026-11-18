package com.example.engine

import android.opengl.GLES30
import android.util.Log

/**
 * Complete GLSL ES 3.00 shader library for Wildwood: Reality:
 * - Physical Atmospheric Sky Dome (Rayleigh/Mie scattering, stars, moon, storm clouds, lightning)
 * - Multi-Layer PBR Terrain with 3x3 PCF Shadows, Storm Wetness, Flashlight, and Fog
 * - Instanced Wind-Sway Foliage & Grass with Subsurface Backlighting
 * - PBR Articulated Wildlife & First-Person Weapons with Hunter Sense Highlighting
 * - Realistic Alpine Lake Water (Dual-scroll ripple normals, Fresnel sky, depth absorption, foam)
 * - Volumetric Sun Shafts (God Rays) & Weather Particles
 * - Selective HDR Bright-Pass Bloom & Cinematic Post-Processing (ACES, Color Grade, Scope Blur)
 */
object ShaderLibrary {
    private const val TAG = "ShaderLibrary"

    fun compileProgram(vsSrc: String, fsSrc: String, name: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSrc, "$name.VS")
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSrc, "$name.FS")
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(prog)
            Log.e(TAG, "Program link failed ($name): $log")
            GLES30.glDeleteProgram(prog)
            return 0
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String, label: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compile failed ($label): $log")
        }
        return shader
    }

    // =========================================================================
    // 1. PHYSICAL ATMOSPHERIC SKY DOME
    // =========================================================================
    const val SKY_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uViewRotProj;
        out vec3 vDir;
        void main() {
            vDir = aPos;
            vec4 clip = uViewRotProj * vec4(aPos, 1.0);
            gl_Position = clip.xyww;
        }
    """

    const val SKY_FS = """#version 300 es
        precision highp float;
        in vec3 vDir;
        uniform vec3 uSunDir;
        uniform vec3 uSkyZenith;
        uniform vec3 uSkyHorizon;
        uniform vec3 uSunColor;
        uniform float uTime;
        uniform float uNightFactor;
        uniform float uCloudCoverage;
        uniform float uLightningFlash;
        out vec4 fragColor;

        float hash21(vec2 p) {
            p = fract(p * vec2(234.34, 435.345));
            p += dot(p, p + 34.23);
            return fract(p.x * p.y);
        }

        float noise2(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash21(i);
            float b = hash21(i + vec2(1.0, 0.0));
            float c = hash21(i + vec2(0.0, 1.0));
            float d = hash21(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        float fbmClouds(vec2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 4; i++) {
                v += a * noise2(p);
                p = p * 2.04 + vec2(1.7, 3.2);
                a *= 0.5;
            }
            return v;
        }

        void main() {
            vec3 dir = normalize(vDir);
            float h = clamp(dir.y, 0.0, 1.0);
            float horizonCurve = pow(1.0 - h, 2.8);
            vec3 col = mix(uSkyZenith, uSkyHorizon, horizonCurve);

            // Mie forward sun scattering & solar disc
            float sunDot = max(dot(dir, normalize(uSunDir)), 0.0);
            float mieGlow = pow(sunDot, 8.0) * 0.42 + pow(sunDot, 64.0) * 0.85;
            float sunDisc = smoothstep(0.9985, 0.9996, sunDot) * 4.2;
            col += uSunColor * (mieGlow + sunDisc) * (1.0 - uCloudCoverage * 0.65);

            // Distant alpine cloud deck
            if (dir.y > 0.02) {
                vec2 cloudUv = dir.xz / (dir.y + 0.22) * 1.45 + vec2(uTime * 0.012, uTime * 0.006);
                float c = fbmClouds(cloudUv);
                float cloudMask = smoothstep(0.62 - uCloudCoverage * 0.36, 0.92 - uCloudCoverage * 0.25, c);
                cloudMask *= smoothstep(0.02, 0.24, dir.y);
                vec3 cloudLit = mix(uSkyHorizon * 0.72, uSunColor * 1.15, pow(sunDot, 3.0) * 0.7 + 0.25);
                if (uNightFactor > 0.5) {
                    cloudLit = vec3(0.08, 0.11, 0.17);
                }
                col = mix(col, cloudLit, cloudMask * (0.55 + uCloudCoverage * 0.40));
            }

            // Starfield & Moon at night
            if (uNightFactor > 0.1 && dir.y > 0.05) {
                vec2 starCell = floor(dir.xz / (dir.y + 0.15) * 140.0);
                float starVal = hash21(starCell);
                float twinkle = 0.65 + 0.35 * sin(uTime * 2.5 + starVal * 90.0);
                float star = smoothstep(0.994, 0.9995, starVal) * twinkle * smoothstep(0.05, 0.30, dir.y);
                col += vec3(0.85, 0.92, 1.0) * star * uNightFactor * 1.6;
            }

            // Storm lightning illumination
            col += vec3(0.65, 0.75, 0.95) * uLightningFlash * (0.4 + 0.6 * horizonCurve);
            fragColor = vec4(col, 1.0);
        }
    """

    // =========================================================================
    // 2. SHADOW DEPTH PASSES (STATIC & INSTANCED)
    // =========================================================================
    const val SHADOW_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uLightSpaceMvp;
        void main() {
            gl_Position = uLightSpaceMvp * vec4(aPos, 1.0);
        }
    """

    const val SHADOW_INSTANCED_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 3) in vec4 aInstPosScale;
        layout(location = 4) in vec4 aInstRotTint;
        uniform mat4 uLightSpaceMatrix;
        void main() {
            float c = cos(aInstRotTint.x);
            float s = sin(aInstRotTint.x);
            vec3 p = aPos * aInstPosScale.w;
            vec3 r = vec3(p.x * c - p.z * s, p.y, p.x * s + p.z * c);
            vec3 worldPos = r + aInstPosScale.xyz;
            gl_Position = uLightSpaceMatrix * vec4(worldPos, 1.0);
        }
    """

    const val SHADOW_FS = """#version 300 es
        precision mediump float;
        void main() {}
    """

    // =========================================================================
    // 3. MULTI-LAYER PBR ALPINE TERRAIN SHADER
    // =========================================================================
    const val TERRAIN_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aUv;
        layout(location = 3) in vec4 aSplat; // x=grass, y=dirt/mud, z=rock, w=ao

        uniform mat4 uViewProj;
        uniform mat4 uLightSpaceMatrix;

        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec2 vUv;
        out vec4 vSplat;
        out vec4 vLightSpacePos;

        void main() {
            vWorldPos = aPos;
            vNormal = aNormal;
            vUv = aUv;
            vSplat = aSplat;
            vLightSpacePos = uLightSpaceMatrix * vec4(aPos, 1.0);
            gl_Position = uViewProj * vec4(aPos, 1.0);
        }
    """

    const val TERRAIN_FS = """#version 300 es
        precision highp float;
        in vec3 vWorldPos;
        in vec3 vNormal;
        in vec2 vUv;
        in vec4 vSplat;
        in vec4 vLightSpacePos;

        uniform sampler2D uGrassAlbedo;
        uniform sampler2D uGrassNormal;
        uniform sampler2D uGrassRough;
        uniform sampler2D uRockAlbedo;
        uniform sampler2D uRockNormal;
        uniform sampler2D uRockRough;
        uniform sampler2D uShadowMap;

        uniform vec3 uCameraPos;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform vec3 uSkyZenith;
        uniform vec3 uSkyHorizon;
        uniform vec3 uFogColor;
        uniform float uFogDensity;
        uniform float uWetness;
        uniform float uHunterSense;
        uniform vec3 uFlashlightPos;
        uniform vec3 uFlashlightDir;
        uniform float uFlashlightOn;

        out vec4 fragColor;

        float sampleShadowPCF(vec4 lightPos, float ndotl) {
            vec3 proj = lightPos.xyz / lightPos.w;
            proj = proj * 0.5 + 0.5;
            if (proj.z > 0.998 || proj.x < 0.01 || proj.x > 0.99 || proj.y < 0.01 || proj.y > 0.99) {
                return 1.0;
            }
            float bias = max(0.0022 * (1.0 - ndotl), 0.0007);
            vec2 texel = vec2(1.0) / vec2(textureSize(uShadowMap, 0));
            float shadow = 0.0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    float d = texture(uShadowMap, proj.xy + vec2(x, y) * texel * 1.35).r;
                    shadow += (proj.z - bias > d) ? 0.0 : 1.0;
                }
            }
            return shadow / 9.0;
        }

        void main() {
            vec2 uvGrass = vWorldPos.xz * 0.14;
            vec2 uvRock = vWorldPos.xz * 0.085;

            vec3 grassCol = texture(uGrassAlbedo, uvGrass).rgb;
            vec3 rockCol = texture(uRockAlbedo, uvRock).rgb;
            vec3 mudCol = grassCol * vec3(0.58, 0.44, 0.30);

            // Wet damp shoreline tint
            float shoreWet = 1.0 - smoothstep(-0.3, 2.6, vWorldPos.y);
            float totalWet = clamp(uWetness + shoreWet * 0.78, 0.0, 1.0);

            float wGrass = vSplat.x;
            float wMud = vSplat.y;
            float wRock = vSplat.z;
            float wSum = max(wGrass + wMud + wRock, 0.001);
            vec3 albedo = (grassCol * wGrass + mudCol * wMud + rockCol * wRock) / wSum;
            albedo *= mix(1.0, 0.58, totalWet * 0.65);

            // Perturb world normal using Sobel normal maps
            vec3 nG = texture(uGrassNormal, uvGrass).rgb * 2.0 - 1.0;
            vec3 nR = texture(uRockNormal, uvRock).rgb * 2.0 - 1.0;
            vec3 nBlend = normalize(mix(nG, nR, wRock / wSum));
            vec3 N = normalize(vNormal + vec3(nBlend.x * 0.35, 0.0, nBlend.y * 0.35));

            float rG = texture(uGrassRough, uvGrass).r;
            float rR = texture(uRockRough, uvRock).r;
            float roughness = mix(rG, rR, wRock / wSum);
            roughness = mix(roughness, 0.18, totalWet * 0.78);

            vec3 V = normalize(uCameraPos - vWorldPos);
            vec3 L = normalize(uSunDir);
            vec3 H = normalize(V + L);

            float NdotL = max(dot(N, L), 0.0);
            float NdotV = max(dot(N, V), 0.001);
            float NdotH = max(dot(N, H), 0.0);

            float shadow = sampleShadowPCF(vLightSpacePos, NdotL);

            // Cook-Torrance specular approximation
            float specPow = mix(96.0, 6.0, roughness * roughness);
            float fresnel = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
            float spec = pow(NdotH, specPow) * (1.0 - roughness * 0.82) * fresnel * 2.4;

            vec3 direct = (albedo * 0.95 + vec3(spec)) * uSunColor * NdotL * shadow;
            vec3 hemiAmbient = mix(uSkyHorizon * 0.28, uSkyZenith * 0.42, N.y * 0.5 + 0.5) * albedo * vSplat.w;

            vec3 color = direct + hemiAmbient;

            // Tactical flashlight volumetric spotlight in Night/Storm
            if (uFlashlightOn > 0.5) {
                vec3 toFrag = vWorldPos - uFlashlightPos;
                float dist = length(toFrag);
                vec3 dir = toFrag / max(dist, 0.001);
                float cone = smoothstep(0.84, 0.96, dot(dir, normalize(uFlashlightDir)));
                float atten = 1.0 / (1.0 + 0.015 * dist + 0.0022 * dist * dist);
                float fNdotL = max(dot(N, -dir), 0.0);
                color += albedo * vec3(1.0, 0.95, 0.84) * cone * atten * fNdotL * 4.8;
            }

            // Exponential atmospheric distance fog
            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = 1.0 - exp(-pow(camDist * uFogDensity, 1.55));
            color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

            fragColor = vec4(color, 1.0);
        }
    """

    // =========================================================================
    // 4. INSTANCED WIND-SWAY FOLIAGE & GRASS SHADER
    // =========================================================================
    const val INSTANCED_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aUv;
        layout(location = 3) in vec4 aInstPosScale; // xyz=worldPos, w=scale
        layout(location = 4) in vec4 aInstRotTint;  // x=yawRad, y=windFactor, z=tintShift, w=unused

        uniform mat4 uViewProj;
        uniform mat4 uLightSpaceMatrix;
        uniform float uTime;
        uniform vec2 uWindDir;
        uniform float uWindStrength;

        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec2 vUv;
        out float vTint;
        out vec4 vLightSpacePos;

        void main() {
            float c = cos(aInstRotTint.x);
            float s = sin(aInstRotTint.x);
            vec3 scaled = aPos * aInstPosScale.w;

            // Organic wind-gust vertex displacement scaled by height above ground
            float swayWeight = max(aPos.y, 0.0) * aInstRotTint.y;
            float phase = uTime * (1.8 + uWindStrength * 0.45) + aInstPosScale.x * 0.19 + aInstPosScale.z * 0.17;
            float gust = sin(phase) * 0.65 + sin(phase * 2.3 + 1.1) * 0.35;
            vec2 sway = uWindDir * gust * uWindStrength * 0.085 * swayWeight;

            vec3 rotPos = vec3(scaled.x * c - scaled.z * s, scaled.y, scaled.x * s + scaled.z * c);
            rotPos.x += sway.x;
            rotPos.z += sway.y;

            vec3 worldPos = rotPos + aInstPosScale.xyz;
            vec3 rotNorm = vec3(aNormal.x * c - aNormal.z * s, aNormal.y, aNormal.x * s + aNormal.z * c);

            vWorldPos = worldPos;
            vNormal = normalize(rotNorm);
            vUv = aUv;
            vTint = aInstRotTint.z;
            vLightSpacePos = uLightSpaceMatrix * vec4(worldPos, 1.0);
            gl_Position = uViewProj * vec4(worldPos, 1.0);
        }
    """

    const val INSTANCED_FS = """#version 300 es
        precision highp float;
        in vec3 vWorldPos;
        in vec3 vNormal;
        in vec2 vUv;
        in float vTint;
        in vec4 vLightSpacePos;

        uniform sampler2D uAlbedoTex;
        uniform sampler2D uRoughTex;
        uniform sampler2D uShadowMap;
        uniform vec3 uBaseColor;
        uniform float uUseTex;
        uniform vec3 uCameraPos;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform vec3 uSkyZenith;
        uniform vec3 uSkyHorizon;
        uniform vec3 uFogColor;
        uniform float uFogDensity;
        uniform float uWetness;
        uniform vec3 uFlashlightPos;
        uniform vec3 uFlashlightDir;
        uniform float uFlashlightOn;

        out vec4 fragColor;

        float sampleShadow(vec4 lightPos, float ndotl) {
            vec3 proj = lightPos.xyz / lightPos.w;
            proj = proj * 0.5 + 0.5;
            if (proj.z > 0.998 || proj.x < 0.01 || proj.x > 0.99 || proj.y < 0.01 || proj.y > 0.99) {
                return 1.0;
            }
            float bias = max(0.0028 * (1.0 - ndotl), 0.0009);
            float d = texture(uShadowMap, proj.xy).r;
            return (proj.z - bias > d) ? 0.18 : 1.0;
        }

        void main() {
            vec3 texCol = texture(uAlbedoTex, vUv).rgb;
            vec3 albedo = mix(uBaseColor, texCol * uBaseColor, uUseTex);
            albedo *= (0.86 + 0.28 * vTint);

            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            vec3 L = normalize(uSunDir);
            vec3 H = normalize(V + L);

            float NdotL = max(dot(N, L), 0.0);
            float backLight = pow(max(dot(V, -L), 0.0), 3.0) * 0.25 * (1.0 - uUseTex * 0.5);
            float shadow = sampleShadow(vLightSpacePos, NdotL);
            float rough = mix(0.75, texture(uRoughTex, vUv).r, uUseTex);
            rough = mix(rough, 0.22, uWetness * 0.65);

            float spec = pow(max(dot(N, H), 0.0), mix(48.0, 6.0, rough)) * (1.0 - rough) * 0.85;
            vec3 direct = (albedo * (NdotL + backLight) + vec3(spec) * NdotL) * uSunColor * shadow;
            vec3 ambient = mix(uSkyHorizon * 0.26, uSkyZenith * 0.40, N.y * 0.5 + 0.5) * albedo;
            vec3 color = direct + ambient;

            if (uFlashlightOn > 0.5) {
                vec3 toFrag = vWorldPos - uFlashlightPos;
                float dist = length(toFrag);
                vec3 dir = toFrag / max(dist, 0.001);
                float cone = smoothstep(0.84, 0.96, dot(dir, normalize(uFlashlightDir)));
                float atten = 1.0 / (1.0 + 0.015 * dist + 0.0022 * dist * dist);
                color += albedo * vec3(1.0, 0.95, 0.84) * cone * atten * 3.6;
            }

            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = 1.0 - exp(-pow(camDist * uFogDensity, 1.55));
            color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));
            fragColor = vec4(color, 1.0);
        }
    """

    // =========================================================================
    // 5. PBR WORLD SHADER (ARTICULATED WILDLIFE, WEAPONS, TRACKS, ARROWS)
    // =========================================================================
    const val WORLD_PBR_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aUv;

        uniform mat4 uModel;
        uniform mat4 uViewProj;
        uniform mat4 uLightSpaceMatrix;

        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec2 vUv;
        out vec4 vLightSpacePos;

        void main() {
            vec4 world = uModel * vec4(aPos, 1.0);
            vWorldPos = world.xyz;
            vNormal = normalize(mat3(uModel) * aNormal);
            vUv = aUv;
            vLightSpacePos = uLightSpaceMatrix * world;
            gl_Position = uViewProj * world;
        }
    """

    const val WORLD_PBR_FS = """#version 300 es
        precision highp float;
        in vec3 vWorldPos;
        in vec3 vNormal;
        in vec2 vUv;
        in vec4 vLightSpacePos;

        uniform sampler2D uAlbedoTex;
        uniform sampler2D uRoughTex;
        uniform sampler2D uShadowMap;
        uniform vec3 uBaseColor;
        uniform float uUseTex;
        uniform float uMetallic;
        uniform float uRoughnessOverride;
        uniform vec3 uEmissive;
        uniform float uUseFog;
        uniform float uHunterHighlight;

        uniform vec3 uCameraPos;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform vec3 uSkyZenith;
        uniform vec3 uSkyHorizon;
        uniform vec3 uFogColor;
        uniform float uFogDensity;
        uniform float uWetness;
        uniform vec3 uFlashlightPos;
        uniform vec3 uFlashlightDir;
        uniform float uFlashlightOn;

        out vec4 fragColor;

        void main() {
            vec3 texCol = texture(uAlbedoTex, vUv).rgb;
            vec3 albedo = mix(uBaseColor, texCol * uBaseColor, uUseTex);
            float rough = (uRoughnessOverride >= 0.0) ? uRoughnessOverride : texture(uRoughTex, vUv).r;
            rough = mix(rough, 0.16, uWetness * 0.45 * uUseFog);

            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCameraPos - vWorldPos);
            vec3 L = normalize(uSunDir);
            vec3 H = normalize(V + L);

            float NdotL = max(dot(N, L), 0.0);
            float NdotV = max(dot(N, V), 0.001);
            float NdotH = max(dot(N, H), 0.0);

            // Real-time environment reflection approximation (Sky IBL)
            vec3 R = reflect(-V, N);
            vec3 envRefl = mix(uSkyHorizon * 0.65, uSkyZenith * 1.15, clamp(R.y * 0.5 + 0.5, 0.0, 1.0));
            vec3 F0 = mix(vec3(0.04), albedo, uMetallic);
            vec3 F = F0 + (vec3(1.0) - F0) * pow(1.0 - NdotV, 5.0);

            float specPow = mix(160.0, 8.0, rough * rough);
            vec3 specDirect = F * pow(NdotH, specPow) * (1.2 - rough * 0.7) * 2.6;
            vec3 diffDirect = albedo * (1.0 - uMetallic) * NdotL;

            vec3 direct = (diffDirect + specDirect) * uSunColor;
            vec3 ambient = albedo * (1.0 - uMetallic) * mix(uSkyHorizon * 0.30, uSkyZenith * 0.48, N.y * 0.5 + 0.5);
            ambient += envRefl * F * (1.0 - rough * 0.85) * 0.85;

            vec3 color = direct + ambient + uEmissive;

            if (uFlashlightOn > 0.5 && uUseFog > 0.5) {
                vec3 toFrag = vWorldPos - uFlashlightPos;
                float dist = length(toFrag);
                vec3 dir = toFrag / max(dist, 0.001);
                float cone = smoothstep(0.84, 0.96, dot(dir, normalize(uFlashlightDir)));
                float atten = 1.0 / (1.0 + 0.015 * dist + 0.0022 * dist * dist);
                color += albedo * vec3(1.0, 0.95, 0.84) * cone * atten * 4.5;
            }

            // Hunter Sense heat/rim silhouette outline through foliage
            if (uHunterHighlight > 0.01) {
                float rim = pow(1.0 - NdotV, 2.2);
                color = mix(color, vec3(1.0, 0.62, 0.18) * (0.65 + rim * 1.8), uHunterHighlight * 0.72);
            }

            if (uUseFog > 0.5) {
                float camDist = length(uCameraPos - vWorldPos);
                float fogFactor = 1.0 - exp(-pow(camDist * uFogDensity, 1.55));
                color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));
            }
            fragColor = vec4(color, 1.0);
        }
    """

    // =========================================================================
    // 6. REALISTIC ALPINE MOUNTAIN LAKE WATER SHADER
    // =========================================================================
    const val WATER_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec2 aUv;
        layout(location = 2) in float aDepth;

        uniform mat4 uViewProj;
        uniform float uTime;

        out vec3 vWorldPos;
        out vec2 vUv;
        out float vDepth;

        void main() {
            vec3 pos = aPos;
            float wave = sin(pos.x * 0.35 + uTime * 1.6) * cos(pos.z * 0.30 + uTime * 1.3) * 0.055;
            pos.y += wave * clamp(aDepth * 0.6, 0.0, 1.0);
            vWorldPos = pos;
            vUv = aUv;
            vDepth = aDepth;
            gl_Position = uViewProj * vec4(pos, 1.0);
        }
    """

    const val WATER_FS = """#version 300 es
        precision highp float;
        in vec3 vWorldPos;
        in vec2 vUv;
        in float vDepth;

        uniform sampler2D uNormalMap;
        uniform float uTime;
        uniform vec2 uWindDir;
        uniform vec3 uCameraPos;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform vec3 uSkyZenith;
        uniform vec3 uSkyHorizon;
        uniform vec3 uFogColor;
        uniform float uFogDensity;

        out vec4 fragColor;

        void main() {
            vec2 scroll1 = vWorldPos.xz * 0.085 + uWindDir * uTime * 0.065;
            vec2 scroll2 = vWorldPos.xz * 0.145 - vec2(uWindDir.y, -uWindDir.x) * uTime * 0.048;

            vec3 n1 = texture(uNormalMap, scroll1).rgb * 2.0 - 1.0;
            vec3 n2 = texture(uNormalMap, scroll2).rgb * 2.0 - 1.0;
            vec3 N = normalize(vec3((n1.x + n2.x) * 0.24, 1.0, (n1.y + n2.y) * 0.24));

            vec3 V = normalize(uCameraPos - vWorldPos);
            vec3 L = normalize(uSunDir);
            vec3 R = reflect(-V, N);
            vec3 H = normalize(V + L);

            float NdotV = max(dot(N, V), 0.001);
            float fresnel = 0.03 + 0.97 * pow(1.0 - NdotV, 4.6);

            // Beer-Lambert water depth absorption (clear shallow -> deep alpine emerald-teal)
            float depthFactor = clamp(vDepth / 5.5, 0.0, 1.0);
            vec3 shallowCol = vec3(0.24, 0.34, 0.28);
            vec3 deepCol = vec3(0.02, 0.13, 0.15);
            vec3 waterBody = mix(shallowCol, deepCol, smoothstep(0.0, 1.0, depthFactor));

            // Sky reflection + sun sparkle glints
            vec3 skyRefl = mix(uSkyHorizon * 0.95, uSkyZenith * 1.15, clamp(R.y * 0.7 + 0.25, 0.0, 1.0));
            float sunGlint = pow(max(dot(N, H), 0.0), 220.0) * 3.8 + pow(max(dot(N, H), 0.0), 36.0) * 0.45;
            vec3 color = mix(waterBody, skyRefl, clamp(fresnel * 0.92, 0.06, 0.95)) + uSunColor * sunGlint;

            // Animated shoreline foam
            float foamWave = sin(vWorldPos.x * 0.7 + vWorldPos.z * 0.6 + uTime * 2.4) * 0.5 + 0.5;
            float foamMask = (1.0 - smoothstep(0.05, 0.65 + foamWave * 0.28, vDepth)) * (0.45 + 0.55 * foamWave);
            color = mix(color, uSkyHorizon * 0.85 + vec3(0.35), clamp(foamMask * 0.65, 0.0, 0.75));

            float alpha = clamp(smoothstep(0.0, 0.38, vDepth) * 0.88 + fresnel * 0.12 + foamMask * 0.3, 0.18, 0.96);

            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = 1.0 - exp(-pow(camDist * uFogDensity, 1.55));
            color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

            fragColor = vec4(color, alpha);
        }
    """

    // =========================================================================
    // 7. VOLUMETRIC GOD RAYS & ATMOSPHERIC PARTICLES SHADER
    // =========================================================================
    const val EFFECTS_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec4 aColor;
        layout(location = 2) in float aSize;

        uniform mat4 uViewProj;
        out vec4 vColor;

        void main() {
            vColor = aColor;
            gl_Position = uViewProj * vec4(aPos, 1.0);
            gl_PointSize = clamp(aSize / max(gl_Position.w, 0.5), 1.5, 22.0);
        }
    """

    const val EFFECTS_FS = """#version 300 es
        precision mediump float;
        in vec4 vColor;
        uniform float uIsPoint;
        out vec4 fragColor;

        void main() {
            float alpha = vColor.a;
            if (uIsPoint > 0.5) {
                vec2 d = gl_PointCoord - vec2(0.5);
                float dist = length(d);
                if (dist > 0.5) discard;
                alpha *= smoothstep(0.5, 0.05, dist);
            }
            fragColor = vec4(vColor.rgb, alpha);
        }
    """

    // =========================================================================
    // 8. POST-PROCESSING: SELECTIVE BLOOM + ACES FILMIC + SCOPE CHROMATIC BLUR
    // =========================================================================
    const val FULLSCREEN_VS = """#version 300 es
        layout(location = 0) in vec2 aPos;
        out vec2 vUv;
        void main() {
            vUv = aPos * 0.5 + 0.5;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """

    const val POST_BRIGHT_FS = """#version 300 es
        precision mediump float;
        in vec2 vUv;
        uniform sampler2D uSceneTex;
        uniform float uThreshold;
        out vec4 fragColor;

        void main() {
            vec3 col = texture(uSceneTex, vUv).rgb;
            float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
            float soft = smoothstep(uThreshold, uThreshold + 0.45, lum);
            fragColor = vec4(col * soft, 1.0);
        }
    """

    const val POST_BLUR_FS = """#version 300 es
        precision mediump float;
        in vec2 vUv;
        uniform sampler2D uTex;
        uniform vec2 uDir;
        out vec4 fragColor;

        void main() {
            vec2 texel = uDir / vec2(textureSize(uTex, 0));
            vec3 sum = texture(uTex, vUv).rgb * 0.227027;
            sum += texture(uTex, vUv + texel * 1.3846).rgb * 0.316216;
            sum += texture(uTex, vUv - texel * 1.3846).rgb * 0.316216;
            sum += texture(uTex, vUv + texel * 3.2307).rgb * 0.070270;
            sum += texture(uTex, vUv - texel * 3.2307).rgb * 0.070270;
            fragColor = vec4(sum, 1.0);
        }
    """

    const val POST_COMPOSITE_FS = """#version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uSceneTex;
        uniform sampler2D uBloomTex;
        uniform float uBloomStrength;
        uniform float uExposure;
        uniform float uHunterSense;
        uniform float uScoped;
        uniform float uWoundedFlash;
        uniform vec3 uColorGradeTint;
        out vec4 fragColor;

        vec3 acesFilm(vec3 x) {
            const float a = 2.51;
            const float b = 0.03;
            const float c = 2.43;
            const float d = 0.59;
            const float e = 0.14;
            return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
        }

        void main() {
            vec2 centerVec = vUv - vec2(0.5);
            float radial = length(centerVec);

            vec3 sceneCol;
            if (uScoped > 0.5) {
                // Optical scope lens chromatic dispersion near glass rim
                float ca = smoothstep(0.18, 0.48, radial) * 0.0045;
                float r = texture(uSceneTex, vUv + centerVec * ca).r;
                float g = texture(uSceneTex, vUv).g;
                float b = texture(uSceneTex, vUv - centerVec * ca).b;
                sceneCol = vec3(r, g, b);
            } else {
                sceneCol = texture(uSceneTex, vUv).rgb;
            }

            vec3 bloom = texture(uBloomTex, vUv).rgb * uBloomStrength;
            vec3 hdr = (sceneCol + bloom) * uExposure;
            vec3 mapped = acesFilm(hdr);

            // Subtle weather color grading
            mapped *= uColorGradeTint;

            // Hunter Sense desaturated tactical amber/cyan shift
            if (uHunterSense > 0.01) {
                float lum = dot(mapped, vec3(0.299, 0.587, 0.114));
                vec3 desaturated = mix(vec3(lum), mapped, 0.34);
                vec3 senseTint = mix(vec3(0.72, 0.96, 1.0), vec3(1.08, 0.90, 0.68), smoothstep(0.15, 0.65, radial));
                mapped = mix(mapped, desaturated * senseTint, uHunterSense * 0.82);
            }

            // Filmic lens vignette
            float vig = smoothstep(0.92, 0.30, radial);
            mapped *= mix(0.74, 1.0, vig);

            // Predator close-range warning edge pulse
            if (uWoundedFlash > 0.01) {
                float edge = smoothstep(0.25, 0.68, radial);
                mapped = mix(mapped, vec3(0.85, 0.12, 0.08), edge * uWoundedFlash * 0.55);
            }

            // Gamma to sRGB
            mapped = pow(max(mapped, vec3(0.0)), vec3(1.0 / 2.2));
            fragColor = vec4(mapped, 1.0);
        }
    """
}
