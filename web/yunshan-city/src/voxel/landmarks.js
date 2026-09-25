// Unique / civic structures: palace halls, gates, towers (楼阁), pagoda, pavilions (亭),
// bridges, drum tower, street lanterns.
import { VoxelModel, polyDist } from './model.js';
import { PAL } from './palette.js';
import { Rng } from '../world/noise.js';
import { roofRect, roofPyramid, TILESETS, finial } from './roof.js';
import { platform, stairs, body, brackets, lantern, plaque, balustrade, spread, column } from './arch.js';

// ---------------------------------------------------------------- halls
export function buildHall(def) {
  const tiles = TILESETS[def.tile || 'grey'];
  const rank = def.rank || 1;
  const w = def.w;
  const d = def.d;
  const tiers = def.platform || 1;
  const th = rank >= 3 ? 6 : rank === 2 ? 5 : 4;
  const inset = 12;
  const M = new VoxelModel(-w / 2 - 24, -2, -d / 2 - 70, w / 2 + 24, 230, d / 2 + 70);
  let px0 = -w / 2;
  let px1 = w / 2;
  let pz0 = -d / 2;
  let pz1 = d / 2;
  let y = 0;
  const sw = rank >= 3 ? 30 : rank === 2 ? 20 : 14;
  for (let t = 0; t < tiers; t++) {
    platform(M, px0, pz0, px1, pz1, y, th, {
      mat: rank >= 2 ? PAL.MARBLE : PAL.STONE,
      sumeru: rank >= 2,
      trim: PAL.MARBLE,
      rail: rank >= 2,
      railMat: PAL.MARBLE,
      gaps: [
        [-sw / 2 - 1, pz1 - 2, sw / 2 + 1, pz1 + 1],
        [-sw / 4 - 1, pz0 - 1, sw / 4 + 1, pz0 + 2],
      ],
    });
    y += th;
    if (t < tiers - 1) {
      px0 += inset;
      px1 -= inset;
      pz0 += inset;
      pz1 -= inset;
    }
  }
  const mat = rank >= 2 ? PAL.MARBLE : PAL.STONE;
  stairs(M, -sw / 2, sw / 2, pz1, y, 0, 1, { mat, cheek: mat, ramp: rank >= 3 ? 4 : 0 });
  stairs(M, -sw / 4, sw / 4, pz0, y, 0, -1, { mat, cheek: mat });
  const bx0 = Math.round(px0 + 9);
  const bx1 = Math.round(px1 - 9);
  const bz0 = Math.round(pz0 + 9);
  const bz1 = Math.round(pz1 - 9);
  const H = rank >= 3 ? 36 : rank === 2 ? 28 : 22;
  const bayX = Math.max(3, Math.round((bx1 - bx0) / 17)) | 1;
  const bayZ = Math.max(2, Math.round((bz1 - bz0) / 17));
  body(M, {
    x0: bx0,
    z0: bz0,
    x1: bx1,
    z1: bz1,
    y0: y,
    y1: y + H,
    bayX,
    bayZ,
    colMat: PAL.RED,
    front: (i, n) => (n > 3 && (i === 0 || i === n - 1) ? 'window' : 'door'),
    back: 'window',
    left: 'wall',
    right: 'wall',
    wallMat: PAL.RED_WALL,
    lowWall: PAL.RED_WALL,
    beam: 'painted',
    beamH: 4,
  });
  let top = y + H;
  const bl = rank >= 2 ? 3 : 2;
  brackets(M, bx0, bz0, bx1, bz1, top, bl);
  top += bl;
  const eave = rank >= 3 ? 16 : rank === 2 ? 12 : 10;
  const roofType = def.roof === 'wudian' ? 'hip' : def.roof === 'gable' ? 'gable' : 'xieshan';
  if (def.double) {
    const ringRise = 7;
    roofRect(M, {
      x0: bx0 - eave,
      x1: bx1 + eave,
      z0: bz0 - eave,
      z1: bz1 + eave,
      y: top,
      type: 'hip',
      ring: [bx0 + 3, bz0 + 3, bx1 - 3, bz1 - 3],
      ringRise,
      ringDepth: eave + 3,
      tiles,
      lift: 5,
      tipLen: 4,
      wall: [bx0, bz0, bx1, bz1],
    });
    const cx0 = bx0 + 5;
    const cx1 = bx1 - 5;
    const cz0 = bz0 + 5;
    const cz1 = bz1 - 5;
    const uy0 = top + ringRise - 1;
    const uH = rank >= 3 ? 14 : 12;
    body(M, { x0: cx0, z0: cz0, x1: cx1, z1: cz1, y0: uy0, y1: uy0 + uH, bayX, bayZ, colMat: PAL.RED, front: 'window', back: 'window', left: 'window', right: 'window', lowWall: PAL.PAINT_G, beam: 'painted', beamH: 4 });
    top = uy0 + uH;
    brackets(M, cx0, cz0, cx1, cz1, top, bl);
    top += bl;
    const e2 = eave - 2;
    roofRect(M, { x0: cx0 - e2, x1: cx1 + e2, z0: cz0 - e2, z1: cz1 + e2, y: top, type: roofType, tiles, wall: [cx0, cz0, cx1, cz1], lift: 6, rise: Math.round(((cz1 - cz0) / 2 + e2) * (roofType === 'hip' ? 0.8 : 0.78)), ridgeH: 4, tipLen: 5 });
  } else {
    roofRect(M, { x0: bx0 - eave, x1: bx1 + eave, z0: bz0 - eave, z1: bz1 + eave, y: top, type: roofType, tiles, wall: [bx0, bz0, bx1, bz1], lift: 5, ridgeH: 3, tipLen: 4 });
  }
  // plaque under the eave
  plaque(M, 0, y + H - 8, bz1 - 1, Math.min(18, Math.round((bx1 - bx0) * 0.18)), 5, 1);
  // lanterns hanging at the front
  if (rank <= 2) for (const lx of [bx0 + 6, bx1 - 7]) lantern(M, lx, y + H - 1, bz1 + 1);
  return M;
}

