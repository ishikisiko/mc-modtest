// Chinese roof rasteriser: 庑殿 hip, 歇山 hip-gable, 悬山/硬山 gable, 攒尖 pyramid (n-gon / round),
// and skirt (腰檐) rings. Concave 举折 profile, 起翘 corner lift, 飞檐 corner tips, 筒瓦 tile rows,
// ridges, hip ridges, 鸱吻 ridge ornaments and a gold 宝顶 finial.
import { PAL } from './palette.js';
import { polyDist } from './model.js';

export const TILESETS = {
  grey: { main: PAL.TILE, alt: PAL.TILE_L, ridge: PAL.RIDGE, edge: PAL.EAVE, orn: PAL.RIDGE, gable: PAL.PLASTER },
  yellow: { main: PAL.TILE_Y, alt: PAL.TILE_YL, ridge: PAL.TILE_Y, edge: PAL.TILE_Y, orn: PAL.TILE_YL, gable: PAL.RED },
  green: { main: PAL.TILE_G, alt: PAL.TILE_GL, ridge: PAL.TILE_Y, edge: PAL.TILE_G, orn: PAL.TILE_YL, gable: PAL.RED },
  blue: { main: PAL.TILE_B, alt: PAL.TILE_B, ridge: PAL.TILE_Y, edge: PAL.TILE_B, orn: PAL.GOLD, gable: PAL.RED },
  dark: { main: PAL.RIDGE, alt: PAL.TILE, ridge: PAL.RIDGE, edge: PAL.EAVE, orn: PAL.RIDGE, gable: PAL.PLASTER },
};

const mod = (a, n) => ((a % n) + n) % n;

function profile(u, curve) {
  // concave: gentle at the eave, steep at the ridge
  return (1 - curve) * u + curve * u * u;
}

