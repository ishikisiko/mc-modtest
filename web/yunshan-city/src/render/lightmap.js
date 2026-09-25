// Lantern light-pool map over the city: R = intensity, G = lantern height (normalised).
import * as THREE from 'three';

export const LIGHTMAP_BOUNDS = { x0: -400, z0: -580, x1: 400, z1: 500 };

export function buildLightMap(lights) {
  const B = LIGHTMAP_BOUNDS;
  const W = 800;
  const H = 1080;
  const sx = (B.x1 - B.x0) / W;
  const sz = (B.z1 - B.z0) / H;
  const inten = new Float32Array(W * H);
  const hsum = new Float32Array(W * H);
  for (const [x, y, z, r, k] of lights) {
    const ci = (x - B.x0) / sx;
    const cj = (z - B.z0) / sz;
    const ri = Math.ceil(r / sx);
    for (let j = Math.floor(cj - ri); j <= Math.ceil(cj + ri); j++) {
      if (j < 0 || j >= H) continue;
      for (let i = Math.floor(ci - ri); i <= Math.ceil(ci + ri); i++) {
        if (i < 0 || i >= W) continue;
        const dx = (i + 0.5 - ci) * sx;
        const dz = (j + 0.5 - cj) * sz;
        const d = Math.sqrt(dx * dx + dz * dz);
        if (d > r) continue;
        const f = (1 - d / r) * (1 - d / r) * k;
        inten[j * W + i] += f;
        hsum[j * W + i] += f * y;
      }
    }
  }
  const data = new Uint8Array(W * H * 4);
  for (let p = 0; p < W * H; p++) {
    const I = inten[p];
    const y = I > 0 ? hsum[p] / I : 0;
    data[p * 4] = Math.min(255, Math.round((1 - Math.exp(-I * 1.2)) * 255));
    data[p * 4 + 1] = Math.min(255, Math.max(0, Math.round(((y + 20) / 400) * 255)));
    data[p * 4 + 2] = 0;
    data[p * 4 + 3] = 255;
  }
  const tex = new THREE.DataTexture(data, W, H, THREE.RGBAFormat, THREE.UnsignedByteType);
  tex.magFilter = THREE.LinearFilter;
  tex.minFilter = THREE.LinearFilter;
  tex.needsUpdate = true;
  const bounds = new THREE.Vector4(B.x0, B.z0, 1 / (B.x1 - B.x0), 1 / (B.z1 - B.z0));
  return { tex, bounds };
}
