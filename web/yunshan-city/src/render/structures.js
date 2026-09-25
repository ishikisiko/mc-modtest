// Instanced LOD rendering of voxel prefabs (buildings, trees, props). Each prefab is meshed
// by a worker at five LODs (0.2/0.4/0.8/1.6/3.2 m); instances are bucketed per LOD by distance.
import * as THREE from 'three';
import { getQuadIndex } from './terrainLOD.js';

const BASE_LOD = [60, 150, 360, 850];

function hashSeed(x, z) {
  let h = Math.imul(Math.round(x * 5) | 0, 73856093) ^ Math.imul(Math.round(z * 5) | 0, 19349663);
  h = Math.imul(h ^ (h >>> 13), 0x5bd1e995);
  return ((h ^ (h >>> 15)) >>> 0) % 64;
}

export class StructureManager {
  constructor({ scene, pool, plan, material, depthMaterial, lodScale = 1 }) {
    this.pool = pool;
    this.material = material;
    this.depthMaterial = depthMaterial;
    this.group = new THREE.Group();
    this.group.name = 'structures';
    scene.add(this.group);
    this.lod = BASE_LOD.map((d) => d * lodScale);
    this.prefabs = new Map();
    for (const inst of plan.instances) {
      let p = this.prefabs.get(inst.id);
      if (!p) {
        p = { id: inst.id, inst: [], state: 0, meshes: [], bufs: [], attrs: [], tree: !!inst.tree, lamp: inst.id === 'lamp_post', person: !!inst.person };
        this.prefabs.set(inst.id, p);
      }
      p.inst.push(inst);
    }
    this.frame = 0;
    this.lastCam = new THREE.Vector3(1e9, 0, 0);
    this.stats = { prefabs: this.prefabs.size, ready: 0, drawn: 0, quads: 0 };
    this.dirty = true;
  }

  _minDist(p, cam) {
    let best = 1e18;
    for (const i of p.inst) {
      const dx = i.x - cam.x;
      const dz = i.z - cam.z;
      const d = dx * dx + dz * dz;
      if (d < best) best = d;
    }
    return Math.sqrt(best);
  }

  _request(p, cam) {
    const pri = this._minDist(p, cam) / 450 + (p.tree ? 0.15 : 0);
    if (p.state === 1) {
      const job = this.pool.pending.get('prefab:' + p.id);
      if (job) job.priority = pri;
      return;
    }
    p.state = 1;
    this.pool.submit(
      'prefab:' + p.id,
      { type: 'prefab', prefab: p.id },
      pri,
      (msg) => this._onPrefab(p, msg),
      () => {
        p.state = 3; // failed; do not retry forever
      },
    );
  }

  _onPrefab(p, msg) {
    p.pivot = msg.pivot;
    p.dims = msg.dims;
    const [sx, sy, sz] = msg.dims;
    p.radius = 0.5 * 0.2 * Math.hypot(sx, sy, sz);
    const n = p.inst.length;
    p.lodAvail = [];
    for (let L = 0; L <= 4; L++) {
      const l = msg.lods[L];
      if (!l || !l.quads) {
        p.lodAvail.push(false);
        p.meshes.push(null);
        p.bufs.push(null);
        p.attrs.push(null);
        continue;
      }
      p.lodAvail.push(true);
      const g = new THREE.InstancedBufferGeometry();
      g.setAttribute('aPos', new THREE.Uint16BufferAttribute(l.pos, 4));
      g.setAttribute('aCol', new THREE.Uint8BufferAttribute(l.col, 4, true));
      g.setIndex(getQuadIndex(l.quads));
      g.setDrawRange(0, l.quads * 6);
      const buf = new Float32Array(n * 4);
      const attr = new THREE.InstancedBufferAttribute(buf, 4);
      attr.setUsage(THREE.DynamicDrawUsage);
      g.setAttribute('aInst', attr);
      g.instanceCount = 0;
      const mesh = new THREE.Mesh(g, this.material);
      mesh.frustumCulled = false;
      mesh.visible = false;
      mesh.userData.depthMaterial = this.depthMaterial;
      mesh.userData.quads = l.quads;
      mesh.layers.enable(1);
      this.group.add(mesh);
      p.meshes.push(mesh);
      p.bufs.push(buf);
      p.attrs.push(attr);
    }
    // instance transforms
    const piv = p.pivot.map((v) => v * 0.2);
    const half = [sx * 0.1, sy * 0.1, sz * 0.1];
    p.px = new Float32Array(n * 4);
    p.cx = new Float32Array(n * 3);
    p.cur = new Int8Array(n).fill(-1);
    p.inst.forEach((it, k) => {
      const r = it.rot & 3;
      const rot = (v) => (r === 0 ? v : r === 1 ? [v[2], v[1], -v[0]] : r === 2 ? [-v[0], v[1], -v[2]] : [-v[2], v[1], v[0]]);
      const rp = rot(piv);
      p.px[k * 4] = it.x - rp[0];
      p.px[k * 4 + 1] = it.y - rp[1];
      p.px[k * 4 + 2] = it.z - rp[2];
      p.px[k * 4 + 3] = r + 4 * hashSeed(it.x, it.z);
      const rc = rot([half[0] - piv[0], half[1] - piv[1], half[2] - piv[2]]);
      p.cx[k * 3] = it.x + rc[0];
      p.cx[k * 3 + 1] = it.y + rc[1];
      p.cx[k * 3 + 2] = it.z + rc[2];
    });
    p.state = 2;
    this.stats.ready++;
    this.dirty = true;
  }

