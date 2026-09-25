// Architectural voxel components: platforms (台基/须弥座), stairs, balustrades, columns, lattice
// facades (隔扇/槛窗), painted beams, bracket sets (斗拱), lanterns, plaques.
import { PAL } from './palette.js';

export function spread(a, b, n) {
  // n bays -> n+1 positions from a to b inclusive (integers)
  const out = [];
  for (let i = 0; i <= n; i++) out.push(Math.round(a + ((b - a) * i) / n));
  return out;
}

// stone platform [x0,x1) x [z0,z1), top at y0+h
export function platform(M, x0, z0, x1, z1, y0, h, o = {}) {
  const mat = o.mat || PAL.STONE;
  M.box(x0, y0, z0, x1, y0 + h, z1, mat);
  if (o.cap) M.box(x0, y0 + h - 1, z0, x1, y0 + h, z1, o.cap);
  if (o.sumeru && h >= 5) {
    // recessed waist moulding
    const m0 = y0 + Math.floor(h * 0.35);
    const m1 = y0 + Math.ceil(h * 0.65);
    for (let y = m0; y < m1; y++) {
      for (let x = x0; x < x1; x++) {
        M.set(x, y, z0, 0);
        M.set(x, y, z1 - 1, 0);
      }
      for (let z = z0; z < z1; z++) {
        M.set(x0, y, z, 0);
        M.set(x1 - 1, y, z, 0);
      }
    }
    if (o.trim) {
      M.box(x0, y0, z0, x1, y0 + 1, z1, o.trim);
      M.box(x0, y0 + h - 1, z0, x1, y0 + h, z1, o.trim);
    }
  }
  if (o.rail) balustrade(M, x0, z0, x1, z1, y0 + h, o.railMat || PAL.MARBLE, o.gaps || []);
}

// railing along the rectangle edge at height y (posts every 6, rails at 1 and 4)
export function balustrade(M, x0, z0, x1, z1, y, mat, gaps = [], h = 5) {
  const inGap = (x, z) => gaps.some((g) => x >= g[0] && x < g[2] && z >= g[1] && z < g[3]);
  const put = (x, z, i) => {
    if (inGap(x, z)) return;
    const post = i % 6 === 0;
    if (post) for (let yy = 0; yy < h; yy++) M.set(x, y + yy, z, mat);
    M.set(x, y + h - 2, z, mat);
    M.set(x, y, z, mat);
  };
  let i = 0;
  for (let x = x0; x < x1; x++) put(x, z0, i++);
  i = 0;
  for (let x = x0; x < x1; x++) put(x, z1 - 1, i++);
  i = 0;
  for (let z = z0; z < z1; z++) put(x0, z, i++);
  i = 0;
  for (let z = z0; z < z1; z++) put(x1 - 1, z, i++);
}

// straight stair descending toward +z (dir=1) or -z (dir=-1) from a platform edge at zEdge
// tread 2 voxels, riser 1; x range [xa, xb)
export function stairs(M, xa, xb, zEdge, yTop, yBottom, dir = 1, o = {}) {
  const mat = o.mat || PAL.STONE;
  const steps = yTop - yBottom;
  for (let s = 0; s < steps; s++) {
    const yy = yTop - 1 - s;
    const za = dir > 0 ? zEdge + s * 2 : zEdge - (s + 1) * 2;
    const zb = za + 2;
    M.box(xa, yBottom, za, xb, yy + 1, zb, mat);
  }
  if (o.cheek) {
    // side cheek walls
    const len = steps * 2;
    const zA = dir > 0 ? zEdge : zEdge - len;
    for (let s = 0; s < len; s++) {
      const z = zA + s;
      const hh = dir > 0 ? yTop - Math.floor(s / 2) : yBottom + Math.floor(s / 2) + 1;
      M.box(xa - 1, yBottom, z, xa, hh + 1, z + 1, o.cheek);
      M.box(xb, yBottom, z, xb + 1, hh + 1, z + 1, o.cheek);
    }
  }
  if (o.ramp) {
    // carved imperial ramp (御路) in the middle
    const c = Math.floor((xa + xb) / 2);
    const len = steps * 2;
    const zA = dir > 0 ? zEdge : zEdge - len;
    for (let s = 0; s < len; s++) {
      const z = zA + s;
      const frac = dir > 0 ? 1 - s / len : s / len;
      const hh = yBottom + Math.round(frac * steps);
      M.box(c - o.ramp, yBottom, z, c + o.ramp, hh, z + 1, PAL.MARBLE);
    }
  }
}