// ---------------------------------------------------------------- palace gate
export function buildPalGate(def) {
  const w = def.w;
  const d = def.d;
  const M = new VoxelModel(-w / 2 - 20, -2, -d / 2 - 30, w / 2 + 20, 140, d / 2 + 40);
  const tiles = TILESETS.yellow;
  // red gate base with three arched passages
  const baseH = 30;
  M.box(-w / 2, 0, -d / 2, w / 2, baseH, d / 2, PAL.RED_WALL);
  M.box(-w / 2, baseH - 2, -d / 2, w / 2, baseH, d / 2, PAL.MARBLE);
  M.box(-w / 2, 0, -d / 2, w / 2, 3, d / 2, PAL.MARBLE);
  const arch = (cx, hw, h) => {
    for (let x = -hw; x < hw; x++) {
      const ax = x + 0.5;
      const top = h - hw + Math.sqrt(Math.max(0, hw * hw - ax * ax));
      for (let yy = 0; yy < top; yy++) for (let z = -d / 2; z < d / 2; z++) M.set(cx + x, yy, z, 0);
    }
  };
  arch(0, 10, 24);
  arch(-38, 7, 18);
  arch(38, 7, 18);
  // hall on top
  const bx0 = -w / 2 + 16;
  const bx1 = w / 2 - 16;
  const bz0 = -d / 2 + 8;
  const bz1 = d / 2 - 8;
  balustrade(M, -w / 2, -d / 2, w / 2, d / 2, baseH, PAL.MARBLE, [], 5);
  body(M, { x0: bx0, z0: bz0, x1: bx1, z1: bz1, y0: baseH, y1: baseH + 26, bayX: 7, bayZ: 2, colMat: PAL.RED, front: 'door', back: 'door', beam: 'painted', beamH: 4, wallMat: PAL.RED_WALL });
  let top = baseH + 26;
  brackets(M, bx0, bz0, bx1, bz1, top, 3);
  top += 3;
  roofRect(M, { x0: bx0 - 12, x1: bx1 + 12, z0: bz0 - 12, z1: bz1 + 12, y: top, type: 'xieshan', tiles, wall: [bx0, bz0, bx1, bz1], lift: 5, tipLen: 4 });
  plaque(M, 0, baseH + 18, bz1 - 1, 16, 5, 1);
  for (const lx of [bx0 + 4, bx1 - 5]) lantern(M, lx, top - 2, bz1 + 2);
  return M;
}

// ---------------------------------------------------------------- city gate
function crenels(M, x0, x1, z, y, dir) {
  for (let x = x0; x < x1; x++) {
    M.set(x, y, z, PAL.BRICK_D);
    M.set(x, y + 1, z, PAL.BRICK_D);
    if ((x - x0) % 8 < 5) {
      M.set(x, y + 2, z, PAL.BRICK_D);
      M.set(x, y + 3, z, PAL.BRICK_D);
      M.set(x, y + 4, z, PAL.BRICK_D);
    }
    void dir;
  }
}