  update(camera) {
    this.frame++;
    const cam = camera.position;
    // (re)prioritise requests occasionally
    if (this.frame % 20 === 1) {
      for (const p of this.prefabs.values()) if (p.state === 0 || p.state === 1) this._request(p, cam);
    }
    const moved = cam.distanceToSquared(this.lastCam) > 4;
    if (!moved && !this.dirty && this.frame % 30 !== 0) return;
    this.lastCam.copy(cam);
    this.dirty = false;
    let drawn = 0;
    let quads = 0;
    const T = this.lod;
    for (const p of this.prefabs.values()) {
      if (p.state !== 2) continue;
      const counts = [0, 0, 0, 0, 0];
      const scale = p.tree ? 0.7 : 1;
      const cull = p.person ? 320 : p.lamp ? 450 : p.tree ? 2200 : 6000;
      const n = p.inst.length;
      for (let k = 0; k < n; k++) {
        const dx = p.cx[k * 3] - cam.x;
        const dy = p.cx[k * 3 + 1] - cam.y;
        const dz = p.cx[k * 3 + 2] - cam.z;
        const d = Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - p.radius);
        if (d > cull) {
          p.cur[k] = -1;
          continue;
        }
        let L = d < T[0] * scale ? 0 : d < T[1] * scale ? 1 : d < T[2] * scale ? 2 : d < T[3] * scale ? 3 : 4;
        // hysteresis: keep the finer LOD a little longer
        const prev = p.cur[k];
        if (prev >= 0 && prev === L - 1 && d < T[prev] * scale * 1.08) L = prev;
        while (L < 4 && !p.lodAvail[L]) L++;
        while (L > 0 && !p.lodAvail[L]) L--;
        if (!p.lodAvail[L]) continue;
        p.cur[k] = L;
        const buf = p.bufs[L];
        const c = counts[L]++;
        buf[c * 4] = p.px[k * 4];
        buf[c * 4 + 1] = p.px[k * 4 + 1];
        buf[c * 4 + 2] = p.px[k * 4 + 2];
        buf[c * 4 + 3] = p.px[k * 4 + 3];
      }
      for (let L = 0; L <= 4; L++) {
        const m = p.meshes[L];
        if (!m) continue;
        const c = counts[L];
        m.geometry.instanceCount = c;
        m.visible = c > 0;
        if (c > 0) {
          const a = p.attrs[L];
          a.clearUpdateRanges();
          a.addUpdateRange(0, c * 4);
          a.needsUpdate = true;
          drawn++;
          quads += c * m.userData.quads;
        }
      }
    }
    this.stats.drawn = drawn;
    this.stats.quads = quads;
  }

  get meshes() {
    return this.group.children;
  }

  pendingCount() {
    let c = 0;
    for (const p of this.prefabs.values()) if (p.state === 1 || p.state === 0) c++;
    return c;
  }
}
