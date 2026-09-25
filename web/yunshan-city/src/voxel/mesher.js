// Voxel model mesher: LOD downsampling (surface-colour averaging), per-vertex AO and greedy
// merging. Output quads with aPos (Uint16x4: x,y,z in 0.2 m units, info) and aCol (Uint8x4).
import { PAL_RGB, PAL_TYPE, PAL_JIT, T_WINDOW, T_LANTERN, T_INTERIOR } from './palette.js';

const DIRS = [
  [1, 0, 0],
  [-1, 0, 0],
  [0, 1, 0],
  [0, -1, 0],
  [0, 0, 1],
  [0, 0, -1],
];
// u, v axes per direction with u x v = n (counter-clockwise quads)
const UAX = [1, 2, 2, 0, 0, 1];
const VAX = [2, 1, 0, 2, 1, 0];
const NAX = [0, 0, 1, 1, 2, 2];
const YBIAS = 16; // keeps coarse-LOD vertices non-negative (pivot compensates)

// Build the LOD grid: returns { nx, ny, nz, solid(Uint8), r,g,b (Uint8), type(Uint8), jit(Uint8), scale }
function lodGrid(model, L) {
  const { sx, sy, sz, data } = model;
  if (L === 0) {
    const n = sx * sy * sz;
    const r = new Uint8Array(n);
    const g = new Uint8Array(n);
    const b = new Uint8Array(n);
    const type = new Uint8Array(n);
    const jit = new Uint8Array(n);
    const solid = new Uint8Array(n);
    for (let i = 0; i < n; i++) {
      const m = data[i];
      if (!m) continue;
      solid[i] = 1;
      r[i] = Math.round(PAL_RGB[m * 3] * 255);
      g[i] = Math.round(PAL_RGB[m * 3 + 1] * 255);
      b[i] = Math.round(PAL_RGB[m * 3 + 2] * 255);
      type[i] = PAL_TYPE[m];
      jit[i] = PAL_JIT[m];
    }
    return { nx: sx, ny: sy, nz: sz, solid, r, g, b, type, jit, scale: 1 };
  }
  const f = 1 << L;
  // align the coarse grid so the ground plane (pivot y) falls on a coarse boundary
  const yShift = ((model.oy % f) + f) % f; // fine voxels below the first coarse boundary
  const offY = yShift ? f - yShift : 0;
  const nx = Math.ceil(sx / f);
  const ny = Math.ceil((sy + offY) / f);
  const nz = Math.ceil(sz / f);
  const n = nx * ny * nz;
  const cnt = new Uint16Array(n);
  const sr = new Float32Array(n);
  const sg = new Float32Array(n);
  const sb = new Float32Array(n);
  const sw = new Float32Array(n);
  const tcount = new Uint16Array(n * 10);
  const surf = surfaceMask(model);
  for (let y = 0; y < sy; y++) {
    const cy = Math.floor((y + offY) / f);
    for (let z = 0; z < sz; z++) {
      const cz = Math.floor(z / f);
      for (let x = 0; x < sx; x++) {
        const i = (y * sz + z) * sx + x;
        const m = data[i];
        if (!m) continue;
        const c = (cy * nz + cz) * nx + Math.floor(x / f);
        cnt[c]++;
        const tp = PAL_TYPE[m];
        const wgt = surf[i] ? (tp === T_INTERIOR ? 0.05 : 1) : 0.02;
        sr[c] += PAL_RGB[m * 3] * wgt;
        sg[c] += PAL_RGB[m * 3 + 1] * wgt;
        sb[c] += PAL_RGB[m * 3 + 2] * wgt;
        sw[c] += wgt;
        if (surf[i]) tcount[c * 10 + tp]++;
      }
    }
  }
  const thr = [0, 0.2, 0.14, 0.1, 0.08][Math.min(4, L)] * f * f * f;
  const solid = new Uint8Array(n);
  const r = new Uint8Array(n);
  const g = new Uint8Array(n);
  const b = new Uint8Array(n);
  const type = new Uint8Array(n);
  const jit = new Uint8Array(n);
  for (let c = 0; c < n; c++) {
    if (cnt[c] < Math.max(1, thr)) continue;
    solid[c] = 1;
    const w = sw[c] || 1;
    r[c] = Math.round((sr[c] / w) * 255);
    g[c] = Math.round((sg[c] / w) * 255);
    b[c] = Math.round((sb[c] / w) * 255);
    let best = 0;
    let bc = -1;
    let tot = 0;
    for (let t = 0; t < 10; t++) {
      tot += tcount[c * 10 + t];
      if (tcount[c * 10 + t] > bc) {
        bc = tcount[c * 10 + t];
        best = t;
      }
    }
    const lit = tcount[c * 10 + T_WINDOW] + tcount[c * 10 + T_LANTERN];
    if (tot > 0 && lit / tot > 0.3) best = tcount[c * 10 + T_LANTERN] > tcount[c * 10 + T_WINDOW] ? T_LANTERN : T_WINDOW;
    // glow share (0..15) lets distant buildings keep a faint night glow
    const glow = tot > 0 ? Math.min(15, Math.round((lit / tot) * 40)) : 0;
    type[c] = best | (best === T_WINDOW || best === T_LANTERN ? 0 : glow << 4);
    jit[c] = 1;
  }
  return { nx, ny, nz, solid, r, g, b, type, jit, scale: f, offY };
}