function tunnel(M, cx, hw, h, z0, z1, axis = 'z') {
  for (let x = -hw; x < hw; x++) {
    const ax = x + 0.5;
    const top = h - hw + Math.sqrt(Math.max(0, hw * hw - ax * ax));
    for (let yy = 0; yy < top; yy++)
      for (let z = z0; z < z1; z++) {
        if (axis === 'z') M.set(cx + x, yy, z, 0);
        else M.set(z, yy, cx + x, 0);
      }
    // voussoir ring
    const ty = Math.round(top);
    for (let z = z0; z < z1; z += z1 - z0 - 1) {
      if (axis === 'z') {
        M.set(cx + x, ty, z, PAL.STONE_D);
        M.set(cx + x, ty + 1, z, PAL.STONE_D);
      } else {
        M.set(z, ty, cx + x, PAL.STONE_D);
        M.set(z, ty + 1, cx + x, PAL.STONE_D);
      }
    }
  }
}

function gateTower(M, x0, x1, z0, z1, y, floors, tiles, grand) {
  let top = y;
  let bx0 = x0;
  let bx1 = x1;
  let bz0 = z0;
  let bz1 = z1;
  for (let f = 0; f < floors; f++) {
    const H = f === 0 ? 26 : 20;
    body(M, { x0: bx0, z0: bz0, x1: bx1, z1: bz1, y0: top, y1: top + H, bayX: Math.max(3, Math.round((bx1 - bx0) / 16)) | 1, bayZ: 2, colMat: PAL.RED, front: 'door', back: 'window', left: 'window', right: 'window', beam: 'painted', beamH: 4, lowWall: PAL.RED_WALL });
    for (const [lx, lz] of [
      [bx0 - 1, bz1],
      [bx1, bz1],
      [bx0 - 1, bz0 - 1],
      [bx1, bz0 - 1],
    ])
      lantern(M, lx, top + H - 1, lz);
    top += H;
    brackets(M, bx0, bz0, bx1, bz1, top, 2);
    top += 2;
    if (f < floors - 1) {
      roofRect(M, { x0: bx0 - 10, x1: bx1 + 10, z0: bz0 - 10, z1: bz1 + 10, y: top, type: 'hip', ring: [bx0 + 2, bz0 + 2, bx1 - 2, bz1 - 2], ringRise: 6, ringDepth: 12, tiles, lift: 4 });
      // balcony
      M.box(bx0 - 3, top + 5, bz0 - 3, bx1 + 3, top + 6, bz1 + 3, PAL.WOOD_D);
      balustrade(M, bx0 - 3, bz0 - 3, bx1 + 3, bz1 + 3, top + 6, PAL.RED, [], 4);
      top += 6;
      bx0 += 4;
      bx1 -= 4;
      bz0 += 3;
      bz1 -= 3;
    }
  }
  roofRect(M, { x0: bx0 - 11, x1: bx1 + 11, z0: bz0 - 11, z1: bz1 + 11, y: top, type: 'xieshan', tiles, wall: [bx0, bz0, bx1, bz1], lift: grand ? 7 : 5, ridgeH: 4, tipLen: 5 });
  return top;
}

export function buildCityGate(def) {
  const w = def.w;
  const d = def.d;
  const baseH = Math.max(30, def.baseH || 45);
  const M = new VoxelModel(-w / 2 - 20, -4, -d / 2 - 20, w / 2 + 20, baseH + 150, d / 2 + 20);
  M.box(-w / 2, -3, -d / 2, w / 2, baseH, d / 2, PAL.BRICK);
  // stone plinth course
  M.box(-w / 2, -3, -d / 2, w / 2, 3, d / 2, PAL.STONE);
  tunnel(M, 0, 16, 38, -d / 2 - 1, d / 2 + 1);
  // door leaves (open, recessed)
  for (const x of [-16, 15]) M.box(x, 0, -d / 2 + 6, x + 1, 30, -d / 2 + 12, PAL.RED);
  // parapets
  crenels(M, -w / 2, w / 2, d / 2 - 1, baseH, 1);
  for (let x = -w / 2; x < w / 2; x++) {
    M.set(x, baseH, -d / 2, PAL.BRICK_D);
    M.set(x, baseH + 1, -d / 2, PAL.BRICK_D);
  }
  // plaque over the tunnel (outer face)
  plaque(M, 0, 41, d / 2 - 1, 16, 5, 1);
  gateTower(M, -w / 2 + 22, w / 2 - 22, -d / 2 + 16, d / 2 - 16, baseH, 2, TILESETS.grey, true);
  return M;
}

