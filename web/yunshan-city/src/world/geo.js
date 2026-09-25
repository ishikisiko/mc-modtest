// Natural macro-geography of 云山巨城: a hanging valley on a cloud-wrapped massif.
// Coordinates are metres: +x east, +y up, +z south (north = -z).
import { makeSimplex2, clamp, smoothstep, lerp, hash2 } from './noise.js';

export const TERRACE = 4; // city terrace riser (m); every quay level is a multiple of it

// River pools from south to north. Each pool is flat water; falls sit on the z boundaries.
// quay = level + 2 is always a multiple of TERRACE so street terraces line up across falls.
export const POOLS = [
  { zS: 470, zN: 172, level: 38 },
  { zS: 172, zN: 84, level: 50 },
  { zS: 84, zN: -8, level: 62 },
  { zS: -8, zN: -94, level: 78 },
  { zS: -94, zN: -170, level: 94 },
  { zS: -170, zN: -238, level: 110 },
  { zS: -238, zN: -480, level: 134 },
];

export const SITE = {
  gateZ: 380, // south gate
  plateauEdgeZ: 432,
  basin: { x: -6, z: -318, r: 78 }, // waterfall plunge basin
  fallX: -8, // waterfall centre x
  fallLipY: 236,
  poolY: 134,
  upperY: 238,
  summit: { x: 206, z: -520, y: 404 },
  cloudTop: 6,
};

export function poolAt(z) {
  for (let i = 0; i < POOLS.length; i++) if (z <= POOLS[i].zS && z > POOLS[i].zN) return i;
  return z > POOLS[0].zS ? 0 : POOLS.length - 1;
}

// smooth valley-floor knots (z, quay level) through pool mid points
const FLOOR_KNOTS = POOLS.map((p) => [Math.max(-340, (p.zS + p.zN) / 2), p.level + 2]);

