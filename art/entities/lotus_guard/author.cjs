// 伏莲兽获准三维模型。坐标采用方块单位，+Y 朝上，+Z 朝头。
function makeBoss(THREE) {
  const hash = (x, y, seed) => {
    let n = Math.imul(x + 313, 374761393) + Math.imul(y + 157, 668265263) + Math.imul(seed, 1274126177);
    n = Math.imul(n ^ (n >>> 13), 1274126177);
    return ((n ^ (n >>> 16)) >>> 0) / 4294967296;
  };
  function stone(channels, seed, band) {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 128;
    const context = canvas.getContext('2d');
    const pixels = context.createImageData(128, 128);
    for (let y = 0; y < 128; y++) {
      for (let x = 0; x < 128; x++) {
        const p = (y * 128 + x) * 4;
        const diagonal = (y + Math.floor(x / 5) + Math.floor(3 * Math.sin(x / 14)) + 128) % 128;
        const layer = Math.floor(diagonal / 5);
        const individual = hash(x, y, seed);
        let shade = 15 * (hash(x >> 3, y >> 3, seed + 1) - .5)
          + 17 * (hash(x >> 1, y >> 1, seed + 2) - .5) + 13 * (individual - .5);
        shade += band * (hash(layer, 6, seed + 5) - .45);
        if (diagonal % 16 === 0 && hash(x >> 4, layer, seed + 7) > .2) shade -= 11;
        if (diagonal % 16 === 1 && hash(x >> 4, layer, seed + 7) > .38) shade += 7;
        if (individual > .989) shade += 29;
        if (individual < .016) shade -= 19;
        for (let i = 0; i < 3; i++) pixels.data[p + i] = channels[i] + shade;
        pixels.data[p + 3] = 255;
      }
    }
    context.putImageData(pixels, 0, 0);
    for (let i = 0; i < 14; i++) {
      let x = Math.floor(128 * hash(i, 11, seed)), y = Math.floor(128 * hash(i, 18, seed));
      const length = 2 + Math.floor(14 * hash(i, 17, seed));
      for (let j = 0; j < length; j++) {
        context.fillStyle = 'rgba(27,22,27,.22)';
        context.fillRect(x, y, 2, 1);
        context.fillStyle = 'rgba(229,211,193,.18)';
        context.fillRect(x + 1, y + 1, 2, 1);
        x += 1;
        y += Math.floor(hash(i, j, seed + 7) * 3) - 1;
      }
    }
    const map = new THREE.CanvasTexture(canvas);
    map.encoding = THREE.sRGBEncoding;
    map.wrapS = map.wrapT = THREE.RepeatWrapping;
    map.magFilter = THREE.NearestFilter;
    map.minFilter = THREE.LinearMipmapLinearFilter;
    map.anisotropy = 4;
    return new THREE.MeshStandardMaterial({ map, roughness: .92, vertexColors: true, flatShading: true });
  }
  const mats = {
    basalt: stone([55, 67, 76], 17, 25),
    slate: stone([80, 93, 98], 12, 28),
    stone: stone([111, 120, 119], 11, 31),
    shade: stone([37, 44, 55], 9, 23),
    dark: stone([26, 26, 33], 15, 17),
    plum: stone([94, 59, 76], 9, 39),
    rose: stone([142, 85, 100], 5, 38),
    rim: stone([183, 139, 132], 2, 34),
    under: stone([114, 105, 92], 22, 31),
    chalk: stone([175, 156, 128], 7, 34),
    moss: stone([69, 81, 61], 8, 25),
    damp: stone([76, 106, 96], 23, 21),
    eye: new THREE.MeshBasicMaterial({color: '#a3ebc6', vertexColors: true, toneMapped: false}),
    seep: new THREE.MeshBasicMaterial({color: '#75a796', vertexColors: true, toneMapped: false}),
  };

  const group = new THREE.Group();
  group.name = 'body';
  group.userData.pivot = [0, 2.74, 0];
  function bone(name, pivot, parent = group) {
    const part = new THREE.Group();
    part.name = name;
    part.userData.pivot = pivot;
    parent.add(part);
    return part;
  }
  const sides = [];
  const vector = p => new THREE.Vector3(...p);
  function mean(points) {
    const center = new THREE.Vector3();
    for (const p of points) center.add(vector(p));
    return center.divideScalar(points.length);
  }

  function loft(target, rings, pick) {
    const byMaterial = new Map();
    const midpoints = rings.map(mean);
    let faceId = 0;
    function face(points, out, material) {
      if (!byMaterial.has(material)) byMaterial.set(material, {positions: [], colors: [], uv: []});
      const buf = byMaterial.get(material);
      const n = vector(points[1]).sub(vector(points[0])).cross(vector(points[2]).sub(vector(points[0])));
      if (n.dot(out) < 0) points = points.slice().reverse();
      const an = n.toArray().map(Math.abs);
      const light = .91 + hash(faceId++, 6, 22) * .15;
      for (let j = 1; j < points.length - 1; j++) {
        for (const [x, y, z] of [points[0], points[j], points[j + 1]]) {
          buf.positions.push(x, y, z);
          buf.colors.push(light, light, light);
          if (an[1] > Math.max(an[0], an[2])) buf.uv.push(x / 4.7, z / 4.7);
          else buf.uv.push((an[0] > an[2] ? z : x) / 4.4, y / 4.7);
        }
      }
    }
    for (let i = 0; i < rings.length - 1; i++) {
      for (let j = 0; j < rings[i].length; j++) {
        const k = (j + 1) % rings[i].length;
        const quad = [rings[i][j], rings[i][k], rings[i + 1][k], rings[i + 1][j]];
        const normal = mean(quad).sub(midpoints[i].clone().lerp(midpoints[i + 1], .5));
        face(quad, normal, pick(i, j, normal));
      }
    }
    face(rings[0], midpoints[0].clone().sub(midpoints[1]), pick(-1, -1, null));
    const last = rings.length - 1;
    face(rings[last], midpoints[last].clone().sub(midpoints[last - 1]), pick(last, -1, null));

    const geometry = new THREE.BufferGeometry();
    const all = {positions: [], colors: [], uv: []};
    const materials = [];
    for (const [name, buffer] of byMaterial) {
      geometry.addGroup(all.positions.length / 3, buffer.positions.length / 3, materials.length);
      materials.push(mats[name]);
      for (const key of Object.keys(all)) all[key].push(...buffer[key]);
    }
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(all.positions, 3));
    geometry.setAttribute('color', new THREE.Float32BufferAttribute(all.colors, 3));
    geometry.setAttribute('uv', new THREE.Float32BufferAttribute(all.uv, 2));
    geometry.computeVertexNormals();
    const mesh = new THREE.Mesh(geometry, materials);
    mesh.castShadow = mesh.receiveShadow = true;
    target.add(mesh);
    return mesh;
  }

  // Slightly mismatched corners leave the breaks visible on the silhouette.
  function hull(z, x, width, low, high, bevel = .25) {
    const h = high - low, inner = width * (1 - bevel);
    return [[x - inner, low, z], [x + inner * .92, low + .015, z],
      [x + width, low + h * .32, z], [x + width * .98, high - h * .25, z],
      [x + width * .70, high, z], [x - width * .74, high - .045, z],
      [x - width * 1.02, high - h * .31, z], [x - width, low + h * .24, z]];
  }
  const shell = (i, j) => j === 4 ? 'slate' : j === 2 || j === 6 ? 'basalt'
    : j === 3 || j === 5 ? 'stone' : 'shade';

  loft(group, [
    hull(-5.7, -.65, .46, 1.04, 1.68),
    hull(-4.9, -.34, 1.01, .99, 2.28),
    hull(-3.45, -.02, 2.36, 1.05, 3.67),
    hull(-1.7, .03, 3.06, 1.12, 4.8),
    hull(.5, -.08, 3.22, 1.18, 4.91),
    hull(2.45, .04, 2.95, 1.48, 4.35),
    hull(3.48, 0, 1.93, 1.7, 3.65),
  ], shell);

  loft(group, [
    hull(-4.06, -.28, .65, 3.15, 3.65),
    hull(-2.64, .15, 1.14, 4.02, 4.88),
    hull(-1.11, -.13, 1.47, 4.54, 5.21),
    hull(.57, -.05, 1.61, 4.57, 5.39),
    hull(2.3, .04, 1.49, 4.06, 4.94),
    hull(3.38, -.12, 1.04, 3.2, 4.00),
  ], (_i, j) => j === 4 ? 'plum' : j === 3 || j === 5 ? 'slate' : 'basalt');

  // The skull hangs below the shoulders and sits above a separate wedge-shaped jaw.
  const head = bone('head', [0, 2.88, 2.84]);
  const jaw = bone('jaw', [0, 2.02, 3.65], head);
  loft(head, [
    hull(2.66, -.06, 1.57, 1.86, 3.87),
    hull(3.37, .02, 1.99, 2.0, 3.66),
    hull(4.19, .03, 2.13, 2.42, 3.37, .18),
    hull(4.87, .09, 1.68, 2.31, 3.17, .16),
    hull(5.52, .03, 1.22, 2.28, 2.87, .18),
    hull(5.94, .05, .88, 2.33, 2.65),
  ], (i, j) => j === 4 ? 'stone' : j === 3 || j === 5 || i === 4 ? 'slate'
    : j === 2 || j === 6 ? 'basalt' : 'shade');
  loft(jaw, [
    hull(3.16, 0, 1.48, 1.17, 2.17),
    hull(4.06, .02, 1.68, 1.12, 2.24),
    hull(5.07, .08, 1.36, 1.25, 2.05),
    hull(5.72, .04, .81, 1.55, 2.07),
  ], (_i, j) => j === 4 ? 'dark' : j === 3 || j === 5 ? 'under' : 'basalt');
  loft(head, [hull(3.67, .05, 1.49, 1.89, 2.51), hull(5.13, .06, 1.02, 1.91, 2.36)], () => 'dark');

  function box(target, dims, at, paint) {
    const geo = new THREE.BoxGeometry(...dims);
    const colors = new Float32Array(geo.attributes.position.count * 3).fill(1);
    geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    const mesh = new THREE.Mesh(geo, mats[paint]);
    mesh.position.set(...at);
    mesh.castShadow = mesh.receiveShadow = paint !== 'eye';
    target.add(mesh);
    return mesh;
  }
  for (const side of [-1, 1]) {
    const eyeSocket = new THREE.Group();
    eyeSocket.position.set(side * 1.48, 2.86, 4.79);
    eyeSocket.rotation.y = .56 * side;
    head.add(eyeSocket);
    const opening = box(eyeSocket, [1.08, .35, .39], [0, -.014, .13], 'dark');
    opening.rotation.z = .12 * side;
    const iris = box(eyeSocket, [.73, .13, .068], [-side * .015, -.004, .336], 'eye');
    iris.rotation.z = .14 * side;
    box(eyeSocket, [.14, .12, .045], [side * .10, .017, .369], 'shade');
    const brow = box(eyeSocket, [1.17, .26, .61], [0, .20, .074], 'stone');
    brow.rotation.z = .13 * side;
    const cheek = box(eyeSocket, [.98, .17, .42], [side * .09, -.246, .11], 'basalt');
    cheek.rotation.z = -.19 * side;

    for (let i = 0; i < 4; i++) {
      const z = 4.21 + i * .44;
      const x = side * (1.58 - i * .166);
      const y = 2.28 - .014 * i;
      const tip = i === 1 ? y - .48 : y - .27 + .03 * i;
      loft(head, [hull(z - .15, x, i === 1 ? .15 : .08, tip, y + .012),
        hull(z + .08, x - side * .035, .126, Math.min(y - .17, tip + .12), y + .085)],
        () => i === 1 || i === 3 ? 'chalk' : 'under');
    }

    // Arms are broad buttresses with locked wrist joints and three chisel-like toes.
    function legRing(x, y, z, rx, rz) {
      return [[x - rx * .72,y,z - rz], [x + rx * .72,y,z - rz],
        [x + rx,y,z - rz * .62], [x + rx,y,z + rz * .54],
        [x + rx * .67,y,z + rz], [x - rx * .66,y,z + rz],
        [x - rx,y,z + rz * .59], [x - rx,y,z - rz * .69]];
    }
    const change = side === 1 ? .13 : -.07;
    const label = side > 0 ? 'left' : 'right';
    const foreleg = bone('front_' + label, [side * 2.77, 3.42, 1.82 + change]);
    loft(foreleg, [
      legRing(side * 3.6, .05, 2.99 + change, 1.22, .96),
      legRing(side * 3.65, .38, 2.96 + change, 1.34, 1.16),
      legRing(side * 3.71, 1.4, 2.57 + change, 1.30, 1.10),
      legRing(side * 3.49, 2.54, 1.92 + change, 1.12, 1.38),
      legRing(side * 2.83, 3.65, 1.75 + change, .91, 1.22),
      legRing(side * 2.26, 4.03, 1.69 + change, .53, .71),
    ], (i, j) => i === 0 ? (j === 4 || j === 6 ? 'moss' : 'basalt')
      : j === 3 || j === 6 || i === 4 ? 'stone' : j === 4 || j === 5 ? 'slate' : 'basalt');
    for (let i = 0; i < 2; i++) {
      const layer = i * .78;
      loft(foreleg, [
        legRing(side * (3.58 + .06 * i), 1.05 + layer, 2.51 - .32 * i, 1.00, .96),
        legRing(side * (3.68 + .08 * i), 1.31 + layer, 2.53 - .41 * i, 1.13, 1.13),
        legRing(side * (3.45 + .04 * i), 1.59 + layer, 2.44 - .47 * i, 1.11, 1.14),
      ], (k, j) => j === 4 || j === 5 ? (k === 0 ? 'shade' : 'stone')
        : j === 2 || j === 6 ? 'basalt' : 'slate');
    }
    for (let i = 0; i < 3; i++) {
      const x = side * (2.93 + i * .73), start = 3.35 + change + i * .07;
      loft(foreleg, [
        hull(start, x, .41, .02, .53),
        hull(start + .67, x + side * .045, .34, .025, .45 - i * .04),
        hull(start + 1.20 - i * .08, x + side * .09, .24, .02, .18),
      ], (k, j) => k > 0 && j > 1 || j === -1 ? 'chalk' : j === 4 ? 'stone' : 'basalt');
    }
    const hindleg = bone('hind_' + label, [side * 2.50, 2.78, -2.58 - change]);
    loft(hindleg, [
      legRing(side * 3.21, .04, -2.84 - change, .99, .85),
      legRing(side * 3.30, .61, -2.93 - change, 1.11, 1.20),
      legRing(side * 3.16, 1.68, -2.77 - change, 1.18, 1.16),
      legRing(side * 2.67, 2.76, -2.53 - change, .82, 1.06),
      legRing(side * 2.12, 3.24, -2.39 - change, .59, .63),
    ], (i, j) => i <= 0 ? 'moss' : j > 1 && j < 6 ? 'slate' : 'basalt');
    for (let i = 0; i < 2; i++) {
      const x = side * (2.77 + i * .77);
      loft(hindleg, [hull(-2.67 - change, x, .39, .05, .41), hull(-1.42 - change - .12 * i, x, .28, .03, .18)],
        (k, j) => j === 4 || k === 1 ? 'under' : 'basalt');
    }

    // A wet fracture in the flank sits behind the petals in the closed pose.
    const fracture = new THREE.Group();
    fracture.position.set(side * 3.17, 3.54, -.42);
    group.add(fracture);
    function slice(planeX, shape) {
      return shape.map(([y, z]) => [side * planeX, y, z]);
    }
    const scar = [[.29,.99],[.32,.39],[.21,-.16],[.16,-.93],[-.13,-1.12],[-.32,-.39],[-.29,.30],[-.17,1.12]];
    loft(fracture, [slice(-.20, scar), slice(.17, scar.map(([y,z]) => [.83*y,.89*z]))],
      (i,j) => j < 0 ? 'shade' : j < 4 ? 'basalt' : 'dark');
    for (const [y, z, t] of [[.025,.66,.92],[-.051,.14,1.0],[-.026,-.38,.73],[-.075,-.76,.61]]) {
      const notch = [[y+.22*t,z+.07],[y+.10*t,z+.025],[y-.066*t,z-.064],[y-.23*t,z-.07],[y-.19*t,z+.014],[y-.10*t,z+.104],[y+.12*t,z+.12]];
      loft(fracture, [slice(.14, notch), slice(.202, notch)], () => 'dark');
      const vein = notch.map(([v,w],j) => [y + (.60 + .09*(j%2)) * (v-y) + .008, z + .54 * (w-z) + .02]);
      loft(fracture, [slice(.2, vein), slice(.214, vein)], () => 'seep');
    }
  }

  // Each closed petal belongs to the same armoured flank. Its outer rib is thick and beveled.
  // Sections are given as outboard-offset, height, axial offset, half-span and thickness.
  const petals = [
    { z: 1.71, pivot: [2.73, 2.83], sections: [
      [-.22,-.33, .08,.65,.72], [-.02,.18,.02,1.09,1.03], [.57,1.25,.06,1.32,1.10],
      [1.02,2.15,.32,1.16,.93], [.89,2.87,.56,.85,.71], [.56,3.35,.46,.43,.42],
      [.51,3.49,.31,.15,.20]] },
    { z: -.30, pivot: [2.71, 2.79], sections: [
      [-.22,-.35, .02,.71,.72], [.06,.24,.04,1.06,.98], [.72,1.37,-.06,1.21,1.24],
      [1.26,2.35,-.19,1.22,1.01], [1.17,3.22,-.35,.84,.72], [.91,3.68,-.62,.42,.43],
      [.69,3.94,-.58,.14,.22]] },
    { z: -2.16, pivot: [2.44, 2.62], sections: [
      [-.25,-.32,.03,.59,.67], [.02,.21,-.08,.97,.93], [.67,1.32,-.15,1.17,1.00],
      [1.06,2.14,-.41,1.05,.87], [.91,2.84,-.70,.72,.64], [.72,3.31,-.83,.44,.32],
      [.59,3.43,-1.01,.17,.19]] },
  ];
  for (const sign of [-1, 1]) {
    const flap = new THREE.Group();
    const origin = [sign * 2.69, 2.76, 0];
    flap.name = sign > 0 ? "left_armor" : "right_armor";
    flap.userData.pivot = origin;
    flap.position.fromArray(origin);
    sides.push(flap);
    group.add(flap);
    for (let p = 0; p < petals.length; p++) {
      const {z, pivot, sections} = petals[p];
      const erosion = sign === -1 ? [-.30,.04,-.20][p] : 0;
      const rings = sections.map(([offset, rise, sweep, span, thickness], i) => {
        const x = sign * (pivot[0] + offset), y = pivot[1] + rise + erosion * i / 6, centerZ = z + sweep;
        const chip = sinuous(p, i, sign);
        const coords = [
          [-.47,-.06,-span*.69], [-.02,-.06,-span], [.29,-.035,-span*.78],
          [.52, .085,-span*.27], [.54, .03,span*.24], [.25,-.088,span*.87],
          [-.035,-.09,span], [-.50,-.04,span*.65], [-.64,-.17,0],
        ];
        return coords.map(([u, v, w]) => [
          x + sign * (u * thickness + .11 * w) - origin[0],
          y + v * thickness + chip * w - origin[1],
          centerZ + w * (sign > 0 ? 1 : .955),
        ]);
      });
      loft(flap, rings, (i, s) => {
        if (i >= 5 || i === 4 && (s === 2 || s === 4 || s === 5)) return 'rim';
        if (i === 4 || i === 3 && (s === 3 || s === 4)) return 'rose';
        if (i <= 0 || s >= 7) return 'plum';
        if (s === 4 || s === 5 || s === 1) return i >= 2 ? 'rose' : 'slate';
        if (s === 3) return 'rim';
        return i > 2 ? 'rose' : 'plum';
      });
    }
  }
  function sinuous(p, i, side) {
    return Math.sin(p * 2.08 + i * .93 + (side < 0 ? .42 : 0)) * .12;
  }

  // A saddle of overlapping tail plates finishes the silhouette behind the lotus armor.
  const tail = bone('tail', [0, 2.49, -3.57]);
  loft(tail, [
    hull(-5.90, -.49, .26, 1.27, 1.54),
    hull(-5.35, -.38, .59, 1.31, 2.01),
    hull(-4.52, -.07, .86, 1.8, 2.51),
    hull(-3.94, .02, 1.04, 2.23, 3.0),
    hull(-3.51, .05, .98, 2.37, 3.14),
  ], (i, j) => j === 4 || i === 0 ? 'rose' : j === 3 || j === 5 ? 'plum' : 'slate');

  return { group, sides, mats, box };
}

module.exports = { makeBoss };