export function buildWaterGate(def) {
  const w = def.w;
  const d = def.d;
  const baseH = Math.max(30, def.baseH || 40);
  const M = new VoxelModel(-w / 2 - 10, -16, -d / 2 - 10, w / 2 + 10, baseH + 90, d / 2 + 10);
  M.box(-w / 2, -14, -d / 2, w / 2, baseH, d / 2, PAL.BRICK);
  M.box(-w / 2, -14, -d / 2, w / 2, 2, d / 2, PAL.STONE);
  // water arch (the river runs along z through x in [-30, 30])
  for (let x = -32; x < 32; x++) {
    const ax = x + 0.5;
    const top = 30 - 32 + Math.sqrt(Math.max(0, 32 * 32 - ax * ax));
    for (let yy = -14; yy < top; yy++) for (let z = -d / 2 - 1; z < d / 2 + 1; z++) M.set(x, yy, z, 0);
  }
  // portcullis bars
  for (let x = -30; x < 30; x += 4) for (let yy = 10; yy < 28; yy++) M.set(x, yy, d / 2 - 4, PAL.WOOD_D);
  crenels(M, -w / 2, w / 2, d / 2 - 1, baseH, 1);
  crenels(M, -w / 2, w / 2, -d / 2, baseH, -1);
  // small watch pavilion on top
  const px = 0;
  const pz = 0;
  for (const [cx, cz] of [
    [-12, -8],
    [10, -8],
    [-12, 6],
    [10, 6],
  ])
    column(M, px + cx, pz + cz, baseH, baseH + 18, PAL.RED);
  M.box(-12, baseH + 16, -8, 12, baseH + 18, 8, PAL.PAINT_B);
  roofRect(M, { x0: -20, x1: 20, z0: -16, z1: 16, y: baseH + 18, type: 'xieshan', tiles: TILESETS.grey, lift: 4 });
  lantern(M, -13, baseH + 17, 8);
  lantern(M, 12, baseH + 17, 8);
  return M;
}

// ---------------------------------------------------------------- drum tower
export function buildDrumTower(def) {
  const w = def.w;
  const d = def.d;
  const M = new VoxelModel(-w / 2 - 16, -2, -d / 2 - 16, w / 2 + 16, 160, d / 2 + 16);
  const baseH = 30;
  M.box(-w / 2, 0, -d / 2, w / 2, baseH, d / 2, PAL.BRICK);
  M.box(-w / 2, 0, -d / 2, w / 2, 2, d / 2, PAL.STONE);
  tunnel(M, 0, 9, 22, -d / 2 - 1, d / 2 + 1, 'z');
  tunnel(M, 0, 8, 20, -w / 2 - 1, w / 2 + 1, 'x');
  balustrade(M, -w / 2, -d / 2, w / 2, d / 2, baseH, PAL.STONE, [], 5);
  gateTower(M, -w / 2 + 12, w / 2 - 12, -d / 2 + 10, d / 2 - 10, baseH, 2, TILESETS.grey, false);
  return M;
}

