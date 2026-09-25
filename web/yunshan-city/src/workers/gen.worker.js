// Generation worker: terrain node meshing and prefab voxel model meshing.
import { createField } from '../world/field.js';
import { meshTerrainNode } from '../voxel/terrainMesher.js';
import { buildPrefab } from '../voxel/builders.js';
import { meshModel } from '../voxel/mesher.js';
import { makeCatalog } from '../voxel/catalog.js';

let field = null;
let plan = null;
let catalog = null;
const modelCache = new Map(); // id -> VoxelModel (LOD0), kept small (LRU-ish)

function getModel(id) {
  let m = modelCache.get(id);
  if (m) {
    modelCache.delete(id);
    modelCache.set(id, m);
    return m;
  }
  const def = plan.uniques[id] || catalog.defs[id];
  if (!def) throw new Error('unknown prefab ' + id);
  m = buildPrefab(def);
  modelCache.set(id, m);
  if (modelCache.size > 24) modelCache.delete(modelCache.keys().next().value);
  return m;
}

self.onmessage = (e) => {
  const msg = e.data;
  try {
    if (msg.type === 'init') {
      plan = msg.plan;
      field = createField(plan);
      catalog = makeCatalog(plan.seed);
      self.postMessage({ type: 'ready' });
    } else if (msg.type === 'terrain') {
      const r = meshTerrainNode(field, msg.level, msg.i, msg.j);
      self.postMessage({ type: 'terrain', id: msg.id, level: msg.level, i: msg.i, j: msg.j, ...r }, [r.data.buffer]);
    } else if (msg.type === 'prefab') {
      const model = getModel(msg.prefab);
      const lods = [];
      const transfer = [];
      let pivot = null;
      let dims = null;
      for (let L = 0; L <= 4; L++) {
        const r = meshModel(model, L);
        pivot = r.pivot;
        dims = r.dims;
        lods.push({ pos: r.pos, col: r.col, quads: r.quads });
        transfer.push(r.pos.buffer, r.col.buffer);
      }
      modelCache.delete(msg.prefab);
      self.postMessage({ type: 'prefab', id: msg.id, prefab: msg.prefab, lods, pivot, dims }, transfer);
    }
  } catch (err) {
    self.postMessage({ type: 'error', id: msg.id, message: String(err && err.stack ? err.stack : err) });
  }
};
