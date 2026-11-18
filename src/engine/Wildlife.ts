import * as THREE from 'three';
import { HitZone, Species, TrophyRating } from '../types';
import { clamp, seededRandom } from './Noise';
import { Terrain } from './Terrain';
import { PbrTextureAtlas } from './TextureSynthesizer';
import { AssetLoader, DropInModelKey } from './AssetLoader';

export type AnimalState = 'GRAZING' | 'WANDERING' | 'ALERT' | 'FLEEING' | 'WOUNDED' | 'DOWNED';

export interface AnimalHitResult {
  animal: WildlifeAnimal;
  hitZone: HitZone;
  distanceMeters: number;
  downedImmediately: boolean;
  rating: TrophyRating;
  scorePoints: number;
}

export interface TrackDecal {
  mesh: THREE.Mesh;
  x: number;
  z: number;
  isBlood: boolean;
  age: number;
}

export class WildlifeAnimal {
  readonly root = new THREE.Group();
  readonly legs: THREE.Object3D[] = [];
  readonly eyes: THREE.Mesh[] = [];
  headGroup = new THREE.Group();

  state: AnimalState = 'GRAZING';
  pos = new THREE.Vector3();
  yaw = 0;
  speed = 0;
  targetYaw = 0;
  stateTimer = 4;
  animPhase = 0;
  health = 100;
  bleedRate = 0;
  fallProgress = 0;
  trackTimer = 0;
  callTimer = 8;
  harvestLogged = false;
  lastHitZone: HitZone = 'BODY';
  lastHitDistance = 50;

  constructor(
    readonly id: number,
    readonly species: Species,
    readonly scale: number,
    readonly furMat: THREE.MeshStandardMaterial,
    readonly boneMat: THREE.MeshStandardMaterial
  ) {
    this.buildProceduralRig();
  }

