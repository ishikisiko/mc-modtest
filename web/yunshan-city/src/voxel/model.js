// Dense voxel model in pivot-relative voxel coordinates (1 unit = 0.2 m). The pivot is the
// centre of the footprint at ground level (y = 0 is the first voxel layer above the ground).

export class VoxelModel {
  constructor(x0, y0, z0, x1, y1, z1) {
    this.x0 = Math.floor(x0);
    this.y0 = Math.floor(y0);
    this.z0 = Math.floor(z0);
    this.x1 = Math.ceil(x1);
    this.y1 = Math.ceil(y1);
    this.z1 = Math.ceil(z1);
    this.sx = this.x1 - this.x0;
    this.sy = this.y1 - this.y0;
    this.sz = this.z1 - this.z0;
    this.data = new Uint8Array(this.sx * this.sy * this.sz);
  }

  inb(x, y, z) {
    return x >= this.x0 && y >= this.y0 && z >= this.z0 && x < this.x1 && y < this.y1 && z < this.z1;
  }

  idx(x, y, z) {
    return ((y - this.y0) * this.sz + (z - this.z0)) * this.sx + (x - this.x0);
  }

  set(x, y, z, m) {
    x = Math.floor(x);
    y = Math.floor(y);
    z = Math.floor(z);
    if (this.inb(x, y, z)) this.data[this.idx(x, y, z)] = m;
  }

  setIfEmpty(x, y, z, m) {
    x = Math.floor(x);
    y = Math.floor(y);
    z = Math.floor(z);
    if (this.inb(x, y, z)) {
      const i = this.idx(x, y, z);
      if (!this.data[i]) this.data[i] = m;
    }
  }

  get(x, y, z) {
    x = Math.floor(x);
    y = Math.floor(y);
    z = Math.floor(z);
    return this.inb(x, y, z) ? this.data[this.idx(x, y, z)] : 0;
  }

  // fill [x0,x1) x [y0,y1) x [z0,z1)
  box(x0, y0, z0, x1, y1, z1, m) {
    const ax = Math.max(this.x0, Math.floor(Math.min(x0, x1)));
    const bx = Math.min(this.x1, Math.ceil(Math.max(x0, x1)));
    const ay = Math.max(this.y0, Math.floor(Math.min(y0, y1)));
    const by = Math.min(this.y1, Math.ceil(Math.max(y0, y1)));
    const az = Math.max(this.z0, Math.floor(Math.min(z0, z1)));
    const bz = Math.min(this.z1, Math.ceil(Math.max(z0, z1)));
    for (let y = ay; y < by; y++)
      for (let z = az; z < bz; z++) {
        let i = this.idx(ax, y, z);
        for (let x = ax; x < bx; x++) this.data[i++] = m;
      }
  }

  boxIfEmpty(x0, y0, z0, x1, y1, z1, m) {
    for (let y = Math.floor(y0); y < y1; y++)
      for (let z = Math.floor(z0); z < z1; z++)
        for (let x = Math.floor(x0); x < x1; x++) this.setIfEmpty(x, y, z, m);
  }

  // hollow rectangular shell (walls only) of thickness t
  walls(x0, y0, z0, x1, y1, z1, m, t = 1) {
    this.box(x0, y0, z0, x1, y1, z0 + t, m);
    this.box(x0, y0, z1 - t, x1, y1, z1, m);
    this.box(x0, y0, z0, x0 + t, y1, z1, m);
    this.box(x1 - t, y0, z0, x1, y1, z1, m);
  }

  replace(from, to, x0 = this.x0, y0 = this.y0, z0 = this.z0, x1 = this.x1, y1 = this.y1, z1 = this.z1) {
    for (let y = Math.max(y0, this.y0); y < Math.min(y1, this.y1); y++)
      for (let z = Math.max(z0, this.z0); z < Math.min(z1, this.z1); z++)
        for (let x = Math.max(x0, this.x0); x < Math.min(x1, this.x1); x++) {
          const i = this.idx(x, y, z);
          if (this.data[i] === from) this.data[i] = to;
        }
  }

  // vertical cylinder / n-gon prism centred at (cx, cz)
  prism(cx, cz, r, y0, y1, m, sides = 0, hollow = 0) {
    const R = Math.ceil(r + 1);
    for (let z = Math.floor(cz - R); z <= cz + R; z++)
      for (let x = Math.floor(cx - R); x <= cx + R; x++) {
        const d = polyDist(x + 0.5 - cx, z + 0.5 - cz, sides);
        if (d <= r && (!hollow || d > r - hollow)) for (let y = Math.floor(y0); y < y1; y++) this.set(x, y, z, m);
      }
  }

