import * as THREE from 'three';
import { WeatherPreset } from '../types';
import { seededRandom } from './Noise';
import { Terrain } from './Terrain';
import { PbrTextureAtlas } from './TextureSynthesizer';

export interface TreeCollider {
  x: number;
  z: number;
  radius: number;
}

/**
 * Dense living alpine forest built with THREE.InstancedMesh (Douglas firs, birches,
 * craggy boulders, moss-covered logs, stealth ferns, wind-swaying grass blades),
 * volumetric god-ray light shafts, and atmospheric weather particles.
 */
export class FoliageSystem {
  readonly group = new THREE.Group();
  readonly treeColliders: TreeCollider[] = [];
  readonly bushPositions: THREE.Vector3[] = [];

  private pineTrunks!: THREE.InstancedMesh;
  private pineCanopyLower!: THREE.InstancedMesh;
  private pineCanopyUpper!: THREE.InstancedMesh;
  private birchTrunks!: THREE.InstancedMesh;
  private birchCanopies!: THREE.InstancedMesh;
  private boulders!: THREE.InstancedMesh;
  private mossLogs!: THREE.InstancedMesh;
  private ferns!: THREE.InstancedMesh;
  private grassBlades!: THREE.InstancedMesh;

  private pineTrunkMat!: THREE.MeshStandardMaterial;
  private birchTrunkMat!: THREE.MeshStandardMaterial;
  private rockMat!: THREE.MeshStandardMaterial;
  private grassMat!: THREE.MeshStandardMaterial;
  private fernMat!: THREE.MeshStandardMaterial;

  private maxFerns = 0;
  private maxGrass = 0;

  // Volumetric sun shafts (God Rays)
  private readonly godRayGroup = new THREE.Group();
  private readonly godRayMat: THREE.MeshBasicMaterial;

  // Weather particles (pollen / fireflies / storm rain)
  private readonly particleCount = 420;
  private readonly particlePositions: Float32Array;
  private readonly particlePhases: Float32Array;
  private readonly particles: THREE.Points;
  private readonly particleMat: THREE.PointsMaterial;

  private readonly windUniform = { value: 0 };