// Rectangular roof. s: { x0,x1,z0,z1 (eave outline, voxels, exclusive max), y (eave layer),
//   type: 'hip'|'xieshan'|'gable'|'flat', rise, curve, lift, thick, tiles, wall:[x0,z0,x1,z1] (for soffit),
//   ring: [x0,z0,x1,z1] (only rasterise outside this rect: skirt roof), ringRise,
//   ridgeH, ornaments, gableEnds: 'overhang'|'wall', tipLen }
export function roofRect(M, s) {
  const { x0, x1, z0, z1, y } = s;
  const type = s.type || 'xieshan';
  const W = x1 - x0;
  const D = z1 - z0;
  const alongX = W >= D;
  const half = (alongX ? D : W) / 2;
  const rise = s.rise !== undefined ? s.rise : Math.round(half * 0.72);
  const curve = s.curve !== undefined ? s.curve : 0.62;
  const lift = s.lift !== undefined ? s.lift : Math.max(1, Math.round(half * 0.16));
  const liftR = s.liftR !== undefined ? s.liftR : half * 0.75;
  const thick = s.thick || 2;
  const ts = s.tiles || TILESETS.grey;
  const k = s.hipK || 1.15;
  const g = s.gableInset !== undefined ? s.gableInset : Math.round(half * 0.5);
  const ridgeH = s.ridgeH !== undefined ? s.ridgeH : type === 'gable' ? 2 : 3;
  const ring = s.ring;
  const wall = s.wall;
  const rowStep = s.rowStep || 3;
  // first pass: heights
  const Hh = new Float32Array(W * D);
  const kind = new Uint8Array(W * D); // 0 slope, 1 end-slope, 2 gable zone
  for (let z = 0; z < D; z++) {
    for (let x = 0; x < W; x++) {
      const cx = x + 0.5;
      const cz = z + 0.5;
      const tS = alongX ? Math.min(cz, D - cz) : Math.min(cx, W - cx);
      const tL = alongX ? Math.min(cx, W - cx) : Math.min(cz, D - cz);
      let t;
      let onEnd = false;
      if (type === 'gable' || type === 'flat') t = tS;
      else if (type === 'hip') {
        t = Math.min(tS, tL * k);
        onEnd = tL * k < tS;
      } else {
        if (tL >= g) {
          t = tS;
          kind[z * W + x] = 2;
        } else {
          t = Math.min(tS, tL * k);
          onEnd = tL * k < tS;
        }
      }
      if (onEnd) kind[z * W + x] = 1;
      const u = Math.min(1, t / half);
      let h;
      if (ring) h = (s.ringRise || rise) * Math.min(1, t / Math.max(1, s.ringDepth || half));
      else h = type === 'flat' ? 0 : rise * profile(u, curve);
      if (type !== 'gable' && type !== 'flat') {
        const cd = tS + tL;
        const lf = Math.max(0, 1 - cd / liftR);
        h += lift * lf * lf;
      } else if (type === 'gable') {
        // slight rise toward the gable ends (生起)
        const lf = Math.max(0, 1 - tL / (half * 0.9));
        h += lift * 0.5 * lf * lf * (1 - u);
      }
      Hh[z * W + x] = h;
    }
  }
  // raster
  for (let z = 0; z < D; z++) {
    for (let x = 0; x < W; x++) {
      const wx = x0 + x;
      const wz = z0 + z;
      if (ring && wx >= ring[0] && wx < ring[2] && wz >= ring[1] && wz < ring[3]) continue;
      const cx = x + 0.5;
      const cz = z + 0.5;
      const tS = alongX ? Math.min(cz, D - cz) : Math.min(cx, W - cx);
      const tL = alongX ? Math.min(cx, W - cx) : Math.min(cz, D - cz);
      const kd = kind[z * W + x];
      const h = Hh[z * W + x];
      const top = y + Math.round(h);
      const t = kd === 1 ? tL * k : tS;
      const eaveRow = Math.min(tS, tL) < 1.01 && !ring ? true : ring ? Math.min(tS, tL) < 1.01 : false;
      // tile row along the eave direction of this slope
      const rowC = kd === 1 ? (alongX ? wz : wx) : alongX ? wx : wz;
      const tube = mod(rowC, rowStep) === 0 && !eaveRow;
      let mat = eaveRow ? ts.edge : ts.main;
      for (let yy = top - thick + 1; yy <= top; yy++) M.set(wx, yy, wz, mat);
      if (tube) M.set(wx, top + 1, wz, ts.alt);
      // soffit below the overhang
      const outside = wall ? wx < wall[0] || wx >= wall[2] || wz < wall[1] || wz >= wall[3] : false;
      if (outside) M.set(wx, top - thick, wz, PAL.SOFFIT);
      // ridges
      if (!ring && type !== 'flat') {
        const isMain = kd !== 1 && tS >= half - 1.0;
        const isHip = (type === 'hip' || type === 'xieshan') && kd !== 2 && Math.abs(tS - tL * k) < 0.9 && tS < half - 0.5;
        const isGableEdge = type === 'xieshan' && kd === 2 && tL < g + 1 && tS < half - 0.5;
        if (isMain) for (let r = 1; r <= ridgeH; r++) M.set(wx, top + r, wz, ts.ridge);
        else if (isHip || isGableEdge) {
          M.set(wx, top + 1, wz, ts.ridge);
          if (tS > 3) M.set(wx, top + 2, wz, ts.ridge);
        }
        if (type === 'xieshan' && kd === 1) {
          // vertical gable (山花) wall filling the step up to the gable roof
          const tS2 = tS;
          const hg = y + Math.round(rise * profile(Math.min(1, tS2 / half), curve));
          if (tL >= g - 1.01 && hg > top) for (let yy = top + 1; yy <= hg; yy++) M.set(wx, yy, wz, ts.gable);
        }
      }
    }
  }
  // main ridge ornaments (鸱吻 / upturned ridge ends)
  if (!ring && type !== 'flat' && s.ornaments !== false) {
    const topY = y + Math.round(rise + (type === 'gable' ? 0 : 0));
    let ra;
    let rb;
    if (type === 'hip') {
      const ext = half / k;
      ra = alongX ? x0 + Math.round(ext) : z0 + Math.round(ext);
      rb = alongX ? x1 - Math.round(ext) - 1 : z1 - Math.round(ext) - 1;
    } else if (type === 'xieshan') {
      ra = alongX ? x0 + g : z0 + g;
      rb = alongX ? x1 - g - 1 : z1 - g - 1;
    } else {
      ra = alongX ? x0 : z0;
      rb = alongX ? x1 - 1 : z1 - 1;
    }
    const mid = alongX ? Math.floor((z0 + z1) / 2) : Math.floor((x0 + x1) / 2);
    if (rb > ra) {
      for (const e of [ra, rb]) {
        const dir = e === ra ? -1 : 1;
        for (let r = 0; r < 3; r++) {
          const hy = topY + ridgeH + 1 + r;
          const off = r === 2 ? -dir : 0;
          if (alongX) {
            M.set(e + off, hy, mid, ts.orn);
            M.set(e + off, hy, mid - 1, ts.orn);
          } else {
            M.set(mid, hy, e + off, ts.orn);
            M.set(mid - 1, hy, e + off, ts.orn);
          }
        }
      }
    }
  }
  // flying corner tips (飞檐)
  if (type !== 'gable' && type !== 'flat') {
    const tipLen = s.tipLen !== undefined ? s.tipLen : Math.max(2, Math.round(half * 0.12) + 1);
    const cornerY = y + Math.round(lift) + 0;
    const corners = [
      [x0, z0, -1, -1],
      [x1 - 1, z0, 1, -1],
      [x0, z1 - 1, -1, 1],
      [x1 - 1, z1 - 1, 1, 1],
    ];
    for (const [cx, cz, dx, dz] of corners) {
      for (let i = 1; i <= tipLen; i++) {
        const yy = cornerY + Math.round(i * 0.9) + 1;
        M.set(cx + dx * i, yy, cz + dz * i, ts.ridge);
        M.set(cx + dx * i, yy - 1, cz + dz * i, ts.edge);
        if (i < tipLen) {
          M.set(cx + dx * i - dx, yy - 1, cz + dz * i, ts.edge);
          M.set(cx + dx * i, yy - 1, cz + dz * i - dz, ts.edge);
        }
      }
    }
  }
}