// ---------------------------------------------------------------- multi-storey tower (楼阁)
export function buildLou(def) {
  const tiles = TILESETS[def.tile || 'grey'];
  const floors = def.floors || 3;
  const w = def.w;
  const d = def.d;
  const grand = !!def.grand;
  const M = new VoxelModel(-w / 2 - 30, -40, -d / 2 - 40, w / 2 + 30, 320, d / 2 + 40);
  // stone platform (for the grand tower, a tall rock-cut podium)
  const ph = grand ? 12 : 6;
  platform(M, -w / 2, -d / 2, w / 2, d / 2, grand ? -38 : 0, ph + (grand ? 38 : 0), { mat: PAL.STONE, rail: true, railMat: PAL.MARBLE, gaps: [[-10, d / 2 - 2, 10, d / 2 + 1]] });
  stairs(M, -8, 8, d / 2, ph, 0, 1, { mat: PAL.STONE, cheek: PAL.STONE });
  let y = ph;
  let inset = grand ? 14 : 8;
  let bx0 = -w / 2 + inset;
  let bx1 = w / 2 - inset;
  let bz0 = -d / 2 + inset;
  let bz1 = d / 2 - inset;
  for (let f = 0; f < floors; f++) {
    const H = grand ? (f === 0 ? 30 : 24 - f) : f === 0 ? 24 : 18;
    const bays = Math.max(3, Math.round((bx1 - bx0) / 15)) | 1;
    body(M, {
      x0: bx0,
      z0: bz0,
      x1: bx1,
      z1: bz1,
      y0: y,
      y1: y + H,
      bayX: bays,
      bayZ: bays,
      colMat: PAL.RED,
      front: 'door',
      back: 'window',
      left: 'window',
      right: 'window',
      beam: 'painted',
      beamH: 4,
      lowWall: PAL.RED_WALL,
    });
    for (const [lx, lz] of [
      [bx0 - 1, bz1],
      [bx1, bz1],
      [bx0 - 1, bz0 - 1],
      [bx1, bz0 - 1],
    ])
      lantern(M, lx, y + H - 1, lz);
    if (f === 0) plaque(M, 0, y + H - 9, bz1 - 1, Math.round((bx1 - bx0) * 0.3), 6, 1);
    y += H;
    brackets(M, bx0, bz0, bx1, bz1, y, grand ? 3 : 2);
    y += grand ? 3 : 2;
    if (f < floors - 1) {
      const e = grand ? 12 : 9;
      roofRect(M, { x0: bx0 - e, x1: bx1 + e, z0: bz0 - e, z1: bz1 + e, y, type: 'hip', ring: [bx0 + 2, bz0 + 2, bx1 - 2, bz1 - 2], ringRise: 6, ringDepth: e + 2, tiles, lift: grand ? 6 : 4, tipLen: grand ? 5 : 3 });
      // balcony (平座) with railing
      M.box(bx0 - 4, y + 5, bz0 - 4, bx1 + 4, y + 7, bz1 + 4, PAL.WOOD_D);
      balustrade(M, bx0 - 4, bz0 - 4, bx1 + 4, bz1 + 4, y + 7, PAL.RED, [], 4);
      // lanterns under the skirt eave corners
      for (const [lx, lz] of [
        [bx0 - e + 2, bz0 - e + 2],
        [bx1 + e - 3, bz0 - e + 2],
        [bx0 - e + 2, bz1 + e - 3],
        [bx1 + e - 3, bz1 + e - 3],
      ])
        lantern(M, lx, y + 2, lz);
      y += 7;
      const step = grand ? 3 : 2;
      bx0 += step;
      bx1 -= step;
      bz0 += step;
      bz1 -= step;
    }
  }
  const e = grand ? 14 : 10;
  if (grand) {
    // double-eave crown: skirt + xieshan
    roofRect(M, { x0: bx0 - e, x1: bx1 + e, z0: bz0 - e, z1: bz1 + e, y, type: 'hip', ring: [bx0 + 2, bz0 + 2, bx1 - 2, bz1 - 2], ringRise: 6, ringDepth: e + 2, tiles, lift: 7, tipLen: 6 });
    const cx0 = bx0 + 4;
    const cx1 = bx1 - 4;
    const cz0 = bz0 + 4;
    const cz1 = bz1 - 4;
    body(M, { x0: cx0, z0: cz0, x1: cx1, z1: cz1, y0: y + 5, y1: y + 17, bayX: 3, bayZ: 3, colMat: PAL.RED, front: 'window', back: 'window', left: 'window', right: 'window', beam: 'painted', beamH: 4, lowWall: PAL.PAINT_G });
    brackets(M, cx0, cz0, cx1, cz1, y + 17, 3);
    roofRect(M, { x0: cx0 - e, x1: cx1 + e, z0: cz0 - e, z1: cz1 + e, y: y + 20, type: 'xieshan', tiles, wall: [cx0, cz0, cx1, cz1], lift: 8, ridgeH: 4, tipLen: 6, rise: Math.round(((cz1 - cz0) / 2 + e) * 0.85) });
  } else {
    roofRect(M, { x0: bx0 - e, x1: bx1 + e, z0: bz0 - e, z1: bz1 + e, y, type: 'xieshan', tiles, wall: [bx0, bz0, bx1, bz1], lift: 5, tipLen: 4 });
  }
  return M;
}

