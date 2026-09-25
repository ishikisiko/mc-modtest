// Voxel trees: 迎客松-style layered pines, cypress, broadleaf, blossom, maple, willow, bamboo.
import { VoxelModel } from './model.js';
import { PAL } from './palette.js';
import { Rng, hash3 } from '../world/noise.js';

function holes(seed, amount) {
  return (x, y, z, d) => {
    if (d < 0.45) return 0;
    const h = hash3(x, y, z, seed) / 4294967296;
    return h < amount ? 0.6 : (h - 0.5) * 0.25;
  };
}

function layered(r, dark, mid, light, cy, ry) {
  return (x, y, z) => {
    const t = (y + 0.5 - cy) / ry;
    if (t > 0.35) return light;
    if (t < -0.35) return dark;
    return r.next() < 0.18 ? light : mid;
  };
}

function trunk(M, r, H, base, taper, leanAmt) {
  let x = 0;
  let z = 0;
  const lx = r.range(-leanAmt, leanAmt);
  const lz = r.range(-leanAmt, leanAmt);
  const pts = [];
  for (let y = 0; y < H; y++) {
    x += lx + r.range(-0.18, 0.18);
    z += lz + r.range(-0.18, 0.18);
    pts.push([x, y, z]);
    const rad = base - (base - taper) * (y / H);
    const R = Math.ceil(rad);
    for (let dz = -R; dz <= R; dz++)
      for (let dx = -R; dx <= R; dx++) if (dx * dx + dz * dz <= rad * rad + 0.2) M.set(Math.round(x) + dx, y, Math.round(z) + dz, y < 2 ? PAL.BARK : PAL.BARK);
  }
  // root flare
  for (const [dx, dz] of [
    [2, 0],
    [-2, 0],
    [0, 2],
    [0, -2],
  ])
    M.set(dx, 0, dz, PAL.BARK);
  return pts;
}

function pine(M, r, s = 1) {
  const H = Math.round(r.int(40, 64) * s);
  const pts = trunk(M, r, H, 1.7 * s + 0.3, 0.6, 0.22);
  const seed = r.int(1, 1e9);
  const nb = r.int(4, 7);
  for (let i = 0; i < nb; i++) {
    const t = 0.42 + (0.5 * i) / nb + r.range(-0.04, 0.04);
    const p = pts[Math.min(H - 1, Math.floor(t * H))];
    const ang = i * 2.4 + r.range(-0.5, 0.5);
    const len = ((1 - t) * 20 + 5) * s;
    const ex = p[0] + Math.cos(ang) * len;
    const ez = p[2] + Math.sin(ang) * len;
    const ey = p[1] + len * r.range(0.08, 0.3);
    M.line(p[0], p[1], p[2], ex, ey, ez, 0.7, PAL.BARK);
    const rx = (5 + len * 0.32) * r.range(0.9, 1.15);
    const rz = rx * r.range(0.7, 0.95);
    const ry = 2.3 * s + 0.4;
    M.ellipsoid(ex, ey + 1, ez, rx, ry, rz, layered(r, PAL.PINE_D, PAL.PINE, PAL.PINE_L, ey + 1, ry), holes(seed + i, 0.12));
  }
  const top = pts[H - 1];
  M.ellipsoid(top[0], top[1] + 1, top[2], 7 * s + 1, 2.6 * s + 0.4, 6 * s + 1, layered(r, PAL.PINE_D, PAL.PINE, PAL.PINE_L, top[1] + 1, 2.6), holes(seed + 99, 0.1));
}

function cypress(M, r, s = 1) {
  const H = Math.round(r.int(38, 56) * s);
  trunk(M, r, Math.round(H * 0.3), 1.1, 0.8, 0.05);
  const R = r.range(7, 9) * s;
  const seed = r.int(1, 1e9);
  for (let y = 4; y < H; y++) {
    const t = (y - 4) / (H - 4);
    const rad = R * Math.pow(1 - t, 0.8) * Math.min(1, (t + 0.12) * 3.5) * (0.92 + 0.08 * Math.sin(t * 23 + seed));
    const Rr = Math.ceil(rad);
    for (let dz = -Rr; dz <= Rr; dz++)
      for (let dx = -Rr; dx <= Rr; dx++) {
        const d = (dx * dx + dz * dz) / (rad * rad + 0.01);
        if (d > 1) continue;
        if (d > 0.5 && hash3(dx, y, dz, seed) / 4294967296 < 0.2) continue;
        M.set(dx, y, dz, d < 0.3 ? PAL.PINE_D : hash3(dx, y, dz, seed + 1) / 4294967296 < 0.25 ? PAL.PINE : PAL.CYPRESS);
      }
  }
}

function broad(M, r, s, mats) {
  const H = Math.round(r.int(26, 40) * s);
  const pts = trunk(M, r, H, 2.4 * s + 0.3, 1.1, 0.12);
  const top = pts[H - 1];
  const seed = r.int(1, 1e9);
  const R0 = r.range(14, 19) * s;
  const cx0 = top[0];
  const cz0 = top[2];
  const cy0 = top[1] + R0 * 0.25;
  // main crown mass
  M.ellipsoid(cx0, cy0, cz0, R0 * 0.62, R0 * 0.55, R0 * 0.62, layered(r, mats[0], mats[1], mats[2], cy0, R0 * 0.55), holes(seed, 0.1), true);
  const n = r.int(6, 10);
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 + r.range(-0.4, 0.4);
    const el = r.range(-0.35, 0.75);
    const dr = R0 * r.range(0.45, 0.72);
    const cx = cx0 + Math.cos(a) * Math.cos(el) * dr;
    const cz = cz0 + Math.sin(a) * Math.cos(el) * dr;
    const cy = cy0 + Math.sin(el) * dr * 0.7;
    if (el < 0.2) M.line(top[0], top[1] - 4, top[2], cx, cy - 2, cz, 1.0, PAL.BARK);
    const rr = R0 * r.range(0.38, 0.55);
    M.ellipsoid(cx, cy, cz, rr, rr * 0.82, rr, layered(r, mats[0], mats[1], mats[2], cy, rr * 0.82), holes(seed + i, 0.12), true);
  }
}