function surfaceMask(model) {
  const { sx, sy, sz, data } = model;
  const out = new Uint8Array(data.length);
  for (let y = 0; y < sy; y++)
    for (let z = 0; z < sz; z++)
      for (let x = 0; x < sx; x++) {
        const i = (y * sz + z) * sx + x;
        if (!data[i]) continue;
        if (x === 0 || x === sx - 1 || y === sy - 1 || z === 0 || z === sz - 1) {
          out[i] = 1;
          continue;
        }
        if (!data[i - 1] || !data[i + 1] || !data[i - sx] || !data[i + sx] || !data[i + sx * sz] || (y > 0 && !data[i - sx * sz])) out[i] = 1;
      }
  return out;
}

class Out {
  constructor() {
    this.pos = new Uint16Array(4096 * 16);
    this.col = new Uint8Array(4096 * 16);
    this.q = 0;
  }
  grow() {
    const p = new Uint16Array(this.pos.length * 2);
    p.set(this.pos);
    this.pos = p;
    const c = new Uint8Array(this.col.length * 2);
    c.set(this.col);
    this.col = c;
  }
}

export function meshModel(model, L) {
  const G = lodGrid(model, L);
  const { nx, ny, nz, solid, r, g, b, type, jit } = G;
  const f = G.scale;
  const offY = G.offY || 0;
  // cells below pivot ground (coarse index) treated as solid when outside the model
  const groundC = Math.floor((model.oy + offY) / f); // coarse y index of the ground plane
  const dims = [nx, ny, nz];
  const at = (x, y, z) => {
    if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) return y < groundC ? 1 : 0;
    return solid[(y * nz + z) * nx + x];
  };
  const out = new Out();
  const key = new Float64Array(Math.max(nx * ny, ny * nz, nx * nz));
  const aoK = new Uint8Array(key.length);
  const cellIdx = new Int32Array(key.length);
  const p = [0, 0, 0];
  for (let d = 0; d < 6; d++) {
    const [dx, dy, dz] = DIRS[d];
    const ua = UAX[d];
    const va = VAX[d];
    const na = NAX[d];
    const nu = dims[ua];
    const nv = dims[va];
    const nn = dims[na];
    const ux = ua === 0 ? 1 : 0;
    const uy = ua === 1 ? 1 : 0;
    const uz = ua === 2 ? 1 : 0;
    const vx = va === 0 ? 1 : 0;
    const vy = va === 1 ? 1 : 0;
    const vz = va === 2 ? 1 : 0;
    for (let s = 0; s < nn; s++) {
      // build mask
      let any = false;
      for (let v = 0; v < nv; v++) {
        for (let u = 0; u < nu; u++) {
          p[na] = s;
          p[ua] = u;
          p[va] = v;
          const idx = (p[1] * nz + p[2]) * nx + p[0];
          const mi = v * nu + u;
          key[mi] = -1;
          if (!solid[idx]) continue;
          if (at(p[0] + dx, p[1] + dy, p[2] + dz)) continue;
          // AO for the 4 corners of this face (u,v order: (0,0),(1,0),(1,1),(0,1))
          const ox = p[0] + dx;
          const oy = p[1] + dy;
          const oz = p[2] + dz;
          const u_m = at(ox - ux, oy - uy, oz - uz);
          const u_p = at(ox + ux, oy + uy, oz + uz);
          const v_m = at(ox - vx, oy - vy, oz - vz);
          const v_p = at(ox + vx, oy + vy, oz + vz);
          const c_mm = at(ox - ux - vx, oy - uy - vy, oz - uz - vz);
          const c_pm = at(ox + ux - vx, oy + uy - vy, oz + uz - vz);
          const c_pp = at(ox + ux + vx, oy + uy + vy, oz + uz + vz);
          const c_mp = at(ox - ux + vx, oy - uy + vy, oz - uz + vz);
          const a0 = u_m && v_m ? 0 : 3 - (u_m + v_m + c_mm);
          const a1 = u_p && v_m ? 0 : 3 - (u_p + v_m + c_pm);
          const a2 = u_p && v_p ? 0 : 3 - (u_p + v_p + c_pp);
          const a3 = u_m && v_p ? 0 : 3 - (u_m + v_p + c_mp);
          const ao = a0 | (a1 << 2) | (a2 << 4) | (a3 << 6);
          key[mi] = ((((r[idx] * 256 + g[idx]) * 256 + b[idx]) * 16 + type[idx]) * 4 + jit[idx]) * 256 + ao;
          aoK[mi] = ao;
          cellIdx[mi] = idx;
          any = true;
        }
      }
      if (!any) continue;
      // greedy merge
      for (let v = 0; v < nv; v++) {
        for (let u = 0; u < nu; u++) {
          const mi = v * nu + u;
          const k = key[mi];
          if (k < 0) continue;
          let w = 1;
          while (u + w < nu && key[mi + w] === k) w++;
          let h = 1;
          outer: while (v + h < nv) {
            for (let q = 0; q < w; q++) if (key[(v + h) * nu + u + q] !== k) break outer;
            h++;
          }
          for (let hv = 0; hv < h; hv++) for (let q = 0; q < w; q++) key[(v + hv) * nu + u + q] = -1;
          emit(out, d, na, ua, va, s, u, v, w, h, aoK[mi], cellIdx[mi], G, f, offY, L);
        }
      }
    }
  }
  const q = out.q;
  return { pos: out.pos.slice(0, q * 16), col: out.col.slice(0, q * 16), quads: q, pivot: [model.ox, model.oy + YBIAS, model.oz], dims: [model.sx, model.sy, model.sz] };
}