// ---------------------------------------------------------------- pagoda (八角密檐/楼阁式塔)
export function buildPagoda(def) {
  const sides = def.sides || 8;
  const R0 = def.r;
  const floors = def.floors || 9;
  const M = new VoxelModel(-R0 - 30, -2, -R0 - 30, R0 + 30, 420, R0 + 30);
  // octagonal base platform with steps
  M.prism(0, 0, R0 + 12, 0, 8, PAL.STONE, sides);
  M.prism(0, 0, R0 + 12, 7, 8, PAL.MARBLE, sides);
  for (let a = 0; a < 4; a++) {
    for (let s = 0; s < 8; s++) {
      const dist = R0 + 12 + s * 2;
      for (let u = -6; u < 6; u++) {
        for (let k = 0; k < 2; k++) {
          const r = dist + k;
          const x = a === 0 ? u : a === 1 ? r : a === 2 ? u : -r - 1;
          const z = a === 0 ? r : a === 1 ? u : a === 2 ? -r - 1 : u;
          for (let yy = 0; yy < 8 - s; yy++) M.set(x, yy, z, PAL.STONE);
        }
      }
    }
  }
  let y = 8;
  for (let f = 0; f < floors; f++) {
    const r = Math.round(R0 * (1 - 0.034 * f));
    const H = f === 0 ? 34 : Math.round(20 - f * 0.5);
    // body
    M.prism(0, 0, r, y, y + H, PAL.PLASTER, sides);
    M.prism(0, 0, r - 2, y, y + H, PAL.INTERIOR, sides);
    // corner columns & beam band
    const rv = r / Math.cos(Math.PI / sides);
    for (let i = 0; i < sides; i++) {
      const a = (i / sides) * Math.PI * 2;
      const cx = Math.round(Math.cos(a) * (rv - 1.2));
      const cz = Math.round(Math.sin(a) * (rv - 1.2));
      M.box(cx - 1, y, cz - 1, cx + 1, y + H, cz + 1, PAL.RED);
    }
    for (let yy = y + H - 3; yy < y + H; yy++) M.prism(0, 0, r, yy, yy + 1, yy === y + H - 2 ? PAL.PAINT_G : PAL.PAINT_B, sides, 1);
    // doors / windows on alternating faces
    for (let i = 0; i < sides; i++) {
      if ((i + f) % 2) continue;
      const a = ((i + 0.5) / sides) * Math.PI * 2;
      const nx = Math.cos(a);
      const nz = Math.sin(a);
      const tx = -nz;
      const tz = nx;
      const hw = f === 0 ? 5 : 3;
      const dh = f === 0 ? 20 : Math.round(H * 0.55);
      for (let u = -hw; u < hw; u++)
        for (let v = 0; v < dh; v++) {
          const arch = v > dh - hw ? Math.hypot(u + 0.5, v - (dh - hw)) > hw : false;
          if (arch) continue;
          const px = Math.round(nx * (r - 0.5) + tx * (u + 0.5));
          const pz = Math.round(nz * (r - 0.5) + tz * (u + 0.5));
          M.set(px, y + 3 + v, pz, f === 0 ? PAL.RED : v % 3 === 0 ? PAL.LATTICE : PAL.PAPER);
          M.set(Math.round(px - nx), y + 3 + v, Math.round(pz - nz), PAL.INTERIOR);
        }
    }
    y += H;
    // eave ring
    const e = f === 0 ? 11 : 8;
    roofPyramid(M, { cx: 0, cz: 0, R: r + e, y, sides, ringR: r - 1, ringRise: 5, tiles: TILESETS.grey, lift: 3, thick: 2, finial: false, tipLen: 3 });
    // bells / lanterns at the corners
    const rvE = (r + e) / Math.cos(Math.PI / sides);
    for (let i = 0; i < sides; i++) {
      const a = (i / sides) * Math.PI * 2;
      lantern(M, Math.round(Math.cos(a) * (rvE + 1)), y + 4, Math.round(Math.sin(a) * (rvE + 1)), f % 2 ? PAL.LANTERN_W : PAL.LANTERN);
    }
    y += 5;
    if (f < 4 && f > 0) balustrade8(M, r + 3, y, sides);
  }
  // crown: octagonal pyramid + spire (塔刹)
  const rTop = Math.round(R0 * (1 - 0.034 * floors));
  roofPyramid(M, { cx: 0, cz: 0, R: rTop + 8, y, sides, tiles: TILESETS.grey, rise: rTop + 6, lift: 3, finial: false });
  const sy = y + rTop + 7;
  M.box(-2, sy, -2, 2, sy + 4, 2, PAL.GOLD);
  for (let k = 0; k < 7; k++) {
    const yy = sy + 4 + k * 4;
    M.box(-1, yy, -1, 1, yy + 4, 1, PAL.GOLD);
    M.box(-3 + (k > 3 ? 1 : 0), yy, -3 + (k > 3 ? 1 : 0), 3 - (k > 3 ? 1 : 0), yy + 1, 3 - (k > 3 ? 1 : 0), PAL.GOLD);
  }
  finial(M, 0, sy + 32, 0, 8, PAL.GOLD);
  return M;
}

function balustrade8(M, r, y, sides) {
  const R = Math.ceil(r + 1);
  for (let z = -R; z <= R; z++)
    for (let x = -R; x <= R; x++) {
      const d = polyDist(x + 0.5, z + 0.5, sides);
      if (d > r - 1 && d <= r) {
        M.set(x, y, z, PAL.WOOD_D);
        M.set(x, y + 3, z, PAL.RED);
        if ((x + z) % 5 === 0) {
          M.set(x, y + 1, z, PAL.RED);
          M.set(x, y + 2, z, PAL.RED);
        }
      } else if (d <= r - 1 && d > r - 4) M.set(x, y - 1, z, PAL.WOOD_D);
    }
}

