import * as THREE from 'three';
import { Sky } from 'three/examples/jsm/objects/Sky.js';
import { WeatherPreset } from '../types';

export interface AtmosphereVisuals {
  sunDir: THREE.Vector3;
  sunColor: THREE.Color;
  skyZenith: THREE.Color;
  skyHorizon: THREE.Color;
  fogColor: THREE.Color;
  fogDensity: number;
  wetness: number;
  exposure: number;
  isNight: boolean;
}

interface PresetConfig {
  elevationDeg: number;
  azimuthDeg: number;
  turbidity: number;
  rayleigh: number;
  mieCoefficient: number;
  mieDirectionalG: number;
  sunColor: number;
  sunIntensity: number;
  hemiSky: number;
  hemiGround: number;
  hemiIntensity: number;
  skyZenith: number;
  skyHorizon: number;
  fogColor: number;
  fogDensity: number;
  wetness: number;
  exposure: number;
  isNight: boolean;
}

const PRESET_CONFIGS: Record<WeatherPreset, PresetConfig> = {
  GOLDEN_HOUR: {
    elevationDeg: 11.5,
    azimuthDeg: -132,
    turbidity: 8.5,
    rayleigh: 2.8,
    mieCoefficient: 0.008,
    mieDirectionalG: 0.86,
    sunColor: 0xffc67d,
    sunIntensity: 3.1,
    hemiSky: 0x89a8d0,
    hemiGround: 0x2c241b,
    hemiIntensity: 0.85,
    skyZenith: 0x254878,
    skyHorizon: 0xf49d5a,
    fogColor: 0xc98c5b,
    fogDensity: 0.0044,
    wetness: 0.08,
    exposure: 1.06,
    isNight: false
  },
  MISTY_DAWN: {
    elevationDeg: 17,
    azimuthDeg: -72,
    turbidity: 4.8,
    rayleigh: 1.6,
    mieCoefficient: 0.012,
    mieDirectionalG: 0.82,
    sunColor: 0xdbeeff,
    sunIntensity: 2.35,
    hemiSky: 0xa6c4d8,
    hemiGround: 0x24312a,
    hemiIntensity: 1.05,
    skyZenith: 0x3b6285,
    skyHorizon: 0xc1d7e2,
    fogColor: 0x9fb8c4,
    fogDensity: 0.0072,
    wetness: 0.42,
    exposure: 1.04,
    isNight: false
  },
  RAINY_STORM: {
    elevationDeg: 28,
    azimuthDeg: -105,
    turbidity: 14,
    rayleigh: 0.7,
    mieCoefficient: 0.025,
    mieDirectionalG: 0.7,
    sunColor: 0x96a8ba,
    sunIntensity: 1.15,
    hemiSky: 0x5c6e7f,
    hemiGround: 0x1a221d,
    hemiIntensity: 0.95,
    skyZenith: 0x24303c,
    skyHorizon: 0x4f5f6d,
    fogColor: 0x4b5a66,
    fogDensity: 0.0085,
    wetness: 0.92,
    exposure: 0.95,
    isNight: false
  },
  MOONLIT_NIGHT: {
    elevationDeg: 24,
    azimuthDeg: 145,
    turbidity: 1.2,
    rayleigh: 0.18,
    mieCoefficient: 0.003,
    mieDirectionalG: 0.9,
    sunColor: 0x8eb4e8,
    sunIntensity: 0.68,
    hemiSky: 0x233859,
    hemiGround: 0x0b120e,
    hemiIntensity: 0.55,
    skyZenith: 0x060c1a,
    skyHorizon: 0x15253e,
    fogColor: 0x0f1a2d,
    fogDensity: 0.0064,
    wetness: 0.25,
    exposure: 0.98,
    isNight: true
  }
};

/**
 * Physically Based Atmospheric Scattering (THREE.Sky + PMREM HDR Environment Map),
 * player-frustum-following DirectionalLight soft shadows, exponential aerial fog,
 * starfield for night, and storm lightning flash dynamics.
 */
export class Atmosphere {
  readonly sky: Sky;
  readonly sunLight: THREE.DirectionalLight;
  readonly hemiLight: THREE.HemisphereLight;
  readonly flashlight: THREE.SpotLight;
  readonly flashlightTarget: THREE.Object3D;
  readonly fog: THREE.FogExp2;
  readonly stars: THREE.Points;

