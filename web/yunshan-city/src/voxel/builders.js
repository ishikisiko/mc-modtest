// Prefab dispatcher: definition -> cropped voxel model.
import { buildHouse, buildShop, buildCourt, buildTea } from './buildings.js';
import { buildTree } from './trees.js';
import { buildHall, buildPalGate, buildCityGate, buildWaterGate, buildDrumTower, buildLou, buildPagoda, buildPavilion, buildArchBridge, buildLangQiao, buildLamp, buildWallTower, buildBoat } from './landmarks.js';

const BUILDERS = {
  house: buildHouse,
  shop: buildShop,
  court: buildCourt,
  tea: buildTea,
  tree: buildTree,
  hall: buildHall,
  palgate: buildPalGate,
  citygate: buildCityGate,
  watergate: buildWaterGate,
  drumtower: buildDrumTower,
  lou: buildLou,
  pagoda: buildPagoda,
  pavilion: buildPavilion,
  archbridge: buildArchBridge,
  langqiao: buildLangQiao,
  lamp: buildLamp,
  walltower: buildWallTower,
  boat: buildBoat,
};

export function buildPrefab(def) {
  const fn = BUILDERS[def.kind];
  if (!fn) throw new Error('no builder for ' + def.kind);
  const M = fn(def);
  return M.crop();
}
