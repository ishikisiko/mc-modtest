// Deterministic catalogue of repeatable prefab variants (houses, shops, courtyards, trees...).
// Footprints are in voxels (0.2 m). The pivot of every prefab is the centre of its footprint
// at ground level; rot k turns the front (+z) to face [south, east, north, west][k].
import { Rng } from '../world/noise.js';

export const V = 0.2; // metres per voxel

function even(n) {
  return n % 2 === 0 ? n : n + 1;
}

export function makeCatalog(seed = 1) {
  const rng = new Rng(seed * 131 + 17);
  const defs = {};
  const groups = { house: [], shop: [], court: [], tea: [], pine: [], cypress: [], broad: [], blossom: [], maple: [], willow: [], bamboo: [], person: [] };
  const add = (group, def) => {
    defs[def.id] = def;
    groups[group].push(def.id);
  };

  const houseStyles = ['hui', 'jiangnan', 'timber', 'hui', 'jiangnan'];
  for (let i = 0; i < 16; i++) {
    const style = houseStyles[i % houseStyles.length];
    const w = even(rng.int(34, 62));
    const d = even(rng.int(28, 42));
    add('house', {
      id: `house_${i}`,
      kind: 'house',
      style,
      w,
      d,
      stories: rng.chance(0.3) ? 2 : 1,
      seed: rng.int(1, 1e9),
    });
  }
  for (let i = 0; i < 8; i++) {
    const w = even(rng.int(36, 56));
    const d = even(rng.int(40, 54));
    add('shop', { id: `shop_${i}`, kind: 'shop', style: i % 2 ? 'timber' : 'jiangnan', w, d, stories: 2, seed: rng.int(1, 1e9) });
  }
  for (let i = 0; i < 6; i++) {
    const w = even(rng.int(80, 104));
    const d = even(rng.int(84, 110));
    add('court', { id: `court_${i}`, kind: 'court', style: i % 3 === 0 ? 'hui' : 'jiangnan', w, d, seed: rng.int(1, 1e9) });
  }
  for (let i = 0; i < 4; i++) {
    const w = even(rng.int(56, 70));
    const d = even(rng.int(48, 60));
    add('tea', { id: `tea_${i}`, kind: 'tea', w, d, stories: 2 + (i % 2), seed: rng.int(1, 1e9) });
  }

  // trees (footprint = canopy extent)
  for (let i = 0; i < 6; i++) add('pine', { id: `pine_${i}`, kind: 'tree', species: 'pine', w: 50, d: 50, seed: rng.int(1, 1e9) });
  for (let i = 0; i < 3; i++) add('cypress', { id: `cypress_${i}`, kind: 'tree', species: 'cypress', w: 24, d: 24, seed: rng.int(1, 1e9) });
  for (let i = 0; i < 4; i++) add('broad', { id: `broad_${i}`, kind: 'tree', species: 'broad', w: 46, d: 46, seed: rng.int(1, 1e9) });
  for (let i = 0; i < 3; i++) add('blossom', { id: `blossom_${i}`, kind: 'tree', species: 'blossom', w: 36, d: 36, seed: rng.int(1, 1e9) });
  for (let i = 0; i < 3; i++) add('maple', { id: `maple_${i}`, kind: 'tree', species: 'maple', w: 36, d: 36, seed: rng.int(1, 1e9) });
  for (let i = 0; i < 3; i++) add('willow', { id: `willow_${i}`, kind: 'tree', species: 'willow', w: 40, d: 40, seed: rng.int(1, 1e9) });
  for (let i = 0; i < 2; i++) add('bamboo', { id: `bamboo_${i}`, kind: 'tree', species: 'bamboo', w: 26, d: 26, seed: rng.int(1, 1e9) });

  for (let i = 0; i < 12; i++) add('person', { id: `person_${i}`, kind: 'person', w: 4, d: 4, seed: rng.int(1, 1e9) });
  return { defs, groups };
}