export function createGeo(seed = 1) {
  const nA = makeSimplex2(seed * 7 + 1);
  const nB = makeSimplex2(seed * 7 + 2);
  const nC = makeSimplex2(seed * 7 + 3);
  const nD = makeSimplex2(seed * 7 + 4);
  const nE = makeSimplex2(seed * 7 + 5);

  function axisX(z) {
    return 16 * Math.sin(z / 190 + 0.7) + 8 * Math.sin(z / 83 + 2.0);
  }

  // smooth valley floor (approximate quay level), monotone through pool mid points
  function floorY(z) {
    const k = FLOOR_KNOTS;
    if (z >= k[0][0]) return k[0][1] - (z - k[0][0]) * 0.02;
    for (let i = 0; i < k.length - 1; i++) {
      const [za, ya] = k[i];
      const [zb, yb] = k[i + 1];
      if (z <= za && z >= zb) {
        const t = (za - z) / (za - zb);
        const s = t * t * (3 - 2 * t);
        return ya + (yb - ya) * (0.35 * t + 0.65 * s);
      }
    }
    return k[k.length - 1][1];
  }

  function halfWidth(z) {
    // flat-ish valley floor half width
    return 58 + 16 * Math.sin(z / 140 + 1.3) - 18 * smoothstep(0, -300, z);
  }

  // ridge crest distance from axis and crest height above floor, per side (+1 east, -1 west)
  function ridge(z, side) {
    if (side > 0) {
      const dist = 236 + 26 * Math.sin(z / 150 + 0.4) - 40 * smoothstep(-100, -320, z);
      const top = 118 + 40 * smoothstep(300, -60, z) + 30 * Math.sin(z / 95 + 1.1) + 70 * smoothstep(-160, -330, z);
      return [dist, top];
    }
    const dist = 262 + 30 * Math.sin(z / 170 + 2.2) - 50 * smoothstep(-80, -320, z);
    const top = 104 + 34 * smoothstep(320, -40, z) + 26 * Math.sin(z / 110 + 0.3) + 80 * smoothstep(-150, -330, z);
    return [dist, top];
  }

  function ridgedFbm(x, z, oct, cell) {
    let amp = 1;
    let freq = 1 / 260;
    let sum = 0;
    let norm = 0;
    let prev = 1;
    for (let o = 0; o < oct; o++) {
      if (1 / freq < cell * 3) break;
      let n = 1 - Math.abs(nA(x * freq, z * freq));
      n *= n;
      sum += n * amp * prev;
      norm += amp;
      prev = clamp(n * 1.6, 0, 1);
      amp *= 0.5;
      freq *= 2.03;
    }
    return norm > 0 ? sum / norm : 0;
  }

  function fbm(noise, x, z, baseFreq, oct, cell) {
    let amp = 1;
    let freq = baseFreq;
    let sum = 0;
    let norm = 0;
    for (let o = 0; o < oct; o++) {
      if (1 / freq < cell * 3) break;
      sum += noise(x * freq, z * freq) * amp;
      norm += amp;
      amp *= 0.5;
      freq *= 2.01;
    }
    return norm > 0 ? sum / norm : 0;
  }

  // karst pillars rising out of the cloud sea (jittered grid)
  const PILLAR_CELL = 104;
  function pillars(x, z) {
    const ci = Math.floor(x / PILLAR_CELL);
    const cj = Math.floor(z / PILLAR_CELL);
    let best = -1e9;
    for (let dj = -1; dj <= 1; dj++) {
      for (let di = -1; di <= 1; di++) {
        const i = ci + di;
        const j = cj + dj;
        const h = hash2(i, j, seed * 31 + 5);
        if ((h & 255) < 105) continue;
        const px = (i + 0.2 + 0.6 * ((h >>> 8) & 255) / 255) * PILLAR_CELL;
        const pz = (j + 0.2 + 0.6 * ((h >>> 16) & 255) / 255) * PILLAR_CELL;
        // keep pillars out of the city plateau and inside the near cloud sea
        const plateau = plateauMask(px, pz);
        if (plateau > 0.02) continue;
        if (px * px + (pz + 100) * (pz + 100) > 1500 * 1500) continue;
        const rr = ((h >>> 24) & 255) / 255;
        const r0 = 9 + 34 * rr * rr;
        const dx = x - px;
        const dz = z - pz;
        const ang = Math.atan2(dz, dx);
        const r = Math.sqrt(dx * dx + dz * dz) * (1 + 0.1 * Math.sin(ang * 2 + (h & 7)) + 0.07 * Math.sin(ang * 5 + i * 1.7));
        const distC = Math.sqrt(px * px + (pz + 60) * (pz + 60));
        const hash2v = hash2(j, i, seed + 99) / 4294967296;
        let top = -60 + 190 * hash2v * hash2v + 60 * smoothstep(900, 400, distC);
        if (r < r0) {
          top -= 10 * (r / r0) * (r / r0);
        } else {
          top -= 10 + (r - r0) * 7;
        }
        if (top > best) best = top;
      }
    }
    return best;
  }

  // 1 inside the elevated valley plateau, 0 in the outer lowlands
  function plateauMask(x, z) {
    const d = x - axisX(z);
    const side = d >= 0 ? 1 : -1;
    const [rd] = ridge(z, side);
    const lateral = smoothstep(rd + 190, rd + 20, Math.abs(d));
    const south = smoothstep(SITE.plateauEdgeZ + 90, SITE.plateauEdgeZ - 10, z);
    return lateral * south;
  }

  const LOWLAND = -210;

  // main natural height; cell = sample footprint for octave cutoff
  function height(x, z, cell = 0.2) {
    const ax = axisX(z);
    const d = x - ax;
    const side = d >= 0 ? 1 : -1;
    const ad = Math.abs(d);
    const fy = floorY(z);
    const w = halfWidth(z);
    const [rd, rTop] = ridge(z, side);
    const dp = Math.max(0, ad - w);
    const span = Math.max(40, rd - w);
    const t = dp / span;

    // gentle warp of contour lines on the inhabited slopes
    const warp = fbm(nB, x, z, 1 / 140, 3, cell) * 14 * smoothstep(0, 30, dp);
    const tw = Math.max(0, (dp + warp) / span);
    let inner;
    if (tw <= 1) inner = rTop * (0.34 * tw + 0.1 * tw * tw + 0.56 * tw * tw * tw * tw);
    else inner = rTop * (1 + 0.6 * (tw - 1)) + 20; // extrapolation (cut by smooth min)
    inner += fy + 1.2 * Math.min(1, dp / 12);
    // outer slope beyond the ridge
    const outer = fy + rTop - (ad - rd) * 1.35 - 0.004 * (ad - rd) * (ad - rd);
    // smooth min for a rounded crest
    const k = 18;
    const hdiff = clamp(0.5 + 0.5 * (outer - inner) / k, 0, 1);
    let h = lerp(outer, inner, hdiff) - k * hdiff * (1 - hdiff);

    // south plateau edge falls into the cloud sea
    const edgeZ = SITE.plateauEdgeZ + 16 * Math.sin(x / 55 + 1) + 10 * Math.sin(x / 23);
    if (z > edgeZ - 30) {
      const drop = Math.max(0, z - edgeZ);
      h -= drop * 2.6 + drop * drop * 0.02;
      h = Math.max(h, LOWLAND);
    }

    // ruggedness on the mountains, calm on the inhabited slopes
    const rugged = smoothstep(0.35, 0.95, t) + smoothstep(SITE.basin.z + 60, SITE.basin.z - 40, z) * 0.6;
    const rf = ridgedFbm(x, z, 6, cell);
    h += (rf - 0.45) * (18 + 70 * rugged) * (0.35 + 0.65 * rugged);
    h += fbm(nC, x, z, 1 / 55, 3, cell) * (2.2 + 6 * rugged);

    // northern cirque around the plunge basin and the upper valley beyond it
    const b = SITE.basin;
    const bx = x - b.x;
    const bz = z - b.z;
    const br = Math.sqrt(bx * bx + bz * bz);
    const northness = smoothstep(0.15, -0.35, bz / Math.max(br, 1)); // 1 to the north of basin centre
    const bang = Math.atan2(bz, bx);
    const rimR = b.r + 10 * Math.sin(bang * 3 + 1.2) + 6 * Math.sin(bang * 7);
    // the upper plateau is a bounded massif behind the cirque, not an endless table
    const massif = smoothstep(820, 560, br) * smoothstep(520, 330, Math.abs(bx));
    const cliffT = smoothstep(rimR, rimR + 5, br) * northness * massif;
    // upper plateau height behind the cliff (rises to the north and east)
    let upper = SITE.upperY + (-(z - b.z) - 60) * 0.12 + ridgedFbm(x + 500, z, 5, cell) * 38 - 12;
    upper += 50 * smoothstep(120, 260, Math.abs(x - SITE.fallX)) * smoothstep(80, 10, x - SITE.fallX + 200 * 0); // flanking massifs
    if (cliffT > 0) h = Math.max(h, lerp(h, upper, cliffT));
    // basin floor bowl
    if (br < rimR + 4 && bz > -rimR - 6) {
      const bowl = SITE.poolY - 2 + (br / rimR) * (br / rimR) * 8;
      const inBowl = smoothstep(rimR + 4, rimR - 12, br);
      h = lerp(h, Math.min(h, bowl), inBowl);
    }

    // summit peak massif (north-east)
    const s = SITE.summit;
    const sx = x - s.x;
    const sz = z - s.z;
    const sr = Math.sqrt(sx * sx + sz * sz);
    const peak = s.y - Math.pow(sr / 22, 1.25) * 12 - (sr > 70 ? (sr - 70) * 0.75 : 0) + (ridgedFbm(x + 1300, z - 700, 5, cell) - 0.4) * 26 * smoothstep(12, 80, sr);
    // keep terrain around the summit below it so the peak reads clearly
    h = Math.min(h, s.y - 60 + sr * 0.55);
    h = Math.max(h, peak);

    // distant mountain ranges and karst pillars
    const plateau = plateauMask(x, z);
    const dc = Math.sqrt(x * x + (z + 150) * (z + 150));
    const far = smoothstep(900, 2600, dc);
    // isolated far ridges & peaks (sparse, so the horizon reads as layered silhouettes)
    // broad massifs: low-frequency mask shaped with ridged detail
    const mass = fbm(nD, x + 7000, z - 3000, 1 / 1400, 3, cell) * 0.5 + 0.5; // 0..1
    const rn = ridgedFbm(x * 0.3 + 3000, z * 0.3 - 2000, 4, cell * 0.3);
    const northBoost = 0.8 + 0.5 * smoothstep(-400, -1600, z);
    const massK = smoothstep(0.42, 0.8, mass);
    const range = LOWLAND + far * massK * (260 + 520 * rn) * northBoost + (1 - far) * (fbm(nD, x, z, 1 / 300, 3, cell) * 40);
    const low = Math.max(range, pillars(x, z));
    if (plateau < 1) h = lerp(Math.max(low, Math.min(h, low + 1e9)), h, plateau) * 1;
    if (plateau < 1) h = Math.max(h, low);

    return h;
  }

  // rough 0..1 vegetation density used for natural surface + tree scattering
  function forest(x, z) {
    return clamp(0.5 + 0.6 * fbm(nE, x, z, 1 / 90, 3, 0.2), 0, 1);
  }

  return { axisX, floorY, halfWidth, ridge, height, forest, plateauMask, fbm, noise: { nA, nB, nC, nD, nE } };
}
