// Quadtree LOD manager for heightfield voxel terrain nodes built by workers.
import * as THREE from 'three';
import { NODE_CELLS, ROOT_LEVEL, nodeInfo } from '../voxel/terrainMesher.js';

const NONE = 0;
const REQUESTED = 1;
const READY = 2;

let sharedIndex = null;
let sharedIndexQuads = 0;
export function getQuadIndex(quads) {
  if (!sharedIndex || quads > sharedIndexQuads) {
    let n = Math.max(65536, sharedIndexQuads * 2);
    while (n < quads) n *= 2;
    const idx = new Uint32Array(n * 6);
    for (let q = 0; q < n; q++) {
      const v = q * 4;
      const o = q * 6;
      idx[o] = v;
      idx[o + 1] = v + 1;
      idx[o + 2] = v + 2;
      idx[o + 3] = v;
      idx[o + 4] = v + 2;
      idx[o + 5] = v + 3;
    }
    sharedIndex = new THREE.BufferAttribute(idx, 1);
    sharedIndexQuads = n;
  }
  return sharedIndex;
}

export class TerrainLOD {
  constructor({ scene, pool, material, depthMaterial, lodK = 2.2 }) {
    this.scene = scene;
    this.pool = pool;
    this.material = material;
    this.depthMaterial = depthMaterial;
    this.lodK = lodK;
    this.nodes = new Map();
    this.group = new THREE.Group();
    this.group.name = 'terrain';
    scene.add(this.group);
    this.root = this._node(ROOT_LEVEL, 0, 0, null);
    this.frame = 0;
    this.drawn = [];
    this.stats = { nodes: 0, drawn: 0, quads: 0, pending: 0, bytes: 0 };
    this._cam = new THREE.Vector3();
  }

  _node(level, i, j, parent) {
    const key = `${level}:${i}:${j}`;
    let n = this.nodes.get(key);
    if (!n) {
      const info = nodeInfo(level, i, j);
      n = {
        key,
        level,
        i,
        j,
        ...info,
        parent,
        state: NONE,
        mesh: null,
        children: null,
        yMin: parent ? parent.yMin : -250,
        yMax: parent ? parent.yMax : 900,
        lastUsed: 0,
        quads: 0,
        bytes: 0,
      };
      this.nodes.set(key, n);
    }
    return n;
  }

  _children(n) {
    if (!n.children) {
      const l = n.level - 1;
      n.children = [
        this._node(l, n.i * 2, n.j * 2, n),
        this._node(l, n.i * 2 + 1, n.j * 2, n),
        this._node(l, n.i * 2, n.j * 2 + 1, n),
        this._node(l, n.i * 2 + 1, n.j * 2 + 1, n),
      ];
    }
    return n.children;
  }

