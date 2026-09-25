// Repeatable city buildings: 民居 (Hui / Jiangnan / timber), 商铺, 合院 compounds, 茶楼.
import { VoxelModel } from './model.js';
import { PAL } from './palette.js';
import { Rng } from '../world/noise.js';
import { roofRect, TILESETS } from './roof.js';
import { platform, body, lantern, plaque, balustrade, spread } from './arch.js';
import { smallTree } from './trees.js';

// 马头墙 stepped gable wall at x = xw (thickness 2) spanning z in [z0, z1), rising above the roof
function horseHeadWall(M, xw, z0, z1, yEave, rise, dir) {
  const D = z1 - z0;
  const steps = 3;
  const stepLen = Math.ceil(D / (steps * 2 - 1));
  for (let z = z0; z < z1; z++) {
    const fromEdge = Math.min(z - z0, z1 - 1 - z);
    const tier = Math.min(steps - 1, Math.floor(fromEdge / stepLen));
    const top = yEave + 3 + Math.round(((tier + 1) / steps) * (rise + 2));
    for (let y = 0; y <= top; y++) {
      M.set(xw, y, z, PAL.PLASTER);
      M.set(xw + dir, y, z, PAL.PLASTER);
    }
    // black tile cap with an overhanging lip
    M.set(xw, top + 1, z, PAL.TILE);
    M.set(xw + dir, top + 1, z, PAL.TILE);
    M.set(xw - dir, top, z, PAL.EAVE);
    M.set(xw + 2 * dir, top, z, PAL.EAVE);
    // upturned ends (the "horse heads") at each tier start
    if (fromEdge % stepLen === 0) {
      M.set(xw, top + 2, z, PAL.TILE);
      M.set(xw + dir, top + 2, z, PAL.TILE);
    }
  }
}

