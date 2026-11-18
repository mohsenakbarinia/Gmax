import * as THREE from 'three';
import { WeaponType } from '../types';
import { clamp, lerp } from './Noise';
import { PbrTextureAtlas } from './TextureSynthesizer';
import { AssetLoader } from './AssetLoader';

export interface ActiveProjectile {
  weapon: WeaponType;
  pos: THREE.Vector3;
  prevPos: THREE.Vector3;
  vel: THREE.Vector3;
  origin: THREE.Vector3;
  age: number;
  damageMult: number;
  mesh: THREE.Object3D;
}

interface BrassShell {
  mesh: THREE.Mesh;
  vel: THREE.Vector3;
  spin: THREE.Vector3;
  age: number;
}

/**
 * First-Person PBR Weapons (.308 Bolt-Action Rifle + Silent Carbon Recurve Bow),
 * realistic ballistic drop + crosswind drift, brass shell ejection, muzzle flash, and embedded arrows.
 */
export class WeaponSystem {
  readonly viewRig = new THREE.Group();
  readonly worldGroup = new THREE.Group();
  readonly projectiles: ActiveProjectile[] = [];
  private readonly shells: BrassShell[] = [];
  private readonly embeddedArrows: THREE.Object3D[] = [];

  private readonly rifleGroup = new THREE.Group();
  private readonly bowGroup = new THREE.Group();
  private readonly nockedArrow = new THREE.Group();
  private readonly muzzleFlash: THREE.Mesh;

  private readonly walnutMat = new THREE.MeshStandardMaterial({ color: 0x5c361c, roughness: 0.44, metalness: 0.05 });
  private readonly metalMat = new THREE.MeshStandardMaterial({ color: 0x2a313a, roughness: 0.28, metalness: 0.88 });
  private readonly glassMat = new THREE.MeshStandardMaterial({
    color: 0x5da9d8,
    roughness: 0.08,
    metalness: 0.2,
    transparent: true,
    opacity: 0.72
  });

  activeWeapon: WeaponType = 'RIFLE_308';
  readonly rifleMagSize = 5;
  rifleAmmo = 5;
  reloading = false;
  reloadTimer = 0;
  readonly reloadDuration = 2.1;
  boltTimer = 0;
  readonly boltDuration = 0.85;
  muzzleFlashTimer = 0;

  bowDrawing = false;
  bowDrawTension = 0;

  adsBlend = 0;
  recoilKick = 0;
  walkBobPhase = 0;
  walkBobAmount = 0;

  constructor(private readonly assetLoader: AssetLoader) {
    this.muzzleFlash = new THREE.Mesh(
      new THREE.SphereGeometry(0.08, 8, 8),
      new THREE.MeshBasicMaterial({ color: 0xffd269, transparent: true, opacity: 0.92 })
    );
    this.muzzleFlash.scale.set(1, 1, 2.4);
    this.muzzleFlash.position.set(0, 0.04, -0.88);
    this.muzzleFlash.visible = false;

    this.buildRifleRig();
    this.buildBowRig();

    this.viewRig.add(this.rifleGroup);
    this.viewRig.add(this.bowGroup);
    this.bowGroup.visible = false;

    void this.tryUpgradeDropIns();
  }

  private async tryUpgradeDropIns(): Promise<void> {
    const rifle = await this.assetLoader.tryLoadModel('rifle');
    if (rifle) {
      this.rifleGroup.clear();
      this.rifleGroup.add(rifle, this.muzzleFlash);
    }
    const bow = await this.assetLoader.tryLoadModel('bow');
    if (bow) {
      this.bowGroup.clear();
      this.bowGroup.add(bow, this.nockedArrow);
    }
  }