// Pyramid (攒尖) roof over a regular polygon (sides 4/6/8) or circle (sides 0), centred at (cx, cz)
// with apothem R at the eave, eave layer y. Optional ring (skirt) mode rasterises only r > ringR.
export function roofPyramid(M, s) {
  const { cx, cz, R, y } = s;
  const sides = s.sides || 0;
  const rise = s.rise !== undefined ? s.rise : Math.round(R * 1.0);
  const curve = s.curve !== undefined ? s.curve : 0.55;
  const lift = s.lift !== undefined ? s.lift : Math.max(1, Math.round(R * 0.18));
  const thick = s.thick || 2;
  const ts = s.tiles || TILESETS.grey;
  const ringR = s.ringR || 0;
  const ringRise = s.ringRise || rise;
  const RR = Math.ceil(R + 2);
  const vertexAng = sides ? (Math.PI * 2) / sides : 0;
  const angOff = sides === 4 ? Math.PI / 4 : 0; // square pyramids have corners on the diagonals
  for (let z = Math.floor(cz - RR); z <= cz + RR; z++) {
    for (let x = Math.floor(cx - RR); x <= cx + RR; x++) {
      const dx = x + 0.5 - cx;
      const dz = z + 0.5 - cz;
      const d = polyDist(dx, dz, sides);
      if (d > R) continue;
      if (ringR && d < ringR) continue;
      const t = R - d;
      const u = Math.min(1, t / R);
      let h;
      if (ringR) h = ringRise * Math.min(1, t / Math.max(1, R - ringR));
      else h = rise * profile(u, curve);
      // corner proximity
      let cf = 0;
      if (sides) {
        const a = Math.atan2(dz, dx) - angOff;
        const rel = mod(a, vertexAng);
        const delta = Math.min(rel, vertexAng - rel);
        cf = 1 - delta / (vertexAng / 2);
      }
      const lf = Math.max(0, 1 - t / (R * 0.45));
      h += lift * lf * lf * (sides ? cf * cf : 0.3);
      const top = y + Math.round(h);
      const eaveRow = t < 1.01;
      const ang = Math.atan2(dz, dx);
      const rowC = Math.round((ang * R) / 1.0);
      const tube = mod(rowC, 3) === 0 && !eaveRow && !ringR;
      const mat = eaveRow ? ts.edge : ts.main;
      for (let yy = top - thick + 1; yy <= top; yy++) M.set(x, yy, z, mat);
      if (tube) M.set(x, top + 1, z, ts.alt);
      if (s.wallR !== undefined && d > s.wallR) M.set(x, top - thick, z, PAL.SOFFIT);
      // hip ridges along vertices
      if (sides && !ringR) {
        const a = Math.atan2(dz, dx) - angOff;
        const rel = mod(a, vertexAng);
        const delta = Math.min(rel, vertexAng - rel);
        if (delta * Math.hypot(dx, dz) < 0.75 && u < 0.97) M.set(x, top + 1, z, ts.ridge);
      }
    }
  }
  // corner tips
  if (sides && s.tips !== false) {
    const tipLen = s.tipLen !== undefined ? s.tipLen : Math.max(2, Math.round(R * 0.2));
    const rv = R / Math.cos(Math.PI / sides); // circumradius
    for (let i = 0; i < sides; i++) {
      const a = i * vertexAng + angOff;
      const ux = Math.cos(a);
      const uz = Math.sin(a);
      for (let k = 0; k <= tipLen; k++) {
        const r = rv - 0.5 + k;
        const px = cx + ux * r;
        const pz = cz + uz * r;
        const yy = y + Math.round(lift) + 1 + Math.round(k * 0.9);
        M.set(px, yy, pz, ts.ridge);
        M.set(px, yy - 1, pz, ts.edge);
      }
    }
  }
  // finial
  if (!ringR && s.finial !== false) {
    const topY = y + rise + 1;
    finial(M, cx, topY, cz, s.finialH || Math.max(4, Math.round(R * 0.35)), s.finialMat || PAL.GOLD);
  }
}

export function finial(M, cx, y, cz, h, mat = PAL.GOLD) {
  const x = Math.round(cx);
  const z = Math.round(cz);
  M.box(x - 2, y, z - 2, x + 2, y + 1, z + 2, mat);
  M.box(x - 1, y + 1, z - 1, x + 1, y + h, z + 1, mat);
  const b = y + Math.max(2, Math.floor(h * 0.5));
  M.box(x - 2, b, z - 2, x + 2, b + 2, z + 2, mat);
  M.box(x - 1, y + h, z - 1, x + 1, y + h + 1, z + 1, mat);
}
