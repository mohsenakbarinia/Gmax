import * as THREE from 'three';
import { Terrain } from './Terrain';

const WATER_VERTEX_SHADER = /* glsl */ `
  uniform float uTime;
  uniform vec2 uWindDir;
  attribute float aDepth;
  varying vec3 vWorldPos;
  varying vec2 vUv;
  varying float vDepth;

  void main() {
    vUv = uv;
    vDepth = aDepth;
    vec3 pos = position;
    float waveMask = smoothstep(0.0, 1.4, aDepth);
    float w1 = sin(pos.x * 0.24 + uTime * 1.65 + uWindDir.x * 2.0) * 0.055;
    float w2 = cos(pos.z * 0.31 - uTime * 1.35 + uWindDir.y * 2.0) * 0.045;
    pos.y += (w1 + w2) * waveMask;
    vec4 wp = modelMatrix * vec4(pos, 1.0);
    vWorldPos = wp.xyz;
    gl_Position = projectionMatrix * viewMatrix * wp;
  }
`;

const WATER_FRAGMENT_SHADER = /* glsl */ `
  precision highp float;
  uniform float uTime;
  uniform vec2 uWindDir;
  uniform vec3 uSunDir;
  uniform vec3 uSunColor;
  uniform vec3 uSkyZenith;
  uniform vec3 uSkyHorizon;
  uniform vec3 uFogColor;
  uniform float uFogDensity;
  uniform sampler2D uNormalMap;
  uniform float uHasNormalMap;

  varying vec3 vWorldPos;
  varying vec2 vUv;
  varying float vDepth;

  vec3 procRipple(vec2 p, float t) {
    float h1 = sin(p.x * 2.4 + t * 1.7) * cos(p.y * 2.1 - t * 1.3);
    float h2 = sin((p.x + p.y) * 3.7 - t * 2.2) * 0.5;
    float dx = cos(p.x * 2.4 + t * 1.7) * 0.14 + cos((p.x + p.y) * 3.7 - t * 2.2) * 0.1;
    float dz = -sin(p.y * 2.1 - t * 1.3) * 0.14 + cos((p.x + p.y) * 3.7 - t * 2.2) * 0.1;
    return normalize(vec3(dx, 1.0, dz + h1 * 0.02 + h2 * 0.02));
  }

  void main() {
    if (vDepth <= 0.005) discard;

    vec2 wUv1 = vWorldPos.xz * 0.045 + uWindDir * uTime * 0.028;
    vec2 wUv2 = vWorldPos.xz * 0.082 - vec2(uWindDir.y, -uWindDir.x) * uTime * 0.022;

    vec3 n;
    if (uHasNormalMap > 0.5) {
      vec3 n1 = texture2D(uNormalMap, wUv1).xyz * 2.0 - 1.0;
      vec3 n2 = texture2D(uNormalMap, wUv2).xyz * 2.0 - 1.0;
      vec3 blended = normalize(vec3((n1.x + n2.x) * 0.28, 1.0, (n1.y + n2.y) * 0.28));
      n = blended;
    } else {
      n = procRipple(vWorldPos.xz * 0.25, uTime);
    }

    vec3 V = normalize(cameraPosition - vWorldPos);
    vec3 L = normalize(uSunDir);
    vec3 H = normalize(L + V);

    // Physically accurate Schlick Fresnel reflection
    float cosTheta = max(dot(n, V), 0.0);
    float fresnel = clamp(0.03 + 0.97 * pow(1.0 - cosTheta, 5.0), 0.04, 0.94);

    // Sky reflection gradient
    vec3 R = reflect(-V, n);
    float skyT = clamp(R.y * 0.8 + 0.2, 0.0, 1.0);
    vec3 reflCol = mix(uSkyHorizon, uSkyZenith, skyT);

    // Depth absorption (clear shallow rocky turquoise -> deep alpine emerald-teal)
    float depthFactor = clamp(vDepth / 5.2, 0.0, 1.0);
    vec3 shallowCol = vec3(0.16, 0.34, 0.30);
    vec3 deepCol = vec3(0.025, 0.095, 0.11);
    vec3 waterBody = mix(shallowCol, deepCol, depthFactor);

    // Sparkling sun specular glints
    float spec = pow(max(dot(n, H), 0.0), 220.0) * 2.8 + pow(max(dot(n, H), 0.0), 36.0) * 0.22;
    vec3 specCol = uSunColor * spec;

    // Animated shoreline foam
    float shoreMask = 1.0 - smoothstep(0.02, 0.72, vDepth);
    float foamWave = 0.5 + 0.5 * sin(vWorldPos.x * 1.4 + vWorldPos.z * 1.1 - uTime * 2.8);
    float foam = shoreMask * smoothstep(0.32, 0.85, foamWave) * 0.72;

    vec3 col = mix(waterBody, reflCol, fresnel) + specCol;
    col = mix(col, vec3(0.88, 0.93, 0.95), foam);

    // Exponential distance fog synced to horizon
    float dist = length(cameraPosition - vWorldPos);
    float fogFactor = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
    col = mix(col, uFogColor, clamp(fogFactor, 0.0, 1.0));

    float alpha = clamp(smoothstep(0.0, 0.35, vDepth) * 0.88 + fresnel * 0.14 + foam * 0.3, 0.15, 0.96);
    gl_FragColor = vec4(col, alpha);
  }
`;