export function buildHouse(def) {
  const r = new Rng(def.seed);
  const { w, d } = def;
  const hw = w / 2;
  const hd = d / 2;
  const stories = def.stories || 1;
  const style = def.style;
  const storyH = style === 'hui' ? 18 : 16;
  const M = new VoxelModel(-hw - 6, -3, -hd - 8, hw + 6, 70, hd + 8);
  // stone plinth
  platform(M, -hw + 1, -hd + 1, hw - 1, hd - 1, 0, 2, { mat: PAL.STONE });
  const x0 = -hw + 3;
  const x1 = hw - 3;
  const z0 = -hd + 3;
  const z1 = hd - 3;
  const y0 = 2;
  const wallTop = y0 + storyH * stories;
  const bayX = Math.max(2, Math.round((x1 - x0) / 12));
  const bayZ = Math.max(1, Math.round((z1 - z0) / 14));
  if (style === 'hui') {
    // tall white walls with small windows
    M.box(x0, y0, z0, x1, wallTop, z1, PAL.INTERIOR);
    M.walls(x0, y0, z0, x1, wallTop, z1, PAL.PLASTER);
    // dark base course
    M.walls(x0, y0, z0, x1, y0 + 2, z1, PAL.STONE_D);
    // small windows (front & back), upper floors
    for (let s = 0; s < stories; s++) {
      const wy = y0 + s * storyH + 9;
      for (const xw of spread(x0 + 4, x1 - 7, Math.max(1, Math.round((x1 - x0) / 14)))) {
        if (s === 0 && Math.abs(xw + 1.5) < 5) continue;
        for (const zz of [z1 - 1, z0]) {
          M.box(xw, wy, zz, xw + 3, wy + 4, zz + 1, PAL.WOOD_D);
          M.box(xw + 1, wy + 1, zz, xw + 2, wy + 3, zz + 1, PAL.PAPER);
        }
      }
    }
    // doorway with a tiled canopy (门罩)
    M.box(-3, y0, z1 - 1, 3, y0 + 11, z1, PAL.WOOD_D);
    M.box(-2, y0, z1 - 1, 2, y0 + 10, z1, PAL.LATTICE);
    M.box(-4, y0 + 11, z1 - 1, 4, y0 + 13, z1, PAL.STONE_D);
    M.box(-5, y0 + 13, z1 - 1, 5, y0 + 14, z1 + 2, PAL.TILE);
    M.box(-4, y0 + 14, z1 - 1, 4, y0 + 15, z1 + 1, PAL.TILE_L);
    lantern(M, -4, y0 + 12, z1 + 1);
    lantern(M, 3, y0 + 12, z1 + 1);
    // wall cap under the eave
    M.walls(x0, wallTop - 1, z0, x1, wallTop, z1, PAL.STONE_D);
    // roof (硬山: no gable overhang), front/back eaves overhang
    const rise = Math.round((z1 - z0) * 0.36);
    roofRect(M, {
      x0: x0 + 1,
      x1: x1 - 1,
      z0: z0 - 3,
      z1: z1 + 3,
      y: wallTop,
      type: 'gable',
      rise,
      tiles: TILESETS.grey,
      wall: [x0, z0, x1, z1],
      ridgeH: 2,
    });
    horseHeadWall(M, x0, z0 - 1, z1 + 1, wallTop, rise, 1);
    horseHeadWall(M, x1 - 1, z0 - 1, z1 + 1, wallTop, rise, -1);
    return M;
  }
  const colMat = style === 'timber' ? PAL.WOOD_D : PAL.WOOD_D;
  const wallMat = style === 'timber' ? PAL.WOOD : PAL.PLASTER;
  for (let s = 0; s < stories; s++) {
    const sy0 = y0 + s * storyH;
    const sy1 = sy0 + storyH;
    body(M, {
      x0,
      z0,
      x1,
      z1,
      y0: sy0,
      y1: sy1,
      bayX,
      bayZ,
      colMat,
      wallMat,
      lowWall: style === 'timber' ? PAL.WOOD_D : PAL.PLASTER,
      front: s === 0 ? (i, n) => (i === Math.floor(n / 2) ? 'door' : 'window') : 'window',
      back: 'smallwin',
      left: 'wall',
      right: 'wall',
      beam: 'plain',
      beamH: 2,
    });
    if (s > 0) {
      // balcony with railing on the upper floor
      M.box(x0 - 1, sy0 - 1, z1, x1 + 1, sy0, z1 + 3, PAL.WOOD_D);
      balustrade(M, x0 - 1, z1, x1 + 1, z1 + 3, sy0, PAL.WOOD_L, [[x0, z1, x1, z1 + 2]], 4);
    }
  }
  const eave = style === 'jiangnan' ? 4 : 3;
  roofRect(M, {
    x0: x0 - 2,
    x1: x1 + 2,
    z0: z0 - eave,
    z1: z1 + eave,
    y: wallTop,
    type: 'gable',
    rise: Math.round((z1 - z0 + 2 * eave) * 0.34),
    tiles: TILESETS.grey,
    wall: [x0, z0, x1, z1],
    lift: style === 'jiangnan' ? 3 : 1,
    ridgeH: 2,
  });
  // door lanterns
  if (r.chance(0.7)) {
    lantern(M, -5, y0 + storyH - 3, z1 + 1);
    lantern(M, 4, y0 + storyH - 3, z1 + 1);
  }
  if (r.chance(0.3)) plaque(M, 0, y0 + storyH - 5, z1 - 1, 8, 3, 1);
  return M;
}

export function buildShop(def) {
  const r = new Rng(def.seed);
  const { w, d } = def;
  const hw = w / 2;
  const hd = d / 2;
  const M = new VoxelModel(-hw - 6, -3, -hd - 8, hw + 6, 70, hd + 10);
  platform(M, -hw + 1, -hd + 1, hw - 1, hd - 1, 0, 2, { mat: PAL.STONE });
  const x0 = -hw + 3;
  const x1 = hw - 3;
  const z0 = -hd + 3;
  const z1 = hd - 3;
  const H1 = 17;
  const H2 = 14;
  const bayX = Math.max(3, Math.round((x1 - x0) / 11));
  body(M, { x0, z0, x1, z1, y0: 2, y1: 2 + H1, bayX, bayZ: 2, colMat: PAL.RED, front: 'shop', back: 'wall', beam: 'plain', beamH: 2, wallMat: def.style === 'timber' ? PAL.WOOD : PAL.PLASTER });
  // upper floor slightly overhanging the street with a balcony
  body(M, { x0, z0, x1, z1: z1 + 1, y0: 2 + H1, y1: 2 + H1 + H2, bayX, bayZ: 2, colMat: PAL.WOOD_D, front: 'window', back: 'smallwin', lowWall: PAL.WOOD_D, beam: 'plain', beamH: 2, wallMat: def.style === 'timber' ? PAL.WOOD : PAL.PLASTER });
  M.box(x0 - 1, 1 + H1, z1, x1 + 1, 2 + H1, z1 + 3, PAL.WOOD_D);
  balustrade(M, x0 - 1, z1 + 1, x1 + 1, z1 + 3, 2 + H1, PAL.RED, [[x0 - 1, z1, x1 + 1, z1 + 2]], 4);
  // signboard hanging over the shop front
  plaque(M, 0, 2 + H1 - 5, z1, Math.min(14, x1 - x0 - 6), 4, 1);
  // cloth banner
  const bx = x1 - 3;
  for (let v = 0; v < 9; v++) M.set(bx, 2 + H1 - 2 - v, z1 + 2, v % 3 === 1 ? PAL.CLOTH_W : r.chance(0.5) ? PAL.CLOTH_R : PAL.CLOTH_B);
  // lanterns row
  for (let x = x0 + 2; x < x1 - 1; x += 6) lantern(M, x, 2 + H1 - 1, z1 + 2);
  const topY = 2 + H1 + H2;
  const big = w > 46;
  roofRect(M, {
    x0: x0 - 3,
    x1: x1 + 3,
    z0: z0 - 4,
    z1: z1 + 5,
    y: topY,
    type: big ? 'xieshan' : 'gable',
    rise: Math.round((z1 - z0 + 9) * 0.33),
    tiles: TILESETS.grey,
    wall: [x0, z0, x1, z1 + 1],
    lift: big ? 4 : 2,
  });
  return M;
}

