// Deterministic hashing, RNG and 2D simplex noise shared by the main thread and workers.

export function hash2(x, z, seed = 0) {
  // 32-bit integer hash of two integers (xxhash-style avalanche).
  let h = (seed ^ 0x9e3779b9) | 0;
  h = Math.imul(h ^ (x | 0), 0x85ebca6b);
  h = (h << 13) | (h >>> 19);
  h = Math.imul(h ^ (z | 0), 0xc2b2ae35);
  h ^= h >>> 16;
  h = Math.imul(h, 0x7feb352d);
  h ^= h >>> 15;
  h = Math.imul(h, 0x846ca68b);
  h ^= h >>> 16;
  return h >>> 0;
}

export function hash3(x, y, z, seed = 0) {
  return hash2(hash2(x, y, seed), z, seed + 17);
}

export function rand2(x, z, seed = 0) {
  return hash2(x, z, seed) / 4294967296;
}

export function rand3(x, y, z, seed = 0) {
  return hash3(x, y, z, seed) / 4294967296;
}

// mulberry32 PRNG
export class Rng {
  constructor(seed) {
    this.s = seed >>> 0;
  }
  next() {
    let t = (this.s = (this.s + 0x6d2b79f5) >>> 0);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }
  range(a, b) {
    return a + (b - a) * this.next();
  }
  int(a, b) {
    // inclusive a, exclusive b
    return a + Math.floor((b - a) * this.next());
  }
  pick(arr) {
    return arr[Math.floor(this.next() * arr.length)];
  }
  chance(p) {
    return this.next() < p;
  }
}

const F2 = 0.5 * (Math.sqrt(3) - 1);
const G2 = (3 - Math.sqrt(3)) / 6;
const GX = new Float64Array([1, -1, 1, -1, 1, -1, 0, 0, 0.7071, -0.7071, 0.7071, -0.7071]);
const GZ = new Float64Array([1, 1, -1, -1, 0, 0, 1, -1, 0.7071, 0.7071, -0.7071, -0.7071]);

export function makeSimplex2(seed) {
  const rng = new Rng(seed);
  const p = new Uint8Array(256);
  for (let i = 0; i < 256; i++) p[i] = i;
  for (let i = 255; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1));
    const t = p[i];
    p[i] = p[j];
    p[j] = t;
  }
  const perm = new Uint8Array(512);
  const permMod = new Uint8Array(512);
  for (let i = 0; i < 512; i++) {
    perm[i] = p[i & 255];
    permMod[i] = perm[i] % 12;
  }
  return function simplex2(xin, yin) {
    const s = (xin + yin) * F2;
    const i = Math.floor(xin + s);
    const j = Math.floor(yin + s);
    const t = (i + j) * G2;
    const x0 = xin - (i - t);
    const y0 = yin - (j - t);
    let i1, j1;
    if (x0 > y0) {
      i1 = 1;
      j1 = 0;
    } else {
      i1 = 0;
      j1 = 1;
    }
    const x1 = x0 - i1 + G2;
    const y1 = y0 - j1 + G2;
    const x2 = x0 - 1 + 2 * G2;
    const y2 = y0 - 1 + 2 * G2;
    const ii = i & 255;
    const jj = j & 255;
    let n = 0;
    let t0 = 0.5 - x0 * x0 - y0 * y0;
    if (t0 > 0) {
      const g = permMod[ii + perm[jj]];
      t0 *= t0;
      n += t0 * t0 * (GX[g] * x0 + GZ[g] * y0);
    }
    let t1 = 0.5 - x1 * x1 - y1 * y1;
    if (t1 > 0) {
      const g = permMod[ii + i1 + perm[jj + j1]];
      t1 *= t1;
      n += t1 * t1 * (GX[g] * x1 + GZ[g] * y1);
    }
    let t2 = 0.5 - x2 * x2 - y2 * y2;
    if (t2 > 0) {
      const g = permMod[ii + 1 + perm[jj + 1]];
      t2 *= t2;
      n += t2 * t2 * (GX[g] * x2 + GZ[g] * y2);
    }
    return 70 * n;
  };
}

export function smoothstep(a, b, x) {
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

export function clamp(x, a, b) {
  return x < a ? a : x > b ? b : x;
}

export function lerp(a, b, t) {
  return a + (b - a) * t;
}