function emit(out, d, na, ua, va, s, u, v, w, h, ao, idx, G, f, offY, L) {
  if ((out.q + 1) * 16 > out.pos.length) out.grow();
  const plane = DIRS[d][na] > 0 ? s + 1 : s;
  const corners = [
    [u, v],
    [u + w, v],
    [u + w, v + h],
    [u, v + h],
  ];
  const aos = [ao & 3, (ao >> 2) & 3, (ao >> 4) & 3, (ao >> 6) & 3];
  // order: v0=(u,v), v1=(u+w? ) must satisfy (v1-v0) x (v2-v0) = n with u x v = n:
  // (0,0) -> (1,0) along u, -> (1,1), -> (0,1)
  let order = [0, 1, 2, 3];
  if (aos[0] + aos[2] < aos[1] + aos[3]) order = [1, 2, 3, 0];
  const pos = out.pos;
  const col = out.col;
  let pi = out.q * 16;
  const info = d | 0;
  const cu = u + w * 0.5;
  const cv = v + h * 0.5;
  for (const k of order) {
    const c = [0, 0, 0];
    c[na] = plane;
    c[ua] = corners[k][0];
    c[va] = corners[k][1];
    const sg = [1, 1, 1];
    sg[ua] = corners[k][0] > cu ? 2 : 0;
    sg[va] = corners[k][1] > cv ? 2 : 0;
    pos[pi] = c[0] * f;
    pos[pi + 1] = c[1] * f - offY + YBIAS;
    pos[pi + 2] = c[2] * f;
    pos[pi + 3] = info | (aos[k] << 3) | (Math.min(7, L) << 5) | (G.jit[idx] << 8) | (sg[0] << 10) | (sg[1] << 12) | (sg[2] << 14);
    col[pi] = G.r[idx];
    col[pi + 1] = G.g[idx];
    col[pi + 2] = G.b[idx];
    col[pi + 3] = G.type[idx];
    pi += 4;
  }
  out.q++;
}