export function buildCourt(def) {
  const r = new Rng(def.seed);
  const { w, d } = def;
  const hw = w / 2;
  const hd = d / 2;
  const M = new VoxelModel(-hw - 4, -3, -hd - 6, hw + 4, 70, hd + 6);
  // courtyard paving
  M.box(-hw + 1, 0, -hd + 1, hw - 1, 1, hd - 1, PAL.STONE);
  // enclosure wall with tile cap
  const wallH = 13;
  const ex0 = -hw + 1;
  const ex1 = hw - 1;
  const ez0 = -hd + 1;
  const ez1 = hd - 1;
  M.walls(ex0, 1, ez0, ex1, wallH, ez1, def.style === 'hui' ? PAL.PLASTER : PAL.BRICK, 2);
  for (let x = ex0 - 1; x < ex1 + 1; x++) {
    M.set(x, wallH, ez0 - 0, PAL.TILE);
    M.set(x, wallH, ez0 + 1, PAL.TILE);
    M.set(x, wallH, ez1 - 1, PAL.TILE);
    M.set(x, wallH, ez1 - 2, PAL.TILE);
    M.set(x, wallH + 1, ez0 + (x & 1), PAL.TILE_L);
  }
  for (let z = ez0; z < ez1; z++) {
    M.set(ex0, wallH, z, PAL.TILE);
    M.set(ex0 + 1, wallH, z, PAL.TILE);
    M.set(ex1 - 1, wallH, z, PAL.TILE);
    M.set(ex1 - 2, wallH, z, PAL.TILE);
  }
  // main hall at the back (north, -z)
  const mhD = Math.round(d * 0.3);
  const mz0 = ez0 + 3;
  const mz1 = mz0 + mhD;
  const mx0 = ex0 + 8;
  const mx1 = ex1 - 8;
  platform(M, mx0 - 2, mz0 - 1, mx1 + 2, mz1 + 3, 1, 3, { mat: PAL.STONE });
  body(M, { x0: mx0, z0: mz0, x1: mx1, z1: mz1, y0: 4, y1: 22, bayX: 5, bayZ: 2, colMat: PAL.RED, front: (i, n) => (Math.abs(i - (n - 1) / 2) < 1.1 ? 'door' : 'window'), beam: 'painted', beamH: 3, lowWall: PAL.BRICK });
  roofRect(M, { x0: mx0 - 4, x1: mx1 + 4, z0: mz0 - 4, z1: mz1 + 4, y: 22, type: 'xieshan', tiles: TILESETS.grey, wall: [mx0, mz0, mx1, mz1], lift: 3 });
  // side wings facing the court
  const wingW = Math.round(w * 0.2);
  const wz0 = mz1 + 6;
  const wz1 = ez1 - 12;
  if (wz1 - wz0 > 14) {
    for (const side of [-1, 1]) {
      const wx0 = side < 0 ? ex0 + 3 : ex1 - 3 - wingW;
      const wx1 = wx0 + wingW;
      platform(M, wx0 - 1, wz0 - 1, wx1 + 1, wz1 + 1, 1, 2, { mat: PAL.STONE });
      body(M, {
        x0: wx0,
        z0: wz0,
        x1: wx1,
        z1: wz1,
        y0: 3,
        y1: 18,
        bayX: 1,
        bayZ: Math.max(2, Math.round((wz1 - wz0) / 10)),
        colMat: PAL.WOOD_D,
        front: 'wall',
        back: 'wall',
        left: side < 0 ? 'wall' : (i) => (i % 2 ? 'window' : 'door'),
        right: side < 0 ? (i) => (i % 2 ? 'window' : 'door') : 'wall',
        beam: 'plain',
        beamH: 2,
        wallMat: PAL.PLASTER,
      });
      roofRect(M, { x0: wx0 - 3, x1: wx1 + 3, z0: wz0 - 2, z1: wz1 + 2, y: 18, type: 'gable', tiles: TILESETS.grey, wall: [wx0, wz0, wx1, wz1], lift: 1 });
    }
  }
  // gate house at the front
  const gx0 = -7;
  const gx1 = 7;
  const gz0 = ez1 - 8;
  const gz1 = ez1 + 1;
  M.box(gx0, 1, gz0, gx1, 16, gz1, PAL.INTERIOR);
  M.box(gx0, 1, gz1 - 1, gx1, 16, gz1, PAL.BRICK_D);
  M.box(-3, 1, gz1 - 1, 3, 12, gz1, PAL.RED);
  M.box(-2, 2, gz1 - 1, 2, 11, gz1, PAL.RED_WALL);
  M.set(-1, 7, gz1 - 1, PAL.GOLD);
  M.set(0, 7, gz1 - 1, PAL.GOLD);
  plaque(M, 0, 12, gz1 - 1, 8, 3, 1);
  roofRect(M, { x0: gx0 - 3, x1: gx1 + 3, z0: gz0 - 2, z1: gz1 + 3, y: 16, type: 'gable', tiles: TILESETS.grey, lift: 2 });
  lantern(M, -5, 15, gz1 + 1);
  lantern(M, 4, 15, gz1 + 1);
  // a small ornamental tree in the courtyard
  smallTree(M, r.chance(0.5) ? -Math.round(w * 0.12) : Math.round(w * 0.12), 1, Math.round((mz1 + wz1) / 2) + 4, r.int(1, 1e6), r.chance(0.5) ? 'blossom' : 'pine');
  return M;
}

