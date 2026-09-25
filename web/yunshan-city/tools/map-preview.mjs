// Offline top-down hillshade preview of the world height field.
// Usage: node tools/map-preview.mjs out.png [x0 z0 x1 z1 metersPerPixel] [--natural]
import { writePNG } from './png.mjs';
import { createGeo, SITE } from '../src/world/geo.js';

const args = process.argv.slice(2);
const out = args[0] || 'map.png';
const nums = args.slice(1).filter((a) => !a.startsWith('--')).map(Number);
const [x0, z0, x1, z1, mpp] = nums.length >= 5 ? nums : [-700, -900, 700, 700, 1.5];
const natural = args.includes('--natural');

let sampler;
const geo = createGeo(1);
if (natural) {
  sampler = (x, z, cell) => ({ h: geo.height(x, z, cell), mat: 0 });
} else {
  const { createField } = await import('../src/world/field.js');
  const { buildPlan } = await import('../src/world/plan.js');
  const plan = buildPlan(1);
  const field = createField(plan);
  const out4 = new Float64Array(4);
  sampler = (x, z, cell) => {
    field.sample(x, z, cell, out4);
    return { h: out4[0], mat: out4[1] };
  };
}

const W = Math.round((x1 - x0) / mpp);
const H = Math.round((z1 - z0) / mpp);
const hs = new Float64Array((W + 1) * (H + 1));
const mats = new Uint8Array((W + 1) * (H + 1));
const t0 = performance.now();
for (let j = 0; j <= H; j++) {
  for (let i = 0; i <= W; i++) {
    const s = sampler(x0 + i * mpp, z0 + j * mpp, mpp);
    hs[j * (W + 1) + i] = s.h;
    mats[j * (W + 1) + i] = s.mat;
  }
}
const dt = performance.now() - t0;
console.log(`sampled ${(W + 1) * (H + 1)} in ${dt.toFixed(0)} ms (${((dt * 1000) / ((W + 1) * (H + 1))).toFixed(2)} us/sample)`);

const MATCOL = {
  7: [150, 140, 125], // road
  8: [175, 165, 150], // steps
  9: [95, 90, 88], // wall brick
  10: [120, 115, 105], // masonry
  11: [230, 225, 215], // marble
  12: [40, 90, 110], // water
  13: [90, 140, 150], // paddy
  15: [170, 160, 145], // plaza
  16: [150, 170, 70],
  17: [110, 100, 95],
  19: [230, 240, 245],
  20: [140, 60, 50], // lot
};

const rgb = new Uint8Array(W * H * 3);
let minH = 1e9;
let maxH = -1e9;
for (const h of hs) {
  if (h < minH) minH = h;
  if (h > maxH) maxH = h;
}
for (let j = 0; j < H; j++) {
  for (let i = 0; i < W; i++) {
    const k = j * (W + 1) + i;
    const h = hs[k];
    const dx = (hs[k + 1] - h) / mpp;
    const dz = (hs[k + W + 1] - h) / mpp;
    // light from north-west, normal = (-dx, 1, -dz)
    const len = Math.sqrt(dx * dx + dz * dz + 1);
    const ndl = (-dx * -0.5 + 1 * 0.7 + -dz * -0.5) / (len * 1.0488);
    const shade = 0.35 + 0.75 * Math.max(0, ndl);
    let c;
    const m = mats[k];
    if (MATCOL[m]) c = MATCOL[m];
    else if (h < SITE.cloudTop) c = [205, 215, 225];
    else {
      const t = Math.min(1, Math.max(0, (h - 0) / 420));
      const slope = Math.sqrt(dx * dx + dz * dz);
      c = slope > 1.1 ? [120, 115, 110] : [70 + 120 * t, 110 + 80 * t, 60 + 110 * t];
    }
    const contour = Math.abs(((h % 20) + 20) % 20) < 0.9 * Math.max(1, Math.sqrt(dx * dx + dz * dz) * mpp) ? 0.75 : 1;
    const p = (j * W + i) * 3;
    rgb[p] = Math.min(255, c[0] * shade * contour);
    rgb[p + 1] = Math.min(255, c[1] * shade * contour);
    rgb[p + 2] = Math.min(255, c[2] * shade * contour);
  }
}
// markers
function mark(x, z, col) {
  const i = Math.round((x - x0) / mpp);
  const j = Math.round((z - z0) / mpp);
  for (let dj = -3; dj <= 3; dj++)
    for (let di = -3; di <= 3; di++) {
      const ii = i + di;
      const jj = j + dj;
      if (ii < 0 || jj < 0 || ii >= W || jj >= H) continue;
      const p = (jj * W + ii) * 3;
      rgb[p] = col[0];
      rgb[p + 1] = col[1];
      rgb[p + 2] = col[2];
    }
}
mark(SITE.summit.x, SITE.summit.z, [255, 0, 0]);
mark(SITE.fallX, SITE.basin.z - SITE.basin.r, [0, 120, 255]);
mark(0, SITE.gateZ, [255, 200, 0]);
writePNG(out, W, H, rgb);
console.log(`height range ${minH.toFixed(1)} .. ${maxH.toFixed(1)}; wrote ${out} ${W}x${H}`);
