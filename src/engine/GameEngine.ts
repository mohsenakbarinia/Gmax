import * as THREE from 'three';
import {
  CareerStats,
  CompassMarker,
  DEFAULT_SETTINGS,
  DEFAULT_STATS,
  GameMode,
  GameSettings,
  HIT_ZONE_LABEL,
  HUNTING_CONTRACTS,
  HudState,
  INITIAL_HUD,
  QUALITY_PROFILES,
  SPECIES_LABEL,
  STANCE_CONFIG,
  Stance,
  TrophyBanner,
  TrophyRecord,
  matchesContract
} from '../types';
import { AssetLoader } from './AssetLoader';
import { Atmosphere } from './Atmosphere';
import { AudioEngine } from './AudioEngine';
import { FoliageSystem } from './Foliage';
import { clamp, wrapDegrees } from './Noise';
import { Terrain } from './Terrain';
import { synthesizeAllPbrTextures } from './TextureSynthesizer';
import { AlpineWater } from './WaterShader';
import { WeaponSystem } from './Weapons';
import { AnimalHitResult, WildlifeAnimal, WildlifeSystem } from './Wildlife';

const STORAGE_SETTINGS = 'wildwood_reality_settings_v1';
const STORAGE_STATS = 'wildwood_reality_stats_v1';
const STORAGE_TROPHIES = 'wildwood_reality_trophies_v1';

export class GameEngine {
  readonly renderer: THREE.WebGLRenderer;
  readonly scene: THREE.Scene;
  readonly camera: THREE.PerspectiveCamera;

  readonly assetLoader = new AssetLoader();
  readonly audio = new AudioEngine();
  readonly terrain: Terrain;
  readonly water: AlpineWater;
  readonly atmosphere: Atmosphere;
  readonly foliage: FoliageSystem;
  readonly wildlife: WildlifeSystem;
  readonly weapons: WeaponSystem;

  settings: GameSettings;
  stats: CareerStats;
  trophies: TrophyRecord[];

  gameMode: GameMode = 'TITLE_ORBIT';
  private rafId = 0;
  private lastTs = 0;
  private simTime = 0;
  private titleOrbitAngle = 0.45;
  private hudEmitAccum = 0;
  private fpsFrames = 0;
  private fpsAccum = 0;
  private currentFps = 60;

  // Player state
  private readonly playerPos = new THREE.Vector3(42, 0, 64);
  private readonly camForward = new THREE.Vector3(0, 0, -1);
  private readonly camRight = new THREE.Vector3(1, 0, 0);
  private yawRad = -2.35;
  private pitchRad = -0.05;
  private verticalVel = 0;
  private grounded = true;
  private stance: Stance = 'STAND';
  private currentEyeHeight = 1.72;

  // Input state
  private joyX = 0;
  private joyY = 0;
  private sprintLocked = false;
  private keys = new Set<string>();
  private scoped = false;
  private scopeZoom = 6.0;
  private holdingBreath = false;
  private breathStamina = 1.0;
  private breathExhausted = false;
  private heartbeatAccum = 0;

  // Hunter sense, flashlight, wind, stealth
  private hunterSenseRemaining = 0;
  private hunterSenseCooldown = 0;
  private flashlightOn = false;
  private windDirectionDeg = 42;
  private windSpeedMps = 3.4;
  private windDirX = 0.67;
  private windDirZ = 0.74;
  private stealthVisibility = 0.35;
  private stealthNoise = 0.1;
  private stepAccum = 0;
  private hitStopTimer = 0;
  private statusAlert: string | null = null;
  private statusAlertTimer = 0;
  private activeBanner: TrophyBanner | null = null;
  private bannerTimer = 0;
  private textureProgress = 0;
  private texturesReady = false;

  private readonly onHudUpdate: (hud: HudState) => void;
  private readonly onDataChange: (stats: CareerStats, trophies: TrophyRecord[]) => void;