  private buildProceduralRig(): void {
    const addMesh = (
      geo: THREE.BufferGeometry,
      mat: THREE.Material,
      x: number,
      y: number,
      z: number,
      parent: THREE.Object3D = this.root
    ): THREE.Mesh => {
      const m = new THREE.Mesh(geo, mat);
      m.position.set(x, y, z);
      m.castShadow = true;
      m.receiveShadow = true;
      parent.add(m);
      return m;
    };

    if (this.species === 'ROYAL_STAG') {
      // Torso
      const torso = addMesh(new THREE.SphereGeometry(0.45, 14, 12), this.furMat, 0, 1.28, 0);
      torso.scale.set(0.92, 0.92, 2.05);
      // 4 articulated legs
      const legGeo = new THREE.CylinderGeometry(0.07, 0.055, 1.12, 8);
      legGeo.translate(0, -0.56, 0);
      const legCoords = [
        [-0.22, 1.12, 0.54],
        [0.22, 1.12, 0.54],
        [-0.22, 1.14, -0.54],
        [0.22, 1.14, -0.54]
      ];
      for (const [lx, ly, lz] of legCoords) {
        const leg = addMesh(legGeo, this.furMat, lx, ly, lz);
        this.legs.push(leg);
      }
      // White rump tail
      addMesh(
        new THREE.SphereGeometry(0.13, 8, 8),
        new THREE.MeshStandardMaterial({ color: 0xe6dfd0, roughness: 0.85 }),
        0,
        1.34,
        -0.94
      );
      // Neck, Head & Royal Branching Antler Rack
      this.headGroup.position.set(0, 1.48, 0.66);
      this.root.add(this.headGroup);
      const neck = addMesh(new THREE.CylinderGeometry(0.14, 0.21, 0.72, 8), this.furMat, 0, 0.26, 0.14, this.headGroup);
      neck.rotation.x = 0.42;
      const head = addMesh(new THREE.SphereGeometry(0.18, 10, 10), this.furMat, 0, 0.62, 0.34, this.headGroup);
      head.scale.set(0.88, 0.85, 1.65);

      // Branching Antlers (Left & Right main beams + tines)
      for (const side of [-1, 1]) {
        const beam = addMesh(
          new THREE.CylinderGeometry(0.022, 0.036, 0.85, 6),
          this.boneMat,
          side * 0.22,
          0.95,
          0.22,
          this.headGroup
        );
        beam.rotation.z = -side * 0.52;
        beam.rotation.x = -0.25;

        const tine1 = addMesh(
          new THREE.CylinderGeometry(0.014, 0.024, 0.42, 5),
          this.boneMat,
          side * 0.34,
          1.12,
          0.35,
          this.headGroup
        );
        tine1.rotation.z = -side * 0.25;
        tine1.rotation.x = 0.45;

        const tine2 = addMesh(
          new THREE.CylinderGeometry(0.012, 0.022, 0.38, 5),
          this.boneMat,
          side * 0.44,
          1.28,
          0.16,
          this.headGroup
        );
        tine2.rotation.z = -side * 0.78;
      }
    } else if (this.species === 'WILD_BOAR') {
      const torso = addMesh(new THREE.SphereGeometry(0.42, 12, 10), this.furMat, 0, 0.68, 0);
      torso.scale.set(1.04, 0.94, 1.72);
      const legGeo = new THREE.CylinderGeometry(0.07, 0.05, 0.52, 7);
      legGeo.translate(0, -0.26, 0);
      for (const [lx, ly, lz] of [
        [-0.22, 0.52, 0.42],
        [0.22, 0.52, 0.42],
        [-0.22, 0.52, -0.42],
        [0.22, 0.52, -0.42]
      ]) {
        this.legs.push(addMesh(legGeo, this.furMat, lx, ly, lz));
      }
      this.headGroup.position.set(0, 0.72, 0.64);
      this.root.add(this.headGroup);
      const head = addMesh(new THREE.SphereGeometry(0.24, 10, 10), this.furMat, 0, 0, 0.06, this.headGroup);
      head.scale.set(0.95, 0.92, 1.38);
      const snout = addMesh(
        new THREE.CylinderGeometry(0.09, 0.11, 0.18, 8),
        new THREE.MeshStandardMaterial({ color: 0x4b3530, roughness: 0.7 }),
        0,
        -0.06,
        0.34,
        this.headGroup
      );
      snout.rotation.x = Math.PI * 0.5;
      // Curved ivory tusks
      for (const side of [-1, 1]) {
        const tusk = addMesh(
          new THREE.ConeGeometry(0.025, 0.22, 6),
          this.boneMat,
          side * 0.12,
          -0.05,
          0.32,
          this.headGroup
        );
        tusk.rotation.z = -side * 0.65;
        tusk.rotation.x = 0.55;
      }
    } else {
      // Timber Wolf
      const torso = addMesh(new THREE.SphereGeometry(0.29, 12, 10), this.furMat, 0, 0.76, 0);
      torso.scale.set(0.95, 0.95, 2.35);
      const legGeo = new THREE.CylinderGeometry(0.055, 0.042, 0.68, 7);
      legGeo.translate(0, -0.34, 0);
      for (const [lx, ly, lz] of [
        [-0.16, 0.68, 0.44],
        [0.16, 0.68, 0.44],
        [-0.16, 0.68, -0.44],
        [0.16, 0.68, -0.44]
      ]) {
        this.legs.push(addMesh(legGeo, this.furMat, lx, ly, lz));
      }
      const tail = addMesh(new THREE.CylinderGeometry(0.05, 0.08, 0.58, 7), this.furMat, 0, 0.72, -0.78);
      tail.rotation.x = -0.8;
      this.headGroup.position.set(0, 0.92, 0.68);
      this.root.add(this.headGroup);
      const head = addMesh(new THREE.SphereGeometry(0.18, 10, 10), this.furMat, 0, 0, 0.08, this.headGroup);
      head.scale.set(0.95, 0.9, 1.55);
      // Reflective eyes for Moonlit Night
      const eyeMat = new THREE.MeshStandardMaterial({
        color: 0xffd24c,
        emissive: 0xffb824,
        emissiveIntensity: 0
      });
      for (const side of [-1, 1]) {
        const eye = addMesh(new THREE.SphereGeometry(0.025, 6, 6), eyeMat, side * 0.075, 0.04, 0.28, this.headGroup);
        this.eyes.push(eye);
      }
    }

    this.root.scale.setScalar(this.scale);
  }

  replaceWithDropIn(model: THREE.Group): void {
    this.root.clear();
    this.legs.length = 0;
    this.eyes.length = 0;
    model.scale.setScalar(this.scale);
    this.root.add(model);
  }