export function buildTea(def) {
  const { w, d } = def;
  const hw = w / 2;
  const hd = d / 2;
  const floors = def.stories || 2;
  const M = new VoxelModel(-hw - 8, -3, -hd - 8, hw + 8, 110, hd + 8);
  platform(M, -hw + 1, -hd + 1, hw - 1, hd - 1, 0, 3, { mat: PAL.STONE, rail: false });
  let x0 = -hw + 5;
  let x1 = hw - 5;
  let z0 = -hd + 5;
  let z1 = hd - 5;
  let y = 3;
  for (let f = 0; f < floors; f++) {
    const H = f === 0 ? 17 : 15;
    body(M, { x0, z0, x1, z1, y0: y, y1: y + H, bayX: 5, bayZ: 3, colMat: PAL.RED, front: f === 0 ? 'door' : 'window', back: 'window', left: 'window', right: 'window', beam: 'painted', beamH: 3, lowWall: PAL.WOOD_D });
    // lanterns at the corners
    for (const [lx, lz] of [
      [x0 - 1, z0 - 1],
      [x1, z0 - 1],
      [x0 - 1, z1],
      [x1, z1],
    ])
      lantern(M, lx, y + H - 1, lz);
    y += H;
    if (f < floors - 1) {
      // skirt eave + balcony
      roofRect(M, { x0: x0 - 5, x1: x1 + 5, z0: z0 - 5, z1: z1 + 5, y: y - 1, type: 'hip', ring: [x0, z0, x1, z1], ringRise: 4, ringDepth: 5, tiles: TILESETS.grey, lift: 2 });
      M.box(x0 - 2, y + 2, z0 - 2, x1 + 2, y + 3, z1 + 2, PAL.WOOD_D);
      balustrade(M, x0 - 2, z0 - 2, x1 + 2, z1 + 2, y + 3, PAL.RED, [], 4);
      x0 += 1;
      x1 -= 1;
      z0 += 1;
      z1 -= 1;
      y += 3;
    }
  }
  roofRect(M, { x0: x0 - 5, x1: x1 + 5, z0: z0 - 5, z1: z1 + 5, y, type: 'xieshan', tiles: TILESETS.grey, wall: [x0, z0, x1, z1], lift: 4 });
  return M;
}