  constructor(
    private readonly canvas: HTMLCanvasElement,
    onHudUpdate: (hud: HudState) => void,
    onDataChange: (stats: CareerStats, trophies: TrophyRecord[]) => void
  ) {
    this.onHudUpdate = onHudUpdate;
    this.onDataChange = onDataChange;

    this.settings = this.loadJson<GameSettings>(STORAGE_SETTINGS, DEFAULT_SETTINGS);
    this.stats = this.loadJson<CareerStats>(STORAGE_STATS, DEFAULT_STATS);
    this.trophies = this.loadJson<TrophyRecord[]>(STORAGE_TROPHIES, []);

    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      powerPreference: 'high-performance'
    });
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(65, window.innerWidth / Math.max(window.innerHeight, 1), 0.1, 1400);
    this.scene.add(this.camera);

    this.terrain = new Terrain();
    this.scene.add(this.terrain.mesh);

    this.water = new AlpineWater(this.terrain);
    this.scene.add(this.water.mesh);

    this.atmosphere = new Atmosphere(this.scene, this.renderer);
    this.foliage = new FoliageSystem(this.terrain);
    this.scene.add(this.foliage.group);

    this.wildlife = new WildlifeSystem(this.terrain, this.assetLoader);
    this.scene.add(this.wildlife.group);

    this.weapons = new WeaponSystem(this.assetLoader);
    this.camera.add(this.weapons.viewRig);
    this.scene.add(this.weapons.worldGroup);

    this.applySettings(this.settings);
    this.resetValley(true);
    this.handleResize();

    window.addEventListener('resize', this.handleResize);
    window.addEventListener('keydown', this.handleKeyDown);
    window.addEventListener('keyup', this.handleKeyUp);
    window.addEventListener('deviceorientation', this.handleGyro);

    // Start async 512x512 procedural PBR synthesis + optional HDRI probe
    void this.bootstrapAssets();

    this.lastTs = performance.now();
    this.rafId = requestAnimationFrame(this.loop);
  }

  private async bootstrapAssets(): Promise<void> {
    const hdri = await this.assetLoader.tryLoadHdri();
    if (hdri) this.atmosphere.setDropInHdri(hdri);

    const atlas = await synthesizeAllPbrTextures((p) => {
      this.textureProgress = p;
      this.emitHud();
    });
    this.terrain.applyPbrTextures(atlas.forestFloor);
    this.water.setNormalMap(atlas.alpineRock.normalMap);
    this.foliage.applyPbrAtlas(atlas);
    this.wildlife.applyPbrAtlas(atlas);
    this.weapons.applyPbrAtlas(atlas);

    this.texturesReady = true;
    this.textureProgress = 1;
    this.emitHud();
  }

  private loadJson<T>(key: string, fallback: T): T {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return fallback;
      return { ...fallback, ...JSON.parse(raw) };
    } catch {
      return fallback;
    }
  }

  private persistProgress(): void {
    try {
      localStorage.setItem(STORAGE_STATS, JSON.stringify(this.stats));
      localStorage.setItem(STORAGE_TROPHIES, JSON.stringify(this.trophies));
    } catch {
      // Ignore storage quota issues in private browsing
    }
    this.onDataChange({ ...this.stats }, [...this.trophies]);
  }

  applySettings(next: GameSettings): void {
    const weatherChanged = this.settings.weatherPreset !== next.weatherPreset;
    this.settings = next;
    try {
      localStorage.setItem(STORAGE_SETTINGS, JSON.stringify(next));
    } catch {
      // ignore
    }

    const q = QUALITY_PROFILES[next.graphicsQuality];
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, q.pixelRatioCap));
    this.atmosphere.setShadowMapSize(q.shadowMapSize);
    this.foliage.setDensity(q.grassDensity, q.godRays);
    this.atmosphere.applyPreset(next.weatherPreset);
    this.terrain.setWetness(this.atmosphere.visuals.wetness);
    this.audio.setMasterVolume(next.masterVolume);
    this.audio.setWeather(next.weatherPreset);

    if (weatherChanged && next.weatherPreset === 'MOONLIT_NIGHT') {
      this.flashlightOn = true;
    }
    this.emitHud();
  }

  deleteTrophy(id: string): void {
    this.trophies = this.trophies.filter((t) => t.id !== id);
    this.persistProgress();
  }

  resetValley(keepTitle = false): void {
    const sx = 42;
    const sz = 64;
    this.playerPos.set(sx, this.terrain.getHeightAt(sx, sz), sz);
    this.yawRad = -2.38;
    this.pitchRad = -0.06;
    this.verticalVel = 0;
    this.grounded = true;
    this.stance = 'STAND';
    this.currentEyeHeight = STANCE_CONFIG.STAND.eyeHeight;
    this.scoped = false;
    this.scopeZoom = 6.0;
    this.holdingBreath = false;
    this.breathStamina = 1.0;
    this.breathExhausted = false;
    this.sprintLocked = false;
    this.hunterSenseRemaining = 0;
    this.hunterSenseCooldown = 0;
    this.activeBanner = null;
    this.statusAlert = null;
    this.weapons.reset();
    this.wildlife.reset();
    this.gameMode = keepTitle ? 'TITLE_ORBIT' : 'PLAYING';
    this.weapons.viewRig.visible = !keepTitle;
    this.emitHud();
  }

  startHunt(): void {
    this.audio.start();
    if (this.gameMode === 'TITLE_ORBIT') {
      this.resetValley(false);
    } else {
      this.gameMode = 'PLAYING';
      this.weapons.viewRig.visible = true;
    }
    if (this.settings.weatherPreset === 'MOONLIT_NIGHT') {
      this.flashlightOn = true;
    }
    this.emitHud();
  }

  pauseHunt(): void {
    if (this.gameMode === 'PLAYING') {
      this.gameMode = 'PAUSED';
      this.joyX = 0;
      this.joyY = 0;
      this.holdingBreath = false;
      this.weapons.cancelBowDraw();
      this.emitHud();
    }
  }

  resumeHunt(): void {
    if (this.gameMode === 'PAUSED') {
      this.gameMode = 'PLAYING';
      this.emitHud();
    }
  }

  returnToTitle(): void {
    this.gameMode = 'TITLE_ORBIT';
    this.scoped = false;
    this.holdingBreath = false;
    this.weapons.cancelBowDraw();
    this.weapons.viewRig.visible = false;
    this.emitHud();
  }

  // --- Controls ---
  setJoystick(x: number, y: number, sprintLock: boolean): void {
    this.joyX = clamp(x, -1, 1);
    this.joyY = clamp(y, -1, 1);
    this.sprintLocked = sprintLock;
  }

  addTouchLookDelta(dxPx: number, dyPx: number): void {
    if (this.gameMode !== 'PLAYING') return;
    const mag = Math.hypot(dxPx, dyPx);
    const curve = 0.65 + clamp(mag / 26, 0, 1.45);
    const sens = this.scoped
      ? this.settings.adsSensitivity * (4.0 / this.scopeZoom) * 0.0024
      : this.settings.normalSensitivity * 0.0036;
    this.yawRad -= dxPx * sens * curve;
    this.pitchRad = clamp(this.pitchRad - dyPx * sens * curve, -1.28, 1.28);
  }

  toggleScope(): void {
    if (this.gameMode !== 'PLAYING') return;
    this.scoped = !this.scoped;
    if (!this.scoped) this.holdingBreath = false;
    this.emitHud();
  }

  setScopeZoom(zoom: number): void {
    this.scopeZoom = clamp(zoom, 4, 12);
    this.emitHud();
  }

  setHoldingBreath(active: boolean): void {
    this.holdingBreath = active && this.scoped && !this.breathExhausted;
  }

  cycleStance(): void {
    if (this.gameMode !== 'PLAYING') return;
    this.stance = this.stance === 'STAND' ? 'CROUCH' : this.stance === 'CROUCH' ? 'PRONE' : 'STAND';
    this.sprintLocked = false;
    this.emitHud();
  }

  jump(): void {
    if (this.gameMode !== 'PLAYING') return;
    if (this.stance !== 'STAND') {
      this.stance = 'STAND';
      return;
    }
    if (this.grounded) {
      this.verticalVel = 5.4;
      this.grounded = false;
    }
  }

  reloadWeapon(): void {
    if (this.gameMode !== 'PLAYING') return;
    if (this.weapons.startReload()) {
      this.scoped = false;
      this.emitHud();
    }
  }

  switchWeapon(): void {
    if (this.gameMode !== 'PLAYING') return;
    this.scoped = false;
    this.holdingBreath = false;
    this.weapons.switchWeapon();
    this.emitHud();
  }

  activateHunterSense(): void {
    if (this.gameMode !== 'PLAYING' || this.hunterSenseCooldown > 0) return;
    this.hunterSenseRemaining = 6.0;
    this.hunterSenseCooldown = 14.0;
    this.vibrate(25);
    this.emitHud();
  }

  toggleFlashlight(): void {
    if (this.gameMode !== 'PLAYING') return;
    this.flashlightOn = !this.flashlightOn;
    this.emitHud();
  }

  onFirePress(): void {
    if (this.gameMode !== 'PLAYING') return;
    this.audio.start();
    if (this.weapons.activeWeapon === 'RIFLE_308') {
      const aimDir = this.getAimDirection();
      if (this.weapons.fireRifle(this.camera.position, aimDir, this.camRight)) {
        this.audio.playRifleShot();
        this.vibrate(55);
        this.pitchRad = clamp(this.pitchRad + 0.032, -1.28, 1.28);
        this.wildlife.spookNear(this.playerPos, 115);
        this.emitHud();
      } else if (this.weapons.rifleAmmo === 0 && !this.weapons.reloading) {
        this.reloadWeapon();
      }
    } else {
      this.weapons.beginBowDraw();
      this.emitHud();
    }
  }

  onFireRelease(): void {
    if (this.gameMode !== 'PLAYING') return;
    if (this.weapons.activeWeapon === 'RECURVE_BOW' && this.weapons.bowDrawing) {
      const aimDir = this.getAimDirection();
      if (this.weapons.releaseBow(this.camera.position, aimDir)) {
        this.audio.playBowRelease();
        this.vibrate(24);
      }
      this.emitHud();
    }
  }

  cancelBowDraw(): void {
    this.weapons.cancelBowDraw();
    this.emitHud();
  }

  private vibrate(ms: number): void {
    if (this.settings.hapticsEnabled && typeof navigator !== 'undefined' && navigator.vibrate) {
      try {
        navigator.vibrate(ms);
      } catch {
        // ignore unsupported browsers
      }
    }
  }

  private getAimDirection(): THREE.Vector3 {
    const dir = new THREE.Vector3();
    this.camera.getWorldDirection(dir);
    return dir.normalize();
  }

  private recordHarvest(hit: AnimalHitResult): void {
    const activeContract = HUNTING_CONTRACTS[this.stats.activeContractIndex % HUNTING_CONTRACTS.length];
    const completed = matchesContract(
      activeContract,
      hit.animal.species,
      this.weapons.activeWeapon,
      hit.hitZone,
      hit.distanceMeters
    );

    const record: TrophyRecord = {
      id: `${Date.now()}-${Math.floor(Math.random() * 10000)}`,
      species: hit.animal.species,
      weapon: this.weapons.activeWeapon,
      hitZone: hit.hitZone,
      rating: hit.rating,
      distanceMeters: hit.distanceMeters,
      scorePoints: hit.scorePoints,
      weatherPreset: this.settings.weatherPreset,
      createdAtMs: Date.now()
    };

    this.trophies = [record, ...this.trophies].slice(0, 50);
    this.stats = {
      totalHarvests: this.stats.totalHarvests + 1,
      goldTrophies: this.stats.goldTrophies + (hit.rating === 'GOLD' ? 1 : 0),
      longestVitalShotMeters:
        hit.hitZone !== 'BODY'
          ? Math.max(this.stats.longestVitalShotMeters, hit.distanceMeters)
          : this.stats.longestVitalShotMeters,
      completedContractsCount: this.stats.completedContractsCount + (completed ? 1 : 0),
      activeContractIndex: (this.stats.activeContractIndex + (completed ? 1 : 0)) % HUNTING_CONTRACTS.length
    };
    this.persistProgress();

    this.activeBanner = {
      headline: `${HIT_ZONE_LABEL[hit.hitZone]} — ${hit.rating} TROPHY`,
      subline: `${SPECIES_LABEL[hit.animal.species]} • ${hit.distanceMeters}m • +${hit.scorePoints} pts`,
      rating: hit.rating,
      contractCompleted: completed,
      timestampMs: Date.now()
    };
    this.bannerTimer = 4.6;
    if (hit.hitZone !== 'BODY') {
      this.hitStopTimer = 0.14;
      this.audio.playVitalConfirm();
    }
    this.vibrate(70);
    this.emitHud();
  }

  private computeRangefinder(): number | null {
    if (!this.scoped || this.gameMode !== 'PLAYING') return null;
    const origin = this.camera.position;
    const dir = this.getAimDirection();
    // First check distance to aimed animal
    for (const a of this.wildlife.animals) {
      if (a.state === 'DOWNED') continue;
      const toA = a.pos.clone().add(new THREE.Vector3(0, 1.0, 0)).sub(origin);
      const proj = toA.dot(dir);
      if (proj > 4 && proj < 300) {
        const perp = toA.clone().addScaledVector(dir, -proj).length();
        if (perp < 1.35 * a.scale) return Math.round(proj);
      }
    }
    // Otherwise raymarch terrain
    for (let d = 6; d <= 280; d += 4) {
      const px = origin.x + dir.x * d;
      const py = origin.y + dir.y * d;
      const pz = origin.z + dir.z * d;
      if (py <= this.terrain.getHeightAt(px, pz)) return Math.round(d);
    }
    return 280;
  }

  private handleResize = (): void => {
    const w = this.canvas.clientWidth || window.innerWidth;
    const h = this.canvas.clientHeight || window.innerHeight;
    this.camera.aspect = w / Math.max(h, 1);
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h, false);
  };

  private handleKeyDown = (e: KeyboardEvent): void => {
    this.keys.add(e.code);
    if (this.gameMode !== 'PLAYING') return;
    if (e.code === 'Space') this.jump();
    if (e.code === 'KeyC' || e.code === 'ControlLeft') this.cycleStance();
    if (e.code === 'KeyR') this.reloadWeapon();
    if (e.code === 'KeyQ') this.activateHunterSense();
    if (e.code === 'KeyF') this.toggleFlashlight();
    if (e.code === 'Digit1' || e.code === 'Digit2') this.switchWeapon();
    if (e.code === 'Escape') this.pauseHunt();
  };

  private handleKeyUp = (e: KeyboardEvent): void => {
    this.keys.delete(e.code);
  };

  private lastGamma: number | null = null;
  private lastBeta: number | null = null;
  private handleGyro = (e: DeviceOrientationEvent): void => {
    if (this.gameMode !== 'PLAYING' || this.settings.gyroMode === 'OFF') return;
    if (this.settings.gyroMode === 'SCOPE_ONLY' && !this.scoped) {
      this.lastGamma = e.gamma;
      this.lastBeta = e.beta;
      return;
    }
    if (e.gamma != null && e.beta != null && this.lastGamma != null && this.lastBeta != null) {
      const dg = clamp(e.gamma - this.lastGamma, -6, 6);
      const db = clamp(e.beta - this.lastBeta, -6, 6);
      const scale = this.scoped ? 0.008 : 0.012;
      this.yawRad -= dg * scale;
      this.pitchRad = clamp(this.pitchRad + db * scale, -1.28, 1.28);
    }
    this.lastGamma = e.gamma;
    this.lastBeta = e.beta;
  };

  private loop = (now: number): void => {
    this.rafId = requestAnimationFrame(this.loop);
    const rawDt = clamp((now - this.lastTs) / 1000, 0.001, 0.05);
    this.lastTs = now;

    this.fpsFrames++;
    this.fpsAccum += rawDt;
    if (this.fpsAccum >= 0.5) {
      this.currentFps = Math.round(this.fpsFrames / this.fpsAccum);
      this.fpsFrames = 0;
      this.fpsAccum = 0;
    }

    const dt = this.hitStopTimer > 0 ? ((this.hitStopTimer -= rawDt), rawDt * 0.22) : rawDt;
    this.simTime += dt;

    // Wind drift
    this.windDirectionDeg = wrapDegrees(this.windDirectionDeg + Math.sin(this.simTime * 0.07) * 2.2 * dt);
    const wRad = THREE.MathUtils.degToRad(this.windDirectionDeg);
    this.windDirX = Math.sin(wRad);
    this.windDirZ = Math.cos(wRad);
    this.windSpeedMps =
      this.settings.weatherPreset === 'RAINY_STORM'
        ? 6.8 + Math.sin(this.simTime * 0.4) * 1.5
        : this.settings.weatherPreset === 'GOLDEN_HOUR'
          ? 3.2 + Math.sin(this.simTime * 0.25) * 0.8
          : 2.1 + Math.sin(this.simTime * 0.2) * 0.6;

    if (this.statusAlertTimer > 0) {
      this.statusAlertTimer -= rawDt;
      if (this.statusAlertTimer <= 0) this.statusAlert = null;
    }
    if (this.bannerTimer > 0) {
      this.bannerTimer -= rawDt;
      if (this.bannerTimer <= 0) this.activeBanner = null;
    }

    if (this.hunterSenseRemaining > 0) this.hunterSenseRemaining = Math.max(0, this.hunterSenseRemaining - dt);
    if (this.hunterSenseCooldown > 0) this.hunterSenseCooldown = Math.max(0, this.hunterSenseCooldown - dt);

    let moveSpeed = 0;
    let swayX = 0;
    let swayY = 0;

    if (this.gameMode === 'TITLE_ORBIT') {
      this.titleOrbitAngle += dt * 0.11;
      const r = 82;
      const cx = Terrain.LAKE_CENTER_X + Math.cos(this.titleOrbitAngle) * r;
      const cz = Terrain.LAKE_CENTER_Z + Math.sin(this.titleOrbitAngle) * r;
      const cy = Math.max(this.terrain.getHeightAt(cx, cz) + 14, 18);
      this.camera.position.set(cx, cy, cz);
      this.camera.lookAt(Terrain.LAKE_CENTER_X + 10, 4.5, Terrain.LAKE_CENTER_Z + 8);
      this.camera.fov = 60;
      this.camera.updateProjectionMatrix();
    } else if (this.gameMode === 'PLAYING') {
      // Combine touch joystick + WASD keyboard
      let inX = this.joyX;
      let inY = this.joyY;
      if (this.keys.has('KeyW')) inY += 1;
      if (this.keys.has('KeyS')) inY -= 1;
      if (this.keys.has('KeyD')) inX += 1;
      if (this.keys.has('KeyA')) inX -= 1;
      const mag = Math.hypot(inX, inY);
      if (mag > 1) {
        inX /= mag;
        inY /= mag;
      }

      const sprinting =
        (this.sprintLocked || this.keys.has('ShiftLeft')) &&
        inY > 0.35 &&
        this.stance === 'STAND' &&
        !this.scoped &&
        !this.weapons.bowDrawing;

      const stanceCfg = STANCE_CONFIG[this.stance];
      const baseSpeed = sprinting ? 7.2 : 4.2 * stanceCfg.speedMult;
      const fwdX = -Math.sin(this.yawRad);
      const fwdZ = -Math.cos(this.yawRad);
      const rightX = Math.cos(this.yawRad);
      const rightZ = -Math.sin(this.yawRad);
      this.camRight.set(rightX, 0, rightZ);

      const vx = (fwdX * inY + rightX * inX) * baseSpeed;
      const vz = (fwdZ * inY + rightZ * inX) * baseSpeed;
      moveSpeed = Math.hypot(vx, vz);

      let nextX = clamp(this.playerPos.x + vx * dt, -182, 182);
      let nextZ = clamp(this.playerPos.z + vz * dt, -182, 182);

      // Soft trunk collider pushback
      for (const col of this.foliage.treeColliders) {
        const dx = nextX - col.x;
        const dz = nextZ - col.z;
        const minR = col.radius + 0.45;
        const dSq = dx * dx + dz * dz;
        if (dSq < minR * minR && dSq > 1e-4) {
          const d = Math.sqrt(dSq);
          nextX = col.x + (dx / d) * minR;
          nextZ = col.z + (dz / d) * minR;
        }
      }

      // Keep player out of deep lake center
      const groundH = this.terrain.getHeightAt(nextX, nextZ);
      if (groundH > -0.65) {
        this.playerPos.x = nextX;
        this.playerPos.z = nextZ;
      }

      // Gravity & smooth ground snap
      const targetGround = Math.max(this.terrain.getHeightAt(this.playerPos.x, this.playerPos.z), -0.55);
      if (!this.grounded) {
        this.verticalVel -= 15.5 * dt;
        this.playerPos.y += this.verticalVel * dt;
        if (this.playerPos.y <= targetGround) {
          this.playerPos.y = targetGround;
          this.verticalVel = 0;
          this.grounded = true;
        }
      } else {
        this.playerPos.y = targetGround;
      }

      // Smooth eye height & footsteps
      this.currentEyeHeight += (stanceCfg.eyeHeight - this.currentEyeHeight) * Math.min(1, dt * 8);
      if (this.grounded && moveSpeed > 0.45) {
        this.stepAccum += moveSpeed * dt;
        if (this.stepAccum >= (sprinting ? 2.3 : 1.75)) {
          this.stepAccum = 0;
          const surf = this.terrain.getSurfaceType(this.playerPos.x, this.playerPos.z);
          this.audio.playFootstep(surf, stanceCfg.noiseMult * (sprinting ? 1.25 : 0.85));
        }
      }

      // Hold Breath stabilization & lung stamina
      if (this.holdingBreath && this.scoped) {
        this.breathStamina = Math.max(0, this.breathStamina - dt * 0.24);
        this.heartbeatAccum += dt;
        if (this.heartbeatAccum >= 0.72) {
          this.heartbeatAccum = 0;
          this.audio.playHeartbeat();
        }
        if (this.breathStamina <= 0.01) {
          this.holdingBreath = false;
          this.breathExhausted = true;
        }
      } else {
        this.breathStamina = Math.min(1, this.breathStamina + dt * 0.18);
        if (this.breathStamina > 0.35) this.breathExhausted = false;
      }

      // Scope sway
      const swayMult = this.scoped ? (this.holdingBreath ? 0.14 : 1.0) * stanceCfg.visMult : 0;
      swayX = Math.sin(this.simTime * 1.45) * 0.0085 * swayMult;
      swayY = Math.cos(this.simTime * 2.05) * 0.007 * swayMult;

      // Camera placement & dynamic optical scope FOV
      this.camera.position.set(this.playerPos.x, this.playerPos.y + this.currentEyeHeight, this.playerPos.z);
      this.camera.rotation.order = 'YXZ';
      this.camera.rotation.y = this.yawRad + swayX;
      this.camera.rotation.x = clamp(this.pitchRad + swayY, -1.32, 1.32);
      this.camera.rotation.z = 0;

      const targetFov =
        this.scoped && this.weapons.activeWeapon === 'RIFLE_308'
          ? clamp(62 / this.scopeZoom, 5.2, 34)
          : this.weapons.bowDrawing
            ? 56 - this.weapons.bowDrawTension * 10
            : 65;
      this.camera.fov += (targetFov - this.camera.fov) * Math.min(1, dt * 12);
      this.camera.updateProjectionMatrix();

      // Hide 3D rifle when full-screen optical scope is active
      this.weapons.viewRig.visible = !(this.scoped && this.weapons.activeWeapon === 'RIFLE_308');

      // Stealth meters (brush cover + stance + speed)
      const inBrush = this.foliage.isInBrushCover(this.playerPos.x, this.playerPos.z);
      const targetVis = clamp(stanceCfg.visMult * (inBrush ? 0.42 : 1.0) + (moveSpeed > 4.5 ? 0.2 : 0), 0.08, 1);
      const targetNoise = clamp((moveSpeed / 7.2) * stanceCfg.noiseMult * (sprinting ? 1.25 : 0.9), 0.04, 1);
      this.stealthVisibility += (targetVis - this.stealthVisibility) * Math.min(1, dt * 5);
      this.stealthNoise += (targetNoise - this.stealthNoise) * Math.min(1, dt * 6);
    }

    // Update weapons & ballistic impact detection
    this.weapons.update(
      dt,
      this.simTime,
      this.scoped,
      moveSpeed,
      this.windDirX,
      this.windDirZ,
      this.windSpeedMps
    );

    for (let i = this.weapons.projectiles.length - 1; i >= 0; i--) {
      const p = this.weapons.projectiles[i];
      const hit = this.wildlife.testSegmentHit(p.prevPos, p.pos, p.origin, p.damageMult);
      if (hit) {
        if (p.weapon === 'RECURVE_BOW') {
          this.weapons.embedArrow(p.pos, p.vel.clone().normalize());
        }
        this.weapons.worldGroup.remove(p.mesh);
        this.weapons.projectiles.splice(i, 1);
        if (hit.downedImmediately) {
          hit.animal.harvestLogged = true;
          this.recordHarvest(hit);
        } else {
          this.statusAlert = `${SPECIES_LABEL[hit.animal.species]} wounded — follow blood trail!`;
          this.statusAlertTimer = 3.5;
        }
        continue;
      }
      // Terrain impact
      const th = this.terrain.getHeightAt(p.pos.x, p.pos.z);
      if (p.pos.y <= th) {
        if (p.weapon === 'RECURVE_BOW') {
          this.weapons.embedArrow(new THREE.Vector3(p.pos.x, th + 0.05, p.pos.z), p.vel.clone().normalize());
        }
        this.weapons.worldGroup.remove(p.mesh);
        this.weapons.projectiles.splice(i, 1);
      }
    }

    // Update wildlife, atmosphere, water & foliage
    this.camera.getWorldDirection(this.camForward);
    const senseFactor = clamp(this.hunterSenseRemaining / 0.8, 0, 1);
    this.wildlife.update(
      dt,
      this.playerPos,
      this.stealthNoise,
      this.stealthVisibility,
      this.windDirX,
      this.windDirZ,
      senseFactor,
      this.atmosphere.visuals.isNight,
      (sp, dist, pan) => {
        if (this.gameMode === 'PLAYING') this.audio.playWildlifeCall(sp, dist, pan);
      },
      (animal: WildlifeAnimal) => {
        this.recordHarvest({
          animal,
          hitZone: animal.lastHitZone,
          distanceMeters: animal.lastHitDistance,
          downedImmediately: true,
          rating: animal.lastHitZone === 'BODY' ? 'BRONZE' : 'SILVER',
          scorePoints: 320 + animal.lastHitDistance * 2
        });
      }
    );

    const q = QUALITY_PROFILES[this.settings.graphicsQuality];
    this.atmosphere.update(
      dt,
      this.camera.position,
      this.camForward,
      this.flashlightOn && this.gameMode !== 'TITLE_ORBIT',
      () => {
        if (this.gameMode === 'PLAYING') this.audio.playThunder();
      }
    );
    this.water.update(
      this.simTime,
      this.windDirX,
      this.windDirZ,
      this.atmosphere.visuals.sunDir,
      this.atmosphere.visuals.sunColor,
      this.atmosphere.visuals.skyZenith,
      this.atmosphere.visuals.skyHorizon,
      this.atmosphere.visuals.fogColor,
      this.atmosphere.visuals.fogDensity
    );
    this.foliage.update(
      dt,
      this.simTime,
      this.camera.position,
      this.settings.weatherPreset,
      this.windDirX,
      this.windDirZ,
      q.godRays
    );

    this.renderer.render(this.scene, this.camera);

    // Throttled HUD emission (~18Hz) so React stays lightweight
    this.hudEmitAccum += rawDt;
    if (this.hudEmitAccum >= 0.055) {
      this.hudEmitAccum = 0;
      this.emitHud(swayX, swayY);
    }
  };

  private buildCompassMarkers(): CompassMarker[] {
    if (this.gameMode !== 'PLAYING') return [];
    const markers: CompassMarker[] = [];
    const sense = this.hunterSenseRemaining > 0;
    for (const a of this.wildlife.animals) {
      if (a.state === 'DOWNED') continue;
      const dx = a.pos.x - this.playerPos.x;
      const dz = a.pos.z - this.playerPos.z;
      const d = Math.hypot(dx, dz);
      if (d < (sense ? 160 : 72)) {
        const bearing = wrapDegrees(THREE.MathUtils.radToDeg(Math.atan2(dx, -dz)));
        markers.push({
          bearingDeg: bearing,
          distanceMeters: Math.round(d),
          kind: a.state === 'WOUNDED' ? 'BLOOD' : sense ? 'ANIMAL' : 'TRACK'
        });
      }
    }
    return markers;
  }

  private emitHud(swayX = 0, swayY = 0): void {
    const heading = wrapDegrees(THREE.MathUtils.radToDeg(-this.yawRad));
    const hud: HudState = {
      ...INITIAL_HUD,
      gameMode: this.gameMode,
      textureProgress: this.textureProgress,
      texturesReady: this.texturesReady,
      headingDeg: heading,
      windDirectionDeg: this.windDirectionDeg,
      windSpeedMps: this.windSpeedMps,
      stance: this.stance,
      sprintLocked: this.sprintLocked,
      sprinting: this.sprintLocked && this.joyY > 0.35,
      stealthVisibility: this.stealthVisibility,
      stealthNoise: this.stealthNoise,
      activeWeapon: this.weapons.activeWeapon,
      rifleAmmo: this.weapons.rifleAmmo,
      rifleMagSize: this.weapons.rifleMagSize,
      reloading: this.weapons.reloading,
      reloadProgress: this.weapons.reloading ? clamp(this.weapons.reloadTimer / this.weapons.reloadDuration, 0, 1) : 0,
      bowDrawing: this.weapons.bowDrawing,
      bowDrawTension: this.weapons.bowDrawTension,
      scoped: this.scoped,
      scopeZoom: this.scopeZoom,
      rangefinderMeters: this.computeRangefinder(),
      holdingBreath: this.holdingBreath,
      breathStamina: this.breathStamina,
      hunterSenseActive: this.hunterSenseRemaining > 0,
      hunterSenseRemaining: this.hunterSenseRemaining,
      hunterSenseCooldownRatio: clamp(this.hunterSenseCooldown / 14, 0, 1),
      flashlightOn: this.flashlightOn,
      fps: this.currentFps,
      compassMarkers: this.buildCompassMarkers(),
      statusAlert: this.statusAlert,
      trophyBanner: this.activeBanner,
      woundedFlash: 0,
      scopeSwayX: swayX,
      scopeSwayY: swayY
    };
    this.onHudUpdate(hud);
  }

  dispose(): void {
    cancelAnimationFrame(this.rafId);
    window.removeEventListener('resize', this.handleResize);
    window.removeEventListener('keydown', this.handleKeyDown);
    window.removeEventListener('keyup', this.handleKeyUp);
    window.removeEventListener('deviceorientation', this.handleGyro);
    this.atmosphere.dispose();
    this.audio.dispose();
    this.renderer.dispose();
  }
}
