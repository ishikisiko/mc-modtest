// The deterministic master plan of 云山巨城: river & lake, walls, gates, palace, roads, stairs,
// building lots, trees, lanterns, tour route and viewpoints. Runs on the main thread; the
// resulting plain object is posted to workers which rebuild the same field from it.
import { createGeo, SITE, POOLS, TERRACE } from './geo.js';
import { Rng, hash2, smoothstep, clamp } from './noise.js';
import { TM } from './materials.js';
import { createField, MODE, KIND, FT, quayAt, poolLevelAt as poolLevel } from './field.js';
import { makeCatalog } from '../voxel/catalog.js';
import { resample, smoothField, chaikin, pointInPoly, segDist, polyLength } from './geom.js';

const V = 0.2;
const q = (y) => Math.round(y * 5) / 5;

export const PALACE = { x: -22, x0: -58, x1: 14, zGate: -56, zRear: -228, y1: 80, y2: 96, y3: 112 };
export const LEDGE = { x: 30, z: -377, y: 178, rx: 19, rz: 15 };
export const RIVER_HW = 7;

export function buildPlan(seed = 1) {
  const geo = createGeo(seed);
  const rng = new Rng(seed * 9973 + 11);
  const catalog = makeCatalog(seed);
  const features = [];
  const instances = [];
  const lights = [];
  const uniques = {}; // unique prefab defs by id
  const plan = { seed, features, instances, lights, uniques, grids: {}, tour: [], views: {}, labels: [], paths: {} };

  // ---------- feature helpers ----------
  const rect = (x0, z0, x1, z1, y, mode, top, side, extra = {}) => {
    features.push({ t: FT.RECT, x0: Math.min(x0, x1), z0: Math.min(z0, z1), x1: Math.max(x0, x1), z1: Math.max(z0, z1), y: q(y), mode, top, side, ...extra });
  };
  const ell = (cx, cz, rx, rz, y, mode, top, side, extra = {}) => {
    features.push({ t: FT.ELL, cx, cz, rx, rz, y: q(y), mode, top, side, ...extra });
  };
  // polyline of [x, z, y] -> segments
  let pathIds = 0;
  const path = (pts, hw, mode, top, side, kind = KIND.PLAIN, extra = {}) => {
    let s = 0;
    const pid = ++pathIds; // segments of one path: nearest segment wins (straight stair treads)
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i];
      const b = pts[i + 1];
      const L = Math.hypot(b[0] - a[0], b[1] - a[1]);
      features.push({ t: FT.SEG, ax: a[0], az: a[1], ay: a[2], bx: b[0], bz: b[1], by: b[2], hw, mode, top, side, kind, s0: s, round: true, pid, ...extra });
      s += L;
    }
  };
  const addInst = (id, x, y, z, rot = 0, extra = {}) => {
    instances.push({ id, x: q(x), y: q(y), z: q(z), rot: rot & 3, ...extra });
  };
  const light = (x, y, z, r = 7, k = 1) => lights.push([x, y, z, r, k]);

  // natural height helper
  const natural = (x, z) => geo.height(x, z, 0.5);

  // ---------- ridge crests for walls ----------
  function crest(z, side, x0) {
    let bx = x0;
    let bh = -1e9;
    for (let dx = -70; dx <= 70; dx += 3) {
      const x = x0 + dx;
      const h = (natural(x - 5, z) + natural(x, z) * 2 + natural(x + 5, z)) / 4 - Math.abs(dx) * 0.05;
      if (h > bh) {
        bh = h;
        bx = x;
      }
    }
    return [bx, bh];
  }

  function wallLine(side, zFrom, zTo) {
    const pts = [];
    const step = zFrom > zTo ? -24 : 24;
    for (let z = zFrom; step < 0 ? z >= zTo : z <= zTo; z += step) {
      const [rd] = geo.ridge(z, side);
      const guess = geo.axisX(z) + side * rd;
      const [x] = crest(z, side, guess);
      pts.push([x, z]);
    }
    return pts;
  }

  // West & east ridge walls (south -> north)
  let westW = wallLine(-1, 396, -262);
  let eastW = wallLine(1, 396, -286);
  westW = smoothField(smoothField(westW, 0, 2, 1), 1, 0);
  eastW = smoothField(smoothField(eastW, 0, 2, 1), 1, 0);
  westW = chaikin(westW, 2);
  eastW = chaikin(eastW, 2);

  // south wall from SW corner to SE corner, with gaps for the main gate and water gate
  const GATE = { x: -30, z: 386, w: 44, d: 20 };
  const WGATE = { x: 44, z: 386, w: 28 };
  const swCorner = westW[0];
  const seCorner = eastW[0];
  const southLine = [];
  for (let x = swCorner[0]; x <= seCorner[0]; x += 12) southLine.push([x, 388 + 6 * Math.sin(x / 70) - 2]);
  southLine[0] = [swCorner[0], swCorner[1]];
  southLine.push([seCorner[0], seCorner[1]]);

  // city polygon (inside walls) closed along the northern cirque
  const cityPoly = [];
  for (const p of westW) cityPoly.push([p[0], p[1]]);
  const b = SITE.basin;
  const wEnd = westW[westW.length - 1];
  const eEnd = eastW[eastW.length - 1];
  cityPoly.push([wEnd[0] + 30, -300]);
  cityPoly.push([b.x - 70, b.z - 40]);
  cityPoly.push([b.x - 20, b.z - 72]);
  cityPoly.push([b.x + 40, b.z - 70]);
  cityPoly.push([b.x + 88, b.z - 40]);
  cityPoly.push([eEnd[0] - 20, -300]);
  for (let i = eastW.length - 1; i >= 0; i--) cityPoly.push([eastW[i][0], eastW[i][1]]);
  for (let i = southLine.length - 1; i >= 0; i--) cityPoly.push(southLine[i]);
  plan.cityPoly = cityPoly;

  // wall heights
  const wallify = (line, height) => {
    let pts = resample(line, 6).map((p) => [p[0], p[1], natural(p[0], p[1])]);
    pts = smoothField(pts, 2, 3, 2);
    return pts.map((p) => [p[0], p[1], q(p[2] + height)]);
  };

  // ---------- terrace/slope grid ----------
  {
    const cell = 4;
    const x0 = -380;
    const z0 = -440;
    const nx = Math.ceil(760 / cell) + 1;
    const nz = Math.ceil(900 / cell) + 1;
    const data = new Float32Array(nx * nz);
    const hs = new Float32Array(nx * nz);
    const inside = new Uint8Array(nx * nz);
    for (let j = 0; j < nz; j++)
      for (let i = 0; i < nx; i++) {
        const x = x0 + i * cell;
        const z = z0 + j * cell;
        hs[j * nx + i] = natural(x, z);
        inside[j * nx + i] = pointInPoly(x, z, cityPoly) ? 1 : 0;
      }
    for (let j = 0; j < nz; j++)
      for (let i = 0; i < nx; i++) {
        const k = j * nx + i;
        const i0 = Math.max(0, i - 1);
        const i1 = Math.min(nx - 1, i + 1);
        const j0 = Math.max(0, j - 1);
        const j1 = Math.min(nz - 1, j + 1);
        const gx = (hs[j * nx + i1] - hs[j * nx + i0]) / ((i1 - i0) * cell);
        const gz = (hs[j1 * nx + i] - hs[j0 * nx + i]) / ((j1 - j0) * cell);
        const slope = Math.hypot(gx, gz);
        data[k] = inside[k] * (1 - smoothstep(0.62, 0.95, slope));
      }
    // erode the terraced mask 2 cells from the wall line
    const er = new Float32Array(data);
    for (let j = 0; j < nz; j++)
      for (let i = 0; i < nx; i++) {
        let m = data[j * nx + i];
        for (let dj = -2; dj <= 2 && m > 0; dj++)
          for (let di = -2; di <= 2; di++) {
            const ii = Math.min(nx - 1, Math.max(0, i + di));
            const jj = Math.min(nz - 1, Math.max(0, j + dj));
            if (!inside[jj * nx + ii]) {
              m = 0;
              break;
            }
          }
        er[j * nx + i] = m;
      }
    plan.grids.terr = { x0, z0, cell, nx, nz, data: er };
  }

  // ---------- water: basin, waterfall slot, lake, river ----------
  // plunge basin
  ell(b.x, b.z - 6, 64, 62, SITE.poolY, MODE.SET, TM.WATER, TM.FALL, { rimW: 7, rimY: SITE.poolY + 2, rimTop: TM.MASONRY, rimSide: TM.MASONRY });
  // waterfall slot: vertical fall face from the lip to the pool
  const fallZ = -392;
  rect(SITE.fallX - 10, fallZ - 1, SITE.fallX + 10, b.z - 50, SITE.poolY, MODE.SET, TM.FOAM, TM.FALL);
  // ragged fall face: strands break over the lip at slightly different depths
  for (let x = SITE.fallX - 10; x < SITE.fallX + 10; x += 1.2) {
    const back = rng.range(0, 2.6);
    rect(x, fallZ - 1 - back, x + 1.2, fallZ - 1, SITE.poolY, MODE.SET, TM.FOAM, TM.FALL);
  }
  // upper river on the plateau feeding the fall
  const upperRiver = chaikin(
    [
      [SITE.fallX, fallZ - 1],
      [SITE.fallX - 4, -420],
      [-22, -466],
      [-14, -520],
      [0, -580],
      [8, -640],
      [0, -720],
    ],
    2,
  );
  path(upperRiver.map((p) => [p[0], p[1], SITE.fallLipY]), 7.5, MODE.SET, TM.WATER, TM.FALL, KIND.PLAIN, { round: true });
  // rocky lip teeth that split the fall into strands
  for (let i = 0; i < 7; i++) {
    const x = SITE.fallX - 9 + i * 3 + rng.range(-0.6, 0.6);
    rect(x, fallZ - 2.4, x + rng.range(0.6, 1.4), fallZ + rng.range(-0.4, 1.2), SITE.fallLipY + rng.range(0.4, 1.4), MODE.SET, TM.ROCK, TM.CLIFF);
  }

  // river polyline (north -> south) through the valley into the lake
  const riverPts = chaikin(
    [
      [30, -238],
      [42, -214],
      [48, -190],
      [50, -170],
      [48, -150],
      [44, -120],
      [38, -94],
      [30, -66],
      [24, -36],
      [20, -8],
      [18, 20],
      [22, 50],
      [22, 84],
      [20, 110],
      [18, 140],
      [20, 172],
      [22, 190],
    ],
    2,
  );
  plan.paths.river = riverPts;
  // river channel + quays
  for (let i = 0; i < riverPts.length - 1; i++) {
    const a = riverPts[i];
    const c = riverPts[i + 1];
    features.push({ t: FT.SEG, ax: a[0], az: a[1], ay: 0, bx: c[0], bz: c[1], by: 0, hw: RIVER_HW, qw: 9, mode: MODE.SET, top: TM.WATER, side: TM.FALL, kind: KIND.RIVER, s0: 0, round: true });
  }
  // lake
  const LAKE = { x: 20, z: 262, rx: 46, rz: 90 };
  ell(LAKE.x, LAKE.z, LAKE.rx, LAKE.rz, 38, MODE.SET, TM.WATER, TM.FALL, { rimW: 4, rimY: 40, rimTop: TM.MASONRY, rimSide: TM.MASONRY });
  // lake island
  ell(26, 250, 11, 9, 40.4, MODE.SET, TM.COURT, TM.MASONRY);
  // outlet channel through the water gate to the plateau edge
  const outlet = [
    [26, 346],
    [36, 370],
    [44, 392],
    [48, 420],
    [50, 446],
  ];
  for (let i = 0; i < outlet.length - 1; i++) {
    const a = outlet[i];
    const c = outlet[i + 1];
    features.push({ t: FT.SEG, ax: a[0], az: a[1], ay: 0, bx: c[0], bz: c[1], by: 0, hw: 6, qw: 3, mode: MODE.SET, top: TM.WATER, side: TM.FALL, kind: KIND.RIVER, s0: 0, round: true });
  }
  // sheer cliff below the outlet so the river falls into the cloud sea
  rect(38, 440, 62, 520, -205, MODE.MIN, -1, TM.CLIFF);

  // ---------- farmland outside the south gate ----------
  rect(-150, 398, -40, 434, 0, MODE.TERR, TM.PADDY, TM.EARTH, { step: 0.6, grid: 9 });
  rect(-20, 398, 34, 434, 0, MODE.TERR, TM.PADDY, TM.EARTH, { step: 0.6, grid: 9 });
  rect(62, 398, 150, 434, 0, MODE.TERR, TM.PADDY, TM.EARTH, { step: 0.6, grid: 9 });

  // ---------- palace terraces ----------
  const P = PALACE;
  rect(P.x0, -110, P.x1, P.zGate, P.y1, MODE.SET, TM.COURT, TM.MASONRY);
  rect(P.x0, -186, P.x1, -110, P.y2, MODE.SET, TM.COURT, TM.MARBLE);
  rect(P.x0, P.zRear, P.x1, -186, P.y3, MODE.SET, TM.COURT, TM.MARBLE);
  // imperial way (marble centre strip)
  rect(P.x - 3, P.zRear + 4, P.x + 3, P.zGate, 0, MODE.PAINT, TM.MARBLE, -1);
  // grand stairs between terraces
  path([[P.x, -84, P.y1], [P.x, -110.2, P.y2]], 8, MODE.SET, TM.STEPS, TM.MARBLE, KIND.PLAIN, { round: false });
  path([[P.x, -164, P.y2], [P.x, -186.2, P.y3]], 7, MODE.SET, TM.STEPS, TM.MARBLE, KIND.PLAIN, { round: false });
  // plaza in front of the palace gate
  rect(P.x - 26, -56, P.x + 26, -24, P.y1, MODE.SET, TM.PLAZA, TM.MASONRY);

  // ---------- market square ----------
  const MARKET = { x: -44, z: 176, w: 56, d: 44 };
  const marketY = TERRACE * Math.round(natural(MARKET.x, MARKET.z) / TERRACE);
  rect(MARKET.x - MARKET.w / 2, MARKET.z - MARKET.d / 2, MARKET.x + MARKET.w / 2, MARKET.z + MARKET.d / 2, marketY, MODE.SET, TM.PLAZA, TM.MASONRY);
  plan.market = { ...MARKET, y: marketY };

  // ---------- temple terrace & pagoda knoll (east slope) ----------
  const PAGODA = { x: 176, z: 18 };
  const pagY = q(natural(PAGODA.x, PAGODA.z));
  ell(PAGODA.x, PAGODA.z, 17, 17, pagY, MODE.SET, TM.PLAZA, TM.MASONRY);
  const TEMPLE = { x: 120, z: 110, w: 44, d: 60 };
  const tY = TERRACE * Math.round(natural(TEMPLE.x, TEMPLE.z) / TERRACE);
  rect(TEMPLE.x - TEMPLE.w / 2, TEMPLE.z - TEMPLE.d / 2, TEMPLE.x + TEMPLE.w / 2, TEMPLE.z + TEMPLE.d / 2, tY, MODE.SET, TM.COURT, TM.MASONRY);
  plan.temple = { ...TEMPLE, y: tY };
  plan.pagoda = { ...PAGODA, y: pagY };

  // west ridge tower terrace
  const WTOWER = { x: 0, z: 0 };
  {
    // pick a knoll on the west slope near z=40
    let best = [-170, 40];
    let bh = -1e9;
    for (let x = -230; x <= -130; x += 6) {
      const h = natural(x, 40) - Math.abs(x + 185) * 0.3;
      if (h > bh) {
        bh = h;
        best = [x, 40];
      }
    }
    WTOWER.x = best[0];
    WTOWER.z = best[1];
    WTOWER.y = q(natural(best[0], best[1]));
    ell(WTOWER.x, WTOWER.z, 12, 12, WTOWER.y, MODE.SET, TM.PLAZA, TM.MASONRY);
    plan.wtower = WTOWER;
  }

  // ---------- waterfall tower ledge, basin trails, summit ----------
  ell(LEDGE.x, LEDGE.z, LEDGE.rx, LEDGE.rz, LEDGE.y, MODE.SET, TM.PLAZA, TM.CLIFF);
  const S = SITE.summit;
  const sumY = q(S.y);
  ell(S.x, S.z, 9, 9, sumY, MODE.SET, TM.PLAZA, TM.ROCK);
  plan.summitY = sumY;

  // ---------- roads ----------
  const roadY = (pts, lift = 0, passes = 4) => {
    let p = resample(pts, 4).map((pt) => [pt[0], pt[1], natural(pt[0], pt[1]) + lift]);
    p = smoothField(p, 2, passes, 3);
    return p;
  };

  // main avenue from the south gate to the palace plaza
  let avenue = chaikin(
    [
      [GATE.x, 404],
      [GATE.x, 380],
      [-34, 340],
      [-40, 290],
      [-44, 230],
      [-44, 176],
      [-42, 120],
      [-36, 70],
      [-30, 20],
      [-24, -20],
      [P.x, -40],
    ],
    2,
  );
  avenue = roadY(avenue, 0.6, 6);
  // force known levels at the market & palace plaza
  avenue = avenue.map((p) => {
    if (Math.abs(p[1] - MARKET.z) < MARKET.d / 2) p[2] = marketY;
    if (p[1] < -24) p[2] = P.y1;
    return p;
  });
  avenue = smoothField(avenue, 2, 2, 2).map((p) => {
    if (Math.abs(p[1] - MARKET.z) < MARKET.d / 2 - 2) p[2] = marketY;
    if (p[1] < -28) p[2] = P.y1;
    return p;
  });
  plan.paths.avenue = avenue;
  path(avenue, 4.6, MODE.SET, TM.ROAD, TM.MASONRY);

  // approach from the cloud sea up to the gate
  const approach = [
    [-60, 468, -6],
    [-18, 458, 4],
    [-66, 448, 14],
    [-22, 438, 26],
    [-44, 424, 36],
    [GATE.x, 406, 41],
  ];
  plan.paths.approach = approach;
  path(approach, 2.6, MODE.SET, TM.STEPS, TM.CLIFF);

  // palace east lane beside the river, rising with the quays
  // (quays are part of the river features)

  // basin: from the palace rear up the river stair to the basin rim, cliff stairs to the ledge,
  // then up to the plateau and along the ridge to the summit
  const cliff1 = [
    [58, -290, SITE.poolY + 2],
    [72, -318, SITE.poolY + 10],
    [70, -344, SITE.poolY + 20],
    [58, -360, SITE.poolY + 30],
    [48, -370, LEDGE.y - 4],
    [LEDGE.x + 10, LEDGE.z + 2, LEDGE.y],
  ];
  path(cliff1, 2.6, MODE.SET, TM.STEPS, TM.CLIFF);
  const cliff2 = [
    [LEDGE.x + 14, LEDGE.z - 8, LEDGE.y],
    [60, -398, LEDGE.y + 12],
    [52, -414, LEDGE.y + 26],
    [70, -424, LEDGE.y + 40],
    [86, -434, SITE.upperY - 2],
    [104, -440, SITE.upperY + 4],
  ];
  path(cliff2, 2.6, MODE.SET, TM.STEPS, TM.CLIFF);
  // ridge trail to the summit (uniform climb)
  const trailXZ = chaikin(
    [
      [104, -440],
      [150, -425],
      [200, -440],
      [235, -470],
      [240, -505],
      [225, -530],
      [S.x + 4, S.z - 8],
    ],
    2,
  );
  // the trail hugs the mountain surface (smoothed), carving steep stone stairs where needed
  let trail = resample(trailXZ, 3).map((p) => [p[0], p[1], natural(p[0], p[1])]);
  trail = smoothField(trail, 2, 4, 2);
  trail[0][2] = SITE.upperY + 4;
  trail[trail.length - 1][2] = sumY;
  trail = smoothField(trail, 2, 2, 1);
  for (let i = 1; i < trail.length; i++) trail[i][2] = Math.max(trail[i][2], trail[i - 1][2] - 0.5);
  trail[trail.length - 1][2] = sumY;
  path(trail, 1.6, MODE.SET, TM.STEPS, TM.ROCK);
  plan.paths.summit = [...cliff1, ...cliff2, ...trail];

  // ---------- walls ----------
  const WALL_H = 9;
  const wW = wallify(westW, WALL_H);
  const eW = wallify(eastW, WALL_H);
  const sW = wallify(southLine, WALL_H);
  const addWall = (pts, hw, kind, top, side, cityIn) => {
    // determine which side is "outer" by probing
    let s = 0;
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i];
      const c = pts[i + 1];
      const L = Math.hypot(c[0] - a[0], c[1] - a[1]);
      if (L < 0.01) continue;
      const mx = (a[0] + c[0]) / 2;
      const mz = (a[1] + c[1]) / 2;
      // left normal of a->c
      const nx = -(c[1] - a[1]) / L;
      const nz = (c[0] - a[0]) / L;
      const leftInside = cityIn(mx + nx * 12, mz + nz * 12);
      features.push({ t: FT.SEG, ax: a[0], az: a[1], ay: a[2], bx: c[0], bz: c[1], by: c[2], hw, mode: MODE.MAX, top, side, kind, s0: s, round: true, outer: leftInside ? -1 : 1 });
      s += L;
    }
  };
  const inCity = (x, z) => pointInPoly(x, z, cityPoly);
  // split the south wall at the gate & water gate
  const sW1 = sW.filter((p) => p[0] < GATE.x - GATE.w / 2 + 1);
  const sW2 = sW.filter((p) => p[0] > GATE.x + GATE.w / 2 - 1 && p[0] < WGATE.x - WGATE.w / 2 + 1);
  const sW3 = sW.filter((p) => p[0] > WGATE.x + WGATE.w / 2 - 1);
  addWall(wW, 4, KIND.WALL, TM.WALLTOP, TM.BRICK, inCity);
  addWall(eW, 4, KIND.WALL, TM.WALLTOP, TM.BRICK, inCity);
  for (const seg of [sW1, sW2, sW3]) if (seg.length > 1) addWall(seg, 4, KIND.WALL, TM.WALLTOP, TM.BRICK, inCity);
  plan.walls = { west: wW, east: eW, south: sW };

  // palace enclosure walls (red walls with yellow glazed caps), stepped with the terraces
  const inPal = (x, z) => x > P.x0 && x < P.x1 && z > P.zRear && z < P.zGate;
  const palY = (z) => (z > -110 ? P.y1 : z > -186 ? P.y2 : P.y3) + 5.2;
  const zCuts = [P.zGate, -110, -186, P.zRear];
  for (const x of [P.x0, P.x1]) {
    for (let k = 0; k < 3; k++) {
      const za = zCuts[k];
      const zb = zCuts[k + 1];
      const y = palY((za + zb) / 2);
      addWall([[x, za, y], [x, zb, y]], 1.2, KIND.PALWALL, TM.GLAZE_Y, TM.REDWALL, inPal);
    }
  }
  addWall([[P.x0, P.zRear, palY(P.zRear + 1)], [P.x1, P.zRear, palY(P.zRear + 1)]], 1.2, KIND.PALWALL, TM.GLAZE_Y, TM.REDWALL, inPal);
  addWall([[P.x1, P.zGate, P.y1 + 5.2], [P.x + 13, P.zGate, P.y1 + 5.2]], 1.2, KIND.PALWALL, TM.GLAZE_Y, TM.REDWALL, inPal);
  addWall([[P.x - 13, P.zGate, P.y1 + 5.2], [P.x0, P.zGate, P.y1 + 5.2]], 1.2, KIND.PALWALL, TM.GLAZE_Y, TM.REDWALL, inPal);

  // ---------- field so far (used for lots, lanes and prefab sites) ----------
  let field = createField(plan);
  const H = (x, z) => field.heightAt(x, z);

  // ---------- stair lanes up both slopes ----------
  const lanes = [];
  const laneZ = [318, 250, 196, 150, 104, 60, 16, -32, -76, -120];
  for (const z0 of laneZ) {
    // west lane from the avenue up the slope
    const ax = avenue.reduce((best, p) => (Math.abs(p[1] - z0) < Math.abs(best[1] - z0) ? p : best))[0];
    const wEnd = westW.reduce((best, p) => (Math.abs(p[1] - z0) < Math.abs(best[1] - z0) ? p : best));
    const west = [];
    for (let x = ax - 6; x > wEnd[0] + 18; x -= 5) west.push([x, z0 + 5 * Math.sin(x / 23)]);
    // east lane from the river quay
    const rx = riverPts.reduce((best, p) => (Math.abs(p[1] - z0) < Math.abs(best[1] - z0) ? p : best))[0];
    const eEnd2 = eastW.reduce((best, p) => (Math.abs(p[1] - z0) < Math.abs(best[1] - z0) ? p : best));
    const east = [];
    const startE = z0 > 175 && z0 < 350 ? LAKE.x + LAKE.rx * Math.sqrt(Math.max(0, 1 - ((z0 - LAKE.z) / LAKE.rz) ** 2)) + 6 : rx + RIVER_HW + 9;
    for (let x = startE; x < eEnd2[0] - 18; x += 5) east.push([x, z0 + 5 * Math.sin(x / 19)]);
    for (const L of [west, east]) {
      if (L.length < 3) continue;
      let p = L.map((pt) => [pt[0], pt[1], natural(pt[0], pt[1])]);
      p = smoothField(p, 2, 3, 2);
      // keep lanes inside terraced city
      p = p.filter((pt) => field.terrAt(pt[0], pt[1]) > 0.3);
      if (p.length < 3) continue;
      lanes.push(p);
      path(p, 1.5, MODE.SET, TM.STEPS, TM.MASONRY, KIND.LANE, { round: true });
    }
  }
  plan.paths.lanes = lanes;
  field = createField(plan);

  // ---------- reservation grid (1 m) ----------
  const RG = { x0: -400, z0: -460, nx: 800, nz: 940 };
  const res = new Uint8Array(RG.nx * RG.nz);
  const resMark = (x0, z0, x1, z1, v = 1) => {
    const i0 = Math.max(0, Math.floor(Math.min(x0, x1) - RG.x0));
    const i1 = Math.min(RG.nx - 1, Math.ceil(Math.max(x0, x1) - RG.x0));
    const j0 = Math.max(0, Math.floor(Math.min(z0, z1) - RG.z0));
    const j1 = Math.min(RG.nz - 1, Math.ceil(Math.max(z0, z1) - RG.z0));
    for (let j = j0; j <= j1; j++) for (let i = i0; i <= i1; i++) res[j * RG.nx + i] = Math.max(res[j * RG.nx + i], v);
  };
  const resFree = (x0, z0, x1, z1) => {
    const i0 = Math.floor(Math.min(x0, x1) - RG.x0);
    const i1 = Math.ceil(Math.max(x0, x1) - RG.x0);
    const j0 = Math.floor(Math.min(z0, z1) - RG.z0);
    const j1 = Math.ceil(Math.max(z0, z1) - RG.z0);
    if (i0 < 0 || j0 < 0 || i1 >= RG.nx || j1 >= RG.nz) return false;
    for (let j = j0; j <= j1; j++) for (let i = i0; i <= i1; i++) if (res[j * RG.nx + i]) return false;
    return true;
  };
  const resPath = (pts, hw) => {
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i];
      const c = pts[i + 1];
      const L = Math.hypot(c[0] - a[0], c[1] - a[1]);
      const n = Math.max(1, Math.ceil(L / 0.8));
      for (let k = 0; k <= n; k++) {
        const x = a[0] + ((c[0] - a[0]) * k) / n;
        const z = a[1] + ((c[1] - a[1]) * k) / n;
        resMark(x - hw, z - hw, x + hw, z + hw);
      }
    }
  };
  resPath(avenue, 6.5);
  resPath(riverPts, RIVER_HW + 10);
  resPath(outlet, 10);
  for (const L of lanes) resPath(L, 2.5);
  resPath(wW, 9);
  resPath(eW, 9);
  resPath(sW, 9);
  resMark(LAKE.x - LAKE.rx - 6, LAKE.z - LAKE.rz - 6, LAKE.x + LAKE.rx + 6, LAKE.z + LAKE.rz + 6);
  resMark(P.x0 - 3, P.zRear - 3, P.x1 + 3, -22);
  resMark(MARKET.x - MARKET.w / 2 - 1, MARKET.z - MARKET.d / 2 - 1, MARKET.x + MARKET.w / 2 + 1, MARKET.z + MARKET.d / 2 + 1);
  resMark(TEMPLE.x - TEMPLE.w / 2 - 2, TEMPLE.z - TEMPLE.d / 2 - 2, TEMPLE.x + TEMPLE.w / 2 + 2, TEMPLE.z + TEMPLE.d / 2 + 2);
  resMark(PAGODA.x - 20, PAGODA.z - 20, PAGODA.x + 20, PAGODA.z + 20);
  resMark(WTOWER.x - 14, WTOWER.z - 14, WTOWER.x + 14, WTOWER.z + 14);
  resMark(b.x - 90, b.z - 90, b.x + 95, b.z + 80);
  resMark(GATE.x - 30, GATE.z - 18, GATE.x + 30, GATE.z + 30);

  // ---------- unique landmark prefabs ----------
  const U = (id, def) => {
    uniques[id] = { id, ...def };
    return id;
  };
  U('lamp_post', { kind: 'lamp', seed: 3 });
  // city gate & water gate
  const gateY = q(avenue[1][2]);
  U('gate_south', { kind: 'citygate', w: GATE.w / V, d: GATE.d / V, baseH: Math.round((sW1[sW1.length - 1][2] - gateY) / V), seed: 11 });
  addInst('gate_south', GATE.x, gateY, GATE.z, 0);
  rect(GATE.x - GATE.w / 2 - 1, GATE.z - 14, GATE.x + GATE.w / 2 + 1, GATE.z + 14, gateY, MODE.SET, TM.ROAD, TM.MASONRY);
  const wgY = 40;
  U('gate_water', { kind: 'watergate', w: WGATE.w / V, d: 12 / V, baseH: Math.round((sW3[0][2] - wgY) / V), seed: 12 });
  addInst('gate_water', WGATE.x, wgY - 2, WGATE.z, 0);

  // palace buildings
  U('pal_gate', { kind: 'palgate', w: 44 / V, d: 16 / V, seed: 21 });
  addInst('pal_gate', P.x, P.y1, P.zGate - 1, 0);
  U('pal_main', { kind: 'hall', rank: 3, w: 46 / V, d: 26 / V, platform: 3, roof: 'wudian', double: true, tile: 'yellow', seed: 22 });
  addInst('pal_main', P.x, P.y2, -144, 0);
  U('pal_rear', { kind: 'hall', rank: 2, w: 36 / V, d: 18 / V, platform: 2, roof: 'xieshan', double: true, tile: 'yellow', seed: 23 });
  addInst('pal_rear', P.x, P.y3, -210, 0);
  U('pal_side', { kind: 'hall', rank: 1, w: 26 / V, d: 12 / V, platform: 1, roof: 'xieshan', double: false, tile: 'green', seed: 24 });
  addInst('pal_side', P.x0 + 9, P.y2, -146, 1);
  addInst('pal_side', P.x1 - 9, P.y2, -146, 3);
  addInst('pal_side', P.x0 + 9, P.y1, -84, 1);
  addInst('pal_side', P.x1 - 9, P.y1, -84, 3);
  U('pal_gatehall', { kind: 'hall', rank: 1, w: 24 / V, d: 11 / V, platform: 1, roof: 'xieshan', double: false, tile: 'yellow', seed: 25 });
  addInst('pal_gatehall', P.x, P.y2, -116, 0);
  U('pal_corner', { kind: 'pavilion', sides: 4, r: 5 / V, double: true, tile: 'yellow', seed: 26 });
  addInst('pal_corner', P.x0 + 3, P.y1, P.zGate - 3, 0);
  addInst('pal_corner', P.x1 - 3, P.y1, P.zGate - 3, 0);
  addInst('pal_corner', P.x0 + 3, P.y3, P.zRear + 3, 0);
  addInst('pal_corner', P.x1 - 3, P.y3, P.zRear + 3, 0);
  resMark(P.x0 - 3, P.zRear - 3, P.x1 + 3, P.zGate + 3);

  // drum tower in the market square
  U('drum_tower', { kind: 'drumtower', w: 18 / V, d: 14 / V, seed: 31 });
  addInst('drum_tower', MARKET.x - 16, marketY, MARKET.z - 4, 1);

  // temple on the east slope, facing west to the valley
  U('temple_hall', { kind: 'hall', rank: 2, w: 28 / V, d: 16 / V, platform: 2, roof: 'xieshan', double: true, tile: 'green', seed: 41 });
  addInst('temple_hall', TEMPLE.x + 8, tY, TEMPLE.z, 3);
  U('temple_side', { kind: 'hall', rank: 1, w: 18 / V, d: 10 / V, platform: 1, roof: 'xieshan', double: false, tile: 'grey', seed: 42 });
  addInst('temple_side', TEMPLE.x - 6, tY, TEMPLE.z - 20, 0);
  addInst('temple_side', TEMPLE.x - 6, tY, TEMPLE.z + 20, 2);
  // pagoda
  U('pagoda', { kind: 'pagoda', sides: 8, r: 8.4 / V, floors: 13, seed: 51 });
  addInst('pagoda', PAGODA.x, pagY, PAGODA.z, 0);
  // west ridge tower
  U('west_tower', { kind: 'lou', floors: 3, w: 14 / V, d: 14 / V, tile: 'grey', seed: 52 });
  addInst('west_tower', WTOWER.x, WTOWER.y, WTOWER.z, 1);

  // waterfall tower (landmark)
  U('guanpu', { kind: 'lou', floors: 5, w: 20 / V, d: 20 / V, tile: 'green', grand: true, seed: 61 });
  addInst('guanpu', LEDGE.x, LEDGE.y, LEDGE.z, 3);
  // summit pavilion
  U('summit_ting', { kind: 'pavilion', sides: 6, r: 4.2 / V, double: true, tile: 'grey', seed: 71 });
  addInst('summit_ting', S.x, sumY, S.z, 0);
  // mid-trail pavilion
  const midT = trail[Math.floor(trail.length * 0.45)];
  U('trail_ting', { kind: 'pavilion', sides: 4, r: 2.4 / V, double: false, tile: 'grey', seed: 72 });
  const midY = q(midT[2]);
  ell(midT[0] + 4, midT[1], 3.2, 3.2, midY, MODE.SET, TM.PLAZA, TM.ROCK);
  addInst('trail_ting', midT[0] + 4, midY, midT[1], 0);
  // lake island pavilion
  U('lake_ting', { kind: 'pavilion', sides: 8, r: 3.6 / V, double: true, tile: 'grey', seed: 73 });
  addInst('lake_ting', 26, 40.4, 250, 0);
  // basin pavilion on the west shore
  U('basin_ting', { kind: 'pavilion', sides: 6, r: 3 / V, double: false, tile: 'green', seed: 74 });
  addInst('basin_ting', b.x - 58, SITE.poolY + 2, b.z + 10, 0);

  // bridges across the river (deck at quay level)
  const bridgeAt = (z, kind, id, span, width) => {
    const p = riverPts.reduce((best, pt) => (Math.abs(pt[1] - z) < Math.abs(best[1] - z) ? pt : best));
    const y = quayAt(z);
    U(id, { kind, span: span / V, width: width / V, seed: Math.round(z * 7) });
    addInst(id, p[0], y, z, 1); // rot 1: span along x
    return [p[0], y, z];
  };
  const bridges = [];
  bridges.push(bridgeAt(126, 'archbridge', 'bridge_a', 26, 7));
  bridges.push(bridgeAt(36, 'archbridge', 'bridge_b', 26, 6));
  bridges.push(bridgeAt(-50, 'archbridge', 'bridge_c', 26, 8));
  bridges.push(bridgeAt(-134, 'archbridge', 'bridge_d', 26, 6));
  bridges.push(bridgeAt(-206, 'langqiao', 'bridge_e', 28, 6));
  plan.bridges = bridges;
  // lake bridges: zig-zag causeway to the island + arched outlet bridge
  path(
    [
      [LAKE.x - LAKE.rx - 2, 244, 39.2],
      [4, 244, 39.2],
      [8, 252, 39.2],
      [15, 252, 39.2],
    ],
    1.3,
    MODE.SET,
    TM.WOOD,
    TM.WOOD,
  );
  U('bridge_lake', { kind: 'archbridge', span: 22 / V, width: 5 / V, seed: 91 });
  addInst('bridge_lake', 30, 40, 356, 1);

  // ---------- wall watchtowers (敌楼) ----------
  U('wall_tower', { kind: 'walltower', w: 50, seed: 81 });
  const towerOn = (pts, every, rotFor) => {
    for (let i = Math.floor(every / 2); i < pts.length - 3; i += every) {
      const p = pts[i];
      const a = pts[Math.max(0, i - 2)];
      const c = pts[Math.min(pts.length - 1, i + 2)];
      const grade = Math.abs(c[2] - a[2]) / (Math.hypot(c[0] - a[0], c[1] - a[1]) || 1);
      if (grade > 0.45) continue;
      addInst('wall_tower', p[0], p[2], p[1], rotFor(p));
      resMark(p[0] - 7, p[1] - 7, p[0] + 7, p[1] + 7);
    }
  };
  towerOn(wW, 17, () => 3);
  towerOn(eW, 17, () => 1);
  towerOn(sW1, 12, () => 0);
  towerOn(sW3, 12, () => 0);

  // ---------- boats on the lake and river pools ----------
  U('boat_a', { kind: 'boat', len: 30, seed: 3 });
  U('boat_b', { kind: 'boat', len: 38, seed: 8 });
  for (let k = 0; k < 7; k++) {
    const a = rng.range(0, Math.PI * 2);
    const rr = Math.sqrt(rng.range(0.15, 0.75));
    const x = LAKE.x + Math.cos(a) * LAKE.rx * rr;
    const z = LAKE.z + Math.sin(a) * LAKE.rz * rr;
    if (Math.hypot(x - 26, z - 250) < 18 || Math.abs(z - 244) < 6) continue;
    addInst(k % 2 ? 'boat_a' : 'boat_b', x, 37.8, z, rng.int(0, 4));
  }
  for (const zc of [128, 20, -60]) {
    const p = riverPts.reduce((best, pt) => (Math.abs(pt[1] - zc) < Math.abs(best[1] - zc) ? pt : best));
    addInst('boat_a', p[0] + rng.range(-2, 2), poolLevel(zc) - 0.2, zc, rng.chance(0.5) ? 0 : 2);
  }

  // ---------- hermit pavilions on karst pillars rising from the clouds ----------
  U('pillar_ting', { kind: 'pavilion', sides: 6, r: 2.8 / V, double: false, tile: 'green', seed: 75 });
  {
    const cands = geo
      .listPillars(0, 60, 760)
      .filter((p) => p.top > 45 && p.r > 13)
      .sort((a, c) => c.top - a.top)
      .slice(0, 4);
    plan.pillarSites = [];
    for (const p of cands) {
      const y = q(p.top - 1);
      ell(p.x, p.z, 7.5, 7.5, y, MODE.SET, TM.PLAZA, TM.ROCK);
      addInst('pillar_ting', p.x, y, p.z, rng.int(0, 4));
      for (let k = 0; k < 3; k++) {
        const a = rng.range(0, Math.PI * 2);
        const tx = p.x + Math.cos(a) * rng.range(9, Math.max(10, p.r * 0.7));
        const tz = p.z + Math.sin(a) * rng.range(9, Math.max(10, p.r * 0.7));
        plan.pillarSites.push([tx, tz]);
      }
      light(p.x, y + 3, p.z, 10, 1.2);
    }
  }

  // ---------- building lots ----------
  const avenueNear = (x, z) => {
    let best = 1e9;
    let bp = null;
    for (const p of avenue) {
      const d = Math.hypot(p[0] - x, p[1] - z);
      if (d < best) {
        best = d;
        bp = p;
      }
    }
    return [best, bp];
  };
  const gradAt = (x, z) => {
    const e = 6;
    return [(natural(x + e, z) - natural(x - e, z)) / (2 * e), (natural(x, z + e) - natural(x, z - e)) / (2 * e)];
  };
  const lotTmp = new Float64Array(4);
  const lotSample = (x, z) => field.sample(x, z, 0.2, lotTmp)[0];
  const cands = [];
  for (let z = -236; z <= 382; z += 2.5) {
    for (let x = -300; x <= 300; x += 2.5) {
      const jx = x + (rand01(x, z, 1) - 0.5) * 2.4;
      const jz = z + (rand01(x, z, 2) - 0.5) * 2.4;
      if (field.terrAt(jx, jz) < 0.6) continue;
      const [dAve] = avenueNear(jx, jz);
      cands.push([jx, jz, dAve + rand01(x, z, 3) * 30]);
    }
  }
  function rand01(x, z, s) {
    return hash2(Math.round(x * 10), Math.round(z * 10), seed * 100 + s) / 4294967296;
  }
  cands.sort((a, c) => a[2] - c[2]);
  let lotCount = 0;
  for (const [x, z] of cands) {
    const [dAve, ap] = avenueNear(x, z);
    let group;
    let rot;
    if (dAve < 20) {
      group = 'shop';
      // face the avenue
      const dx = ap[0] - x;
      const dz = ap[1] - z;
      rot = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : dz > 0 ? 0 : 2;
    } else {
      const r = rand01(x, z, 7);
      const nearLake = Math.hypot((x - LAKE.x) / (LAKE.rx + 30), (z - LAKE.z) / (LAKE.rz + 30)) < 1;
      if (nearLake && r < 0.35) group = 'tea';
      else if (z < -20 && x < 60 && r < 0.45) group = 'court';
      else if (r < 0.12) group = 'court';
      else if (r < 0.2) group = 'shop';
      else group = 'house';
      const [gx, gz] = gradAt(x, z);
      if (Math.hypot(gx, gz) < 0.05) rot = rand01(x, z, 8) < 0.7 ? 0 : 1 + Math.floor(rand01(x, z, 9) * 3);
      else rot = Math.abs(gx) > Math.abs(gz) ? (gx > 0 ? 3 : 1) : gz > 0 ? 2 : 0;
    }
    const ids = catalog.groups[group];
    const first = Math.floor(rand01(x, z, 11) * ids.length);
    // try the chosen variant, then fall back to smaller houses
    const tries = [ids[first], ids[(first + 1) % ids.length]];
    const small = catalog.groups.house.filter((hid) => catalog.defs[hid].w * catalog.defs[hid].d < 1500);
    tries.push(small[Math.floor(rand01(x, z, 12) * small.length)]);
    for (const id of tries) {
      const def = catalog.defs[id];
      const w = (rot & 1 ? def.d : def.w) * V;
      const d = (rot & 1 ? def.w : def.d) * V;
      const m = 0.9;
      const x0 = x - w / 2;
      const x1 = x + w / 2;
      const z0 = z - d / 2;
      const z1 = z + d / 2;
      if (!resFree(x0 - m, z0 - m, x1 + m, z1 + m)) continue;
      // terrain check: all probes terraced & range within one terrace
      let lo = 1e9;
      let hi = -1e9;
      let ok = true;
      for (let pj = 0; pj <= 2 && ok; pj++)
        for (let pi = 0; pi <= 2; pi++) {
          const px = x0 + (w * pi) / 2;
          const pz = z0 + (d * pj) / 2;
          if (field.terrAt(px, pz) < 0.5) {
            ok = false;
            break;
          }
          const h = lotSample(px, pz);
          lo = Math.min(lo, h);
          hi = Math.max(hi, h);
        }
      if (!ok || hi - lo > TERRACE + 0.3) continue;
      const y = q(lo);
      rect(x0 - 0.4, z0 - 0.4, x1 + 0.4, z1 + 0.4, y, MODE.SET, TM.COURT, TM.MASONRY);
      addInst(id, x, y, z, rot);
      resMark(x0 - m, z0 - m, x1 + m, z1 + m);
      lotCount++;
      break;
    }
  }
  plan.lotCount = lotCount;
  field = createField(plan);

  // ---------- lanterns ----------
  // along the avenue both sides every 12 m
  {
    let acc2 = 0;
    for (let i = 1; i < avenue.length; i++) {
      const a = avenue[i - 1];
      const c = avenue[i];
      const L = Math.hypot(c[0] - a[0], c[1] - a[1]);
      acc2 += L;
      if (acc2 < 12) continue;
      acc2 = 0;
      const nx = -(c[1] - a[1]) / L;
      const nz = (c[0] - a[0]) / L;
      for (const s of [-1, 1]) {
        const x = c[0] + nx * s * 5.2;
        const z = c[1] + nz * s * 5.2;
        const y = H(x, z);
        addInst('lamp_post', x, y, z, 0);
        light(x, y + 2.6, z, 9, 1);
      }
    }
  }
  // along the quays
  for (let i = 0; i < riverPts.length; i += 3) {
    const p = riverPts[i];
    for (const s of [-1, 1]) {
      const x = p[0] + s * (RIVER_HW + 1.6);
      const z = p[1];
      const y = H(x, z);
      addInst('lamp_post', x, y, z, 0);
      light(x, y + 2.6, z, 8, 1);
    }
  }
  // around the lake
  for (let a = 0; a < Math.PI * 2; a += Math.PI / 14) {
    const x = LAKE.x + Math.cos(a) * (LAKE.rx + 2.2);
    const z = LAKE.z + Math.sin(a) * (LAKE.rz + 2.2);
    if (Math.abs(z - 244) < 3 && x < LAKE.x) continue;
    const y = H(x, z);
    addInst('lamp_post', x, y, z, 0);
    light(x, y + 2.6, z, 8, 1);
  }
  // palace courts
  for (const [z, y] of [
    [-66, P.y1],
    [-100, P.y1],
    [-124, P.y2],
    [-168, P.y2],
    [-196, P.y3],
  ]) {
    for (const s of [-1, 1]) {
      addInst('lamp_post', P.x + s * 10, y, z, 0);
      light(P.x + s * 10, y + 2.6, z, 10, 1);
    }
  }
  // market square ring
  for (let k = 0; k < 10; k++) {
    const x = MARKET.x - MARKET.w / 2 + 3 + (k % 5) * ((MARKET.w - 6) / 4);
    const z = k < 5 ? MARKET.z - MARKET.d / 2 + 3 : MARKET.z + MARKET.d / 2 - 3;
    addInst('lamp_post', x, marketY, z, 0);
    light(x, marketY + 2.6, z, 10, 1);
  }
  // trail lanterns
  {
    const sp = plan.paths.summit;
    for (let i = 1; i < sp.length - 1; i += 3) {
      const a = sp[i - 1];
      const c = sp[i + 1];
      const L = Math.hypot(c[0] - a[0], c[1] - a[1]) || 1;
      const nx = -(c[1] - a[1]) / L;
      const nz = (c[0] - a[0]) / L;
      const off = i % 2 ? 3.2 : -3.2;
      const x = sp[i][0] + nx * off;
      const z = sp[i][1] + nz * off;
      const y = H(x, z);
      if (Math.abs(y - sp[i][2]) > 3) continue;
      addInst('lamp_post', x, y, z, 0);
      light(x, y + 2.6, z, 7, 0.8);
    }
  }
  // lights for landmark buildings (glow pools)
  const glowAt = (x, y, z, r, k) => light(x, y, z, r, k);
  glowAt(LEDGE.x, LEDGE.y + 6, LEDGE.z, 26, 1.6);
  glowAt(S.x, sumY + 3, S.z, 12, 1.2);
  glowAt(P.x, P.y2 + 8, -144, 30, 1.4);
  glowAt(GATE.x, gateY + 4, GATE.z, 22, 1.4);
  glowAt(PAGODA.x, pagY + 4, PAGODA.z, 18, 1.2);
  glowAt(MARKET.x, marketY + 4, MARKET.z, 26, 1.0);
  for (const bp of bridges) glowAt(bp[0], bp[1] + 2, bp[2], 12, 1.0);

  // ---------- people (scale figures) ----------
  {
    const people = catalog.groups.person;
    const pr = new Rng(seed * 77 + 5);
    const person = (x, z, rot, y) => {
      const yy = y !== undefined ? y : H(x, z);
      addInst(people[pr.int(0, people.length)], x, yy, z, rot, { person: 1 });
    };
    U('guard', { kind: 'person', guard: true, seed: 13 });
    const guard = (x, z, rot, y) => addInst('guard', x, y !== undefined ? y : H(x, z), z, rot, { person: 1 });
    // along the avenue
    for (let i = 2; i < avenue.length - 1; i += 3) {
      const a = avenue[i - 1];
      const c = avenue[i + 1];
      const L = Math.hypot(c[0] - a[0], c[1] - a[1]) || 1;
      const nx = -(c[1] - a[1]) / L;
      const nz = (c[0] - a[0]) / L;
      const n = pr.int(1, 4);
      for (let k = 0; k < n; k++) {
        const off = pr.range(-3.6, 3.6);
        const x = avenue[i][0] + nx * off + pr.range(-1, 1);
        const z = avenue[i][1] + nz * off + pr.range(-1, 1);
        person(x, z, pr.chance(0.5) ? 0 : 2);
      }
    }
    // people climbing the stair lanes
    for (const L of lanes) for (let i = 3; i < L.length; i += 7) person(L[i][0] + pr.range(-0.6, 0.6), L[i][1] + pr.range(-0.6, 0.6), pr.chance(0.5) ? 1 : 3);
    // market crowd
    const mk = plan.market;
    for (let k = 0; k < 26; k++) {
      const x = mk.x - mk.w / 2 + 3 + pr.range(0, mk.w - 6);
      const z = mk.z - mk.d / 2 + 3 + pr.range(0, mk.d - 6);
      if (Math.abs(x - (mk.x - 16)) < 11 && Math.abs(z - (mk.z - 4)) < 11) continue;
      person(x, z, pr.int(0, 4), mk.y);
    }
    // lake promenade & quays
    for (let a = 0.2; a < Math.PI * 2; a += 0.45) {
      const x = LAKE.x + Math.cos(a) * (LAKE.rx + 3.4);
      const z = LAKE.z + Math.sin(a) * (LAKE.rz + 3.4);
      if (pr.chance(0.35)) person(x, z, pr.int(0, 4));
    }
    for (let i = 2; i < riverPts.length; i += 5) {
      const p = riverPts[i];
      const s = pr.chance(0.5) ? 1 : -1;
      person(p[0] + s * (RIVER_HW + pr.range(2.5, 6)), p[1] + pr.range(-2, 2), pr.chance(0.5) ? 1 : 3);
    }
    // palace guards along the imperial way and at the gates
    for (let z = -118; z >= -130; z -= 3) {
      guard(P.x - 7, z, 1, P.y2);
      guard(P.x + 7, z, 3, P.y2);
    }
    for (const dx of [-9, -5, 5, 9]) guard(P.x + dx, -40, 0, P.y1);
    for (const dx of [-5, -2.6, 2.6, 5]) guard(GATE.x + dx, GATE.z + 16, 0);
    // guards on the city walls beside the watchtowers
    for (const t of instances.filter((it) => it.id === 'wall_tower')) {
      const along = t.rot & 1 ? [0, 1] : [1, 0];
      for (const s of [-1, 1]) guard(t.x + along[0] * s * 7.5, t.z + along[1] * s * 7.5, t.rot, t.y);
    }
    // visitors at the waterfall tower, the summit and on the trail
    for (let k = 0; k < 4; k++) {
      const a = -0.6 + k * 0.5;
      person(LEDGE.x + Math.cos(a) * 12, LEDGE.z + Math.sin(a) * 10, 3, LEDGE.y);
    }
    for (let k = 0; k < 3; k++) person(S.x - 5 + k * 3.4, S.z + 6.5, 0, sumY);
    for (let i = 6; i < trail.length; i += 14) person(trail[i][0], trail[i][1], pr.int(0, 4));
  }

  // ---------- trees ----------
  const treeGroups = catalog.groups;
  const pickTree = (g, x, z) => treeGroups[g][Math.floor(rand01(x, z, 21) * treeGroups[g].length)];
  const treeTmp = new Float64Array(4);
  const occupiedByLot = (x, z) => {
    const i = Math.floor(x - RG.x0);
    const j = Math.floor(z - RG.z0);
    if (i < 0 || j < 0 || i >= RG.nx || j >= RG.nz) return false;
    return res[j * RG.nx + i] > 0;
  };
  let treeCount = 0;
  const T0 = -1150;
  const T1 = 1150;
  for (let z = T0; z <= 900; z += 6) {
    for (let x = T0; x <= T1; x += 6) {
      const dc = Math.hypot(x, z + 100);
      // sparser far away
      const spacingKeep = dc < 700 ? 1 : dc < 1000 ? 0.45 : 0.2;
      if (rand01(x, z, 30) > spacingKeep) continue;
      const jx = x + (rand01(x, z, 31) - 0.5) * 5.4;
      const jz = z + (rand01(x, z, 32) - 0.5) * 5.4;
      field.sample(jx, jz, 0.2, treeTmp);
      const h = treeTmp[0];
      const top = treeTmp[1];
      if (h < SITE.cloudTop - 30) continue;
      const inCityT = field.terrAt(jx, jz) > 0.3;
      if (top !== TM.NATURAL && top !== TM.GARDEN) continue;
      if (occupiedByLot(jx, jz)) continue;
      const hx = field.heightAt(jx + 1.2, jz);
      const hz = field.heightAt(jx, jz + 1.2);
      const slope = Math.hypot(hx - h, hz - h) / 1.2;
      const forest = geo.forest(jx, jz);
      let g = null;
      const r = rand01(x, z, 33);
      if (inCityT) {
        if (r > 0.1) continue;
        g = r < 0.02 ? 'blossom' : r < 0.06 ? 'broad' : r < 0.07 ? 'maple' : 'bamboo';
      } else {
        if (slope > 2.2) {
          if (r > 0.18) continue;
          g = 'pine';
        } else {
          if (r > forest * 0.9) continue;
          const alt = h;
          if (alt > 330 && rand01(x, z, 36) > 0.35) continue;
          if (alt > 230 || slope > 1.1) g = r < 0.75 * forest ? 'pine' : 'cypress';
          else if (alt < 60 && r < 0.2) g = 'bamboo';
          else {
            const k = rand01(x, z, 34);
            g = k < 0.4 ? 'pine' : k < 0.72 ? 'broad' : k < 0.8 ? 'maple' : k < 0.95 ? 'cypress' : 'blossom';
          }
        }
      }
      addInst(pickTree(g, x, z), jx, h, jz, Math.floor(rand01(x, z, 35) * 4), { tree: 1 });
      treeCount++;
    }
  }
  // willows along the river and lake
  for (let i = 1; i < riverPts.length; i += 2) {
    const p = riverPts[i];
    for (const s of [-1, 1]) {
      if (rand01(p[0], p[1], 40 + s) < 0.35) continue;
      const x = p[0] + s * (RIVER_HW + 5.5);
      const z = p[1] + 3;
      addInst(pickTree('willow', x, z), x, H(x, z), z, Math.floor(rand01(x, z, 41) * 4), { tree: 1 });
      treeCount++;
    }
  }
  for (let a = 0.1; a < Math.PI * 2; a += Math.PI / 11) {
    const x = LAKE.x + Math.cos(a) * (LAKE.rx + 5);
    const z = LAKE.z + Math.sin(a) * (LAKE.rz + 5);
    if (!resFree(x - 0.5, z - 0.5, x + 0.5, z + 0.5) && rand01(x, z, 42) < 0.5) continue;
    addInst(pickTree('willow', x, z), x, H(x, z), z, Math.floor(rand01(x, z, 43) * 4), { tree: 1 });
    treeCount++;
  }
  // blossom trees in the palace courts
  for (const [x, z, y] of [
    [P.x - 18, -96, P.y1],
    [P.x + 18, -96, P.y1],
    [P.x - 20, -176, P.y2],
    [P.x + 20, -176, P.y2],
    [P.x - 22, -220, P.y3],
    [P.x + 22, -220, P.y3],
  ]) {
    addInst(pickTree('blossom', x, z), x, y, z, 0, { tree: 1 });
    treeCount++;
  }
  // pines around the summit & ledge
  for (let k = 0; k < 10; k++) {
    const a = (k / 10) * Math.PI * 2;
    const x = S.x + Math.cos(a) * rng.range(11, 20);
    const z = S.z + Math.sin(a) * rng.range(11, 20);
    addInst(pickTree('pine', x, z), x, H(x, z), z, k & 3, { tree: 1 });
  }
  for (const [tx, tz] of plan.pillarSites || []) {
    addInst(pickTree('pine', tx, tz), tx, H(tx, tz), tz, Math.floor(rand01(tx, tz, 44) * 4), { tree: 1 });
    treeCount++;
  }
  plan.treeCount = treeCount;

  // ---------- tour route & viewpoints ----------
  const tour = [];
  const push = (pts, lift) => {
    for (const p of pts) tour.push([p[0], (p[2] !== undefined ? p[2] : H(p[0], p[1])) + lift, p[1]]);
  };
  push(approach.slice(0, 6), 3);
  push(avenue.filter((_, i) => i % 3 === 0 && avenue[i][1] > -30), 3.2);
  // drone move over the palace gate, the courts and the halls, then on to the basin
  tour.push(
    [P.x, P.y1 + 10, -34],
    [P.x, P.y1 + 34, -52],
    [P.x, P.y1 + 30, -80],
    [P.x, P.y2 + 24, -106],
    [P.x, P.y2 + 40, -128],
    [P.x, P.y2 + 42, -160],
    [P.x + 4, P.y3 + 28, -196],
    [P.x + 16, P.y3 + 26, -222],
    [18, SITE.poolY + 12, -244],
    [30, SITE.poolY + 7, -262],
    [44, SITE.poolY + 5, -280],
  );
  // drone flight: in front of the falls, around the tower, then along the ridge to the summit
  tour.push(
    [26, SITE.poolY + 10, -318],
    [6, SITE.poolY + 18, -350],
    [-6, SITE.poolY + 40, -360],
    [4, SITE.poolY + 62, -358],
    [24, LEDGE.y + 26, -352],
    [48, LEDGE.y + 30, -356],
    [58, LEDGE.y + 38, -378],
    [48, LEDGE.y + 50, -400],
    [70, SITE.upperY + 16, -420],
  );
  for (let i = 0; i < trail.length - 4; i += 4) {
    const p = trail[i];
    tour.push([p[0], Math.max(p[2], natural(p[0], p[1])) + 10, p[1]]);
  }
  tour.push([S.x + 16, sumY + 9, S.z - 12], [S.x + 13, sumY + 8, S.z - 2], [S.x + 12, sumY + 8, S.z + 8]);
  // ensure the tour never dips below the ground (+2.2 m eye clearance)
  plan.tour = resample(
    tour.map((p) => [p[0], p[2], p[1]]),
    6,
  ).map((p) => [p[0], Math.max(p[2], H(p[0], p[1]) + 2.2), p[1]]);

  plan.views = {
    overview: { pos: [-40, 330, 760], target: [0, 110, -120] },
    gate: { pos: [-60, 62, 470], target: [-30, 48, 380] },
    avenue: { pos: [-44, 52, 250], target: [-40, 60, 120] },
    palace: { pos: [-22, 112, 10], target: [-22, 100, -160] },
    waterfall: { pos: [-60, 170, -210], target: [-5, 200, -392] },
    tower: { pos: [-40, 196, -330], target: [30, 196, -377] },
    summit: { pos: [150, 430, -440], target: [206, 405, -520] },
    clouds: { pos: [-150, 58, 720], target: [-10, 100, 20] },
  };
  plan.labels = [
    { text: '正阳门', pos: [GATE.x, gateY + 22, GATE.z] },
    { text: '观瀑阁', pos: [LEDGE.x, LEDGE.y + 52, LEDGE.z] },
    { text: '揽云亭', pos: [S.x, sumY + 14, S.z] },
    { text: '太和殿', pos: [P.x, P.y2 + 34, -144] },
    { text: '凌霄塔', pos: [PAGODA.x, pagY + 64, PAGODA.z] },
    { text: '飞云瀑', pos: [SITE.fallX, 200, -392] },
    { text: '镜湖', pos: [LAKE.x, 48, LAKE.z], far: 700 },
    { text: '宫城', pos: [P.x, P.y1 + 30, -60], far: 900 },
  ];
  plan.site = { gate: GATE, wgate: WGATE, lake: LAKE, market: plan.market, ledge: LEDGE, palace: PALACE, fallZ };
  return plan;
}
