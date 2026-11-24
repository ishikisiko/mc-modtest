// Export one vertical profile for every 2×2 column; the approach can use one-block treads.
const { buildTerrain } = require('../../blueprints/worldplan/lotus_mountain/terrain.cjs');

const materials = {
  stone: 'andesite',
  darkRock: 'tuff',
  paleRock: 'calcite',
  soil: 'dirt',
  bark: 'spruce_log',
  oakLeaf: 'spruce_leaves',
  mossLeaf: 'azalea_leaves',
  shadeLeaf: 'dark_oak_leaves',
  water: 'water',
  shallows: 'water',
  moss: 'moss_block',
  limestone: 'pink_terracotta',
  cream: 'white_terracotta',
  sediment: 'terracotta',
  deepWater: 'water',
  arenaStone: 'smooth_stone',
  arenaDark: 'polished_andesite',
  arenaRim: 'polished_tuff',
  arenaBase: 'tuff_bricks',
};

function compile() {
  const { M, xMin, xMax, zMin, zMax, yMin, yMax, column, at, blend,
    stairCaps, arenaCenter, arenaTop, poolLevel } = buildTerrain();
  const names = ['air', ...new Set(Object.values(materials))];
  const codes = [];
  codes[0] = 0;
  for (const [key, material] of Object.entries(materials)) codes[M[key]] = names.indexOf(material);

  const caps = new Map(stairCaps.map(step => [column(step.x, step.z), step]));
  // Preview water is static. Close exposed sides with the surrounding rock so source blocks
  // stay in the basin when Minecraft updates the water beside the approach and petals.
  const seal = new Map();
  const water = codes[M.water];
  for (let z = zMin; z <= zMax; z++) {
    for (let x = xMin; x <= xMax; x++) {
      for (let y = yMin; y <= poolLevel; y++) {
        if (codes[at(x, y, z)] !== water) continue;
        for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, -1, 0]]) {
          if (at(x + dx, y + dy, z + dz)) continue;
          const neighbour = column(x + dx, z + dz);
          seal.set(neighbour, Math.max(seal.get(neighbour) ?? yMin, y + dy));
        }
      }
    }
  }
  const profiles = new Map();
  let left = xMax, right = xMin, back = zMax, front = zMin, highest = 0;
  for (let z = zMin; z <= zMax; z++) {
    for (let x = xMin; x <= xMax; x++) {
      const blocks = [];
      const sealedThrough = seal.get(column(x, z)) ?? yMin - 1;
      for (let y = yMin; y <= yMax; y++) {
        const code = codes[at(x, y, z)];
        if (code === undefined) throw Error('Terrain contains a material missing from the runtime palette');
        const material = code || (y <= sealedThrough ? codes[M.sediment] : 0);
        blocks.push(material, material);
      }
      const cap = caps.get(column(x, z));
      if (cap) blocks[cap.y - 2 * yMin] = codes[cap.block];
      while (blocks.length && blocks.at(-1) === 0) blocks.pop();
      if (!blocks.length) continue;
      if (blocks.length > 255) throw Error('Profile exceeds the LTM1 height limit');
      left = Math.min(left, x);
      right = Math.max(right, x);
      back = Math.min(back, z);
      front = Math.max(front, z);
      highest = Math.max(highest, blocks.length + 2 * yMin);
      profiles.set(column(x, z), Buffer.from([blocks.length, blend[column(x, z)], ...blocks]));
    }
  }
  // LTM1, signed big-endian bounds and landmarks, ASCII palette, then z-major profiles.
  const header = Buffer.alloc(4 + 9 * 2);
  header.write('LTM1');
  [left, right, back, front, 2 * yMin, highest, 2 * arenaCenter.x,
    2 * (arenaTop + 1), 2 * arenaCenter.z].forEach((value, i) => header.writeInt16BE(value, 4 + 2 * i));
  const palette = Buffer.from(names.join('\n'), 'ascii');
  const parts = [header, Buffer.from([palette.length >> 8, palette.length & 255]), palette];
  const empty = Buffer.from([0]);
  for (let z = back; z <= front; z++) {
    for (let x = left; x <= right; x++) parts.push(profiles.get(column(x, z)) ?? empty);
  }
  return { bytes: Buffer.concat(parts), bounds: [left, right, back, front], poolLevel, caps };
}

if (require.main === module) process.stdout.write(compile().bytes);
module.exports = { compile };