  private buildRifleRig(): void {
    // Walnut stock & fore-end
    const stock = new THREE.Mesh(new THREE.BoxGeometry(0.055, 0.09, 0.42), this.walnutMat);
    stock.position.set(0, -0.045, 0.16);
    const forend = new THREE.Mesh(new THREE.BoxGeometry(0.048, 0.055, 0.48), this.walnutMat);
    forend.position.set(0, -0.02, -0.25);

    // Brushed gunmetal receiver & fluted barrel
    const receiver = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.025, 0.24, 12), this.metalMat);
    receiver.rotation.x = Math.PI * 0.5;
    receiver.position.set(0, 0.025, -0.06);

    const barrel = new THREE.Mesh(new THREE.CylinderGeometry(0.014, 0.018, 0.68, 12), this.metalMat);
    barrel.rotation.x = Math.PI * 0.5;
    barrel.position.set(0, 0.032, -0.48);

    // Bolt handle
    const bolt = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, 0.08, 8), this.metalMat);
    bolt.rotation.z = Math.PI * 0.42;
    bolt.position.set(0.045, 0.025, -0.02);

    // Optical Scope tube & objective lens
    const scopeTube = new THREE.Mesh(new THREE.CylinderGeometry(0.026, 0.022, 0.34, 14), this.metalMat);
    scopeTube.rotation.x = Math.PI * 0.5;
    scopeTube.position.set(0, 0.082, -0.1);

    const lens = new THREE.Mesh(new THREE.CircleGeometry(0.021, 12), this.glassMat);
    lens.position.set(0, 0.082, 0.072);

    this.rifleGroup.add(stock, forend, receiver, barrel, bolt, scopeTube, lens, this.muzzleFlash);
  }

  private buildBowRig(): void {
    const riser = new THREE.Mesh(new THREE.BoxGeometry(0.036, 0.36, 0.052), this.metalMat);
    const upperLimb = new THREE.Mesh(new THREE.BoxGeometry(0.026, 0.48, 0.018), this.metalMat);
    upperLimb.position.set(0, 0.38, 0.07);
    upperLimb.rotation.x = 0.28;
    const lowerLimb = new THREE.Mesh(new THREE.BoxGeometry(0.026, 0.48, 0.018), this.metalMat);
    lowerLimb.position.set(0, -0.38, 0.07);
    lowerLimb.rotation.x = -0.28;

    const bowString = new THREE.Mesh(
      new THREE.CylinderGeometry(0.003, 0.003, 1.15, 6),
      new THREE.MeshBasicMaterial({ color: 0xe6e9e5 })
    );
    bowString.position.set(0, 0, 0.14);

    // Nocked Carbon Arrow
    const shaft = new THREE.Mesh(new THREE.CylinderGeometry(0.006, 0.006, 0.68, 6), this.metalMat);
    shaft.rotation.x = Math.PI * 0.5;
    const tip = new THREE.Mesh(
      new THREE.ConeGeometry(0.016, 0.06, 4),
      new THREE.MeshStandardMaterial({ color: 0xd9a05b, metalness: 0.9, roughness: 0.2 })
    );
    tip.rotation.x = -Math.PI * 0.5;
    tip.position.z = -0.36;
    this.nockedArrow.add(shaft, tip);
    this.nockedArrow.position.set(0.032, 0.03, -0.14);

    this.bowGroup.add(riser, upperLimb, lowerLimb, bowString, this.nockedArrow);
  }

  applyPbrAtlas(atlas: PbrTextureAtlas): void {
    this.walnutMat.map = atlas.walnutWood.albedo;
    this.walnutMat.normalMap = atlas.walnutWood.normalMap;
    this.walnutMat.roughnessMap = atlas.walnutWood.roughnessMap;
    this.walnutMat.color.setHex(0xffffff);
    this.walnutMat.needsUpdate = true;

    this.metalMat.map = atlas.gunmetal.albedo;
    this.metalMat.normalMap = atlas.gunmetal.normalMap;
    this.metalMat.roughnessMap = atlas.gunmetal.roughnessMap;
    this.metalMat.color.setHex(0xffffff);
    this.metalMat.needsUpdate = true;
  }

  reset(): void {
    this.rifleAmmo = this.rifleMagSize;
    this.reloading = false;
    this.reloadTimer = 0;
    this.boltTimer = 0;
    this.bowDrawing = false;
    this.bowDrawTension = 0;
    this.recoilKick = 0;
    for (const p of this.projectiles) this.worldGroup.remove(p.mesh);
    this.projectiles.length = 0;
    for (const s of this.shells) this.worldGroup.remove(s.mesh);
    this.shells.length = 0;
    for (const a of this.embeddedArrows) this.worldGroup.remove(a);
    this.embeddedArrows.length = 0;
  }

  switchWeapon(): void {
    this.cancelBowDraw();
    this.reloading = false;
    this.activeWeapon = this.activeWeapon === 'RIFLE_308' ? 'RECURVE_BOW' : 'RIFLE_308';
    this.rifleGroup.visible = this.activeWeapon === 'RIFLE_308';
    this.bowGroup.visible = this.activeWeapon === 'RECURVE_BOW';
  }

  startReload(): boolean {
    if (this.activeWeapon !== 'RIFLE_308' || this.reloading || this.rifleAmmo >= this.rifleMagSize) {
      return false;
    }
    this.reloading = true;
    this.reloadTimer = 0;
    return true;
  }

  beginBowDraw(): boolean {
    if (this.activeWeapon !== 'RECURVE_BOW' || this.bowDrawing) return false;
    this.bowDrawing = true;
    this.bowDrawTension = 0.08;
    return true;
  }

  cancelBowDraw(): void {
    this.bowDrawing = false;
    this.bowDrawTension = 0;
  }

  fireRifle(camPos: THREE.Vector3, aimDir: THREE.Vector3, camRight: THREE.Vector3): boolean {
    if (this.activeWeapon !== 'RIFLE_308' || this.reloading || this.boltTimer > 0 || this.rifleAmmo <= 0) {
      return false;
    }
    this.rifleAmmo--;
    this.boltTimer = this.boltDuration;
    this.muzzleFlashTimer = 0.075;
    this.recoilKick = 1.0;

    // Spawn high-velocity .308 tracer round
    const mesh = new THREE.Mesh(
      new THREE.SphereGeometry(0.035, 6, 6),
      new THREE.MeshBasicMaterial({ color: 0xffe494 })
    );
    const start = camPos.clone().addScaledVector(aimDir, 0.8);
    mesh.position.copy(start);
    this.worldGroup.add(mesh);

    this.projectiles.push({
      weapon: 'RIFLE_308',
      pos: start.clone(),
      prevPos: start.clone(),
      vel: aimDir.clone().multiplyScalar(195),
      origin: camPos.clone(),
      age: 0,
      damageMult: 1.0,
      mesh
    });

    // Eject spinning brass casing
    const shell = new THREE.Mesh(
      new THREE.CylinderGeometry(0.008, 0.008, 0.042, 6),
      new THREE.MeshStandardMaterial({ color: 0xe6b94c, metalness: 0.9, roughness: 0.2 })
    );
    shell.position.copy(camPos).addScaledVector(camRight, 0.24).addScaledVector(aimDir, 0.35);
    shell.position.y -= 0.14;
    this.worldGroup.add(shell);
    this.shells.push({
      mesh: shell,
      vel: camRight.clone().multiplyScalar(1.8 + Math.random() * 0.6).add(new THREE.Vector3(0, 1.5, 0)),
      spin: new THREE.Vector3(9, 13, 6),
      age: 0
    });

    return true;
  }

  releaseBow(camPos: THREE.Vector3, aimDir: THREE.Vector3): boolean {
    if (!this.bowDrawing) return false;
    const tension = this.bowDrawTension;
    this.bowDrawing = false;
    this.bowDrawTension = 0;
    if (tension < 0.2) return false;

    this.recoilKick = 0.42;
    const arrowMesh = this.nockedArrow.clone(true);
    const start = camPos.clone().addScaledVector(aimDir, 0.7);
    arrowMesh.position.copy(start);
    this.worldGroup.add(arrowMesh);

    const speed = lerp(28, 76, tension);
    this.projectiles.push({
      weapon: 'RECURVE_BOW',
      pos: start.clone(),
      prevPos: start.clone(),
      vel: aimDir.clone().multiplyScalar(speed),
      origin: camPos.clone(),
      age: 0,
      damageMult: lerp(0.55, 1.25, tension),
      mesh: arrowMesh
    });
    return true;
  }

  embedArrow(pos: THREE.Vector3, dir: THREE.Vector3): void {
    const arr = this.nockedArrow.clone(true);
    arr.position.copy(pos);
    arr.lookAt(pos.clone().add(dir));
    this.worldGroup.add(arr);
    this.embeddedArrows.push(arr);
    if (this.embeddedArrows.length > 24) {
      const old = this.embeddedArrows.shift();
      if (old) this.worldGroup.remove(old);
    }
  }

  update(
    dt: number,
    time: number,
    isScoped: boolean,
    moveSpeed: number,
    windX: number,
    windZ: number,
    windSpeedMps: number
  ): void {
    this.adsBlend += ((isScoped ? 1 : 0) - this.adsBlend) * Math.min(1, dt * 11);
    this.recoilKick = Math.max(0, this.recoilKick - dt * 5.2);

    if (this.muzzleFlashTimer > 0) {
      this.muzzleFlashTimer = Math.max(0, this.muzzleFlashTimer - dt);
      this.muzzleFlash.visible = this.muzzleFlashTimer > 0;
    }
    if (this.boltTimer > 0) {
      this.boltTimer = Math.max(0, this.boltTimer - dt);
    }
    if (this.reloading) {
      this.reloadTimer += dt;
      if (this.reloadTimer >= this.reloadDuration) {
        this.reloading = false;
        this.rifleAmmo = this.rifleMagSize;
      }
    }
    if (this.bowDrawing) {
      this.bowDrawTension = clamp(this.bowDrawTension + dt * 0.95, 0, 1);
    }

    // Walk bob & breathing animation on first-person view rig
    const targetBob = clamp(moveSpeed / 5.5, 0, 1) * (1 - this.adsBlend * 0.8);
    this.walkBobAmount += (targetBob - this.walkBobAmount) * Math.min(1, dt * 8);
    this.walkBobPhase += dt * (4.5 + moveSpeed * 1.15);

    const bobX = Math.cos(this.walkBobPhase) * 0.012 * this.walkBobAmount;
    const bobY = Math.sin(this.walkBobPhase * 2) * 0.01 * this.walkBobAmount;
    const breathY = Math.sin(time * 1.6) * 0.004;
    const reloadDip = this.reloading ? Math.sin((this.reloadTimer / this.reloadDuration) * Math.PI) * 0.22 : 0;

    if (this.activeWeapon === 'RIFLE_308') {
      this.rifleGroup.position.set(
        lerp(0.24, 0.0, this.adsBlend) + bobX,
        lerp(-0.22, -0.1, this.adsBlend) - reloadDip + bobY + breathY,
        lerp(-0.46, -0.34, this.adsBlend) + this.recoilKick * 0.09
      );
      this.rifleGroup.rotation.set(
        this.recoilKick * 0.16 - reloadDip * 0.9,
        0,
        this.boltTimer > 0 ? Math.sin((this.boltTimer / this.boltDuration) * Math.PI) * -0.24 : 0
      );
    } else {
      this.bowGroup.position.set(
        lerp(-0.22, -0.09, this.bowDrawTension * 0.8 + this.adsBlend * 0.2) + bobX,
        -0.1 + bobY + breathY,
        -0.52 + this.recoilKick * 0.06
      );
      this.bowGroup.rotation.z = lerp(-0.24, -0.06, this.bowDrawTension);
      this.nockedArrow.position.z = -0.14 + this.bowDrawTension * 0.3;
    }

    // Integrate ballistic projectiles (gravity drop + crosswind drift)
    for (let i = this.projectiles.length - 1; i >= 0; i--) {
      const p = this.projectiles[i];
      p.prevPos.copy(p.pos);
      p.age += dt;
      const grav = p.weapon === 'RIFLE_308' ? 9.81 : 12.2;
      const windFactor = p.weapon === 'RIFLE_308' ? 0.16 : 0.45;
      p.vel.y -= grav * dt;
      p.vel.x += windX * windSpeedMps * windFactor * dt;
      p.vel.z += windZ * windSpeedMps * windFactor * dt;
      p.pos.addScaledVector(p.vel, dt);
      p.mesh.position.copy(p.pos);
      if (p.weapon === 'RECURVE_BOW') {
        p.mesh.lookAt(p.pos.clone().add(p.vel));
      }
      if (p.age > 5.0) {
        this.worldGroup.remove(p.mesh);
        this.projectiles.splice(i, 1);
      }
    }

    // Ejected brass casings physics
    for (let i = this.shells.length - 1; i >= 0; i--) {
      const s = this.shells[i];
      s.age += dt;
      s.vel.y -= 9.8 * dt;
      s.mesh.position.addScaledVector(s.vel, dt);
      s.mesh.rotation.x += s.spin.x * dt;
      s.mesh.rotation.y += s.spin.y * dt;
      s.mesh.rotation.z += s.spin.z * dt;
      if (s.age > 1.8) {
        this.worldGroup.remove(s.mesh);
        this.shells.splice(i, 1);
      }
    }
  }
}
