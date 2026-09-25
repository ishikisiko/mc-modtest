// Heightfield voxel meshing for one quadtree node. Every column is a stack of voxels whose top
// is quantised to 0.2 m; nodes at level k use k-times-doubled column widths (LOD) but keep the
// 0.2 m height quantum. Output: packed 8-byte vertices (Uint16 x4) for 4-vertex quads.
import { TM } from '../world/materials.js';

export const NODE_CELLS = 64;
export const ROOT_LEVEL = 10;
export const ROOT_SIZE = 0.2 * NODE_CELLS * 2 ** ROOT_LEVEL; // 13107.2 m
export const WORLD_ORIGIN = -ROOT_SIZE / 2;

export function nodeInfo(level, i, j) {
  const cell = 0.2 * 2 ** level;
  const size = cell * NODE_CELLS;
  return { cell, size, x0: WORLD_ORIGIN + i * size, z0: WORLD_ORIGIN + j * size };
}

// face directions: 0 +x, 1 -x, 2 +y, 3 -y, 4 +z, 5 -z
// (u, v) axes with u x v = n, used to emit counter-clockwise quads
const FACE_U = [
  [0, 1, 0],
  [0, 0, 1],
  [0, 0, 1],
  [1, 0, 0],
  [1, 0, 0],
  [0, 1, 0],
];
const FACE_V = [
  [0, 0, 1],
  [0, 1, 0],
  [1, 0, 0],
  [0, 0, 1],
  [0, 1, 0],
  [1, 0, 0],
];
export { FACE_U, FACE_V };

class QuadBuffer {
  constructor(cap = 16384) {
    this.data = new Uint16Array(cap * 16);
    this.count = 0;
  }
  ensure() {
    if ((this.count + 1) * 16 > this.data.length) {
      const n = new Uint16Array(this.data.length * 2);
      n.set(this.data);
      this.data = n;
    }
  }
  // corners: 4 x [x, y, z] in (cell units, 0.2 m units, cell units)
  push(c0, c1, c2, c3, nrm, o0, o1, o2, o3, mat, extra = 0) {
    this.ensure();
    // flip diagonal to avoid anisotropic AO interpolation
    let cs = [c0, c1, c2, c3];
    let os = [o0, o1, o2, o3];
    if (o0 + o2 < o1 + o3) {
      cs = [c1, c2, c3, c0];
      os = [o1, o2, o3, o0];
    }
    const d = this.data;
    let p = this.count * 16;
    // quad centre -> per-vertex outward signs (used to close T-junction pinholes)
    const mx = (c0[0] + c1[0] + c2[0] + c3[0]) * 0.25;
    const my = (c0[1] + c1[1] + c2[1] + c3[1]) * 0.25;
    const mz = (c0[2] + c1[2] + c2[2] + c3[2]) * 0.25;
    for (let k = 0; k < 4; k++) {
      const c = cs[k];
      const sx = c[0] > mx ? 2 : c[0] < mx ? 0 : 1;
      const sy = c[1] > my ? 2 : c[1] < my ? 0 : 1;
      const sz = c[2] > mz ? 2 : c[2] < mz ? 0 : 1;
      d[p++] = c[0] + c[2] * 256;
      d[p++] = c[1];
      d[p++] = nrm | (os[k] << 3) | (sx << 8) | (sy << 10) | (sz << 12);
      d[p++] = mat | (extra << 8);
    }
    this.count++;
  }
}

const OPEN = 31;
const FLOOR = -70 * 5; // 0.2 m units