function willow(M, r, s = 1) {
  const H = Math.round(r.int(26, 36) * s);
  const pts = trunk(M, r, H, 2.2 * s, 1.1, 0.25);
  const top = pts[H - 1];
  const seed = r.int(1, 1e9);
  const R = r.range(15, 19) * s;
  const Ry = R * 0.42;
  M.ellipsoid(top[0], top[1] + 3, top[2], R * 0.8, Ry, R * 0.8, layered(r, PAL.WILLOW_D, PAL.WILLOW, PAL.WILLOW, top[1] + 3, Ry), holes(seed, 0.15));
  // hanging strands from the crown surface, longer toward the rim
  const n = Math.round(300 * s);
  for (let i = 0; i < n; i++) {
    const a = r.range(0, Math.PI * 2);
    const u = Math.sqrt(r.range(0.1, 1));
    const rr = R * u;
    const x = Math.round(top[0] + Math.cos(a) * rr);
    const z = Math.round(top[2] + Math.sin(a) * rr);
    const q = Math.min(1, u / 0.8);
    const y0 = Math.round(top[1] + 3 + Ry * Math.sqrt(Math.max(0, 1 - q * q)) * 0.6);
    const len = Math.round(r.range(0.35, 1) * (8 + 26 * u) * s);
    for (let k = 0; k < len; k++) {
      // arch outward first, then fall
      const out = Math.min(k, 4) * 0.55 * u;
      M.setIfEmpty(Math.round(x + Math.cos(a) * out), y0 - k + Math.min(k, 2), Math.round(z + Math.sin(a) * out), k % 5 === 0 ? PAL.WILLOW_D : PAL.WILLOW);
    }
  }
}

function bamboo(M, r, s = 1) {
  const n = r.int(9, 15);
  const seed = r.int(1, 1e9);
  for (let i = 0; i < n; i++) {
    const a = r.range(0, Math.PI * 2);
    const rr = r.range(0, 6) * s;
    const x = Math.round(Math.cos(a) * rr);
    const z = Math.round(Math.sin(a) * rr);
    const H = Math.round(r.range(34, 58) * s);
    const lean = [r.range(-0.08, 0.08), r.range(-0.08, 0.08)];
    for (let y = 0; y < H; y++) {
      const px = Math.round(x + lean[0] * y);
      const pz = Math.round(z + lean[1] * y);
      M.set(px, y, pz, y % 7 === 0 ? PAL.BARK_G : PAL.BAMBOO);
      if (y > H * 0.45 && y % 4 === 0) {
        const side = (y / 4 + i) % 2 ? 1 : -1;
        M.ellipsoid(px + side * 2, y, pz + (i % 2 ? 1 : -1), 2.6, 1.4, 2.2, PAL.BAMBOO_LEAF, holes(seed + y, 0.2), true);
      }
    }
  }
}

export function buildTree(def) {
  const r = new Rng(def.seed);
  const M = new VoxelModel(-44, -2, -44, 44, 100, 44);
  switch (def.species) {
    case 'pine':
      pine(M, r, r.range(0.9, 1.15));
      break;
    case 'cypress':
      cypress(M, r, r.range(0.9, 1.1));
      break;
    case 'broad':
      broad(M, r, r.range(0.9, 1.15), [PAL.LEAF_D, PAL.LEAF, PAL.LEAF_L]);
      break;
    case 'blossom': {
      const pal = r.chance(0.5) ? [PAL.BLOSSOM_D, PAL.BLOSSOM, PAL.BLOSSOM_W] : [PAL.BLOSSOM, PAL.BLOSSOM_W, PAL.BLOSSOM_W];
      broad(M, r, r.range(0.65, 0.85), pal);
      break;
    }
    case 'maple':
      broad(M, r, r.range(0.75, 0.95), r.chance(0.5) ? [PAL.MAPLE, PAL.MAPLE_O, PAL.MAPLE_Y] : [PAL.MAPLE, PAL.MAPLE, PAL.MAPLE_O]);
      break;
    case 'willow':
      willow(M, r, r.range(0.9, 1.1));
      break;
    case 'bamboo':
      bamboo(M, r, r.range(0.9, 1.1));
      break;
    default:
      broad(M, r, 1, [PAL.LEAF_D, PAL.LEAF, PAL.LEAF_L]);
  }
  return M;
}

// small ornamental tree drawn into another model (courtyards, gardens)
export function smallTree(M, x, y, z, seed, kind = 'blossom') {
  const r = new Rng(seed);
  const T = new VoxelModel(-30, -1, -30, 30, 60, 30);
  if (kind === 'pine') pine(T, r, 0.55);
  else broad(T, r, 0.5, [PAL.BLOSSOM_D, PAL.BLOSSOM, PAL.BLOSSOM_W]);
  M.paste(T, x, y, z);
}