  _dist(n, p) {
    const dx = Math.max(n.x0 - p.x, 0, p.x - (n.x0 + n.size));
    const dz = Math.max(n.z0 - p.z, 0, p.z - (n.z0 + n.size));
    const dy = Math.max(n.yMin - p.y, 0, p.y - n.yMax);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  _request(n, cam) {
    if (n.state !== NONE) {
      if (n.state === REQUESTED) {
        const job = this.pool.pending.get(n.key);
        if (job) job.priority = this._priority(n, cam);
      }
      return;
    }
    n.state = REQUESTED;
    this.pool.submit(
      n.key,
      { type: 'terrain', level: n.level, i: n.i, j: n.j },
      this._priority(n, cam),
      (msg) => this._onMesh(n, msg),
      () => {
        n.state = NONE;
      },
    );
  }

  _priority(n, cam) {
    return this._dist(n, cam) / n.size - n.level * 0.02;
  }

  _onMesh(n, msg) {
    if (n.state !== REQUESTED) return;
    n.yMin = msg.yMin;
    n.yMax = msg.yMax;
    n.quads = msg.quads;
    n.bytes = msg.data.byteLength;
    if (msg.quads > 0) {
      const geo = new THREE.BufferGeometry();
      geo.setAttribute('aData', new THREE.Uint16BufferAttribute(msg.data, 4));
      geo.setIndex(getQuadIndex(msg.quads));
      geo.setDrawRange(0, msg.quads * 6);
      const hy = (msg.yMax - msg.yBase) / 0.2 + 2;
      geo.boundingBox = new THREE.Box3(new THREE.Vector3(0, 0, 0), new THREE.Vector3(NODE_CELLS, hy, NODE_CELLS));
      const sx = NODE_CELLS / 2;
      geo.boundingSphere = new THREE.Sphere(new THREE.Vector3(sx, hy / 2, sx), Math.hypot(sx, hy / 2, sx));
      const mesh = new THREE.Mesh(geo, this.material);
      mesh.position.set(msg.x0, msg.yBase, msg.z0);
      mesh.scale.set(msg.cell, 0.2, msg.cell);
      mesh.matrixAutoUpdate = false;
      mesh.updateMatrix();
      mesh.visible = false;
      mesh.userData.depthMaterial = this.depthMaterial;
      mesh.layers.enable(1);
      this.group.add(mesh);
      n.mesh = mesh;
    }
    n.state = READY;
  }

  _visit(n, cam) {
    n.lastUsed = this.frame;
    if (n.state !== READY) {
      this._request(n, cam);
      return;
    }
    // far levels coarsen sooner: haze hides their detail
    const k = n.level >= 5 ? this.lodK * 0.72 : this.lodK;
    const wantSplit = n.level > 0 && this._dist(n, cam) < k * n.size;
    if (wantSplit) {
      const kids = this._children(n);
      let all = true;
      for (const c of kids) {
        c.lastUsed = this.frame;
        if (c.state !== READY) {
          this._request(c, cam);
          all = false;
        }
      }
      if (all) {
        for (const c of kids) this._visit(c, cam);
        return;
      }
    }
    if (n.mesh) {
      n.mesh.visible = true;
      this.drawn.push(n);
    }
  }

  update(camera) {
    this.frame++;
    const cam = this._cam.copy(camera.position);
    for (const n of this.drawn) if (n.mesh) n.mesh.visible = false;
    this.drawn.length = 0;
    this._visit(this.root, cam);
    // cancel stale requests
    if (this.frame % 30 === 0) this._evict();
    let quads = 0;
    for (const n of this.drawn) quads += n.quads;
    this.stats.drawn = this.drawn.length;
    this.stats.quads = quads;
    this.stats.nodes = this.nodes.size;
  }

  get meshes() {
    return this.drawn.map((n) => n.mesh);
  }

  pendingCount() {
    let c = 0;
    for (const n of this.nodes.values()) if (n.state === REQUESTED) c++;
    return c;
  }

  _evict() {
    const stale = this.frame - 240;
    let bytes = 0;
    for (const n of this.nodes.values()) {
      if (n.state === REQUESTED && n.lastUsed < this.frame - 20 && this.pool.has(n.key)) {
        this.pool.cancel(n.key);
        n.state = NONE;
      }
      if (n.state === READY && n.lastUsed < stale && n.level < ROOT_LEVEL - 2) {
        if (n.mesh) {
          this.group.remove(n.mesh);
          n.mesh.geometry.dispose();
          n.mesh = null;
        }
        n.state = NONE;
      }
      if (n.state === READY) bytes += n.bytes;
    }
    // drop detached leaves from the map to keep traversal cheap
    for (const n of [...this.nodes.values()]) {
      if (n.state === NONE && n.lastUsed < stale && n.parent && !n.children) {
        const p = n.parent;
        if (p.children && p.children.every((c) => c.state === NONE && !c.children && c.lastUsed < stale)) {
          for (const c of p.children) this.nodes.delete(c.key);
          p.children = null;
        }
      }
    }
    this.stats.bytes = bytes;
  }
}