  constructor(private readonly terrain: Terrain) {
    this.godRayMat = new THREE.MeshBasicMaterial({
      color: 0xffd796,
      transparent: true,
      opacity: 0.085,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    this.group.add(this.godRayGroup);

    this.buildForest();

    // Atmospheric weather particles
    const rng = seededRandom(9941);
    this.particlePositions = new Float32Array(this.particleCount * 3);
    this.particlePhases = new Float32Array(this.particleCount);
    for (let i = 0; i < this.particleCount; i++) {
      this.particlePositions[i * 3] = (rng() - 0.5) * 44;
      this.particlePositions[i * 3 + 1] = rng() * 14 + 1;
      this.particlePositions[i * 3 + 2] = (rng() - 0.5) * 44;
      this.particlePhases[i] = rng() * Math.PI * 2;
    }
    const pGeo = new THREE.BufferGeometry();
    pGeo.setAttribute('position', new THREE.BufferAttribute(this.particlePositions, 3));
    this.particleMat = new THREE.PointsMaterial({
      color: 0xffdf99,
      size: 0.18,
      transparent: true,
      opacity: 0.65,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });
    this.particles = new THREE.Points(pGeo, this.particleMat);
    this.particles.frustumCulled = false;
    this.group.add(this.particles);
  }

  private injectWindSway(mat: THREE.MeshStandardMaterial, amplitude: number): void {
    mat.onBeforeCompile = (shader) => {
      shader.uniforms.uWindTime = this.windUniform;
      shader.vertexShader = shader.vertexShader
        .replace(
          '#include <common>',
          `#include <common>
           uniform float uWindTime;`
        )
        .replace(
          '#include <begin_vertex>',
          `#include <begin_vertex>
           float swayH = max(0.0, position.y);
           vec4 instOrigin = instanceMatrix * vec4(0.0, 0.0, 0.0, 1.0);
           float swayPhase = uWindTime * 2.1 + instOrigin.x * 0.25 + instOrigin.z * 0.22;
           transformed.x += sin(swayPhase) * swayH * ${amplitude.toFixed(3)};
           transformed.z += cos(swayPhase * 0.85) * swayH * ${(amplitude * 0.7).toFixed(3)};`
        );
    };
  }

  private buildForest(): void {
    const rng = seededRandom(202611);
    const dummy = new THREE.Object3D();
    const color = new THREE.Color();

    // 1. Tall Multi-Tiered Douglas Firs / Pines (~420)
    const pineTransforms: { x: number; y: number; z: number; s: number; yaw: number; tint: number }[] = [];
    for (let attempt = 0; attempt < 3400 && pineTransforms.length < 420; attempt++) {
      const x = rng() * 365 - 182.5;
      const z = rng() * 365 - 182.5;
      const y = this.terrain.getHeightAt(x, z);
      if (y < 1.2 || y > 32) continue;
      if (this.terrain.trailFactor(x, z) > 0.35) continue;
      const cdx = x - 52;
      const cdz = z - 35;
      if (cdx * cdx + cdz * cdz < 28 * 28) continue;
      if ((x - 42) * (x - 42) + (z - 64) * (z - 64) < 12 * 12) continue;
      const s = 0.85 + rng() * 0.95;
      pineTransforms.push({ x, y: y - 0.15, z, s, yaw: rng() * Math.PI * 2, tint: rng() });
      this.treeColliders.push({ x, z, radius: 0.42 * s });
    }

    const pineTrunkGeo = new THREE.CylinderGeometry(0.14, 0.36, 7.6, 7);
    pineTrunkGeo.translate(0, 3.8, 0);
    this.pineTrunkMat = new THREE.MeshStandardMaterial({ color: 0x5c402c, roughness: 0.9 });
    this.pineTrunks = new THREE.InstancedMesh(pineTrunkGeo, this.pineTrunkMat, pineTransforms.length);
    this.pineTrunks.castShadow = true;
    this.pineTrunks.receiveShadow = true;

    const lowerConeGeo = new THREE.ConeGeometry(2.55, 5.4, 7);
    lowerConeGeo.translate(0, 5.2, 0);
    const upperConeGeo = new THREE.ConeGeometry(1.75, 4.9, 7);
    upperConeGeo.translate(0, 8.2, 0);
    const pineLeafMat = new THREE.MeshStandardMaterial({ color: 0x264428, roughness: 0.84 });
    this.injectWindSway(pineLeafMat, 0.018);

    this.pineCanopyLower = new THREE.InstancedMesh(lowerConeGeo, pineLeafMat, pineTransforms.length);
    this.pineCanopyUpper = new THREE.InstancedMesh(upperConeGeo, pineLeafMat, pineTransforms.length);
    this.pineCanopyLower.castShadow = true;
    this.pineCanopyLower.receiveShadow = true;
    this.pineCanopyUpper.castShadow = true;

    pineTransforms.forEach((t, i) => {
      dummy.position.set(t.x, t.y, t.z);
      dummy.rotation.set(0, t.yaw, 0);
      dummy.scale.setScalar(t.s);
      dummy.updateMatrix();
      this.pineTrunks.setMatrixAt(i, dummy.matrix);
      this.pineCanopyLower.setMatrixAt(i, dummy.matrix);
      this.pineCanopyUpper.setMatrixAt(i, dummy.matrix);
      color.setRGB(0.2 + t.tint * 0.1, 0.36 + t.tint * 0.14, 0.19 + t.tint * 0.08);
      this.pineCanopyLower.setColorAt(i, color);
      this.pineCanopyUpper.setColorAt(i, color);
    });
    this.group.add(this.pineTrunks, this.pineCanopyLower, this.pineCanopyUpper);

    // Volumetric Sun Shafts (16 soft slanted cones slicing through pines)
    const shaftGeo = new THREE.CylinderGeometry(0.45, 2.6, 26, 8, 1, true);
    shaftGeo.translate(0, 13, 0);
    let shaftsPlaced = 0;
    for (const t of pineTransforms) {
      if (shaftsPlaced >= 16) break;
      if (Math.abs(t.x) > 95 || Math.abs(t.z) > 95 || rng() > 0.14) continue;
      const shaft = new THREE.Mesh(shaftGeo, this.godRayMat);
      shaft.position.set(t.x + 1.5, t.y + 0.5, t.z + 1.5);
      shaft.rotation.set(0.32, 0.4, -0.26);
      this.godRayGroup.add(shaft);
      shaftsPlaced++;
    }

    // 2. Alpine Birch Trees (~120)
    const birchTransforms: { x: number; y: number; z: number; s: number; yaw: number; tint: number }[] = [];
    for (let attempt = 0; attempt < 1400 && birchTransforms.length < 120; attempt++) {
      const x = rng() * 320 - 160;
      const z = rng() * 320 - 160;
      const y = this.terrain.getHeightAt(x, z);
      if (y < 0.9 || y > 13.5 || this.terrain.trailFactor(x, z) > 0.4) continue;
      const s = 0.8 + rng() * 0.65;
      birchTransforms.push({ x, y: y - 0.1, z, s, yaw: rng() * Math.PI * 2, tint: rng() });
      this.treeColliders.push({ x, z, radius: 0.28 * s });
    }
    const birchTrunkGeo = new THREE.CylinderGeometry(0.1, 0.22, 6.2, 7);
    birchTrunkGeo.translate(0, 3.1, 0);
    this.birchTrunkMat = new THREE.MeshStandardMaterial({ color: 0xe6e1d5, roughness: 0.78 });
    this.birchTrunks = new THREE.InstancedMesh(birchTrunkGeo, this.birchTrunkMat, birchTransforms.length);
    this.birchTrunks.castShadow = true;

    const birchLeafGeo = new THREE.DodecahedronGeometry(2.0, 1);
    birchLeafGeo.scale(1.0, 1.35, 1.0);
    birchLeafGeo.translate(0, 6.2, 0);
    const birchLeafMat = new THREE.MeshStandardMaterial({ color: 0x7b983c, roughness: 0.82 });
    this.injectWindSway(birchLeafMat, 0.025);
    this.birchCanopies = new THREE.InstancedMesh(birchLeafGeo, birchLeafMat, birchTransforms.length);
    this.birchCanopies.castShadow = true;

    birchTransforms.forEach((t, i) => {
      dummy.position.set(t.x, t.y, t.z);
      dummy.rotation.set(0, t.yaw, 0);
      dummy.scale.setScalar(t.s);
      dummy.updateMatrix();
      this.birchTrunks.setMatrixAt(i, dummy.matrix);
      this.birchCanopies.setMatrixAt(i, dummy.matrix);
      color.setRGB(0.55 + t.tint * 0.25, 0.68 + t.tint * 0.18, 0.22 + t.tint * 0.1);
      this.birchCanopies.setColorAt(i, color);
    });
    this.group.add(this.birchTrunks, this.birchCanopies);

    // 3. Craggy Boulders (~95)
    const boulderTransforms: { x: number; y: number; z: number; sx: number; sy: number; sz: number; yaw: number }[] = [];
    for (let attempt = 0; attempt < 950 && boulderTransforms.length < 95; attempt++) {
      const x = rng() * 340 - 170;
      const z = rng() * 340 - 170;
      const y = this.terrain.getHeightAt(x, z);
      if (y < -0.6 || this.terrain.trailFactor(x, z) > 0.45) continue;
      const s = 0.65 + rng() * 1.45;
      boulderTransforms.push({
        x,
        y: y - 0.18,
        z,
        sx: s * (0.85 + rng() * 0.4),
        sy: s * (0.6 + rng() * 0.35),
        sz: s * (0.85 + rng() * 0.4),
        yaw: rng() * Math.PI * 2
      });
    }
    const boulderGeo = new THREE.DodecahedronGeometry(1.15, 1);
    this.rockMat = new THREE.MeshStandardMaterial({ color: 0x7c8287, roughness: 0.82, metalness: 0.05 });
    this.boulders = new THREE.InstancedMesh(boulderGeo, this.rockMat, boulderTransforms.length);
    this.boulders.castShadow = true;
    this.boulders.receiveShadow = true;
    boulderTransforms.forEach((b, i) => {
      dummy.position.set(b.x, b.y, b.z);
      dummy.rotation.set(rng() * 0.5, b.yaw, rng() * 0.5);
      dummy.scale.set(b.sx, b.sy, b.sz);
      dummy.updateMatrix();
      this.boulders.setMatrixAt(i, dummy.matrix);
    });
    this.group.add(this.boulders);

    // 4. Fallen Moss-Covered Logs (~55)
    const logTransforms: { x: number; y: number; z: number; s: number; yaw: number }[] = [];
    for (let attempt = 0; attempt < 600 && logTransforms.length < 55; attempt++) {
      const x = rng() * 310 - 155;
      const z = rng() * 310 - 155;
      const y = this.terrain.getHeightAt(x, z);
      if (y < 1.2 || y > 16 || this.terrain.trailFactor(x, z) > 0.35) continue;
      logTransforms.push({ x, y: y + 0.16, z, s: 0.85 + rng() * 0.75, yaw: rng() * Math.PI * 2 });
    }
    const logGeo = new THREE.CylinderGeometry(0.24, 0.3, 4.2, 7);
    logGeo.rotateZ(Math.PI * 0.5);
    const logMat = new THREE.MeshStandardMaterial({ color: 0x4b4a34, roughness: 0.92 });
    this.mossLogs = new THREE.InstancedMesh(logGeo, logMat, logTransforms.length);
    this.mossLogs.castShadow = true;
    this.mossLogs.receiveShadow = true;
    logTransforms.forEach((l, i) => {
      dummy.position.set(l.x, l.y, l.z);
      dummy.rotation.set(0, l.yaw, 0.04);
      dummy.scale.setScalar(l.s);
      dummy.updateMatrix();
      this.mossLogs.setMatrixAt(i, dummy.matrix);
    });
    this.group.add(this.mossLogs);

    // 5. Dense Fern Clusters / Stealth Brush (~340)
    const fernTransforms: { x: number; y: number; z: number; s: number; yaw: number }[] = [];
    for (let attempt = 0; attempt < 2200 && fernTransforms.length < 340; attempt++) {
      const x = rng() * 310 - 155;
      const z = rng() * 310 - 155;
      const y = this.terrain.getHeightAt(x, z);
      if (y < 0.7 || y > 17 || this.terrain.trailFactor(x, z) > 0.45) continue;
      fernTransforms.push({ x, y, z, s: 0.85 + rng() * 0.95, yaw: rng() * Math.PI * 2 });
      this.bushPositions.push(new THREE.Vector3(x, y, z));
    }
    this.maxFerns = fernTransforms.length;
    const fernGeo = new THREE.ConeGeometry(1.05, 0.85, 5, 1, true);
    fernGeo.scale(1, -1, 1);
    fernGeo.translate(0, 0.55, 0);
    this.fernMat = new THREE.MeshStandardMaterial({
      color: 0x3b6c2c,
      roughness: 0.85,
      side: THREE.DoubleSide
    });
    this.injectWindSway(this.fernMat, 0.14);
    this.ferns = new THREE.InstancedMesh(fernGeo, this.fernMat, fernTransforms.length);
    this.ferns.receiveShadow = true;
    fernTransforms.forEach((f, i) => {
      dummy.position.set(f.x, f.y, f.z);
      dummy.rotation.set(0, f.yaw, 0);
      dummy.scale.setScalar(f.s);
      dummy.updateMatrix();
      this.ferns.setMatrixAt(i, dummy.matrix);
    });
    this.group.add(this.ferns);

    // 6. Wind-Swaying Grass Tufts (~2400)
    const grassTransforms: { x: number; y: number; z: number; s: number; yaw: number; tint: number }[] = [];
    for (let attempt = 0; attempt < 8500 && grassTransforms.length < 2400; attempt++) {
      const x = rng() * 290 - 145;
      const z = rng() * 290 - 145;
      const y = this.terrain.getHeightAt(x, z);
      if (y < 0.8 || y > 16 || this.terrain.trailFactor(x, z) > 0.5) continue;
      grassTransforms.push({ x, y, z, s: 0.75 + rng() * 0.95, yaw: rng() * Math.PI * 2, tint: rng() });
    }
    this.maxGrass = grassTransforms.length;
    const grassGeo = new THREE.PlaneGeometry(0.45, 0.65);
    grassGeo.translate(0, 0.32, 0);
    this.grassMat = new THREE.MeshStandardMaterial({
      color: 0x4c6f2d,
      roughness: 0.88,
      side: THREE.DoubleSide
    });
    this.injectWindSway(this.grassMat, 0.24);
    this.grassBlades = new THREE.InstancedMesh(grassGeo, this.grassMat, grassTransforms.length);
    grassTransforms.forEach((g, i) => {
      dummy.position.set(g.x, g.y, g.z);
      dummy.rotation.set(0, g.yaw, 0);
      dummy.scale.setScalar(g.s);
      dummy.updateMatrix();
      this.grassBlades.setMatrixAt(i, dummy.matrix);
      color.setRGB(0.28 + g.tint * 0.14, 0.44 + g.tint * 0.18, 0.16 + g.tint * 0.08);
      this.grassBlades.setColorAt(i, color);
    });
    this.group.add(this.grassBlades);
  }

  applyPbrAtlas(atlas: PbrTextureAtlas): void {
    this.pineTrunkMat.map = atlas.pineBark.albedo;
    this.pineTrunkMat.normalMap = atlas.pineBark.normalMap;
    this.pineTrunkMat.roughnessMap = atlas.pineBark.roughnessMap;
    this.pineTrunkMat.color.setHex(0xffffff);
    this.pineTrunkMat.needsUpdate = true;

    this.birchTrunkMat.map = atlas.birchBark.albedo;
    this.birchTrunkMat.normalMap = atlas.birchBark.normalMap;
    this.birchTrunkMat.roughnessMap = atlas.birchBark.roughnessMap;
    this.birchTrunkMat.color.setHex(0xffffff);
    this.birchTrunkMat.needsUpdate = true;

    this.rockMat.map = atlas.alpineRock.albedo;
    this.rockMat.normalMap = atlas.alpineRock.normalMap;
    this.rockMat.roughnessMap = atlas.alpineRock.roughnessMap;
    this.rockMat.color.setHex(0xffffff);
    this.rockMat.needsUpdate = true;
  }

  setDensity(density: number, godRaysEnabled: boolean): void {
    this.ferns.count = Math.floor(this.maxFerns * density);
    this.grassBlades.count = Math.floor(this.maxGrass * density);
    this.godRayGroup.visible = godRaysEnabled;
  }

  isInBrushCover(px: number, pz: number): boolean {
    for (let i = 0; i < this.bushPositions.length; i++) {
      const b = this.bushPositions[i];
      const dx = b.x - px;
      const dz = b.z - pz;
      if (dx * dx + dz * dz < 4.8) return true;
    }
    return false;
  }

  update(
    dt: number,
    time: number,
    camPos: THREE.Vector3,
    weather: WeatherPreset,
    windX: number,
    windZ: number,
    godRaysEnabled: boolean
  ): void {
    this.windUniform.value = time;
    this.godRayGroup.visible =
      godRaysEnabled && (weather === 'GOLDEN_HOUR' || weather === 'MISTY_DAWN');
    this.godRayMat.color.setHex(weather === 'GOLDEN_HOUR' ? 0xffd185 : 0xc6e2f5);
    this.godRayMat.opacity = weather === 'GOLDEN_HOUR' ? 0.085 : 0.055;

    // Update atmospheric particles around player volume
    const range = weather === 'RAINY_STORM' ? 28 : 38;
    const half = range * 0.5;
    for (let i = 0; i < this.particleCount; i++) {
      const idx = i * 3;
      let px = this.particlePositions[idx];
      let py = this.particlePositions[idx + 1];
      let pz = this.particlePositions[idx + 2];
      const ph = this.particlePhases[i];

      if (weather === 'RAINY_STORM') {
        px += windX * 5.2 * dt;
        py -= 19.0 * dt;
        pz += windZ * 5.2 * dt;
      } else if (weather === 'MOONLIT_NIGHT') {
        px += Math.sin(time * 0.9 + ph) * 0.55 * dt;
        py += Math.cos(time * 1.1 + ph * 1.3) * 0.35 * dt;
        pz += Math.cos(time * 0.8 + ph) * 0.55 * dt;
      } else {
        px += (windX * 0.8 + Math.sin(time * 0.5 + ph) * 0.25) * dt;
        py -= (0.25 + 0.1 * Math.sin(time + ph)) * dt;
        pz += (windZ * 0.8 + Math.cos(time * 0.5 + ph) * 0.25) * dt;
      }

      while (px - camPos.x > half) px -= range;
      while (px - camPos.x < -half) px += range;
      while (pz - camPos.z > half) pz -= range;
      while (pz - camPos.z < -half) pz += range;

      const ground = this.terrain.getHeightAt(px, pz);
      if (py < ground + 0.35 || py > ground + 16) {
        py = ground + (weather === 'RAINY_STORM' ? 14 : 1.5 + (ph % 8));
      }

      this.particlePositions[idx] = px;
      this.particlePositions[idx + 1] = py;
      this.particlePositions[idx + 2] = pz;
    }

    if (weather === 'RAINY_STORM') {
      this.particleMat.color.setHex(0xb8cde0);
      this.particleMat.size = 0.14;
      this.particleMat.opacity = 0.55;
    } else if (weather === 'MOONLIT_NIGHT') {
      this.particleMat.color.setHex(0xc8ff6e);
      this.particleMat.size = 0.28;
      this.particleMat.opacity = 0.88;
    } else if (weather === 'MISTY_DAWN') {
      this.particleMat.color.setHex(0xd8ebf5);
      this.particleMat.size = 0.22;
      this.particleMat.opacity = 0.35;
    } else {
      this.particleMat.color.setHex(0xffdf8e);
      this.particleMat.size = 0.2;
      this.particleMat.opacity = 0.62;
    }

    const posAttr = this.particles.geometry.getAttribute('position') as THREE.BufferAttribute;
    posAttr.needsUpdate = true;
  }
}
