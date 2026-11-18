import * as THREE from 'three';
import { SurfaceType } from '../types';
import { clamp, fbm2D, lerp, ridgedFbm2D, smoothstep } from './Noise';
import { PbrTextureSet } from './TextureSynthesizer';

export class Terrain {
  static readonly WORLD_SIZE = 420;
  static readonly HALF_SIZE = Terrain.WORLD_SIZE * 0.5;
  static readonly GRID_RES = 144;
  static readonly WATER_LEVEL = 0.0;
  static readonly LAKE_CENTER_X = -18;
  static readonly LAKE_CENTER_Z = -22;
  static readonly LAKE_RADIUS_X = 56;
  static readonly LAKE_RADIUS_Z = 42;

  private readonly stride = Terrain.GRID_RES + 1;
  private readonly step = Terrain.WORLD_SIZE / Terrain.GRID_RES;
  private readonly heights: Float32Array;
  readonly mesh: THREE.Mesh;
  readonly material: THREE.MeshStandardMaterial;

  constructor() {
    this.heights = new Float32Array(this.stride * this.stride);
    this.generateHeightfield();

    const geometry = this.buildGeometry();
    this.material = new THREE.MeshStandardMaterial({
      vertexColors: true,
      roughness: 0.88,
      metalness: 0.04,
      normalScale: new THREE.Vector2(1.15, 1.15)
    });
    this.mesh = new THREE.Mesh(geometry, this.material);
    this.mesh.receiveShadow = true;
    this.mesh.castShadow = false;
  }

  applyPbrTextures(floorTex: PbrTextureSet): void {
    this.material.map = floorTex.albedo;
    this.material.normalMap = floorTex.normalMap;
    this.material.roughnessMap = floorTex.roughnessMap;
    this.material.needsUpdate = true;
  }

  setWetness(wetness: number): void {
    this.material.roughness = lerp(0.9, 0.32, clamp(wetness, 0, 1));
    this.material.needsUpdate = true;
  }

  private generateHeightfield(): void {
    for (let gz = 0; gz <= Terrain.GRID_RES; gz++) {
      const z = -Terrain.HALF_SIZE + gz * this.step;
      for (let gx = 0; gx <= Terrain.GRID_RES; gx++) {
        const x = -Terrain.HALF_SIZE + gx * this.step;
        this.heights[gz * this.stride + gx] = this.computeRawHeight(x, z);
      }
    }
  }

  private computeRawHeight(x: number, z: number): number {
    const baseHills = (fbm2D(x * 0.0085 + 12.3, z * 0.0085 - 7.1, 5, 42) - 0.42) * 24;
    const ridges = ridgedFbm2D(x * 0.0065 - 3.4, z * 0.0065 + 9.2, 4, 88) * 18;
    const edgeDist = Math.max(Math.abs(x), Math.abs(z)) / Terrain.HALF_SIZE;
    const rimBoost = smoothstep(0.55, 0.98, edgeDist) * 34;

    const lx = (x - Terrain.LAKE_CENTER_X) / Terrain.LAKE_RADIUS_X;
    const lz = (z - Terrain.LAKE_CENTER_Z) / Terrain.LAKE_RADIUS_Z;
    const lakeNoise = (fbm2D(x * 0.025, z * 0.025, 3, 19) - 0.5) * 0.28;
    const lakeDist = Math.hypot(lx, lz) + lakeNoise;
    const lakeCarve = (1 - smoothstep(0.32, 1.18, lakeDist)) * 16.5;

    const cx = x - 52;
    const cz = z - 35;
    const clearing = Math.exp(-(cx * cx + cz * cz) / (46 * 46)) * 4.5;

    let h =
      5.4 +
      baseHills +
      ridges * (0.28 + 0.72 * smoothstep(0.28, 0.85, edgeDist)) +
      rimBoost -
      lakeCarve -
      clearing * 0.45;
    const trail = this.trailFactor(x, z);
    if (trail > 0.01 && h > 0.9) {
      h -= trail * 0.32;
    }
    return h;
  }

  trailFactor(x: number, z: number): number {
    const centerLineX = 44 + Math.sin(z * 0.022) * 28 + Math.cos(z * 0.011) * 14;
    const dist = Math.abs(x - centerLineX);
    return 1 - smoothstep(2.2, 6.2, dist);
  }