  getVitalCenters(): { heart: THREE.Vector3; head: THREE.Vector3; body: THREE.Vector3; radius: number } {
    const fwdX = Math.sin(this.yaw);
    const fwdZ = Math.cos(this.yaw);
    const s = this.scale;
    if (this.species === 'ROYAL_STAG') {
      return {
        heart: new THREE.Vector3(this.pos.x + fwdX * 0.35 * s, this.pos.y + 1.24 * s, this.pos.z + fwdZ * 0.35 * s),
        head: new THREE.Vector3(this.pos.x + fwdX * 0.96 * s, this.pos.y + 1.98 * s, this.pos.z + fwdZ * 0.96 * s),
        body: new THREE.Vector3(this.pos.x, this.pos.y + 1.26 * s, this.pos.z),
        radius: 0.85 * s
      };
    }
    if (this.species === 'WILD_BOAR') {
      return {
        heart: new THREE.Vector3(this.pos.x + fwdX * 0.28 * s, this.pos.y + 0.68 * s, this.pos.z + fwdZ * 0.28 * s),
        head: new THREE.Vector3(this.pos.x + fwdX * 0.72 * s, this.pos.y + 0.72 * s, this.pos.z + fwdZ * 0.72 * s),
        body: new THREE.Vector3(this.pos.x, this.pos.y + 0.68 * s, this.pos.z),
        radius: 0.72 * s
      };
    }
    return {
      heart: new THREE.Vector3(this.pos.x + fwdX * 0.25 * s, this.pos.y + 0.76 * s, this.pos.z + fwdZ * 0.25 * s),
      head: new THREE.Vector3(this.pos.x + fwdX * 0.76 * s, this.pos.y + 0.94 * s, this.pos.z + fwdZ * 0.76 * s),
      body: new THREE.Vector3(this.pos.x, this.pos.y + 0.76 * s, this.pos.z),
      radius: 0.64 * s
    };
  }
}

export class WildlifeSystem {
  readonly group = new THREE.Group();
  readonly animals: WildlifeAnimal[] = [];
  readonly tracks: TrackDecal[] = [];

  private readonly rng = seededRandom(7733);
  private readonly stagMat = new THREE.MeshStandardMaterial({ color: 0x8b5e3c, roughness: 0.82 });
  private readonly boarMat = new THREE.MeshStandardMaterial({ color: 0x3f322a, roughness: 0.88 });
  private readonly wolfMat = new THREE.MeshStandardMaterial({ color: 0x6c6f75, roughness: 0.8 });
  private readonly boneMat = new THREE.MeshStandardMaterial({ color: 0xdfd4bc, roughness: 0.55 });

  private readonly trackGeo = new THREE.CircleGeometry(0.28, 10);
  private readonly footprintMat = new THREE.MeshBasicMaterial({
    color: 0x26e8d8,
    transparent: true,
    opacity: 0,
    depthWrite: false
  });
  private readonly bloodMat = new THREE.MeshBasicMaterial({
    color: 0x990b0b,
    transparent: true,
    opacity: 0.85,
    depthWrite: false
  });

  constructor(private readonly terrain: Terrain, private readonly assetLoader: AssetLoader) {
    this.trackGeo.rotateX(-Math.PI * 0.5);
    this.reset();
    void this.tryUpgradeDropInModels();
  }

  private async tryUpgradeDropInModels(): Promise<void> {
    const map: Record<Species, DropInModelKey> = {
      ROYAL_STAG: 'stag',
      WILD_BOAR: 'boar',
      TIMBER_WOLF: 'wolf'
    };
    const loaded: Partial<Record<Species, THREE.Group>> = {};
    for (const sp of ['ROYAL_STAG', 'WILD_BOAR', 'TIMBER_WOLF'] as Species[]) {
      const m = await this.assetLoader.tryLoadModel(map[sp]);
      if (m) loaded[sp] = m;
    }
    for (const a of this.animals) {
      const m = loaded[a.species];
      if (m) a.replaceWithDropIn(m.clone(true));
    }
  }

  applyPbrAtlas(atlas: PbrTextureAtlas): void {
    for (const mat of [this.stagMat, this.boarMat, this.wolfMat]) {
      mat.map = atlas.animalFur.albedo;
      mat.normalMap = atlas.animalFur.normalMap;
      mat.roughnessMap = atlas.animalFur.roughnessMap;
      mat.needsUpdate = true;
    }
    this.boneMat.map = atlas.antlerBone.albedo;
    this.boneMat.normalMap = atlas.antlerBone.normalMap;
    this.boneMat.roughnessMap = atlas.antlerBone.roughnessMap;
    this.boneMat.needsUpdate = true;
  }

