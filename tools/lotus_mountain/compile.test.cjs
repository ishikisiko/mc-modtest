const assert = require('node:assert/strict');
const { test } = require('node:test');
const { compile } = require('./compile.cjs');

test('the compact arena, treads and basin survive export to the block grid', () => {
  const { bytes, bounds, caps } = compile();
  assert.deepEqual(bounds, [-91, 80, -77, 59]);
  assert.equal(bytes.subarray(0, 4).toString(), 'LTM1');
  const [minX, maxX, minZ, maxZ, bed, , arenaX, arenaY, arenaZ] =
    Array.from({ length: 9 }, (_, i) => bytes.readInt16BE(4 + 2 * i));
  const paletteEnd = 24 + bytes.readUInt16BE(22);
  const palette = bytes.subarray(24, paletteEnd).toString().split('\n');
  const columns = new Map();
  let offset = paletteEnd;
  for (let z = minZ; z <= maxZ; z++) {
    for (let x = minX; x <= maxX; x++) {
      const length = bytes[offset++];
      if (length === 0) continue;
      offset++; // shoulder blend
      columns.set(x + ',' + z, bytes.subarray(offset, offset + length));
      offset += length;
    }
  }
  assert.equal(offset, bytes.length);
  const get = (x, y, z) => palette[columns.get(Math.floor(x / 2) + ',' + Math.floor(z / 2))?.[y - bed] ?? 0];
  assert.equal(arenaY, 46);
  for (let z = -20; z <= 20; z++) {
    for (let x = -25; x <= 25; x++) {
      assert.match(get(arenaX + x, arenaY - 1, arenaZ + z), /^(smooth_stone|polished_andesite|polished_tuff)$/);
      assert.equal(get(arenaX + x, arenaY, arenaZ + z), 'air');
    }
  }
  assert.equal(get(arenaX + 40, arenaY - 3, arenaZ), 'water');
  assert.equal(get(arenaX + 40, arenaY - 2, arenaZ), 'air');
  assert.notEqual(caps.size, 0);
  for (const { x, y, z } of caps.values()) {
    assert.notEqual(get(2 * x, y, 2 * z), 'air');
    assert.equal(get(2 * x, y + 1, 2 * z), 'air');
  }
  for (let z = minZ; z <= maxZ; z++) {
    for (let x = minX; x <= maxX; x++) {
      for (let y = bed; y < arenaY; y++) {
        if (get(2 * x, y, 2 * z) !== 'water') continue;
        for (const [dx, dy, dz] of [[2, 0, 0], [-2, 0, 0], [0, 0, 2], [0, 0, -2], [0, -1, 0]]) {
          assert.notEqual(get(2 * x + dx, y + dy, 2 * z + dz), 'air', `water leak at ${x},${y},${z}`);
        }
      }
    }
  }
});
