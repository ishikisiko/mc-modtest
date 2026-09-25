// Offline preview of voxel prefabs: ray-cast orthographic render to PNG + mesh stats.
// Usage: node tools/prefab-preview.mjs out.png <prefabId|kind:json> [lod] [yawDeg] [px]
import { writePNG } from './png.mjs';
import { buildPrefab } from '../src/voxel/builders.js';
import { meshModel } from '../src/voxel/mesher.js';
import { makeCatalog } from '../src/voxel/catalog.js';
import { buildPlan } from '../src/world/plan.js';
import { PAL_RGB } from '../src/voxel/palette.js';

const [out, specList, lodArg, yawArg, pxArg] = process.argv.slice(2);
const cat = makeCatalog(1);
let planU = null;
// split on commas that are not inside a JSON object
const specs = [];
{
  let depth = 0;
  let cur = '';
  for (const ch of specList) {
    if (ch === '{') depth++;
    if (ch === '}') depth--;
    if (ch === ',' && depth === 0) {
      specs.push(cur);
      cur = '';
    } else cur += ch;
  }
  if (cur) specs.push(cur);
}
const tiles = [];
for (const spec of specs) {
let def;
if (spec.startsWith('{')) def = JSON.parse(spec);
else {
  def = cat.defs[spec];
  if (!def) {
    planU = planU || buildPlan(1).uniques;
    def = planU[spec];
  }
}
if (!def) throw new Error('unknown prefab ' + spec);
let t0 = performance.now();
const model = buildPrefab(def);
const tb = performance.now() - t0;
const solid = model.data.reduce((a, v) => a + (v ? 1 : 0), 0);
console.log(`${def.id || def.kind}: dims ${model.sx}x${model.sy}x${model.sz} (${(model.sx * 0.2).toFixed(1)}x${(model.sy * 0.2).toFixed(1)}x${(model.sz * 0.2).toFixed(1)} m), solid ${solid}, build ${tb.toFixed(0)} ms`);
for (let L = 0; L <= 4; L++) {
  t0 = performance.now();
  const m = meshModel(model, L);
  console.log(`  LOD${L}: quads ${m.quads}, ${(performance.now() - t0).toFixed(0)} ms`);
}

// ray-cast render
const lod = Number(lodArg || 0);
const yaw = ((Number(yawArg || 35) * Math.PI) / 180);
const pitch = (32 * Math.PI) / 180;
const { sx, sy, sz, data } = model;
const d = [-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), -Math.cos(yaw) * Math.cos(pitch)];
const up0 = [0, 1, 0];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => {
  const l = Math.hypot(...a);
  return a.map((v) => v / l);
};
const right = norm(cross(d, up0));
const up = cross(right, d);
const diag = Math.hypot(sx, sy, sz);
const px = Number(pxArg || 2);
const W = Math.ceil(diag * px) + 8;
const H = W;
const rgb = new Uint8Array(W * H * 3);
const c = [sx / 2, sy / 2, sz / 2];
const light = norm([0.5, 0.8, 0.35]);
const get = (x, y, z) => (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz ? 0 : data[(y * sz + z) * sx + x]);
for (let j = 0; j < H; j++) {
  for (let i = 0; i < W; i++) {
    const u = (i - W / 2) / px;
    const v = (H / 2 - j) / px;
    const o = [c[0] + right[0] * u + up[0] * v - d[0] * diag, c[1] + right[1] * u + up[1] * v - d[1] * diag, c[2] + right[2] * u + up[2] * v - d[2] * diag];
    // DDA
    let x = Math.floor(o[0]);
    let y = Math.floor(o[1]);
    let z = Math.floor(o[2]);
    const step = d.map((v2) => (v2 > 0 ? 1 : -1));
    const tDelta = d.map((v2) => Math.abs(1 / v2));
    const tMax = [0, 1, 2].map((k) => {
      const p = o[k];
      const fl = Math.floor(p);
      return d[k] > 0 ? (fl + 1 - p) * tDelta[k] : (p - fl) * tDelta[k];
    });
    let hit = 0;
    let nrm = 1;
    for (let s = 0; s < diag * 3 + 10; s++) {
      if (tMax[0] < tMax[1] && tMax[0] < tMax[2]) {
        x += step[0];
        tMax[0] += tDelta[0];
        nrm = 0;
      } else if (tMax[1] < tMax[2]) {
        y += step[1];
        tMax[1] += tDelta[1];
        nrm = 1;
      } else {
        z += step[2];
        tMax[2] += tDelta[2];
        nrm = 2;
      }
      const m = get(x, y, z);
      if (m) {
        hit = m;
        break;
      }
    }
    const p = (j * W + i) * 3;
    if (!hit) {
      rgb[p] = 225;
      rgb[p + 1] = 230;
      rgb[p + 2] = 235;
      continue;
    }
    const n = [0, 0, 0];
    n[nrm] = -step[nrm];
    const ndl = Math.max(0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2]);
    const shade = 0.45 + 0.65 * ndl;
    rgb[p] = Math.min(255, PAL_RGB[hit * 3] * 255 * shade);
    rgb[p + 1] = Math.min(255, PAL_RGB[hit * 3 + 1] * 255 * shade);
    rgb[p + 2] = Math.min(255, PAL_RGB[hit * 3 + 2] * 255 * shade);
  }
}
void lod;
tiles.push({ W, H, rgb });
}
// tile horizontally (wrap rows at ~1600 px)
const rows = [];
let row = [];
let rw = 0;
for (const t of tiles) {
  if (rw + t.W > 1600 && row.length) {
    rows.push(row);
    row = [];
    rw = 0;
  }
  row.push(t);
  rw += t.W;
}
if (row.length) rows.push(row);
const OW = Math.max(...rows.map((r) => r.reduce((a, t) => a + t.W, 0)));
const OH = rows.reduce((a, r) => a + Math.max(...r.map((t) => t.H)), 0);
const img = new Uint8Array(OW * OH * 3).fill(235);
let oy = 0;
for (const r of rows) {
  let ox = 0;
  for (const t of r) {
    for (let j = 0; j < t.H; j++) for (let i = 0; i < t.W; i++) for (let k = 0; k < 3; k++) img[((oy + j) * OW + ox + i) * 3 + k] = t.rgb[(j * t.W + i) * 3 + k];
    ox += t.W;
  }
  oy += Math.max(...r.map((t) => t.H));
}
writePNG(out, OW, OH, img);
console.log(`wrote ${out} ${OW}x${OH}`);
