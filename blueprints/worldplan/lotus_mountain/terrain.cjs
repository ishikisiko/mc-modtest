// 石莲山地形采用已确认的战台预览坐标。每个预览体素对应 2×2×2 个方块。
function buildTerrain() {
    const M = {
      stone: 1, darkRock: 2, paleRock: 3, vein: 4, grass: 5, soil: 6, gravel: 7,
      road: 8, bark: 9, oakLeaf: 10, mossLeaf: 11, shadeLeaf: 12, water: 13,
      shallows: 14, foam: 15, plank: 16, moss: 17,
      limestone: 18, cream: 19, sediment: 20, deepWater: 21, flowWater: 22, ochre: 23, arenaStone: 24, arenaDark: 25, arenaRim: 26, arenaBase: 27
    };
    const xMin = -95, xMax = 85, zMin = -86, zMax = 65, yMin = -4, yMax = 111;
    const width = xMax - xMin + 1, depth = zMax - zMin + 1;
    const area = width * depth;
    const cells = new Uint8Array(area * (yMax - yMin + 1));
    const blend = new Uint8Array(area);
    const surface = new Int16Array(area).fill(yMin - 1);
    const column = (x, z) => (z - zMin) * width + x - xMin;
    const inside = (x, y, z) => x >= xMin && x <= xMax && z >= zMin && z <= zMax && y >= yMin && y <= yMax;
    const at = (x, y, z) => inside(x, y, z) ? cells[(y - yMin) * area + column(x, z)] : 0;
    function place(x, y, z, block) {
      if (inside(x, y, z)) cells[(y - yMin) * area + column(x, z)] = block;
    }
    function hash(x, y, z) {
      let n = Math.imul(x, 374761393) + Math.imul(y, 668265263) + Math.imul(z, 1274126177);
      n = Math.imul(n ^ (n >>> 13), 1274126177);
      return ((n ^ (n >>> 16)) >>> 0) / 4294967296;
    }
    const mix = (a, b, t) => a + (b - a) * t;
    const clamp = (n, lo, hi) => Math.min(hi, Math.max(lo, n));
    const hill = (x, z = 0) => Math.exp(-(x * x + z * z));
    function noise(x, z, seed = 3) {
      const a = Math.floor(x), b = Math.floor(z), u = x - a, v = z - b;
      const s = u * u * (3 - 2 * u), t = v * v * (3 - 2 * v);
      return mix(mix(hash(a, seed, b), hash(a + 1, seed, b), s),
        mix(hash(a, seed, b + 1), hash(a + 1, seed, b + 1), s), t);
    }
    function pine(x, y, z, height, span, leaf) {
      for (let dy = 1; dy <= height; dy++) {
        if (!at(x, y + dy, z)) place(x, y + dy, z, M.bark);
      }
      for (let dy = 2; dy <= height + 1; dy++) {
        const crown = span * (height + 2 - dy) / (height + 0.3) + (dy % 3 === 0 ? 0.42 : 0.08);
        const local = dy < height * 0.48 ? M.shadeLeaf : leaf;
        for (let dz = -span; dz <= span; dz++) {
          for (let dx = -span; dx <= span; dx++) {
            if (Math.hypot(dx, dz * 1.08) > crown + 0.48 * hash(x + dx, dy, z + dz)) continue;
            if (!at(x + dx, y + dy, z + dz)) place(x + dx, y + dy, z + dz, local);
          }
        }
      }
    }
    const cx = -6, cz = 2, poolLevel = 21;
    function radial(x, z) {
      return Math.hypot((x - cx) * 0.97, (z - cz) * 1.04);
    }
    function basinFloor(x, z, r) {
      const ledge = 15.2 + 10.1 * (r / 66) ** 1.74 + 1.4 * noise(x / 8, z / 9, 43);
      return Math.floor(ledge - 2.5 * hill((x - cx) / 25, (z - cz) / 21));
    }
    function bowlRadius(y, angle) {
      const height = clamp((y - 17) / 51, 0, 1);
      return 42.5 + 24.8 * height ** 0.86 + 1.85 * Math.cos(angle * 7 - 0.8)
        + 2.15 * Math.sin(angle * 3 + 0.04 * y);
    }
    const ridges = [
      [-30, -69, 64, 44, 29, -0.15], [38, -63, 48, 37, 32, 0.29],
      [-71, -25, 39, 25, 39, 0.40], [69, -18, 40, 28, 41, -0.26],
      [-66, 29, 24, 25, 32, 0.18], [59, 40, 23, 27, 34, -0.32],
      [-95, -40, 17, 23, 28, -0.39], [94, -35, 12, 26, 29, 0.16]
    ].map(([x, z, h, w, d, turn]) => [x, z, h, w, d, Math.cos(turn), Math.sin(turn)]);

    for (let z = zMin; z <= zMax; z++) {
      for (let x = xMin; x <= xMax; x++) {
        const r = radial(x, z);
        const angle = Math.atan2(z - cz, x - cx);
        const forward = Math.max(0, Math.sin(angle));
        const innerRim = 70.5 - 23.0 * forward ** 1.75;
        const rimWidth = 9.8 + 3.25 * noise(x / 17, z / 18, 34)
          + 1.25 * Math.cos(angle * 5 + 0.4);
        const toe = (r - innerRim) / rimWidth;
        if (toe >= 1) continue;
        const taper = clamp(toe, 0, 1);
        const fringe = 1 - taper * taper * (3 - 2 * taper);
        // Only the outer slope transitions to the local ground.
        const shoulder = clamp((toe - 0.55) / 0.45, 0, 1);
        blend[column(x, z)] = Math.round(255 * shoulder ** 2 * (3 - 2 * shoulder));
        const rough = noise(x / 13, z / 12, 25), coarse = noise(x / 23, z / 20, 12);
        const grit = noise(x / 5.2, z / 5.7, 39);
        const heart = 44 * hill((x + 5) / 75, (z + 10) / 72);
        let peaks = 0;
        for (const [px, pz, height, w, d, a, b] of ridges) {
          const dx = x - px, dz = z - pz;
          peaks += height * hill((a * dx + b * dz) / w, (-b * dx + a * dz) / d);
        }
        const erosion = (3.0 + 0.08 * heart) * (rough - 0.44) + 4.5 * (grit - 0.49);
        const dz = z - cz;
        const spread = 33.5 + 0.22 * Math.max(0, dz);
        const cut = clamp((dz - 17) / 30, 0, 1) * clamp(1 - Math.abs(x - cx - 0.10 * dz) / spread, 0, 1) ** 0.73;
        const flank = 7.1 + 3.1 * coarse + heart + peaks + erosion;
        const mouth = 10.2 + 4.3 * hill((dz - 20) / 31) + rough * 2.1;
        const height = mix(3.5 + 3.1 * coarse, mix(flank, mouth, Math.min(1, 1.28 * cut)), fringe);
        surface[column(x, z)] = Math.min(yMax - 6, Math.floor(height));
      }
    }

    for (let z = zMin; z <= zMax; z++) {
      for (let x = xMin; x <= xMax; x++) {
        const h = surface[column(x, z)];
        if (h < yMin) continue;
        const r = radial(x, z), angle = Math.atan2(z - cz, x - cx);
        const floor = basinFloor(x, z, r);
        const rough = noise(x / 8, z / 7, 52), coarse = noise(x / 12, z / 13, 81);
        const basin = r < 76;
        const lowerPool = r < 31.7 + 1.6 * Math.sin(3 * angle) && h > poolLevel + 2;
        const voidFrom = floor + 1;
        const layerOffset = 68 + 0.042 * x + 1.8 * Math.sin(z / 32) + coarse * 1.35;
        for (let y = yMin; y <= h; y++) {
          const wall = basin && y >= voidFrom ? bowlRadius(y, angle) : -100;
          if (r < wall) {
            if (lowerPool && y <= poolLevel) place(x, y, z, r < 22.5 ? M.deepWater : M.shallows);
            continue;
          }
          const seam = (y + layerOffset) % 8.7;
          let block = seam < 1.28 ? M.darkRock : seam > 7.4 ? M.paleRock : M.stone;
          if (r < wall + 5 && y < h - 2) {
            const stratum = (y + 88 + 2.25 * Math.cos(angle * 7 + 0.09 * y)) % 9;
            block = stratum < 1.3 ? M.darkRock : stratum > 6.5 ? M.paleRock : M.sediment;
          }
          if (lowerPool && y === floor) block = M.sediment;
          if (y === h) {
            const sideTrees = r > 75 && z < 58 && h < 42 && rough > 0.43;
            if (sideTrees || (r > 81 && h < 26 && coarse > 0.38)) block = M.moss;
            else if (rough > 0.50 || (r > 58 && h < 26)) block = M.paleRock;
          } else if (y === h - 1 && r > 77 && h < 38 && rough > 0.52) block = M.soil;
          place(x, y, z, block);
        }
      }
    }

    // Tall rear folds retain the lotus silhouette. Shorter folds open toward the valley mouth.
    const folds = [
      [-0.10, 9.4, 64.0, 23.0, 44, 0.058], [0.79, 10.1, 51.1, 19.9, 24, -0.038],
      [1.80, 9.8, 49.0, 23.5, 16, 0.048], [2.71, 9.3, 56.9, 22.7, 35, -0.051],
      [3.59, 8.8, 63.4, 24.8, 48, 0.059], [4.44, 8.9, 65.5, 22.1, 54, -0.047],
      [5.42, 10.2, 63.7, 22.7, 48, 0.052]
    ].map(([angle, root, length, width, rise, lean], i) => ({
      a: Math.cos(angle), b: Math.sin(angle), root, length, width, rise, lean, i
    }));
    function wetBlock(block) {
      return block === M.deepWater || block === M.shallows || block === M.water;
    }
    for (const lobe of folds) {
      for (let z = -79; z <= 75; z++) {
        for (let x = -82; x <= 75; x++) {
          const dx = x - cx, dz = z - cz;
          const along = dx * lobe.a + dz * lobe.b;
          const t = (along - lobe.root) / lobe.length;
          if (t < 0 || t > 1) continue;
          const lateral = dz * lobe.a - dx * lobe.b - (along - lobe.root) * lobe.lean;
          const swell = Math.sin(t * Math.PI * 0.93);
          const w = 3.4 + lobe.width * swell ** 0.82;
          const edge = Math.abs(lateral) / w;
          const ragged = 0.105 * (noise(x / 4, z / 4.5, 71 + lobe.i) - 0.5)
            + 0.057 * Math.sin(8.9 * t + lobe.i) * Math.sign(lateral);
          if (edge + ragged > 1) continue;
          const cup = 8.1 * swell * edge ** 1.65 + 1.38 * Math.sin(lateral * 0.40 + 5.8 * t);
          const rim = surface[column(x, z)], r = radial(x, z);
          const towardMouth = clamp((dz - 12) / 34, 0, 1)
            * clamp(1 - Math.abs(dx - dz * 0.10) / (29 + 0.20 * Math.max(0, dz)), 0, 1);
          let top = Math.min(rim - 1, Math.floor(17.75 + lobe.rise * t ** 1.19 + cup - 11.3 * towardMouth));
          if (r < 26.7 && t < 0.38) top = Math.min(top, poolLevel - 1);
          const bed = basinFloor(x, z, r);
          for (let y = bed - 1; y <= top; y++) {
            const cell = at(x, y, z);
            if (cell && !wetBlock(cell)) continue;
            const section = top - y;
            const stratum = (section + 0.8 * Math.sin(t * 16.7 + lateral / 3.1) + 48) % 7.0;
            let block = stratum < 1.08 ? M.darkRock : stratum > 5.39 ? M.paleRock : M.sediment;
            if (section <= 1.3) {
              const grain = noise(x / 5.7, z / 5.1, 62);
              const vein = Math.abs(Math.sin(lateral * 0.38 + t * 4.5 + lobe.i * 0.47));
              if (edge > 0.78 || vein < 0.103) block = M.cream;
              else block = grain > 0.71 ? M.paleRock : M.limestone;
            }
            place(x, y, z, block);
          }
        }
      }
    }

    const arenaTop = poolLevel + 1;
    const arenaCenter = { x: cx, z: cz + 1 };
    const arenaRadiusX = 17.2, arenaRadiusZ = 14.2;
    const stairCaps = [];

    function buildStoneColumn(x, z, top, walkingSurface) {
      for (let y = Math.max(yMin, top - 10); y < top; y++) {
        const block = at(x, y, z);
        if (y >= top - 2 || !block || wetBlock(block)) {
          place(x, y, z, y === top - 1 ? M.arenaRim : M.arenaBase);
        }
      }
      place(x, top, z, walkingSurface);
      for (let y = top + 1; y <= yMax; y++) {
        if (at(x, y, z)) place(x, y, z, 0);
      }
    }

    // A broad level stage rises two blocks above the pool. The narrow border is level with the floor.
    for (let z = arenaCenter.z - 16; z <= arenaCenter.z + 16; z++) {
      for (let x = arenaCenter.x - 19; x <= arenaCenter.x + 19; x++) {
        const u = (x - arenaCenter.x) / arenaRadiusX;
        const v = (z - arenaCenter.z) / arenaRadiusZ;
        const edge = (Math.abs(u) ** 2.45 + Math.abs(v) ** 2.45) ** (1 / 2.45);
        const stoneGrain = noise(x / 5.2, z / 5.0, 61);
        if (edge > 1 + 0.025 * (stoneGrain - 0.5)) continue;
        const ground = edge > 0.90 ? M.arenaRim : stoneGrain < 0.39 ? M.arenaDark : M.arenaStone;
        buildStoneColumn(x, z, arenaTop, ground);
      }
    }

    // The approach crosses the water before descending in one-block treads toward the valley.
    const deckHeight = 2 * (arenaTop + 1);
    const firstStep = cz + 26;
    for (let z = cz + 12; z <= cz + 54; z++) {
      const offset = z - cz;
      const x0 = cx + Math.round(clamp((offset - 10) * 0.107, 0, 4.6));
      const width = offset < 19 || offset >= 49 ? 3 : 2;
      const yBlocks = deckHeight - Math.max(0, z - firstStep);
      const top = Math.floor(yBlocks / 2) - 1;
      for (let x = x0 - width; x <= x0 + width; x++) {
        const stone = Math.abs(x - x0) === width ? M.arenaRim : M.arenaStone;
        buildStoneColumn(x, z, top, stone);
        if (yBlocks % 2) stairCaps.push({ x, z, y: yBlocks - 1, block: stone });
      }
    }

    for (let iz = zMin + 3; iz < zMax - 2; iz += 6) {
      for (let ix = xMin + 3; ix < xMax - 2; ix += 6) {
        const x = ix + Math.floor(4 * hash(ix, 63, iz));
        const z = iz + Math.floor(4 * hash(iz, 53, ix));
        const h = surface[column(x, z)], soil = at(x, h, z);
        if (soil !== M.moss) continue;
        if (hash(x, 42, z) > (h < 32 ? 0.57 : 0.39)) continue;
        pine(x, h, z, 3 + Math.floor(3 * hash(x, 52, z)), 2,
          hash(x, 61, z) > 0.63 ? M.mossLeaf : M.oakLeaf);
      }
    }
    return { M, xMin, xMax, zMin, zMax, yMin, yMax, column, at, blend,
      stairCaps, arenaCenter, arenaTop, poolLevel };
}

module.exports = { buildTerrain };