  getHeightAt(x: number, z: number): number {
    const fx = clamp((x + Terrain.HALF_SIZE) / this.step, 0, Terrain.GRID_RES - 0.001);
    const fz = clamp((z + Terrain.HALF_SIZE) / this.step, 0, Terrain.GRID_RES - 0.001);
    const ix = Math.floor(fx);
    const iz = Math.floor(fz);
    const tx = fx - ix;
    const tz = fz - iz;
    const h00 = this.heights[iz * this.stride + ix];
    const h10 = this.heights[iz * this.stride + ix + 1];
    const h01 = this.heights[(iz + 1) * this.stride + ix];
    const h11 = this.heights[(iz + 1) * this.stride + ix + 1];
    return lerp(lerp(h00, h10, tx), lerp(h01, h11, tx), tz);
  }

  getNormalAt(x: number, z: number, out = new THREE.Vector3()): THREE.Vector3 {
    const eps = 1.4;
    const hl = this.getHeightAt(x - eps, z);
    const hr = this.getHeightAt(x + eps, z);
    const hd = this.getHeightAt(x, z - eps);
    const hu = this.getHeightAt(x, z + eps);
    return out.set(hl - hr, 2 * eps, hd - hu).normalize();
  }

  getSurfaceType(x: number, z: number): SurfaceType {
    const h = this.getHeightAt(x, z);
    if (h < Terrain.WATER_LEVEL + 0.35) return 'SHALLOW_WATER';
    const n = this.getNormalAt(x, z);
    if (n.y < 0.78 || h > 22) return 'ROCK';
    if (this.trailFactor(x, z) > 0.38 || h < 1.6) return 'DIRT';
    return 'GRASS';
  }

  private buildGeometry(): THREE.BufferGeometry {
    const vertCount = this.stride * this.stride;
    const positions = new Float32Array(vertCount * 3);
    const normals = new Float32Array(vertCount * 3);
    const uvs = new Float32Array(vertCount * 2);
    const colors = new Float32Array(vertCount * 3);
    const norm = new THREE.Vector3();

    let p = 0;
    let u = 0;
    for (let gz = 0; gz <= Terrain.GRID_RES; gz++) {
      const z = -Terrain.HALF_SIZE + gz * this.step;
      for (let gx = 0; gx <= Terrain.GRID_RES; gx++) {
        const x = -Terrain.HALF_SIZE + gx * this.step;
        const y = this.heights[gz * this.stride + gx];
        this.getNormalAt(x, z, norm);

        const slope = 1 - norm.y;
        const rockW = clamp(smoothstep(0.16, 0.38, slope) + smoothstep(21, 34, y) * 0.85, 0, 1);
        const shoreMud = 1 - smoothstep(-0.2, 2.2, y);
        const trailDirt = this.trailFactor(x, z) * smoothstep(0.4, 1.8, y);
        const mudW = clamp(shoreMud + trailDirt * 0.85, 0, 1);
        const grassW = clamp(1 - rockW - mudW * 0.78, 0.05, 1);
        const total = grassW + mudW + rockW;
        const ao = clamp(0.72 + (y / 30) * 0.22 - slope * 0.3, 0.45, 1.0);

        // Blend alpine meadow moss, damp trail/shoreline mud, and granite cliffs
        const r = ((grassW * 0.42 + mudW * 0.36 + rockW * 0.56) / total) * ao;
        const g = ((grassW * 0.58 + mudW * 0.28 + rockW * 0.58) / total) * ao;
        const b = ((grassW * 0.31 + mudW * 0.19 + rockW * 0.62) / total) * ao;

        positions[p] = x;
        positions[p + 1] = y;
        positions[p + 2] = z;

        normals[p] = norm.x;
        normals[p + 1] = norm.y;
        normals[p + 2] = norm.z;

        colors[p] = r;
        colors[p + 1] = g;
        colors[p + 2] = b;
        p += 3;

        uvs[u++] = gx / Terrain.GRID_RES;
        uvs[u++] = gz / Terrain.GRID_RES;
      }
    }

    const indices = new Uint32Array(Terrain.GRID_RES * Terrain.GRID_RES * 6);
    let idx = 0;
    for (let gz = 0; gz < Terrain.GRID_RES; gz++) {
      for (let gx = 0; gx < Terrain.GRID_RES; gx++) {
        const i0 = gz * this.stride + gx;
        const i1 = i0 + 1;
        const i2 = i0 + this.stride;
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
    geo.setAttribute('normal', new THREE.BufferAttribute(normals, 3));
    geo.setAttribute('uv', new THREE.BufferAttribute(uvs, 2));
    geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    geo.setIndex(new THREE.BufferAttribute(indices, 1));
    return geo;
  }
}