export function column(M, x, z, y0, y1, mat = PAL.RED, size = 2) {
  M.box(x, y0, z, x + size, y1, z + size, mat);
  // stone plinth
  M.box(x - (size > 1 ? 0 : 0), y0, z, x + size, y0 + 1, z + size, PAL.STONE);
}

// panel fill inside a bay; orientation: 'x' panel spans x at plane z; 'z' spans z at plane x
function panelVoxel(kind, u, v, uw, vh, o) {
  // u across (0..uw-1), v up (0..vh-1); returns material or 0
  const frame = PAL.LATTICE;
  switch (kind) {
    case 'door': {
      if (u === 0 || u === uw - 1 || v === vh - 1) return frame;
      const lower = Math.max(2, Math.floor(vh * 0.32));
      if (v < lower) return v === lower - 1 ? frame : o.doorPanel || PAL.WOOD_D;
      // leaf dividers every 4-5
      const leaf = Math.max(3, Math.round(uw / Math.max(2, Math.round(uw / 5))));
      if (u % leaf === 0) return frame;
      return (u + v) % 2 === 0 || v % 3 === 0 ? frame : PAL.PAPER;
    }
    case 'window': {
      const sill = Math.max(2, Math.floor(vh * 0.4));
      if (v < sill) return o.lowWall || PAL.BRICK;
      if (u === 0 || u === uw - 1 || v === vh - 1 || v === sill) return frame;
      return (u % 2 === 0 && v % 2 === 0) || (u + v) % 4 === 0 ? frame : PAL.PAPER;
    }
    case 'smallwin': {
      const w0 = Math.floor(uw * 0.3);
      const w1 = uw - w0;
      const s0 = Math.floor(vh * 0.45);
      const s1 = Math.min(vh - 2, s0 + Math.max(3, Math.floor(vh * 0.3)));
      if (u >= w0 && u < w1 && v >= s0 && v < s1) {
        if (u === w0 || u === w1 - 1 || v === s0 || v === s1 - 1) return PAL.WOOD_D;
        return u % 2 === 0 ? PAL.LATTICE : PAL.PAPER;
      }
      return o.wallMat || PAL.PLASTER;
    }
    case 'wall':
      return o.wallMat || PAL.PLASTER;
    case 'shop':
      return -1; // handled separately
    default:
      return 0;
  }
}