// ---------------------------------------------------------------- pavilion (亭)
export function buildPavilion(def) {
  const sides = def.sides || 6;
  const r = def.r;
  const tiles = TILESETS[def.tile || 'grey'];
  const M = new VoxelModel(-r - 24, -2, -r - 24, r + 24, 160, r + 24);
  // base
  M.prism(0, 0, r + 5, 0, 4, PAL.STONE, sides);
  M.prism(0, 0, r + 5, 3, 4, PAL.MARBLE, sides);
  // steps in front
  for (let s = 0; s < 4; s++) M.box(-5, 0, Math.round(r + 5 + s * 2 - 1), 5, 4 - s, Math.round(r + 5 + s * 2 + 1), PAL.STONE);
  const H = def.double ? 22 : 20;
  const rv = r / (sides === 4 ? Math.SQRT1_2 * Math.SQRT2 : Math.cos(Math.PI / sides));
  // columns at vertices
  const cols = [];
  for (let i = 0; i < sides; i++) {
    const a = (i / sides) * Math.PI * 2 + (sides === 4 ? Math.PI / 4 : 0);
    const cr = sides === 4 ? r * Math.SQRT2 : rv;
    const cx = Math.round(Math.cos(a) * (cr - 1.5));
    const cz = Math.round(Math.sin(a) * (cr - 1.5));
    cols.push([cx, cz]);
    M.box(cx - 1, 4, cz - 1, cx + 1, 4 + H, cz + 1, PAL.RED);
  }
  // bench railing (美人靠) between columns except the front
  for (let i = 0; i < sides; i++) {
    const [ax, az] = cols[i];
    const [bx, bz] = cols[(i + 1) % sides];
    const mz = (az + bz) / 2;
    const mx = (ax + bx) / 2;
    if (mz > r * 0.6 && Math.abs(mx) < r * 0.6) continue; // front opening
    const n = Math.ceil(Math.hypot(bx - ax, bz - az));
    for (let k = 1; k < n; k++) {
      const x = Math.round(ax + ((bx - ax) * k) / n);
      const z = Math.round(az + ((bz - az) * k) / n);
      M.set(x, 4, z, PAL.WOOD_D);
      M.set(x, 5, z, PAL.WOOD_D);
      M.set(x, 6, z, PAL.RED);
      if (k % 3 === 0) M.set(x, 7, z, PAL.RED);
    }
  }
  // beam ring
  const ringR = sides === 4 ? r : r;
  for (let yy = 4 + H - 3; yy < 4 + H; yy++) M.prism(0, 0, ringR, yy, yy + 1, yy === 4 + H - 2 ? PAL.PAINT_G : PAL.PAINT_B, sides, 1);
  // hanging lanterns at the corners
  let top = 4 + H;
  const eaveR = r + 8;
  if (def.double) {
    roofPyramid(M, { cx: 0, cz: 0, R: eaveR, y: top, sides, ringR: r - 3, ringRise: 5, tiles, lift: 3, finial: false });
    M.prism(0, 0, r - 3, top, top + 10, PAL.RED, sides);
    M.prism(0, 0, r - 4, top, top + 10, PAL.INTERIOR, sides);
    for (let yy = top + 7; yy < top + 10; yy++) M.prism(0, 0, r - 3, yy, yy + 1, PAL.PAINT_B, sides, 1);
    top += 10;
    roofPyramid(M, { cx: 0, cz: 0, R: r + 4, y: top, sides, tiles, rise: Math.round((r + 4) * 1.05), lift: 3, finialH: 7, wallR: r - 3 });
  } else {
    roofPyramid(M, { cx: 0, cz: 0, R: eaveR, y: top, sides, tiles, rise: Math.round(eaveR * 0.95), lift: 3, finialH: 6, wallR: r });
  }
  for (const [cx, cz] of cols) lantern(M, Math.round(cx * 1.12), 4 + H - 1, Math.round(cz * 1.12));
  return M;
}

// ---------------------------------------------------------------- bridges
export function buildArchBridge(def) {
  const S = def.span;
  const W = def.width;
  const hump = Math.round(S * 0.11);
  const M = new VoxelModel(-S / 2 - 2, -16, -W / 2 - 2, S / 2 + 2, hump + 12, W / 2 + 2);
  const archA = Math.round(S * 0.24);
  const archB = Math.round(hump + 8);
  for (let x = -S / 2; x < S / 2; x++) {
    const u = (2 * (x + 0.5)) / S;
    const yd = Math.round(hump * (1 - u * u));
    for (let z = -W / 2; z < W / 2; z++) {
      for (let yy = -14; yy <= yd; yy++) {
        const ex = (x + 0.5) / archA;
        const ey = (yy + 10) / archB;
        const inArch = yy >= -12 && ex * ex + ey * ey < 1;
        if (inArch) continue;
        const ring = ex * ex + ey * ey < 1.35 && yy >= -12;
        M.set(x, yy, z, ring ? PAL.STONE_D : yy === yd ? PAL.STONE : PAL.STONE);
      }
      if (z === -W / 2 || z === W / 2 - 1) {
        // parapet
        const post = (x + S / 2) % 7 === 0;
        M.set(x, yd + 1, z, PAL.MARBLE);
        M.set(x, yd + 2, z, PAL.MARBLE);
        M.set(x, yd + 3, z, post ? PAL.MARBLE : 0);
        if (post) M.set(x, yd + 4, z, PAL.MARBLE);
      }
    }
  }
  return M;
}

