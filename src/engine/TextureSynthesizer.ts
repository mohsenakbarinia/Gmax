import * as THREE from 'three';
import { clamp, lerp, smoothstep, tileableFbm } from './Noise';

export type PbrMaterialKey =
  | 'pineBark'
  | 'birchBark'
  | 'forestFloor'
  | 'alpineRock'
  | 'walnutWood'
  | 'gunmetal'
  | 'animalFur'
  | 'antlerBone';

export interface PbrTextureSet {
  albedo: THREE.CanvasTexture;
  normalMap: THREE.CanvasTexture;
  roughnessMap: THREE.CanvasTexture;
}

export type PbrTextureAtlas = Record<PbrMaterialKey, PbrTextureSet>;

const TEX_SIZE = 512;

function configureTexture(tex: THREE.CanvasTexture, isColor: boolean, repeat = 1): THREE.CanvasTexture {
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(repeat, repeat);
  tex.anisotropy = 4;
  tex.colorSpace = isColor ? THREE.SRGBColorSpace : THREE.NoColorSpace;
  tex.needsUpdate = true;
  return tex;
}

function synthesizeMaterial(key: PbrMaterialKey): PbrTextureSet {
  const w = TEX_SIZE;
  const h = TEX_SIZE;
  const count = w * h;

  const albedoCanvas = document.createElement('canvas');
  albedoCanvas.width = w;
  albedoCanvas.height = h;
  const albedoCtx = albedoCanvas.getContext('2d')!;
  const albedoImg = albedoCtx.createImageData(w, h);

  const roughCanvas = document.createElement('canvas');
  roughCanvas.width = w;
  roughCanvas.height = h;
  const roughCtx = roughCanvas.getContext('2d')!;
  const roughImg = roughCtx.createImageData(w, h);

  const normalCanvas = document.createElement('canvas');
  normalCanvas.width = w;
  normalCanvas.height = h;
  const normalCtx = normalCanvas.getContext('2d')!;
  const normalImg = normalCtx.createImageData(w, h);

  const heightField = new Float32Array(count);
  const invW = 1 / w;
  const invH = 1 / h;

  for (let y = 0; y < h; y++) {
    const v = y * invH;
    const row = y * w;
    for (let x = 0; x < w; x++) {
      const u = x * invW;
      const idx = row + x;
      let r = 0.5;
      let g = 0.5;
      let b = 0.5;
      let ht = 0.5;
      let rough = 0.78;

      switch (key) {
        case 'pineBark': {
          const ridge = tileableFbm(u, v, 14, 4, 4, 11);
          const fine = tileableFbm(u, v, 32, 16, 3, 23);
          const crack = smoothstep(0.28, 0.56, ridge);
          ht = crack * 0.78 + fine * 0.22;
          r = lerp(0.14, 0.34, ht);
          g = lerp(0.09, 0.23, ht);
          b = lerp(0.06, 0.15, ht);
          const lichen = smoothstep(0.72, 0.88, fine);
          r = lerp(r, 0.28, lichen * 0.35);
          g = lerp(g, 0.34, lichen * 0.35);
          b = lerp(b, 0.22, lichen * 0.35);
          rough = clamp(0.92 - ht * 0.12, 0.72, 0.96);
          break;
        }
        case 'birchBark': {
          const paper = tileableFbm(u, v, 6, 12, 4, 41);
          const lenticel = tileableFbm(u, v, 4, 36, 3, 53);
          const darkBand = smoothstep(0.66, 0.8, lenticel);
          ht = 0.7 + paper * 0.2 - darkBand * 0.45;
          r = lerp(0.84, 0.16, darkBand) * (0.9 + paper * 0.12);
          g = lerp(0.82, 0.14, darkBand) * (0.9 + paper * 0.12);
          b = lerp(0.76, 0.12, darkBand) * (0.9 + paper * 0.12);
          rough = lerp(0.68, 0.9, darkBand);
          break;
        }
        case 'forestFloor': {
          const moss = tileableFbm(u, v, 6, 6, 4, 67);
          const needles = tileableFbm(u, v, 28, 28, 3, 79);
          const soil = tileableFbm(u, v, 12, 12, 3, 83);
          const mossMask = smoothstep(0.42, 0.68, moss);
          ht = soil * 0.5 + needles * 0.35 + mossMask * 0.15;
          const soilR = 0.23 * (0.75 + needles * 0.5);
          const soilG = 0.18 * (0.75 + needles * 0.5);
          const soilB = 0.11 * (0.75 + needles * 0.45);
          const mossR = 0.17 + needles * 0.1;
          const mossG = 0.3 + needles * 0.16;
          const mossB = 0.11 + needles * 0.06;
          r = lerp(soilR, mossR, mossMask);
          g = lerp(soilG, mossG, mossMask);
          b = lerp(soilB, mossB, mossMask);
          rough = lerp(0.88, 0.76, mossMask);
          break;
        }
        case 'alpineRock': {
          const macro = tileableFbm(u, v, 5, 5, 4, 101);
          const strata = Math.abs(Math.sin((v * 10 + macro * 2.4) * Math.PI));
          const grit = tileableFbm(u, v, 24, 24, 3, 113);
          ht = macro * 0.55 + strata * 0.25 + grit * 0.2;
          const shade = 0.24 + ht * 0.38;
          r = shade * 0.96;
          g = shade * 0.98;
          b = shade * 1.03;
          const frost = smoothstep(0.76, 0.92, ht);
          r = lerp(r, 0.64, frost * 0.3);
          g = lerp(g, 0.66, frost * 0.3);
          b = lerp(b, 0.68, frost * 0.3);
          rough = clamp(0.86 - strata * 0.2, 0.56, 0.92);
          break;
        }
        case 'walnutWood': {
          const warp = tileableFbm(u, v, 4, 12, 4, 131);
          const rings = 0.5 + 0.5 * Math.sin((u * 18 + warp * 3.8) * Math.PI * 2);
          const pore = tileableFbm(u, v, 32, 8, 2, 139);
          ht = rings * 0.65 + pore * 0.35;
          r = lerp(0.2, 0.45, rings) * (0.9 + pore * 0.16);
          g = lerp(0.1, 0.25, rings) * (0.9 + pore * 0.16);
          b = lerp(0.05, 0.12, rings) * (0.9 + pore * 0.16);
          rough = lerp(0.32, 0.48, 1 - rings);
          break;
        }
        case 'gunmetal': {
          const brush = tileableFbm(u, v, 4, 48, 3, 151);
          const micro = tileableFbm(u, v, 24, 24, 2, 163);
          ht = brush * 0.7 + micro * 0.3;
          const base = 0.15 + ht * 0.11;
          r = base * 0.92;
          g = base * 0.97;
          b = base * 1.12;
          rough = clamp(0.22 + (1 - brush) * 0.16, 0.18, 0.4);
          break;
        }
        case 'animalFur': {
          const strand = tileableFbm(u, v, 24, 8, 4, 173);
          const patch = tileableFbm(u, v, 5, 5, 3, 181);
          ht = strand * 0.75 + patch * 0.25;
          const shade = 0.58 + ht * 0.42;
          r = shade;
          g = shade * 0.96;
          b = shade * 0.9;
          rough = clamp(0.72 + (1 - strand) * 0.2, 0.65, 0.94);
          break;
        }
        case 'antlerBone': {
          const flutes = tileableFbm(u, v, 18, 4, 3, 193);
          const grain = tileableFbm(u, v, 10, 10, 3, 199);
          ht = flutes * 0.65 + grain * 0.35;
          r = lerp(0.58, 0.88, ht);
          g = lerp(0.5, 0.82, ht);
          b = lerp(0.38, 0.68, ht);
          rough = lerp(0.62, 0.38, ht);
          break;
        }
      }

      heightField[idx] = clamp(ht, 0, 1);
      const bi = idx * 4;
      albedoImg.data[bi] = Math.round(clamp(r, 0, 1) * 255);
      albedoImg.data[bi + 1] = Math.round(clamp(g, 0, 1) * 255);
      albedoImg.data[bi + 2] = Math.round(clamp(b, 0, 1) * 255);
      albedoImg.data[bi + 3] = 255;

      const rv = Math.round(clamp(rough, 0.05, 0.98) * 255);
      roughImg.data[bi] = rv;
      roughImg.data[bi + 1] = rv;
      roughImg.data[bi + 2] = rv;
      roughImg.data[bi + 3] = 255;
    }
  }

  // Sobel 3x3 height-to-normal filter
  const normalStrength =
    key === 'pineBark'
      ? 3.4
      : key === 'alpineRock'
        ? 3.2
        : key === 'forestFloor'
          ? 2.4
          : key === 'gunmetal'
            ? 0.8
            : key === 'walnutWood'
              ? 1.1
              : 2.0;

  for (let y = 0; y < h; y++) {
    const ym = (y - 1 + h) % h;
    const yp = (y + 1) % h;
    for (let x = 0; x < w; x++) {
      const xm = (x - 1 + w) % w;
      const xp = (x + 1) % w;

      const tl = heightField[ym * w + xm];
      const tc = heightField[ym * w + x];
      const tr = heightField[ym * w + xp];
      const ml = heightField[y * w + xm];
      const mr = heightField[y * w + xp];
      const bl = heightField[yp * w + xm];
      const bc = heightField[yp * w + x];
      const br = heightField[yp * w + xp];

      const dx = tr + 2 * mr + br - (tl + 2 * ml + bl);
      const dy = bl + 2 * bc + br - (tl + 2 * tc + tr);

      let nx = -dx * normalStrength;
      let ny = -dy * normalStrength;
      let nz = 1;
      const len = Math.hypot(nx, ny, nz);
      nx /= len;
      ny /= len;
      nz /= len;

      const bi = (y * w + x) * 4;
      normalImg.data[bi] = Math.round(clamp(nx * 0.5 + 0.5, 0, 1) * 255);
      normalImg.data[bi + 1] = Math.round(clamp(ny * 0.5 + 0.5, 0, 1) * 255);
      normalImg.data[bi + 2] = Math.round(clamp(nz * 0.5 + 0.5, 0, 1) * 255);
      normalImg.data[bi + 3] = 255;
    }
  }

  albedoCtx.putImageData(albedoImg, 0, 0);
  roughCtx.putImageData(roughImg, 0, 0);
  normalCtx.putImageData(normalImg, 0, 0);

  const rep = key === 'forestFloor' ? 32 : key === 'pineBark' ? 2 : 1;
  return {
    albedo: configureTexture(new THREE.CanvasTexture(albedoCanvas), true, rep),
    normalMap: configureTexture(new THREE.CanvasTexture(normalCanvas), false, rep),
    roughnessMap: configureTexture(new THREE.CanvasTexture(roughCanvas), false, rep)
  };
}

/**
 * Generates all 8 paired 512x512 procedural PBR texture sets asynchronously,
 * yielding between materials so the loading progress bar animates smoothly at 60 FPS.
 */
export async function synthesizeAllPbrTextures(
  onProgress: (progress: number) => void
): Promise<PbrTextureAtlas> {
  const keys: PbrMaterialKey[] = [
    'forestFloor',
    'alpineRock',
    'pineBark',
    'birchBark',
    'walnutWood',
    'gunmetal',
    'animalFur',
    'antlerBone'
  ];
  const atlas = {} as PbrTextureAtlas;
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    atlas[key] = synthesizeMaterial(key);
    onProgress((i + 1) / keys.length);
    await new Promise<void>((resolve) => setTimeout(resolve, 16));
  }
  return atlas;
}