  ellipsoid(cx, cy, cz, rx, ry, rz, m, noiseFn = null, onlyEmpty = false) {
    for (let y = Math.floor(cy - ry - 1); y <= cy + ry + 1; y++)
      for (let z = Math.floor(cz - rz - 1); z <= cz + rz + 1; z++)
        for (let x = Math.floor(cx - rx - 1); x <= cx + rx + 1; x++) {
          const dx = (x + 0.5 - cx) / rx;
          const dy = (y + 0.5 - cy) / ry;
          const dz = (z + 0.5 - cz) / rz;
          let d = dx * dx + dy * dy + dz * dz;
          if (noiseFn) d += noiseFn(x, y, z, d);
          if (d <= 1) {
            if (onlyEmpty) this.setIfEmpty(x, y, z, typeof m === 'function' ? m(x, y, z, d) : m);
            else this.set(x, y, z, typeof m === 'function' ? m(x, y, z, d) : m);
          }
        }
  }

  // thick line from a to b (radius r)
  line(ax, ay, az, bx, by, bz, r, m) {
    const L = Math.hypot(bx - ax, by - ay, bz - az);
    const n = Math.max(1, Math.ceil(L * 2));
    for (let k = 0; k <= n; k++) {
      const t = k / n;
      const x = ax + (bx - ax) * t;
      const y = ay + (by - ay) * t;
      const z = az + (bz - az) * t;
      if (r <= 0.6) this.set(x, y, z, m);
      else {
        const R = Math.ceil(r);
        for (let dy = -R; dy <= R; dy++)
          for (let dz = -R; dz <= R; dz++)
            for (let dx = -R; dx <= R; dx++) if (dx * dx + dy * dy + dz * dz <= r * r) this.set(x + dx, y + dy, z + dz, m);
      }
    }
  }

  // copy another model into this one at offset (pivot-relative), skipping empty voxels
  paste(other, ox, oy, oz, rot = 0) {
    for (let y = other.y0; y < other.y1; y++)
      for (let z = other.z0; z < other.z1; z++)
        for (let x = other.x0; x < other.x1; x++) {
          const m = other.data[other.idx(x, y, z)];
          if (!m) continue;
          let rx = x;
          let rz = z;
          if (rot === 1) {
            rx = z;
            rz = -x - 1;
          } else if (rot === 2) {
            rx = -x - 1;
            rz = -z - 1;
          } else if (rot === 3) {
            rx = -z - 1;
            rz = x;
          }
          this.set(rx + ox, y + oy, rz + oz, m);
        }
  }

  // crop to occupied bounds; returns a compact record for meshing
  crop() {
    let ax = Infinity;
    let ay = Infinity;
    let az = Infinity;
    let bx = -Infinity;
    let by = -Infinity;
    let bz = -Infinity;
    const { sx, sy, sz, data } = this;
    for (let y = 0; y < sy; y++)
      for (let z = 0; z < sz; z++) {
        const row = (y * sz + z) * sx;
        for (let x = 0; x < sx; x++)
          if (data[row + x]) {
            if (x < ax) ax = x;
            if (x > bx) bx = x;
            if (y < ay) ay = y;
            if (y > by) by = y;
            if (z < az) az = z;
            if (z > bz) bz = z;
          }
      }
    if (ax === Infinity) return { sx: 1, sy: 1, sz: 1, ox: 0, oy: 0, oz: 0, data: new Uint8Array(1) };
    const nx = bx - ax + 1;
    const ny = by - ay + 1;
    const nz = bz - az + 1;
    const out = new Uint8Array(nx * ny * nz);
    for (let y = 0; y < ny; y++)
      for (let z = 0; z < nz; z++) {
        const src = ((y + ay) * sz + (z + az)) * sx + ax;
        out.set(data.subarray(src, src + nx), (y * nz + z) * nx);
      }
    // pivot position inside the cropped grid
    return { sx: nx, sy: ny, sz: nz, ox: -(this.x0 + ax), oy: -(this.y0 + ay), oz: -(this.z0 + az), data: out };
  }
}

// distance metric for regular polygons (sides=0 -> circle), using apothem-normalised distance
export function polyDist(dx, dz, sides) {
  if (!sides) return Math.hypot(dx, dz);
  if (sides === 4) return Math.max(Math.abs(dx), Math.abs(dz));
  let m = 0;
  for (let i = 0; i < sides; i++) {
    const a = ((i + 0.5) / sides) * Math.PI * 2;
    const d = dx * Math.cos(a) + dz * Math.sin(a);
    if (d > m) m = d;
  }
  return m;
}