export function meshTerrainNode(field, level, i, j, opts = {}) {
  const N = NODE_CELLS;
  const B = 3;
  const W = N + 2 * B;
  const { cell, x0, z0 } = nodeInfo(level, i, j);
  const geo = field.geo;
  const hs = new Int32Array(W * W);
  const tops = new Uint8Array(W * W);
  const sides = new Uint8Array(W * W);
  const out = new Float64Array(4);
  let minH = 1e9;
  let maxH = -1e9;
  for (let jj = 0; jj < W; jj++) {
    for (let ii = 0; ii < W; ii++) {
      const x = x0 + (ii - B + 0.5) * cell;
      const z = z0 + (jj - B + 0.5) * cell;
      field.sample(x, z, cell, out);
      const k = jj * W + ii;
      let h = Math.round(out[0] * 5);
      let top = out[1];
      // everything deep below the sea of clouds collapses to a flat, cheap floor
      if (h < FLOOR) {
        h = FLOOR;
        top = TM.CLOUDFLOOR;
      }
      hs[k] = h;
      tops[k] = top;
      sides[k] = out[2];
    }
  }
  // skirt depth per border edge cell: min over neighbours at finer / coarser resolution
  const skirt = (x, z) => Math.max(FLOOR, Math.round(field.sample(x, z, cell, out)[0] * 5));
  const edgeMin = new Int32Array(4 * N); // [side][index]
  for (let n = 0; n < N; n++) {
    const c = (n + 0.5) * cell;
    const cc = (Math.floor(n / 2) * 2 + 1) * cell; // coarse cell centre
    // west (x<0), east (x>size), north (z<0), south (z>size)
    const size = N * cell;
    const probes = [
      [
        [-0.25 * cell, (n + 0.25) * cell],
        [-0.25 * cell, (n + 0.75) * cell],
        [-cell, cc],
      ],
      [
        [size + 0.25 * cell, (n + 0.25) * cell],
        [size + 0.25 * cell, (n + 0.75) * cell],
        [size + cell, cc],
      ],
      [
        [(n + 0.25) * cell, -0.25 * cell],
        [(n + 0.75) * cell, -0.25 * cell],
        [cc, -cell],
      ],
      [
        [(n + 0.25) * cell, size + 0.25 * cell],
        [(n + 0.75) * cell, size + 0.25 * cell],
        [cc, size + cell],
      ],
    ];
    for (let s = 0; s < 4; s++) {
      let m = 1e9;
      for (const pr of probes[s]) m = Math.min(m, skirt(x0 + pr[0], z0 + pr[1]));
      edgeMin[s * N + n] = m;
    }
    void c;
  }

  // natural material resolution
  const cellM = cell;
  const matTop = new Uint8Array(W * W);
  const matSide = new Uint8Array(W * W);
  for (let jj = 1; jj < W - 1; jj++) {
    for (let ii = 1; ii < W - 1; ii++) {
      const k = jj * W + ii;
      const h = hs[k];
      let top = tops[k];
      let side = sides[k];
      if (top === TM.NATURAL || side === TM.NATURAL) {
        const dmax = Math.max(Math.abs(hs[k - 1] - h), Math.abs(hs[k + 1] - h), Math.abs(hs[k - W] - h), Math.abs(hs[k + W] - h));
        const slope = (dmax * 0.2) / cellM;
        const x = x0 + (ii - B + 0.5) * cell;
        const z = z0 + (jj - B + 0.5) * cell;
        const y = h * 0.2;
        if (top === TM.NATURAL) {
          const fr = geo.forest(x, z);
          const patch = geo.noise.nE(x / 13, z / 13) + (slope - 1.15) * 1.6;
          if (slope > 1.5) top = TM.ROCK;
          else if (slope > 0.85) top = patch > 0 ? TM.ROCK : TM.MOSS;
          else if (y < 22) top = TM.MOSS;
          else if (y > 300) top = fr > 0.55 ? TM.FOREST : TM.MOSS;
          else top = fr > 0.52 ? TM.FOREST : TM.GRASS;
        }
        if (side === TM.NATURAL) side = TM.CLIFF;
      }
      matTop[k] = top;
      matSide[k] = side;
    }
  }

  // openness (cavity) per cell over radius 2, scaled to cell size
  const openC = new Float32Array(W * W);
  const scaleH = (2.5 * cell) / 0.2; // height diff (in 0.2 units) that counts as full occlusion
  for (let jj = 2; jj < W - 2; jj++) {
    for (let ii = 2; ii < W - 2; ii++) {
      const k = jj * W + ii;
      const h = hs[k];
      let occ = 0;
      for (let dj = -2; dj <= 2; dj++)
        for (let di = -2; di <= 2; di++) {
          if (!di && !dj) continue;
          const dh = hs[k + dj * W + di] - h;
          if (dh > 0) occ += Math.min(1, dh / scaleH) * (Math.abs(di) + Math.abs(dj) > 2 ? 0.6 : 1);
        }
      openC[k] = Math.max(0, 1 - occ / 11);
    }
  }
  // per-vertex openness = average of the 4 surrounding cells
  const vOpen = (ii, jj) => {
    // vertex at corner (ii, jj) in W-grid coordinates (between cells ii-1..ii, jj-1..jj)
    const k = jj * W + ii;
    return (openC[k] + openC[k - 1] + openC[k - W] + openC[k - W - 1]) * 0.25;
  };

  let yMin = 1e9;
  let yMax = -1e9;
  for (let jj = B; jj < B + N; jj++)
    for (let ii = B; ii < B + N; ii++) {
      const h = hs[jj * W + ii];
      if (h < yMin) yMin = h;
      if (h > yMax) yMax = h;
    }
  for (let n = 0; n < 4 * N; n++) if (edgeMin[n] < yMin) yMin = edgeMin[n];
  const extraSkirt = Math.max(2, Math.round((cell * 2) / 0.2));
  const yBase = yMin - extraSkirt - 2;

  const qb = new QuadBuffer(N * N * 2);

  // AO helper for top faces: returns 0..3 for a corner, given occupancy of s1, s2, corner
  const ao = (s1, s2, c) => (s1 && s2 ? 0 : 3 - (s1 + s2 + c));

  // ---- top faces with greedy merge of fully-open equal cells ----
  const mergeKey = new Int32Array(N * N).fill(-1);
  const topOcc = new Uint8Array(N * N * 4);
  for (let cj = 0; cj < N; cj++) {
    for (let ci = 0; ci < N; ci++) {
      const ii = ci + B;
      const jj = cj + B;
      const k = jj * W + ii;
      const h = hs[k];
      const up = (dx, dz) => (hs[k + dz * W + dx] > h ? 1 : 0);
      // corners in order matching the +y face (u=z, v=x): (0,0), (0,1)z, (1,1), (1,0)x
      const a00 = ao(up(-1, 0), up(0, -1), up(-1, -1));
      const a01 = ao(up(-1, 0), up(0, 1), up(-1, 1));
      const a11 = ao(up(1, 0), up(0, 1), up(1, 1));
      const a10 = ao(up(1, 0), up(0, -1), up(1, -1));
      const o00 = Math.round((a00 / 3) * vOpen(ii, jj) * OPEN);
      const o01 = Math.round((a01 / 3) * vOpen(ii, jj + 1) * OPEN);
      const o11 = Math.round((a11 / 3) * vOpen(ii + 1, jj + 1) * OPEN);
      const o10 = Math.round((a10 / 3) * vOpen(ii + 1, jj) * OPEN);
      const t = (cj * N + ci) * 4;
      topOcc[t] = o00;
      topOcc[t + 1] = o01;
      topOcc[t + 2] = o11;
      topOcc[t + 3] = o10;
      if (o00 === o01 && o01 === o11 && o11 === o10 && o00 >= OPEN - 1) mergeKey[cj * N + ci] = (h - yBase) * 256 + matTop[k];
    }
  }
  const used = new Uint8Array(N * N);
  for (let cj = 0; cj < N; cj++) {
    for (let ci = 0; ci < N; ci++) {
      const idx = cj * N + ci;
      if (used[idx]) continue;
      const k = (cj + B) * W + (ci + B);
      const y = hs[k] - yBase;
      const mat = matTop[k];
      const key = mergeKey[idx];
      if (key < 0) {
        const t = idx * 4;
        qb.push([ci, y, cj], [ci, y, cj + 1], [ci + 1, y, cj + 1], [ci + 1, y, cj], 2, topOcc[t], topOcc[t + 1], topOcc[t + 2], topOcc[t + 3], mat);
        used[idx] = 1;
        continue;
      }
      // grow along x
      let w = 1;
      while (ci + w < N && !used[idx + w] && mergeKey[idx + w] === key) w++;
      // grow along z
      let d = 1;
      outer: while (cj + d < N) {
        for (let q = 0; q < w; q++) {
          const id2 = (cj + d) * N + ci + q;
          if (used[id2] || mergeKey[id2] !== key) break outer;
        }
        d++;
      }
      for (let dz = 0; dz < d; dz++) for (let dx = 0; dx < w; dx++) used[(cj + dz) * N + ci + dx] = 1;
      const o = topOcc[idx * 4];
      qb.push([ci, y, cj], [ci, y, cj + d], [ci + w, y, cj + d], [ci + w, y, cj], 2, o, o, o, o, mat);
    }
  }

  // ---- side faces: each cell emits faces toward lower neighbours ----
  const sideOcc = Math.round(OPEN * 0.34);
  for (let cj = 0; cj < N; cj++) {
    for (let ci = 0; ci < N; ci++) {
      const ii = ci + B;
      const jj = cj + B;
      const k = jj * W + ii;
      const h = hs[k];
      const mat = matSide[k];
      const topMat = matTop[k];
      const topO = Math.round(OPEN * vOpen(ii, jj) * 0.95);
      // +x
      {
        let hn = hs[k + 1];
        if (ci === N - 1) hn = Math.min(hn, edgeMin[1 * N + cj] - extraSkirt);
        if (hn < h) {
          const yb = hn - yBase;
          const yt = h - yBase;
          const x = ci + 1;
          qb.push([x, yb, cj + 1], [x, yb, cj], [x, yt, cj], [x, yt, cj + 1], 0, sideOcc, sideOcc, topO, topO, mat, topMat);
        }
      }
      // -x
      {
        let hn = hs[k - 1];
        if (ci === 0) hn = Math.min(hn, edgeMin[0 * N + cj] - extraSkirt);
        if (hn < h) {
          const yb = hn - yBase;
          const yt = h - yBase;
          const x = ci;
          qb.push([x, yb, cj], [x, yb, cj + 1], [x, yt, cj + 1], [x, yt, cj], 1, sideOcc, sideOcc, topO, topO, mat, topMat);
        }
      }
      // +z
      {
        let hn = hs[k + W];
        if (cj === N - 1) hn = Math.min(hn, edgeMin[3 * N + ci] - extraSkirt);
        if (hn < h) {
          const yb = hn - yBase;
          const yt = h - yBase;
          const z = cj + 1;
          qb.push([ci, yb, z], [ci + 1, yb, z], [ci + 1, yt, z], [ci, yt, z], 4, sideOcc, sideOcc, topO, topO, mat, topMat);
        }
      }
      // -z
      {
        let hn = hs[k - W];
        if (cj === 0) hn = Math.min(hn, edgeMin[2 * N + ci] - extraSkirt);
        if (hn < h) {
          const yb = hn - yBase;
          const yt = h - yBase;
          const z = cj;
          qb.push([ci + 1, yb, z], [ci, yb, z], [ci, yt, z], [ci + 1, yt, z], 5, sideOcc, sideOcc, topO, topO, mat, topMat);
        }
      }
    }
  }

  const data = qb.data.slice(0, qb.count * 16);
  return {
    data,
    quads: qb.count,
    yBase: yBase * 0.2,
    yMin: yMin * 0.2,
    yMax: yMax * 0.2,
    cell,
    x0,
    z0,
  };
}
