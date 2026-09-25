// Ambient life: sky lanterns (孔明灯) released over the city at night and flights of cranes
// (仙鹤) gliding around the peaks by day. Both are small 0.2 m voxel figures, animated.
import * as THREE from 'three';
import { COMMON, ATMOSPHERE, LIGHTING } from './glsl.js';

// build a merged box geometry from voxels [x, y, z, hexColor, flag]
function voxelGeometry(voxels, size = 0.2) {
  const pos = [];
  const col = [];
  const nrm = [];
  const flag = [];
  const idx = [];
  const occ = new Set(voxels.map((v) => `${v[0]},${v[1]},${v[2]}`));
  const faces = [
    [[1, 0, 0], [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]]],
    [[-1, 0, 0], [[0, 0, 1], [0, 1, 1], [0, 1, 0], [0, 0, 0]]],
    [[0, 1, 0], [[0, 1, 1], [1, 1, 1], [1, 1, 0], [0, 1, 0]]],
    [[0, -1, 0], [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]]],
    [[0, 0, 1], [[1, 0, 1], [1, 1, 1], [0, 1, 1], [0, 0, 1]]],
    [[0, 0, -1], [[0, 0, 0], [0, 1, 0], [1, 1, 0], [1, 0, 0]]],
  ];
  for (const [x, y, z, hex, f = 0] of voxels) {
    const c = new THREE.Color(hex);
    for (const [n, quad] of faces) {
      if (occ.has(`${x + n[0]},${y + n[1]},${z + n[2]}`)) continue;
      const base = pos.length / 3;
      for (const q of quad) {
        pos.push((x + q[0]) * size, (y + q[1]) * size, (z + q[2]) * size);
        col.push(c.r, c.g, c.b);
        nrm.push(n[0], n[1], n[2]);
        flag.push(f);
      }
      idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  g.setAttribute('normal', new THREE.Float32BufferAttribute(nrm, 3));
  g.setAttribute('aFlag', new THREE.Float32BufferAttribute(flag, 1));
  g.setIndex(idx);
  g.computeBoundingSphere();
  return g;
}

const VERT = /* glsl */ `
attribute vec3 color;
attribute float aFlag;
uniform float uFlap;
uniform float uTime;
varying vec3 vColor;
varying vec3 vN;
varying vec3 vWorld;
varying float vFlag;
void main() {
  vec3 p = position;
  vec3 n = normal;
  // wing flap: flag 2 = wing voxel, hinge along the body axis (z)
  if (aFlag > 1.5 && aFlag < 2.5) {
    float ph = instanceMatrix[3].x * 0.37 + instanceMatrix[3].z * 0.21;
    float a = sin(uTime * 3.2 + ph) * 0.55 * uFlap + 0.12;
    float s = sign(p.x);
    float c = cos(a);
    float sn = sin(a) * s;
    p = vec3(p.x * c, p.y + abs(p.x) * sin(a), p.z);
    n = normalize(vec3(n.x * c - n.y * sn, n.x * sn + n.y * c, n.z));
  }
  vec4 wp = modelMatrix * instanceMatrix * vec4(p, 1.0);
  vWorld = wp.xyz;
  vN = normalize(mat3(modelMatrix * instanceMatrix) * n);
  vColor = color;
  vFlag = aFlag;
  gl_Position = projectionMatrix * viewMatrix * wp;
}
`;

const FRAG = /* glsl */ `
${COMMON}
${ATMOSPHERE}
${LIGHTING}
uniform float uGlow;
uniform float uFade;
varying vec3 vColor;
varying vec3 vN;
varying vec3 vWorld;
varying float vFlag;
void main() {
  vec3 col;
  if (vFlag > 0.5 && vFlag < 1.5) {
    // lantern paper, lit from inside
    col = vColor * (0.3 + 3.2 * uGlow);
  } else {
    col = shade(vColor, normalize(vN), 0.9, 1.0, vWorld);
  }
  col = applyFog(col, vWorld, cameraPosition);
  gl_FragColor = vec4(col * uFade, 1.0);
}
`;

function makeMaterial(uniforms, extra) {
  return new THREE.ShaderMaterial({
    uniforms: { ...uniforms, ...extra },
    vertexShader: VERT,
    fragmentShader: FRAG,
  });
}

export class Life {
  constructor(scene, plan, uniforms) {
    this.plan = plan;
    this.rng = mulberry(20260925);
    // ---- sky lanterns ----
    const lv = [];
    for (let y = 0; y < 4; y++)
      for (let x = 0; x < 3; x++)
        for (let z = 0; z < 3; z++) {
          const edge = (x === 0 || x === 2) && (z === 0 || z === 2);
          if (y === 3 && !(x === 1 && z === 1)) lv.push([x - 1.5, y, z - 1.5, '#ffdca0', 1]);
          else if (y < 3) lv.push([x - 1.5, y, z - 1.5, edge ? '#e8a060' : y === 0 ? '#ffe7b0' : '#ffcf8a', 1]);
        }
    this.lanternMat = makeMaterial(uniforms, { uGlow: { value: 0 }, uFade: { value: 1 }, uFlap: { value: 0 } });
    this.lanterns = new THREE.InstancedMesh(voxelGeometry(lv), this.lanternMat, 80);
    this.lanterns.frustumCulled = false;
    this.lanterns.count = 0;
    scene.add(this.lanterns);
    this.lState = Array.from({ length: 80 }, () => ({ on: false }));
    // release spots: lake shore, market, palace plaza, lots
    const s = plan.site;
    this.spots = [
      [s.lake.x - s.lake.rx - 3, 40, s.lake.z],
      [s.lake.x + s.lake.rx + 3, 40, s.lake.z + 20],
      [s.market.x, s.market.y, s.market.z],
      [plan.site.palace.x, plan.site.palace.y1, -40],
      [26, 41, 250],
    ];
    for (const inst of plan.instances) if ((inst.id.startsWith('house') || inst.id.startsWith('court')) && this.rng() < 0.05) this.spots.push([inst.x, inst.y + 6, inst.z]);

    // ---- cranes ----
    const W = '#f4f2ec';
    const K = '#1d1d1f';
    const R = '#c8322a';
    const cv = [];
    // body along +z (forward), wings along x
    for (let z = -2; z <= 2; z++) cv.push([0, 0, z, W, 0]);
    cv.push([0, 1, 2, W, 0], [0, 1, 3, W, 0], [0, 2, 4, W, 0], [0, 2, 5, K, 0], [0, 3, 5, R, 0], [0, 2, 6, '#c9a54a', 0]);
    cv.push([0, 0, -3, K, 0], [0, -1, -4, '#3a3a3a', 0], [0, -1, -5, '#3a3a3a', 0]);
    for (let x = 1; x <= 6; x++) {
      const c = x >= 5 ? K : W;
      cv.push([x, 0, 0, c, 2], [-x, 0, 0, c, 2], [x, 0, -1, c, 2], [-x, 0, -1, c, 2]);
      if (x <= 4) cv.push([x, 0, 1, W, 2], [-x, 0, 1, W, 2]);
    }
    this.craneMat = makeMaterial(uniforms, { uGlow: { value: 0 }, uFade: { value: 1 }, uFlap: { value: 1 } });
    const cg = voxelGeometry(cv, 0.2); // 13 voxels = 2.6 m wingspan
    this.cranes = new THREE.InstancedMesh(cg, this.craneMat, 24);
    this.cranes.frustumCulled = false;
    scene.add(this.cranes);
    this.flocks = [
      { cx: 60, cz: -380, r: 170, y: 250, speed: 0.045, n: 7, ph: 0 },
      { cx: -60, cz: 60, r: 260, y: 215, speed: -0.032, n: 6, ph: 2 },
      { cx: 180, cz: -470, r: 120, y: 420, speed: 0.05, n: 5, ph: 4 },
      { cx: 40, cz: 420, r: 300, y: 70, speed: 0.028, n: 6, ph: 1 },
    ];
    this._m = new THREE.Matrix4();
    this._q = new THREE.Quaternion();
    this._e = new THREE.Euler();
    this._p = new THREE.Vector3();
    this._s = new THREE.Vector3(1, 1, 1);
    this.t = 0;
  }

  update(dt, day) {
    this.t += dt;
    // ---- sky lanterns ----
    const night = day.lamp;
    const lateEnough = day.time > 19.2 || day.time < 4.5;
    this.lanternMat.uniforms.uGlow.value = 0.6 + 0.4 * night;
    let n = 0;
    let active = 0;
    for (const L of this.lState) if (L.on) active++;
    // when the night starts (or the user jumps to it) fill the sky with already-rising lanterns
    const catchUp = active < 25;
    for (const L of this.lState) {
      if (!L.on && lateEnough && night > 0.85 && this.rng() < (catchUp ? 0.35 : dt * 0.06)) {
        const sp = this.spots[Math.floor(this.rng() * this.spots.length)];
        L.on = true;
        L.vy = 0.9 + this.rng() * 0.8;
        L.ph = this.rng() * 10;
        L.age = catchUp ? this.rng() * 260 : 0;
        L.x = sp[0] + (this.rng() - 0.5) * 12 + L.age * 1.1;
        L.y = sp[1] + 2 + L.age * L.vy;
        L.z = sp[2] + (this.rng() - 0.5) * 12;
      }
      if (!L.on) continue;
      L.age += dt;
      L.x += (1.1 + Math.sin(this.t * 0.2 + L.ph) * 0.6) * dt;
      L.z += Math.cos(this.t * 0.17 + L.ph) * 0.5 * dt;
      L.y += L.vy * dt * (L.age < 3 ? L.age / 3 : 1);
      if (L.y > 520 || L.age > 420 || night < 0.2) {
        L.on = false;
        continue;
      }
      this._p.set(L.x, L.y, L.z);
      this._e.set(Math.sin(this.t + L.ph) * 0.08, L.ph, Math.cos(this.t * 0.8 + L.ph) * 0.08);
      this._q.setFromEuler(this._e);
      this._m.compose(this._p, this._q, this._s);
      this.lanterns.setMatrixAt(n++, this._m);
    }
    this.lanterns.count = n;
    this.lanterns.instanceMatrix.needsUpdate = true;
    this.lanterns.visible = n > 0;

    // ---- cranes (daylight only) ----
    const dayK = THREE.MathUtils.smoothstep(day.sunElev, -4, 6);
    this.craneMat.uniforms.uFade.value = 1;
    let k = 0;
    if (dayK > 0.02) {
      for (const f of this.flocks) {
        const a = this.t * f.speed + f.ph;
        const dir = Math.sign(f.speed);
        for (let i = 0; i < f.n; i++) {
          // loose V formation trailing the leader along the circle
          const lag = Math.floor((i + 1) / 2) * 0.035 * dir;
          const side = i === 0 ? 0 : i % 2 ? 1 : -1;
          const ai = a - lag;
          const rr = f.r + side * Math.floor((i + 1) / 2) * 4;
          const x = f.cx + Math.cos(ai) * rr;
          const z = f.cz + Math.sin(ai) * rr;
          const y = f.y + Math.sin(this.t * 0.3 + i + f.ph) * 3 + Math.floor((i + 1) / 2) * 1.5;
          // heading = tangent of the circle
          const heading = Math.atan2(-Math.sin(ai) * dir, Math.cos(ai) * dir);
          this._p.set(x, y, z);
          this._e.set(0, heading, -0.18 * dir, 'YXZ');
          this._q.setFromEuler(this._e);
          this._m.compose(this._p, this._q, this._s);
          this.cranes.setMatrixAt(k++, this._m);
        }
      }
    }
    this.cranes.count = k;
    this.cranes.instanceMatrix.needsUpdate = true;
    this.cranes.visible = k > 0;
  }
}

function mulberry(seed) {
  let s = seed >>> 0;
  return () => {
    let t = (s = (s + 0x6d2b79f5) >>> 0);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