  reset(): void {
    for (const a of this.animals) this.group.remove(a.root);
    for (const t of this.tracks) this.group.remove(t.mesh);
    this.animals.length = 0;
    this.tracks.length = 0;

    const specs: { sp: Species; x: number; z: number; scale: number }[] = [
      { sp: 'ROYAL_STAG', x: 56, z: 18, scale: 1.18 },
      { sp: 'ROYAL_STAG', x: 18, z: -48, scale: 1.08 },
      { sp: 'ROYAL_STAG', x: -64, z: 22, scale: 1.15 },
      { sp: 'ROYAL_STAG', x: 78, z: -32, scale: 1.12 },
      { sp: 'WILD_BOAR', x: 26, z: 14, scale: 1.05 },
      { sp: 'WILD_BOAR', x: -38, z: 42, scale: 1.1 },
      { sp: 'WILD_BOAR', x: 68, z: 54, scale: 0.98 },
      { sp: 'TIMBER_WOLF', x: -52, z: -56, scale: 1.05 },
      { sp: 'TIMBER_WOLF', x: 84, z: 12, scale: 1.02 }
    ];

    specs.forEach((s, i) => {
      const mat = s.sp === 'ROYAL_STAG' ? this.stagMat : s.sp === 'WILD_BOAR' ? this.boarMat : this.wolfMat;
      const a = new WildlifeAnimal(i + 1, s.sp, s.scale, mat, this.boneMat);
      const y = this.terrain.getHeightAt(s.x, s.z);
      a.pos.set(s.x, Math.max(y, 0.4), s.z);
      a.yaw = this.rng() * Math.PI * 2;
      a.targetYaw = a.yaw;
      a.stateTimer = 2 + this.rng() * 5;
      a.callTimer = 5 + this.rng() * 14;
      a.root.position.copy(a.pos);
      a.root.rotation.y = a.yaw;
      this.animals.push(a);
      this.group.add(a.root);
    });
  }

  spookNear(origin: THREE.Vector3, radius: number): void {
    for (const a of this.animals) {
      if (a.state === 'DOWNED') continue;
      const dx = a.pos.x - origin.x;
      const dz = a.pos.z - origin.z;
      if (dx * dx + dz * dz < radius * radius) {
        a.state = 'FLEEING';
        a.targetYaw = Math.atan2(dx, dz) + (this.rng() - 0.5) * 0.4;
        a.stateTimer = 7 + this.rng() * 4;
      }
    }
  }

  testSegmentHit(
    p0: THREE.Vector3,
    p1: THREE.Vector3,
    shooterPos: THREE.Vector3,
    damageMult: number
  ): AnimalHitResult | null {
    const seg = new THREE.Vector3().subVectors(p1, p0);
    const segLenSq = Math.max(seg.lengthSq(), 1e-6);
    const tmp = new THREE.Vector3();

    for (const a of this.animals) {
      if (a.state === 'DOWNED') continue;
      const vitals = a.getVitalCenters();

      const distToPoint = (pt: THREE.Vector3): number => {
        const t = clamp(tmp.subVectors(pt, p0).dot(seg) / segLenSq, 0, 1);
        tmp.copy(p0).addScaledVector(seg, t);
        return tmp.distanceTo(pt);
      };

      let hitZone: HitZone | null = null;
      if (distToPoint(vitals.head) < 0.34 * a.scale) {
        hitZone = 'HEAD';
      } else if (distToPoint(vitals.heart) < 0.36 * a.scale) {
        hitZone = 'HEART';
      } else if (distToPoint(vitals.heart) < 0.56 * a.scale) {
        hitZone = 'LUNGS';
      } else if (distToPoint(vitals.body) < vitals.radius) {
        hitZone = 'BODY';
      }

      if (!hitZone) continue;

      const distM = Math.max(1, Math.round(shooterPos.distanceTo(a.pos)));
      const dmg =
        hitZone === 'HEART'
          ? 120 * damageMult
          : hitZone === 'HEAD'
            ? 110 * damageMult
            : hitZone === 'LUNGS'
              ? 88 * damageMult
              : 44 * damageMult;

      a.health -= dmg;
      a.lastHitZone = hitZone;
      a.lastHitDistance = distM;
      this.spawnTrack(a.pos.x, a.pos.z, true);

      const downedNow = a.health <= 0;
      if (downedNow) {
        a.state = 'DOWNED';
        a.speed = 0;
      } else {
        a.state = 'WOUNDED';
        a.bleedRate = hitZone === 'LUNGS' ? 24 : 11;
        a.targetYaw = Math.atan2(a.pos.x - shooterPos.x, a.pos.z - shooterPos.z);
        a.stateTimer = 9;
      }

      const rating: TrophyRating =
        hitZone === 'HEART' || (hitZone !== 'BODY' && distM >= 65)
          ? 'GOLD'
          : hitZone !== 'BODY'
            ? 'SILVER'
            : 'BRONZE';
      const basePts = a.species === 'ROYAL_STAG' ? 450 : a.species === 'TIMBER_WOLF' ? 400 : 300;
      const mult = hitZone === 'HEART' ? 2.0 : hitZone === 'HEAD' ? 1.85 : hitZone === 'LUNGS' ? 1.6 : 1.0;
      const scorePoints = Math.round(basePts * mult + distM * 2.4);

      return {
        animal: a,
        hitZone,
        distanceMeters: distM,
        downedImmediately: downedNow,
        rating,
        scorePoints
      };
    }
    return null;
  }

