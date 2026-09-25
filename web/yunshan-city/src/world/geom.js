// Small 2D polyline helpers used by the city plan.

export function polyLength(pts) {
  let L = 0;
  for (let i = 1; i < pts.length; i++) L += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
  return L;
}

// resample a polyline [[x,z,...]] at roughly `step` spacing (keeps extra fields interpolated)
export function resample(pts, step) {
  const out = [pts[0].slice()];
  let carry = 0;
  for (let i = 1; i < pts.length; i++) {
    const a = pts[i - 1];
    const b = pts[i];
    const L = Math.hypot(b[0] - a[0], b[1] - a[1]);
    let t = step - carry;
    while (t <= L) {
      const u = t / L;
      out.push(a.map((v, k) => v + (b[k] - v) * u));
      t += step;
    }
    carry = L - (t - step);
  }
  const last = pts[pts.length - 1];
  const tail = out[out.length - 1];
  if (Math.hypot(last[0] - tail[0], last[1] - tail[1]) > step * 0.35) out.push(last.slice());
  else out[out.length - 1] = last.slice();
  return out;
}

// moving-average smoothing of a field k (keeps endpoints)
export function smoothField(pts, k, passes = 1, win = 1) {
  let p = pts.map((q) => q.slice());
  for (let s = 0; s < passes; s++) {
    const n = p.map((q) => q.slice());
    for (let i = 1; i < p.length - 1; i++) {
      let sum = 0;
      let c = 0;
      for (let j = -win; j <= win; j++) {
        const q = p[Math.min(p.length - 1, Math.max(0, i + j))];
        sum += q[k];
        c++;
      }
      n[i][k] = sum / c;
    }
    p = n;
  }
  return p;
}

// Chaikin corner cutting for gentle curves
export function chaikin(pts, iters = 2) {
  let p = pts;
  for (let it = 0; it < iters; it++) {
    const n = [p[0]];
    for (let i = 0; i < p.length - 1; i++) {
      const a = p[i];
      const b = p[i + 1];
      n.push(a.map((v, k) => v * 0.75 + b[k] * 0.25));
      n.push(a.map((v, k) => v * 0.25 + b[k] * 0.75));
    }
    n.push(p[p.length - 1]);
    p = n;
  }
  return p;
}

export function pointInPoly(x, z, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const xi = poly[i][0];
    const zi = poly[i][1];
    const xj = poly[j][0];
    const zj = poly[j][1];
    if (zi > z !== zj > z && x < ((xj - xi) * (z - zi)) / (zj - zi) + xi) inside = !inside;
  }
  return inside;
}

// nearest point on segment; returns [dist, t]
export function segDist(px, pz, ax, az, bx, bz) {
  const dx = bx - ax;
  const dz = bz - az;
  const L2 = dx * dx + dz * dz;
  let t = L2 > 0 ? ((px - ax) * dx + (pz - az) * dz) / L2 : 0;
  t = t < 0 ? 0 : t > 1 ? 1 : t;
  const qx = ax + dx * t - px;
  const qz = az + dz * t - pz;
  return [Math.sqrt(qx * qx + qz * qz), t];
}