export function buildLangQiao(def) {
  const S = def.span;
  const W = def.width;
  const M = new VoxelModel(-S / 2 - 8, -16, -W / 2 - 8, S / 2 + 8, 80, W / 2 + 8);
  const deckY = 5;
  // piers
  for (const px of [-Math.round(S * 0.18), Math.round(S * 0.18)]) M.box(px - 4, -14, -W / 2, px + 4, deckY, W / 2, PAL.STONE);
  M.box(-S / 2, -2, -W / 2, -S / 2 + 12, deckY, W / 2, PAL.STONE);
  M.box(S / 2 - 12, -2, -W / 2, S / 2, deckY, W / 2, PAL.STONE);
  // deck
  M.box(-S / 2, deckY - 1, -W / 2, S / 2, deckY + 1, W / 2, PAL.WOOD);
  // entry steps
  for (let s = 0; s < 5; s++) {
    M.box(-S / 2 - s * 2 - 2, 0, -W / 2 + 3, -S / 2 - s * 2, deckY - s, W / 2 - 3, PAL.STONE);
    M.box(S / 2 + s * 2, 0, -W / 2 + 3, S / 2 + s * 2 + 2, deckY - s, W / 2 - 3, PAL.STONE);
  }
  // corridor columns & benches
  const H = 18;
  for (let x = -S / 2 + 1; x < S / 2; x += 14) {
    for (const z of [-W / 2, W / 2 - 2]) M.box(x, deckY + 1, z, x + 2, deckY + 1 + H, z + 2, PAL.RED);
  }
  for (let x = -S / 2; x < S / 2; x++) {
    for (const z of [-W / 2, W / 2 - 1]) {
      M.set(x, deckY + 1, z, PAL.WOOD_D);
      M.set(x, deckY + 2, z, PAL.WOOD_D);
      M.set(x, deckY + 3, z, PAL.RED);
    }
    for (const z of [-W / 2, W / 2 - 1]) {
      M.set(x, deckY + H - 2, z, PAL.PAINT_B);
      M.set(x, deckY + H - 1, z, PAL.PAINT_G);
      M.set(x, deckY + H, z, PAL.PAINT_B);
    }
  }
  roofRect(M, { x0: -S / 2 - 3, x1: S / 2 + 3, z0: -W / 2 - 4, z1: W / 2 + 4, y: deckY + H + 1, type: 'gable', tiles: TILESETS.grey, lift: 2, rise: Math.round(W * 0.45) });
  // central pavilion roof
  roofRect(M, { x0: -16, x1: 16, z0: -W / 2 - 5, z1: W / 2 + 5, y: deckY + H + 9, type: 'xieshan', tiles: TILESETS.green, lift: 4, rise: Math.round(W * 0.5) });
  M.box(-12, deckY + H + 1, -W / 2, 12, deckY + H + 9, W / 2, PAL.RED);
  M.box(-11, deckY + H + 1, -W / 2 + 1, 11, deckY + H + 9, W / 2 - 1, PAL.INTERIOR);
  for (let x = -10; x < 10; x += 3) {
    M.set(x, deckY + H + 5, -W / 2, PAL.PAPER);
    M.set(x, deckY + H + 5, W / 2 - 1, PAL.PAPER);
  }
  for (let x = -S / 2 + 8; x < S / 2; x += 28) {
    lantern(M, x, deckY + H + 1, -W / 2 - 2);
    lantern(M, x, deckY + H + 1, W / 2 + 1);
  }
  return M;
}

// ---------------------------------------------------------------- street lantern
export function buildLamp() {
  const M = new VoxelModel(-3, -1, -3, 3, 20, 3);
  M.box(-2, 0, -2, 2, 1, 2, PAL.STONE);
  M.box(-1, 1, -1, 1, 12, 1, PAL.STONE_D);
  M.box(-2, 12, -2, 2, 13, 2, PAL.WOOD_D);
  M.box(-2, 13, -2, 2, 16, 2, PAL.LANTERN_W);
  for (const [x, z] of [
    [-2, -2],
    [1, -2],
    [-2, 1],
    [1, 1],
  ])
    M.box(x, 13, z, x + 1, 16, z + 1, PAL.WOOD_D);
  M.box(-3, 16, -3, 3, 17, 3, PAL.TILE);
  M.box(-1, 17, -1, 1, 18, 1, PAL.TILE);
  return M;
}