export class AlpineWater {
  readonly mesh: THREE.Mesh;
  readonly uniforms: Record<string, THREE.IUniform>;

  constructor(terrain: Terrain) {
    const res = 68;
    const minX = Terrain.LAKE_CENTER_X - Terrain.LAKE_RADIUS_X * 1.42;
    const maxX = Terrain.LAKE_CENTER_X + Terrain.LAKE_RADIUS_X * 1.42;
    const minZ = Terrain.LAKE_CENTER_Z - Terrain.LAKE_RADIUS_Z * 1.42;
    const maxZ = Terrain.LAKE_CENTER_Z + Terrain.LAKE_RADIUS_Z * 1.42;

    const stride = res + 1;
    const vertCount = stride * stride;
    const positions = new Float32Array(vertCount * 3);
    const uvs = new Float32Array(vertCount * 2);
    const depths = new Float32Array(vertCount);

    let p = 0;
    let u = 0;
    let d = 0;
    for (let gz = 0; gz <= res; gz++) {
      const vz = gz / res;
      const z = minZ + (maxZ - minZ) * vz;
      for (let gx = 0; gx <= res; gx++) {
        const vx = gx / res;
        const x = minX + (maxX - minX) * vx;
        const bed = terrain.getHeightAt(x, z);
        positions[p++] = x;
        positions[p++] = Terrain.WATER_LEVEL;
        positions[p++] = z;
        uvs[u++] = vx;
        uvs[u++] = vz;
        depths[d++] = Math.max(0, Terrain.WATER_LEVEL - bed);
      }
    }

    const indices = new Uint32Array(res * res * 6);
    let idx = 0;
    for (let gz = 0; gz < res; gz++) {
      for (let gx = 0; gx < res; gx++) {
        const i0 = gz * stride + gx;
        const i1 = i0 + 1;
        const i2 = i0 + stride;
        const i3 = i2 + 1;
        indices[idx++] = i0;
        indices[idx++] = i2;
        indices[idx++] = i1;
        indices[idx++] = i1;
        indices[idx++] = i2;
        indices[idx++] = i3;
      }
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geo.setAttribute('uv', new THREE.BufferAttribute(uvs, 2));
    geo.setAttribute('aDepth', new THREE.BufferAttribute(depths, 1));
    geo.setIndex(new THREE.BufferAttribute(indices, 1));

    this.uniforms = {
      uTime: { value: 0 },
      uWindDir: { value: new THREE.Vector2(0.7, 0.7) },
      uSunDir: { value: new THREE.Vector3(0.5, 0.3, -0.7).normalize() },
      uSunColor: { value: new THREE.Color(1.0, 0.82, 0.56) },
      uSkyZenith: { value: new THREE.Color(0.18, 0.34, 0.58) },
      uSkyHorizon: { value: new THREE.Color(0.95, 0.64, 0.38) },
      uFogColor: { value: new THREE.Color(0.82, 0.62, 0.44) },
      uFogDensity: { value: 0.0046 },
      uNormalMap: { value: null },
      uHasNormalMap: { value: 0.0 }
    };

    const mat = new THREE.ShaderMaterial({
      vertexShader: WATER_VERTEX_SHADER,
      fragmentShader: WATER_FRAGMENT_SHADER,
      uniforms: this.uniforms,
      transparent: true,
      depthWrite: false
    });

    this.mesh = new THREE.Mesh(geo, mat);
    this.mesh.renderOrder = 2;
  }

  setNormalMap(normalMap: THREE.Texture): void {
    this.uniforms.uNormalMap.value = normalMap;
    this.uniforms.uHasNormalMap.value = 1.0;
  }

  update(
    time: number,
    windX: number,
    windZ: number,
    sunDir: THREE.Vector3,
    sunColor: THREE.Color,
    skyZenith: THREE.Color,
    skyHorizon: THREE.Color,
    fogColor: THREE.Color,
    fogDensity: number
  ): void {
    this.uniforms.uTime.value = time;
    this.uniforms.uWindDir.value.set(windX, windZ);
    this.uniforms.uSunDir.value.copy(sunDir);
    this.uniforms.uSunColor.value.copy(sunColor);
    this.uniforms.uSkyZenith.value.copy(skyZenith);
    this.uniforms.uSkyHorizon.value.copy(skyHorizon);
    this.uniforms.uFogColor.value.copy(fogColor);
    this.uniforms.uFogDensity.value = fogDensity;
  }
}