  private spawnTrack(x: number, z: number, isBlood: boolean): void {
    const y = this.terrain.getHeightAt(x, z) + 0.06;
    const mesh = new THREE.Mesh(this.trackGeo, isBlood ? this.bloodMat : this.footprintMat);
    mesh.position.set(x, y, z);
    if (isBlood) mesh.scale.setScalar(1.3);
    this.group.add(mesh);
    this.tracks.push({ mesh, x, z, isBlood, age: 0 });
    if (this.tracks.length > 90) {
      const oldest = this.tracks.shift();
      if (oldest) this.group.remove(oldest.mesh);
    }
  }

  update(
    dt: number,
    playerPos: THREE.Vector3,
    playerNoise: number,
    playerVisibility: number,
    windDirX: number,
    windDirZ: number,
    hunterSenseFactor: number,
    isNight: boolean,
    onCall: (species: Species, dist: number, pan: number) => void,
    onBleedOutHarvest: (animal: WildlifeAnimal) => void
  ): void {
    // Hunter Sense glowing tracks + wildlife silhouette emissive highlight
    this.footprintMat.opacity = hunterSenseFactor * 0.85;
    const emissiveHex = hunterSenseFactor > 0.05 ? 0xff7a24 : 0x000000;
    for (const mat of [this.stagMat, this.boarMat, this.wolfMat]) {
      mat.emissive.setHex(emissiveHex);
      mat.emissiveIntensity = hunterSenseFactor * 0.72;
    }

    for (const a of this.animals) {
      // Night eye glow on wolves
      for (const eye of a.eyes) {
        const em = eye.material as THREE.MeshStandardMaterial;
        em.emissiveIntensity = isNight && a.state !== 'DOWNED' ? 2.5 : 0;
      }

      if (a.state === 'DOWNED') {
        a.fallProgress = Math.min(1, a.fallProgress + dt * 2.2);
        a.root.rotation.z = a.fallProgress * 1.48;
        a.root.position.y = a.pos.y - a.fallProgress * 0.42 * a.scale;
        continue;
      }

      // Bleed-out tracking for wounded animals
      if (a.state === 'WOUNDED') {
        a.health -= a.bleedRate * dt;
        a.trackTimer -= dt;
        if (a.trackTimer <= 0) {
          this.spawnTrack(a.pos.x, a.pos.z, true);
          a.trackTimer = 0.85;
        }
        if (a.health <= 0) {
          a.state = 'DOWNED';
          a.speed = 0;
          if (!a.harvestLogged) {
            a.harvestLogged = true;
            onBleedOutHarvest(a);
          }
          continue;
        }
      }

      const toAnimalX = a.pos.x - playerPos.x;
      const toAnimalZ = a.pos.z - playerPos.z;
      const dist = Math.hypot(toAnimalX, toAnimalZ);

      // Scent cone (wind blowing from player toward animal!)
      const invDist = 1 / Math.max(dist, 0.001);
      const scentDot = toAnimalX * invDist * windDirX + toAnimalZ * invDist * windDirZ;
      const smelled = dist < 58 && scentDot > 0.72;
      const heard = dist < 18 + playerNoise * 48;
      const seen = dist < 14 + playerVisibility * 44;

      if (a.state !== 'WOUNDED' && a.state !== 'FLEEING') {
        if (smelled || heard || (seen && dist < 24)) {
          a.state = 'FLEEING';
          a.targetYaw = Math.atan2(toAnimalX, toAnimalZ) + (this.rng() - 0.5) * 0.35;
          a.stateTimer = 6.5 + this.rng() * 3.5;
        } else if (seen && a.state !== 'ALERT') {
          a.state = 'ALERT';
          a.targetYaw = Math.atan2(-toAnimalX, -toAnimalZ);
          a.stateTimer = 2.8;
        }
      }

      a.stateTimer -= dt;
      if (a.stateTimer <= 0) {
        if (a.state === 'FLEEING' || a.state === 'ALERT') {
          a.state = 'WANDERING';
          a.targetYaw = this.rng() * Math.PI * 2;
          a.stateTimer = 5 + this.rng() * 6;
        } else if (a.state === 'GRAZING') {
          a.state = 'WANDERING';
          a.targetYaw = a.yaw + (this.rng() - 0.5) * 2.2;
          a.stateTimer = 4 + this.rng() * 5;
        } else if (a.state === 'WANDERING') {
          a.state = 'GRAZING';
          a.stateTimer = 3.5 + this.rng() * 5;
        }
      }

      const desiredSpeed =
        a.state === 'FLEEING' || a.state === 'WOUNDED'
          ? a.species === 'ROYAL_STAG'
            ? 8.4
            : a.species === 'TIMBER_WOLF'
              ? 7.8
              : 6.4
          : a.state === 'WANDERING'
            ? 1.9
            : 0;

      a.speed += (desiredSpeed - a.speed) * Math.min(1, dt * 4);

      // Avoid deep water and valley border walls
      const nx = a.pos.x + Math.sin(a.yaw) * 4.5;
      const nz = a.pos.z + Math.cos(a.yaw) * 4.5;
      if (
        Math.abs(nx) > 168 ||
        Math.abs(nz) > 168 ||
        this.terrain.getHeightAt(nx, nz) < 0.35
      ) {
        a.targetYaw = Math.atan2(-a.pos.x, -a.pos.z) + (this.rng() - 0.5) * 0.6;
      }

      // Smooth yaw turn
      let diff = a.targetYaw - a.yaw;
      while (diff > Math.PI) diff -= Math.PI * 2;
      while (diff < -Math.PI) diff += Math.PI * 2;
      a.yaw += diff * Math.min(1, dt * (a.state === 'FLEEING' ? 5.5 : 2.2));

      a.pos.x += Math.sin(a.yaw) * a.speed * dt;
      a.pos.z += Math.cos(a.yaw) * a.speed * dt;
      a.pos.y = this.terrain.getHeightAt(a.pos.x, a.pos.z);

      // Regular footprint trail for Hunter Sense
      if (a.speed > 0.6 && a.state !== 'WOUNDED') {
        a.trackTimer -= dt;
        if (a.trackTimer <= 0) {
          this.spawnTrack(a.pos.x, a.pos.z, false);
          a.trackTimer = 2.6;
        }
      }

      // Periodic wildlife vocalization calls
      a.callTimer -= dt;
      if (a.callTimer <= 0 && dist < 130) {
        a.callTimer = 14 + this.rng() * 18;
        const pan = clamp(toAnimalX / Math.max(dist, 1), -1, 1);
        onCall(a.species, dist, pan);
      }

      // Animate legs & grazing head dip
      a.animPhase += dt * (a.speed * 2.2 + 0.5);
      const legSwing = a.speed > 0.2 ? Math.sin(a.animPhase) * Math.min(0.65, a.speed * 0.085) : 0;
      if (a.legs.length >= 4) {
        a.legs[0].rotation.x = legSwing;
        a.legs[1].rotation.x = -legSwing;
        a.legs[2].rotation.x = -legSwing;
        a.legs[3].rotation.x = legSwing;
      }
      const targetHeadPitch = a.state === 'GRAZING' ? 0.58 : a.state === 'ALERT' ? -0.12 : 0.04;
      a.headGroup.rotation.x += (targetHeadPitch - a.headGroup.rotation.x) * Math.min(1, dt * 4);

      a.root.position.copy(a.pos);
      a.root.rotation.y = a.yaw;
    }
  }
}
