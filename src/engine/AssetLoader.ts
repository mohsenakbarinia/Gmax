import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { RGBELoader } from 'three/examples/jsm/loaders/RGBELoader.js';

export type DropInModelKey = 'stag' | 'boar' | 'wolf' | 'rifle' | 'bow';

/**
 * Hybrid Asset Drop-In Pipeline.
 * Silently checks local `./assets/models/<name>.glb` and `./assets/hdri/forest.hdr`
 * using safe HEAD probes so the app runs 100% cleanly on GitHub Pages with procedural assets
 * while automatically upgrading whenever real photogrammetry `.glb` or `.hdr` files are added.
 */
export class AssetLoader {
  private readonly gltfLoader = new GLTFLoader();
  private readonly rgbeLoader = new RGBELoader();
  private readonly cachedModels = new Map<DropInModelKey, THREE.Group | null>();

  private async assetExists(relativeUrl: string): Promise<boolean> {
    try {
      const response = await fetch(relativeUrl, { method: 'HEAD', cache: 'no-cache' });
      if (!response.ok) return false;
      const contentType = response.headers.get('content-type') || '';
      // Avoid GitHub Pages / Vite HTML fallback pages masquerading as binary assets
      if (contentType.includes('text/html')) return false;
      return true;
    } catch {
      return false;
    }
  }

  async tryLoadModel(key: DropInModelKey): Promise<THREE.Group | null> {
    if (this.cachedModels.has(key)) {
      const cached = this.cachedModels.get(key);
      return cached ? cached.clone(true) : null;
    }
    const url = `./assets/models/${key}.glb`;
    const exists = await this.assetExists(url);
    if (!exists) {
      this.cachedModels.set(key, null);
      return null;
    }
    try {
      const gltf = await this.gltfLoader.loadAsync(url);
      gltf.scene.traverse((obj) => {
        if ((obj as THREE.Mesh).isMesh) {
          obj.castShadow = true;
          obj.receiveShadow = true;
        }
      });
      this.cachedModels.set(key, gltf.scene);
      return gltf.scene.clone(true);
    } catch {
      this.cachedModels.set(key, null);
      return null;
    }
  }

  async tryLoadHdri(relativeUrl = './assets/hdri/forest.hdr'): Promise<THREE.DataTexture | null> {
    const exists = await this.assetExists(relativeUrl);
    if (!exists) return null;
    try {
      const tex = await this.rgbeLoader.loadAsync(relativeUrl);
      tex.mapping = THREE.EquirectangularReflectionMapping;
      return tex;
    } catch {
      return null;
    }
  }
}
