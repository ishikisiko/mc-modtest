// Combined height/material field: natural geography + city terraces + plan features.
// sample(x, z, cell, out) writes [height(m, quantised to 0.2), topMat, sideMat, flags].
import { createGeo, POOLS, TERRACE } from './geo.js';
import { TM } from './materials.js';

export const MODE = { SET: 0, MAX: 1, MIN: 2, PAINT: 3, TERR: 4, SETUP: 5 };
export const KIND = { PLAIN: 0, WALL: 1, RIVER: 2, LANE: 3, PALWALL: 4 };
export const FT = { RECT: 0, ELL: 1, SEG: 2 };

const BUCKET = 16;

export function poolLevelAt(z) {
  for (let i = 0; i < POOLS.length; i++) if (z <= POOLS[i].zS && z > POOLS[i].zN) return POOLS[i].level;
  return z > POOLS[0].zS ? POOLS[0].level : POOLS[POOLS.length - 1].level;
}

// quay height beside the river: stepped at falls, with a stair ramp on the downstream side
const RAMP = 26;
export function quayAt(z) {
  let y = poolLevelAt(z) + 2;
  for (let i = 0; i < POOLS.length - 1; i++) {
    const zf = POOLS[i].zN; // fall between pool i (south, lower) and pool i+1 (north, higher)
    if (z > zf && z < zf + RAMP) {
      const up = POOLS[i + 1].level + 2;
      const lo = POOLS[i].level + 2;
      const t = (z - zf) / RAMP;
      y = up + (lo - up) * t;
    }
  }
  return y;
}