// Building body with columns & facades.
// o: {x0,z0,x1,z1,y0,y1, bayX, bayZ, front, back, left, right, colMat, wallMat, lowWall, beam:'painted'|'plain'|'none', interior}
export function body(M, o) {
  const { x0, z0, x1, z1, y0, y1 } = o;
  const cs = o.colSize || 2;
  const beamH = o.beam === 'none' ? 0 : o.beamH || 3;
  if (o.interior !== false) M.box(x0 + 1, y0, z0 + 1, x1 - 1, y1, z1 - 1, PAL.INTERIOR);
  const xs = spread(x0, x1 - cs, o.bayX);
  const zs = spread(z0, z1 - cs, o.bayZ);
  const colMat = o.colMat || PAL.RED;
  for (const x of xs) {
    column(M, x, z0, y0, y1, colMat, cs);
    column(M, x, z1 - cs, y0, y1, colMat, cs);
  }
  for (const z of zs) {
    column(M, x0, z, y0, y1, colMat, cs);
    column(M, x1 - cs, z, y0, y1, colMat, cs);
  }
  const vh = y1 - y0 - beamH;
  const sideFill = (kindFor, positions, fixed, axis, inward) => {
    for (let i = 0; i < positions.length - 1; i++) {
      const a = positions[i] + cs;
      const b = positions[i + 1];
      const uw = b - a;
      if (uw <= 0) continue;
      const kind = typeof kindFor === 'function' ? kindFor(i, positions.length - 1) : kindFor;
      if (kind === 'open') continue;
      if (kind === 'shop') {
        // recessed open shop front with counter
        for (let u = 0; u < uw; u++)
          for (let v = 0; v < vh; v++) {
            const p = a + u;
            const put = (d, m) => (axis === 'x' ? M.set(p, y0 + v, fixed + inward * d, m) : M.set(fixed + inward * d, y0 + v, p, m));
            put(0, 0);
            put(1, 0);
            put(2, PAL.INTERIOR);
            if (v < 4) put(0, v === 3 ? PAL.WOOD_L : PAL.WOOD);
          }
        continue;
      }
      for (let u = 0; u < uw; u++)
        for (let v = 0; v < vh; v++) {
          const m = panelVoxel(kind, u, v, uw, vh, o);
          if (!m) continue;
          const p = a + u;
          if (axis === 'x') M.set(p, y0 + v, fixed, m);
          else M.set(fixed, y0 + v, p, m);
        }
    }
  };
  const inset = cs - 1;
  sideFill(o.front || 'door', xs, z1 - 1 - inset, 'x', -1);
  sideFill(o.back || 'wall', xs, z0 + inset, 'x', 1);
  sideFill(o.left || 'wall', zs, x0 + inset, 'z', 1);
  sideFill(o.right || 'wall', zs, x1 - 1 - inset, 'z', -1);
  // beams
  if (beamH > 0) {
    const painted = o.beam === 'painted';
    for (let yy = y1 - beamH; yy < y1; yy++) {
      const band = yy - (y1 - beamH);
      for (let x = x0; x < x1; x++) {
        const m = painted ? (band === 1 ? ((x >> 1) % 3 === 0 ? PAL.GOLD : PAL.PAINT_G) : PAL.PAINT_B) : PAL.WOOD_D;
        M.set(x, yy, z0, m);
        M.set(x, yy, z1 - 1, m);
      }
      for (let z = z0; z < z1; z++) {
        const m = painted ? (band === 1 ? ((z >> 1) % 3 === 0 ? PAL.GOLD : PAL.PAINT_G) : PAL.PAINT_B) : PAL.WOOD_D;
        M.set(x0, yy, z, m);
        M.set(x1 - 1, yy, z, m);
      }
    }
  }
  return { xs, zs };
}

// corbelled bracket band (斗拱) from y upward `layers` layers, stepping out 1 per layer
export function brackets(M, x0, z0, x1, z1, y, layers = 2) {
  for (let l = 0; l < layers; l++) {
    const e = l + 1;
    for (let x = x0 - e; x < x1 + e; x++) {
      const m = ((x - x0) >> 1) % 2 === 0 ? PAL.PAINT_G : PAL.PAINT_B;
      if (((x - x0) & 1) === 0 || l === layers - 1) {
        M.set(x, y + l, z0 - e, m);
        M.set(x, y + l, z1 - 1 + e, m);
      }
    }
    for (let z = z0 - e; z < z1 + e; z++) {
      const m = ((z - z0) >> 1) % 2 === 0 ? PAL.PAINT_G : PAL.PAINT_B;
      if (((z - z0) & 1) === 0 || l === layers - 1) {
        M.set(x0 - e, y + l, z, m);
        M.set(x1 - 1 + e, y + l, z, m);
      }
    }
    // fill the core so the roof has a base
    M.box(x0 - l, y + l, z0 - l, x1 + l, y + l + 1, z1 + l, PAL.INTERIOR);
  }
}

// small hanging lantern (red) under an eave: top at y, occupying 2x3x2
export function lantern(M, x, y, z, mat = PAL.LANTERN) {
  M.set(x, y, z, PAL.WOOD_D);
  M.box(x, y - 3, z, x + 1, y - 1, z + 1, mat);
  M.box(x - 1, y - 3, z, x + 2, y - 2, z + 1, mat);
  M.box(x, y - 3, z - 1, x + 1, y - 2, z + 2, mat);
  M.set(x, y - 4, z, PAL.GOLD);
}

// signboard / plaque on a facade plane (axis 'x' at plane z facing dir)
export function plaque(M, cx, y, z, w, h, dir = 1) {
  const x0 = Math.round(cx - w / 2);
  for (let u = 0; u < w; u++)
    for (let v = 0; v < h; v++) {
      const edge = u === 0 || v === 0 || u === w - 1 || v === h - 1;
      const glyph = !edge && (u % 3 === 1) && v > 0 && v < h - 1;
      M.set(x0 + u, y + v, z + dir, edge ? PAL.GOLD : glyph ? PAL.SIGN_GOLD : PAL.SIGN);
    }
}
