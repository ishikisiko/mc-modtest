/* 只调用原作者 Three 内核，不重写形状，不包含预览人台。 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const repo = path.resolve(process.argv[2]);
globalThis.THREE = require(path.join(repo, 'tools/web/three.min.js'));
const T = globalThis.THREE;
const atlas = path.join(repo, 'art/immortal-armor-atlas');
const engine = require(path.join(atlas, 'core/engine.js'));
const effects = require(path.join(atlas, 'effects/catalogue.js'));
function extract(group) {
  const result = [];
  group.updateWorldMatrix(true, true);
  group.traverse(node => {
    if (!node.isMesh) return;
    const g = node.geometry, p = g.attributes.position, n = g.attributes.normal, uv = g.attributes.uv;
    if (!uv || !n || !node.userData.componentId || node.userData.role === 'avatar') throw Error('缺少真实网格属性');
    const normalMatrix = new T.Matrix3().getNormalMatrix(node.matrixWorld);
    const vertices = [];
    for (let i = 0; i < p.count; i++) {
      const v = new T.Vector3().fromBufferAttribute(p, i).applyMatrix4(node.matrixWorld);
      const normal = new T.Vector3().fromBufferAttribute(n, i).applyMatrix3(normalMatrix).normalize();
      vertices.push([...v.toArray(), uv.getX(i), uv.getY(i), ...normal.toArray()]);
    }
    const batches = new Map(), count = g.index ? g.index.count : p.count;
    for (let i = 0; i < count; i += 3) {
      const part = g.groups.find(b => i >= b.start && i < b.start + b.count);
      const material = Array.isArray(node.material) ? node.material[part ? part.materialIndex : 0] : node.material;
      const key = material.userData.materialKey;
      if (!batches.has(key)) batches.set(key, []);
      const indices = [0, 1, 2].map(j => g.index ? g.index.getX(i + j) : i + j);
      if (node.matrixWorld.determinant() < 0) [indices[1], indices[2]] = [indices[2], indices[1]];
      batches.get(key).push(...indices);
    }
    for (const [material, indices] of batches) result.push({
      id: node.userData.componentId, role: node.userData.role, material,
      detail: node.userData.detail || '', vertices, indices,
      side: (Array.isArray(node.material) ? node.material.find(m => m.userData.materialKey === material) : node.material).side === T.FrontSide ? 'front' : 'double'
    });
  });
  engine.dispose(group);
  return result;
}
const result = [];
for (let level = 1; level <= 5; level++) {
  const specs = JSON.parse(fs.readFileSync(path.join(atlas, `levels/level${level}.json`), 'utf8'));
  if (specs.length !== 3) throw Error('每境必须三套');
  for (const spec of specs) {
    if (spec.level !== level || !effects[spec.id]) throw Error('境界或动态来源缺失');
    result.push({id: spec.id, armor: extract(engine.buildDesign(spec, {avatar: false})),
      ingot: extract(engine.buildIngot(spec)), effects: effects[spec.id]});
  }
}
process.stdout.write(JSON.stringify(result));