  private readonly pmrem: THREE.PMREMGenerator;
  private envRenderTarget: THREE.WebGLRenderTarget | null = null;
  private customHdri: THREE.Texture | null = null;
  private currentPreset: WeatherPreset = 'GOLDEN_HOUR';
  private baseSunIntensity = 3.1;
  private lightningFlash = 0;
  private nextLightning = 12;

  readonly visuals: AtmosphereVisuals = {
    sunDir: new THREE.Vector3(0.5, 0.25, -0.7).normalize(),
    sunColor: new THREE.Color(0xffc67d),
    skyZenith: new THREE.Color(0x254878),
    skyHorizon: new THREE.Color(0xf49d5a),
    fogColor: new THREE.Color(0xc98c5b),
    fogDensity: 0.0044,
    wetness: 0.08,
    exposure: 1.06,
    isNight: false
  };

  constructor(
    private readonly scene: THREE.Scene,
    private readonly renderer: THREE.WebGLRenderer
  ) {
    this.pmrem = new THREE.PMREMGenerator(renderer);
    this.pmrem.compileEquirectangularShader();

    this.sky = new Sky();
    this.sky.scale.setScalar(1200);
    scene.add(this.sky);

    this.sunLight = new THREE.DirectionalLight(0xffc67d, 3.1);
    this.sunLight.castShadow = true;
    this.sunLight.shadow.mapSize.set(2048, 2048);
    this.sunLight.shadow.camera.near = 10;
    this.sunLight.shadow.camera.far = 220;
    const bound = 64;
    this.sunLight.shadow.camera.left = -bound;
    this.sunLight.shadow.camera.right = bound;
    this.sunLight.shadow.camera.top = bound;
    this.sunLight.shadow.camera.bottom = -bound;
    this.sunLight.shadow.bias = -0.0005;
    this.sunLight.shadow.normalBias = 0.035;
    scene.add(this.sunLight);
    scene.add(this.sunLight.target);

    this.hemiLight = new THREE.HemisphereLight(0x89a8d0, 0x2c241b, 0.85);
    scene.add(this.hemiLight);

    this.flashlight = new THREE.SpotLight(0xfff1d6, 0, 75, Math.PI * 0.21, 0.45, 1.3);
    this.flashlightTarget = new THREE.Object3D();
    scene.add(this.flashlight);
    scene.add(this.flashlightTarget);
    this.flashlight.target = this.flashlightTarget;

    this.fog = new THREE.FogExp2(0xc98c5b, 0.0044);
    scene.fog = this.fog;

    // Night sky starfield dome
    const starCount = 900;
    const starPos = new Float32Array(starCount * 3);
    for (let i = 0; i < starCount; i++) {
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos(0.12 + Math.random() * 0.88);
      const r = 420;
      starPos[i * 3] = r * Math.sin(phi) * Math.cos(theta);
      starPos[i * 3 + 1] = r * Math.cos(phi);
      starPos[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);
    }
    const starGeo = new THREE.BufferGeometry();
    starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
    const starMat = new THREE.PointsMaterial({
      color: 0xdce9ff,
      size: 1.6,
      sizeAttenuation: true,
      transparent: true,
      opacity: 0.85,
      fog: false
    });
    this.stars = new THREE.Points(starGeo, starMat);
    this.stars.visible = false;
    scene.add(this.stars);

    this.applyPreset('GOLDEN_HOUR');
  }

  setDropInHdri(hdri: THREE.DataTexture): void {
    this.customHdri = this.pmrem.fromEquirectangular(hdri).texture;
    this.scene.environment = this.customHdri;
  }

  setShadowMapSize(size: number): void {
    if (this.sunLight.shadow.mapSize.width === size) return;
    this.sunLight.shadow.mapSize.set(size, size);
    if (this.sunLight.shadow.map) {
      this.sunLight.shadow.map.dispose();
      this.sunLight.shadow.map = null;
    }
  }