export function createField(plan) {
  const geo = createGeo(plan.seed);
  const feats = plan.features;
  const g = plan.grids.terr;

  // spatial buckets
  let bx0 = 1e9;
  let bz0 = 1e9;
  let bx1 = -1e9;
  let bz1 = -1e9;
  const boxes = feats.map((f) => {
    let b;
    if (f.t === FT.RECT) b = [f.x0, f.z0, f.x1, f.z1];
    else if (f.t === FT.ELL) b = [f.cx - f.rx - (f.rimW || 0), f.cz - f.rz - (f.rimW || 0), f.cx + f.rx + (f.rimW || 0), f.cz + f.rz + (f.rimW || 0)];
    else {
      const r = f.hw + (f.qw || 0) + 1;
      b = [Math.min(f.ax, f.bx) - r, Math.min(f.az, f.bz) - r, Math.max(f.ax, f.bx) + r, Math.max(f.az, f.bz) + r];
    }
    bx0 = Math.min(bx0, b[0]);
    bz0 = Math.min(bz0, b[1]);
    bx1 = Math.max(bx1, b[2]);
    bz1 = Math.max(bz1, b[3]);
    return b;
  });
  bx0 = Math.floor(bx0 / BUCKET) * BUCKET;
  bz0 = Math.floor(bz0 / BUCKET) * BUCKET;
  const bnx = Math.max(1, Math.ceil((bx1 - bx0) / BUCKET));
  const bnz = Math.max(1, Math.ceil((bz1 - bz0) / BUCKET));
  const buckets = new Array(bnx * bnz);
  boxes.forEach((b, idx) => {
    const i0 = Math.max(0, Math.floor((b[0] - bx0) / BUCKET));
    const i1 = Math.min(bnx - 1, Math.floor((b[2] - bx0) / BUCKET));
    const j0 = Math.max(0, Math.floor((b[1] - bz0) / BUCKET));
    const j1 = Math.min(bnz - 1, Math.floor((b[3] - bz0) / BUCKET));
    for (let j = j0; j <= j1; j++)
      for (let i = i0; i <= i1; i++) {
        const k = j * bnx + i;
        (buckets[k] || (buckets[k] = [])).push(idx);
      }
  });
  for (const b of buckets) if (b) b.sort((a, c) => a - c);
  for (const f of feats) {
    if (f.t === FT.SEG) {
      f.dx = f.bx - f.ax;
      f.dz = f.bz - f.az;
      f.L = Math.hypot(f.dx, f.dz) || 1e-6;
      f.L2 = f.L * f.L;
    }
  }

  function terrAt(x, z) {
    if (!g) return 0;
    const fx = (x - g.x0) / g.cell;
    const fz = (z - g.z0) / g.cell;
    if (fx < 0 || fz < 0 || fx >= g.nx - 1 || fz >= g.nz - 1) return 0;
    const i = fx | 0;
    const j = fz | 0;
    const u = fx - i;
    const v = fz - j;
    const d = g.data;
    const k = j * g.nx + i;
    return (d[k] * (1 - u) + d[k + 1] * u) * (1 - v) + (d[k + g.nx] * (1 - u) + d[k + g.nx + 1] * u) * v;
  }

  // scratch state for the current sample
  let H = 0;
  let TOP = 0;
  let SIDE = 0;
  let FLAGS = 0;
  let RBEST = 1e9;
  let GEN = 0;
  const maxPid = feats.reduce((m, f) => Math.max(m, f.pid || 0), 0) + 1;
  const PGEN = new Int32Array(maxPid);
  const PBEST = new Float64Array(maxPid);

  function applyRect(f, x, z) {
    if (x < f.x0 || x >= f.x1 || z < f.z0 || z >= f.z1) return;
    apply(f, f.y, x, z);
  }

  function applyEll(f, x, z) {
    const dx = (x - f.cx) / f.rx;
    const dz = (z - f.cz) / f.rz;
    const r2 = dx * dx + dz * dz;
    if (r2 < 1) {
      apply(f, f.y, x, z);
      return;
    }
    if (f.rimW) {
      // rim ring measured roughly in metres
      const r = Math.sqrt(r2);
      const dm = (r - 1) * Math.min(f.rx, f.rz);
      if (dm < f.rimW) {
        const y = f.rimY;
        if (f.mode === MODE.SET || H < y) H = y;
        TOP = f.rimTop;
        SIDE = f.rimSide;
      }
    }
  }

  function applySeg(f, x, z) {
    let t = ((x - f.ax) * f.dx + (z - f.az) * f.dz) / f.L2;
    const capT = t;
    t = t < 0 ? 0 : t > 1 ? 1 : t;
    const qx = f.ax + f.dx * t - x;
    const qz = f.az + f.dz * t - z;
    const dist = Math.sqrt(qx * qx + qz * qz);
    if (f.kind === KIND.RIVER) {
      // only the nearest river segment classifies this sample
      if (dist >= f.hw + f.qw || dist >= RBEST) return;
      RBEST = dist;
      const zr = f.az + f.dz * t;
      if (dist < f.hw) {
        const lv = poolLevelAt(zr);
        H = lv;
        TOP = TM.WATER;
        SIDE = TM.FALL;
        // foam below falls
        for (let i = 0; i < POOLS.length - 1; i++) {
          const zf = POOLS[i].zN;
          if (zr > zf && zr < zf + 7) TOP = TM.FOAM;
        }
        FLAGS |= 1;
      } else if (dist < f.hw + f.qw) {
        H = quayAt(zr);
        TOP = dist < f.hw + 1.0 ? TM.MASONRY : TM.ROAD;
        SIDE = TM.MASONRY;
        // stair treads on the ramps
        for (let i = 0; i < POOLS.length - 1; i++) {
          const zf = POOLS[i].zN;
          if (zr > zf && zr < zf + RAMP && dist >= f.hw + 1.0) TOP = TM.STEPS;
        }
      }
      return;
    }
    // square caps unless requested round
    if (!f.round && (capT < -0.001 || capT > 1.001)) {
      const over = capT < 0 ? -capT * f.L : (capT - 1) * f.L;
      if (over > 0.01) return;
    }
    if (dist >= f.hw) return;
    if (f.pid) {
      // within one path only the nearest segment classifies the sample
      if (PGEN[f.pid] === GEN && dist >= PBEST[f.pid]) return;
      PGEN[f.pid] = GEN;
      PBEST[f.pid] = dist;
    }
    let y = f.ay + (f.by - f.ay) * t;
    if (f.kind === KIND.WALL || f.kind === KIND.PALWALL) {
      // lateral position (positive = left of a->b)
      const lat = (f.dx * (z - f.az) - f.dz * (x - f.ax)) / f.L;
      const outer = lat * f.outer; // >0 toward the outside
      const s = f.s0 + t * f.L;
      if (outer > f.hw - 0.6) {
        const merlon = s % 2.4 < 1.4;
        y += merlon ? 1.6 : 0.6;
        applyWall(f, y);
        TOP = f.kind === KIND.PALWALL ? TM.GLAZE_Y : TM.BRICK;
        return;
      }
      if (-outer > f.hw - 0.4) {
        y += f.kind === KIND.PALWALL ? 1.2 : 0.8;
        applyWall(f, y);
        TOP = f.kind === KIND.PALWALL ? TM.GLAZE_Y : TM.BRICK;
        return;
      }
      applyWall(f, y);
      return;
    }
    apply(f, y, x, z);
  }

  function applyWall(f, y) {
    if (f.mode === MODE.SET || H < y) {
      H = y;
      TOP = f.top;
      SIDE = f.side;
    }
  }

  function apply(f, y, x, z) {
    switch (f.mode) {
      case MODE.SET:
        H = y;
        break;
      case MODE.MAX:
        if (H >= y) return;
        H = y;
        break;
      case MODE.MIN:
        if (H <= y) return;
        H = y;
        break;
      case MODE.PAINT:
        break;
      case MODE.TERR: {
        const st = f.step;
        H = Math.round(H / st) * st;
        if (f.grid && (((x % f.grid) + f.grid) % f.grid < 0.45 || ((z % f.grid) + f.grid) % f.grid < 0.45)) {
          TOP = TM.EARTH;
          SIDE = f.side;
          H += 0.2;
          return;
        }
        break;
      }
      case MODE.SETUP:
        if (H < y) H = y;
        break;
    }
    if (f.top >= 0) TOP = f.top;
    if (f.side >= 0) SIDE = f.side;
    if (f.flags) FLAGS |= f.flags;
  }

  function sample(x, z, cell, out) {
    H = geo.height(x, z, cell);
    TOP = TM.NATURAL;
    SIDE = TM.NATURAL;
    FLAGS = 0;
    RBEST = 1e9;
    GEN++;
    const tr = terrAt(x, z);
    if (tr > 0.5) {
      H = Math.round(H / TERRACE) * TERRACE;
      TOP = TM.GARDEN;
      SIDE = TM.MASONRY;
      FLAGS |= 2;
    }
    const bi = Math.floor((x - bx0) / BUCKET);
    const bj = Math.floor((z - bz0) / BUCKET);
    if (bi >= 0 && bj >= 0 && bi < bnx && bj < bnz) {
      const b = buckets[bj * bnx + bi];
      if (b) {
        for (let n = 0; n < b.length; n++) {
          const f = feats[b[n]];
          if (f.t === FT.RECT) applyRect(f, x, z);
          else if (f.t === FT.ELL) applyEll(f, x, z);
          else applySeg(f, x, z);
        }
      }
    }
    out[0] = Math.round(H * 5) / 5;
    out[1] = TOP;
    out[2] = SIDE;
    out[3] = FLAGS;
    return out;
  }

  const tmp = new Float64Array(4);
  function heightAt(x, z) {
    return sample(x, z, 0.2, tmp)[0];
  }

  return { sample, heightAt, geo, terrAt };
}