  applyPreset(preset: WeatherPreset): void {
    this.currentPreset = preset;
    const cfg = PRESET_CONFIGS[preset];

    const phi = THREE.MathUtils.degToRad(90 - cfg.elevationDeg);
    const theta = THREE.MathUtils.degToRad(cfg.azimuthDeg);
    this.visuals.sunDir.setFromSphericalCoords(1, phi, theta).normalize();

    const skyUniforms = this.sky.material.uniforms;
    skyUniforms['turbidity'].value = cfg.turbidity;
    skyUniforms['rayleigh'].value = cfg.rayleigh;
    skyUniforms['mieCoefficient'].value = cfg.mieCoefficient;
    skyUniforms['mieDirectionalG'].value = cfg.mieDirectionalG;
    skyUniforms['sunPosition'].value.copy(this.visuals.sunDir);
    this.sky.visible = !cfg.isNight;

    this.sunLight.color.setHex(cfg.sunColor);
    this.baseSunIntensity = cfg.sunIntensity;
    this.sunLight.intensity = cfg.sunIntensity;

    this.hemiLight.color.setHex(cfg.hemiSky);
    this.hemiLight.groundColor.setHex(cfg.hemiGround);
    this.hemiLight.intensity = cfg.hemiIntensity;

    this.visuals.sunColor.setHex(cfg.sunColor);
    this.visuals.skyZenith.setHex(cfg.skyZenith);
    this.visuals.skyHorizon.setHex(cfg.skyHorizon);
    this.visuals.fogColor.setHex(cfg.fogColor);
    this.visuals.fogDensity = cfg.fogDensity;
    this.visuals.wetness = cfg.wetness;
    this.visuals.exposure = cfg.exposure;
    this.visuals.isNight = cfg.isNight;

    this.fog.color.setHex(cfg.fogColor);
    this.fog.density = cfg.fogDensity;
    this.scene.background = cfg.isNight ? new THREE.Color(0x070e1c) : null;
    this.stars.visible = cfg.isNight;
    this.renderer.toneMappingExposure = cfg.exposure;

    // Generate real-time HDR environment map from Sky via PMREMGenerator
    if (!this.customHdri) {
      const envScene = new THREE.Scene();
      if (cfg.isNight) {
        envScene.background = new THREE.Color(0x10203a);
      } else {
        const skyClone = new Sky();
        skyClone.scale.setScalar(200);
        const cu = skyClone.material.uniforms;
        cu['turbidity'].value = cfg.turbidity;
        cu['rayleigh'].value = cfg.rayleigh;
        cu['mieCoefficient'].value = cfg.mieCoefficient;
        cu['mieDirectionalG'].value = cfg.mieDirectionalG;
        cu['sunPosition'].value.copy(this.visuals.sunDir);
        envScene.add(skyClone);
      }
      if (this.envRenderTarget) this.envRenderTarget.dispose();
      this.envRenderTarget = this.pmrem.fromScene(envScene, 0.04);
      this.scene.environment = this.envRenderTarget.texture;
    }
  }

  update(
    dt: number,
    camPos: THREE.Vector3,
    camForward: THREE.Vector3,
    flashlightOn: boolean,
    onThunder?: () => void
  ): void {
    // Keep shadow frustum tightly centered around the player with texel snapping
    const snap = 2.0;
    const cx = Math.floor(camPos.x / snap) * snap;
    const cz = Math.floor(camPos.z / snap) * snap;
    const cy = Math.floor(camPos.y / snap) * snap;
    this.sunLight.target.position.set(cx, cy, cz);
    this.sunLight.position.set(
      cx + this.visuals.sunDir.x * 95,
      cy + this.visuals.sunDir.y * 95,
      cz + this.visuals.sunDir.z * 95
    );
    this.stars.position.set(camPos.x, 0, camPos.z);

    // Tactical Flashlight beam attached to player view
    this.flashlight.intensity = flashlightOn ? 18 : 0;
    if (flashlightOn) {
      this.flashlight.position.copy(camPos).addScaledVector(camForward, 0.25);
      this.flashlightTarget.position.copy(camPos).addScaledVector(camForward, 20);
    }

    // Dynamic storm lightning flash & thunder trigger
    if (this.currentPreset === 'RAINY_STORM') {
      this.nextLightning -= dt;
      if (this.nextLightning <= 0) {
        this.lightningFlash = 1.0;
        this.nextLightning = 12 + Math.random() * 16;
        onThunder?.();
      }
    }
    if (this.lightningFlash > 0) {
      this.lightningFlash = Math.max(0, this.lightningFlash - dt * 3.8);
      this.sunLight.intensity = this.baseSunIntensity + this.lightningFlash * 5.0;
      this.renderer.toneMappingExposure = this.visuals.exposure + this.lightningFlash * 0.55;
    } else {
      this.sunLight.intensity = this.baseSunIntensity;
      this.renderer.toneMappingExposure = this.visuals.exposure;
    }
  }

  dispose(): void {
    this.envRenderTarget?.dispose();
    this.pmrem.dispose();
  }
}
