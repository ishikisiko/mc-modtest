/* 五境衣甲设计册 · 独立几何内核。Three.js r128 由宿主显式提供。 */
(function (root) {
  'use strict';
  const T = root.THREE;
  if (!T) throw new Error('ArmorEngine: 请先将本地 Three.js 赋给 globalThis.THREE');
  const DEG = Math.PI / 180;
  const ROLES = ['head', 'chest', 'legs', 'boots', 'aura', 'ingot'];
  const COMMON = ['id', 'name', 'role', 'material', 'shape', 'position', 'rotation', 'scale'];
  const FIELDS = {
    loft: ['rings', 'segments', 'startAngle', 'endAngle', 'thickness', 'capTop', 'capBottom'],
    tube: ['path', 'radius', 'radii', 'radialSegments', 'steps'],
    ribbon: ['path', 'width', 'widths', 'normal', 'thickness', 'steps'],
    panel: ['polygon', 'depth', 'bevel', 'trimMaterial', 'trimRadius'],
    torus: ['radius', 'tube', 'arc'], ellipsoid: ['radii'], gem: ['radii'],
    box: ['size'], ingot: ['size', 'topMaterial', 'edgeMaterial']
  };
  function fail(path, message) { throw new Error(path + ': ' + message); }
  function object(value, path) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) fail(path, '必须是对象');
  }
  function keys(value, allowed, path) {
    object(value, path);
    Object.keys(value).forEach(k => { if (!allowed.includes(k)) fail(path + '.' + k, '不支持的字段'); });
  }
  function number(value, path, min = -Infinity, max = Infinity) {
    if (typeof value !== 'number' || !Number.isFinite(value)) fail(path, '必须是有限数值');
    if (value < min || value > max) fail(path, '数值超出范围 [' + min + ', ' + max + ']');
  }
  function positive(value, path) { number(value, path); if (value <= 0) fail(path, '必须大于零'); }
  function integer(value, path, min, max) {
    number(value, path, min, max); if (!Number.isInteger(value)) fail(path, '必须是整数');
  }
  function text(value, path) { if (typeof value !== 'string' || !value.trim()) fail(path, '必须是非空文字'); }
  function vector(value, path, count = 3, nonzero = false) {
    if (!Array.isArray(value) || value.length !== count) fail(path, '必须含 ' + count + ' 个数值');
    value.forEach((n, i) => nonzero ? positive(n, path + '[' + i + ']') : number(n, path + '[' + i + ']'));
  }
  function color(value, path) {
    if (typeof value !== 'string' || !/^#[\da-f]{6}$/i.test(value)) fail(path, '必须是 #RRGGBB 颜色');
  }
  function materialKey(key, materials, path) {
    text(key, path); if (!Object.prototype.hasOwnProperty.call(materials, key)) fail(path, '引用了不存在的材质 ' + key);
  }
  function orient(a, b, c) { return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]); }
  function onSegment(a, b, p) {
    return Math.abs(orient(a, b, p)) < 1e-12 && p[0] >= Math.min(a[0], b[0]) - 1e-12 &&
      p[0] <= Math.max(a[0], b[0]) + 1e-12 && p[1] >= Math.min(a[1], b[1]) - 1e-12 && p[1] <= Math.max(a[1], b[1]) + 1e-12;
  }
  function intersects(a, b, c, d) {
    const x = orient(a, b, c), y = orient(a, b, d), z = orient(c, d, a), w = orient(c, d, b);
    return (x * y < 0 && z * w < 0) || onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b);
  }
  function validateComponent(c, materials, path, ids, isIngot) {
    object(c, path);
    if (!Object.prototype.hasOwnProperty.call(FIELDS, c.shape)) fail(path + '.shape', '不支持的 shape ' + String(c.shape));
    keys(c, COMMON.concat(FIELDS[c.shape]), path);
    text(c.id, path + '.id'); text(c.name, path + '.name');
    if (ids.has(c.id)) fail(path + '.id', '重复组件 id ' + c.id); ids.add(c.id);
    if (!ROLES.includes(c.role)) fail(path + '.role', '不支持的 role');
    if (isIngot && c.role !== 'ingot') fail(path + '.role', '锭组件必须使用 ingot');
    if (!isIngot && c.role === 'ingot') fail(path + '.role', '锭组件应放入 ingot.components');
    materialKey(c.material, materials, path + '.material');
    ['position', 'rotation', 'scale'].forEach(k => {
      if (c[k] !== undefined) vector(c[k], path + '.' + k);
    });
    if (c.scale && c.scale.some(x => x === 0)) fail(path + '.scale', '缩放不得为零');
    if (c.shape === 'loft') {
      if (!Array.isArray(c.rings) || c.rings.length < 2 || c.rings.length > 512) fail(path + '.rings', '需要 2..512 个截面');
      c.rings.forEach((r, i) => {
        const p = path + '.rings[' + i + ']'; keys(r, ['y', 'rx', 'rz', 'cx', 'cz', 'twist', 'wave'], p);
        number(r.y, p + '.y'); positive(r.rx, p + '.rx'); positive(r.rz, p + '.rz');
        ['cx', 'cz', 'twist', 'wave'].forEach(k => { if (r[k] !== undefined) number(r[k], p + '.' + k); });
        if (Math.abs(r.wave || 0) >= Math.min(r.rx, r.rz)) fail(p + '.wave', '波幅必须小于最小截面半径');
      });
      if (c.segments !== undefined) integer(c.segments, path + '.segments', 3, 256);
      const start = c.startAngle === undefined ? 0 : c.startAngle, end = c.endAngle === undefined ? 360 : c.endAngle;
      number(start, path + '.startAngle'); number(end, path + '.endAngle');
      if (end <= start || end - start > 360) fail(path + '.endAngle', '需要 0 < endAngle - startAngle <= 360');
      if (c.thickness !== undefined) positive(c.thickness, path + '.thickness');
      ['capTop', 'capBottom'].forEach(k => { if (c[k] !== undefined && typeof c[k] !== 'boolean') fail(path + '.' + k, '必须是布尔值'); });
    } else if (c.shape === 'tube' || c.shape === 'ribbon') {
      if (!Array.isArray(c.path) || c.path.length < 2 || c.path.length > 512) fail(path + '.path', '需要 2..512 个控制点');
      c.path.forEach((p, i) => {
        vector(p, path + '.path[' + i + ']');
        if (i && p.every((n, j) => n === c.path[i - 1][j])) fail(path + '.path[' + i + ']', '相邻控制点不能重合');
      });
      const single = c.shape === 'tube' ? 'radius' : 'width', plural = c.shape === 'tube' ? 'radii' : 'widths';
      if ((c[single] === undefined) === (c[plural] === undefined)) fail(path + '.' + single, '必须且只能提供 ' + single + ' 或 ' + plural);
      if (c[single] !== undefined) positive(c[single], path + '.' + single);
      if (c[plural] !== undefined) {
        if (!Array.isArray(c[plural]) || c[plural].length !== c.path.length) fail(path + '.' + plural, '必须与 path 等长');
        c[plural].forEach((x, i) => positive(x, path + '.' + plural + '[' + i + ']'));
      }
      if (c.steps !== undefined) integer(c.steps, path + '.steps', 2, 512);
      if (c.radialSegments !== undefined) integer(c.radialSegments, path + '.radialSegments', 3, 64);
      if (c.thickness !== undefined) positive(c.thickness, path + '.thickness');
      if (c.normal !== undefined) {
        vector(c.normal, path + '.normal'); if (c.normal.every(n => n === 0)) fail(path + '.normal', '法线不能为零向量');
      }
    } else if (c.shape === 'panel') {
      if (!Array.isArray(c.polygon) || c.polygon.length < 3 || c.polygon.length > 512) fail(path + '.polygon', '需要 3..512 个轮廓点');
      c.polygon.forEach((p, i) => vector(p, path + '.polygon[' + i + ']', 2));
      const poly = c.polygon, n = poly.length;
      let area = 0;
      for (let i = 0; i < n; i++) {
        const a = poly[i], b = poly[(i + 1) % n];
        if (a[0] === b[0] && a[1] === b[1]) fail(path + '.polygon[' + i + ']', '相邻轮廓点不能重合');
        area += a[0] * b[1] - b[0] * a[1];
        for (let j = i + 1; j < n; j++) {
          if (j === i + 1 || (i === 0 && j === n - 1)) continue;
          if (intersects(a, b, poly[j], poly[(j + 1) % n])) fail(path + '.polygon', '轮廓自交或非相邻边接触');
        }
      }
      if (Math.abs(area) < 1e-12) fail(path + '.polygon', '轮廓面积为零');
      if (c.depth !== undefined) positive(c.depth, path + '.depth');
      if (c.bevel !== undefined) number(c.bevel, path + '.bevel', 0);
      if (c.trimMaterial !== undefined) materialKey(c.trimMaterial, materials, path + '.trimMaterial');
      if (c.trimRadius !== undefined) positive(c.trimRadius, path + '.trimRadius');
    } else if (c.shape === 'torus') {
      positive(c.radius, path + '.radius'); positive(c.tube, path + '.tube');
      if (c.arc !== undefined) { positive(c.arc, path + '.arc'); number(c.arc, path + '.arc', 0, 360); }
    } else if (c.shape === 'ellipsoid' || c.shape === 'gem') {
      vector(c.radii, path + '.radii', 3, true);
    } else {
      vector(c.size, path + '.size', 3, true);
      if (c.shape === 'ingot') {
        if (c.size[1] / c.size[0] > .25) fail(path + '.size', '锭的高宽比必须 <= .25');
        ['topMaterial', 'edgeMaterial'].forEach(k => { if (c[k] !== undefined) materialKey(c[k], materials, path + '.' + k); });
      }
    }
  }
  function validateDesign(spec) {
    const p = 'design';
    keys(spec, ['id', 'name', 'level', 'realm', 'style', 'tagline', 'palette', 'description', 'silhouette', 'materialStory', 'progression', 'segmentation', 'parts', 'ingot', 'materials', 'components'], p);
    ['id', 'name', 'realm', 'style', 'tagline', 'description', 'silhouette', 'materialStory', 'progression', 'segmentation'].forEach(k => text(spec[k], p + '.' + k));
    integer(spec.level, p + '.level', 1, 5);
    if (!Array.isArray(spec.palette) || !spec.palette.length) fail(p + '.palette', '需要颜色数组');
    spec.palette.forEach((c, i) => color(c, p + '.palette[' + i + ']'));
    keys(spec.parts, ['head', 'chest', 'legs', 'boots'], p + '.parts');
    ['head', 'chest', 'legs', 'boots'].forEach(role => {
      keys(spec.parts[role], ['name', 'description'], p + '.parts.' + role);
      ['name', 'description'].forEach(k => text(spec.parts[role][k], p + '.parts.' + role + '.' + k));
    });
    object(spec.materials, p + '.materials');
    if (Object.keys(spec.materials).length < 5) fail(p + '.materials', '至少需要五种分工材质');
    Object.entries(spec.materials).forEach(([key, m]) => {
      const q = p + '.materials.' + key;
      keys(m, ['name', 'color', 'metalness', 'roughness', 'opacity', 'emissive', 'emissiveIntensity', 'pixelArt'], q);
      text(m.name, q + '.name'); color(m.color, q + '.color');
      number(m.metalness, q + '.metalness', 0, 1); number(m.roughness, q + '.roughness', 0, 1);
      if (m.opacity !== undefined) number(m.opacity, q + '.opacity', 0, 1);
      if (m.emissive !== undefined) color(m.emissive, q + '.emissive');
      if (m.emissiveIntensity !== undefined) number(m.emissiveIntensity, q + '.emissiveIntensity', 0);
      if (m.pixelArt !== undefined) {
        const a=m.pixelArt;keys(a,['palette','rows','kind'],q+'.pixelArt');
        if(a.kind!==undefined&&!['cloth','metal','jade','glass','trim'].includes(a.kind))fail(q+'.pixelArt.kind','不支持的像素材质种类');
        if(!Array.isArray(a.palette)||a.palette.length<2||a.palette.length>10)fail(q+'.pixelArt.palette','需要2至10色');
        a.palette.forEach((c,i)=>color(c,q+'.pixelArt.palette['+i+']'));
        if(!Array.isArray(a.rows)||a.rows.length!==16||a.rows.some(r=>typeof r!=='string'||r.length!==16||[...r].some(c=>!/^\d$/.test(c)||Number(c)>=a.palette.length)))fail(q+'.pixelArt.rows','需要16行16列合法调色索引');
      }
    });
    const ids = new Set();
    if (!Array.isArray(spec.components) || !spec.components.length) fail(p + '.components', '需要衣甲组件数组');
    spec.components.forEach((c, i) => validateComponent(c, spec.materials, p + '.components[' + i + ']', ids, false));
    ['head', 'chest', 'legs', 'boots'].forEach(role => {
      if (!spec.components.some(c => c.role === role)) fail(p + '.components', '缺少 ' + role + ' 组件');
    });
    keys(spec.ingot, ['name', 'description', 'components'], p + '.ingot');
    text(spec.ingot.name, p + '.ingot.name'); text(spec.ingot.description, p + '.ingot.description');
    if (!Array.isArray(spec.ingot.components) || !spec.ingot.components.length) fail(p + '.ingot.components', '需要锭组件数组');
    spec.ingot.components.forEach((c, i) => validateComponent(c, spec.materials, p + '.ingot.components[' + i + ']', ids, true));
    if (!spec.ingot.components.some(c => c.shape === 'ingot')) fail(p + '.ingot.components', '缺少低扁 ingot 主体');
    return true;
  }

  function buffer(positions, indices) {
    const g = new T.BufferGeometry();
    g.setAttribute('position', new T.Float32BufferAttribute(positions, 3));
    if (indices) g.setIndex(indices);
    g.computeVertexNormals(); g.computeBoundingBox(); g.computeBoundingSphere();
    return g;
  }
  function loft(c) {
    const n = c.segments || 32, rings = c.rings[0].y > c.rings[c.rings.length - 1].y ? c.rings.slice().reverse() : c.rings, m = rings.length, stride = n + 1;
    const thickness = c.thickness === undefined ? .004 : c.thickness;
    const a0 = (c.startAngle || 0) * DEG, arc = ((c.endAngle === undefined ? 360 : c.endAngle) - (c.startAngle || 0)) * DEG;
    const p = [], ix = [];
    const capOffset = [Math.min(thickness, Math.abs(rings[1].y-rings[0].y)*.45), Math.min(thickness, Math.abs(rings[m-1].y-rings[m-2].y)*.45)];
    for (let side = 0; side < 2; side++) rings.forEach((r, ri) => {
      const y = r.y + (side && ri === 0 && c.capBottom ? capOffset[0] : 0) - (side && ri === m-1 && c.capTop ? capOffset[1] : 0);
      for (let j = 0; j <= n; j++) {
        // At a tapered crown tip the inner cavity closes instead of becoming a negative radius.
        const a = a0 + arc * j / n, wave = (r.wave || 0) * Math.cos(a * 6), floor = Math.min(r.rx,r.rz)*.0001;
        const x = (side ? Math.max(floor,r.rx-thickness+wave) : r.rx+wave)*Math.cos(a), z = (side ? Math.max(floor,r.rz-thickness+wave) : r.rz+wave)*Math.sin(a), t = (r.twist || 0) * DEG;
        p.push((r.cx || 0) + x * Math.cos(t) - z * Math.sin(t), y, (r.cz || 0) + x * Math.sin(t) + z * Math.cos(t));
      }
    });
    const inner = m * stride;
    for (let side = 0; side < 2; side++) for (let i = 0; i < m - 1; i++) for (let j = 0; j < n; j++) {
      const a = side * inner + i * stride + j, b = a + stride;
      if (!side) ix.push(a, b, a + 1, a + 1, b, b + 1);
      else ix.push(a, a + 1, b, a + 1, b + 1, b);
    }
    // An open garment ends in an annular rim; a closed cap has two distinct faces.
    for (const top of [false, true]) {
      const offset = top ? (m - 1) * stride : 0;
      if (top ? c.capTop : c.capBottom) {
        const r = rings[top ? m - 1 : 0], center = p.length / 3;
        p.push(r.cx || 0, r.y, r.cz || 0);
        const innerCenter = p.length / 3, innerY = r.y + (top ? -capOffset[1] : capOffset[0]);
        p.push(r.cx || 0, innerY, r.cz || 0);
        for (let j = 0; j < n; j++) {
          const a = offset + j, b = inner + a;
          if (top) ix.push(center, a + 1, a, innerCenter, b, b + 1);
          else ix.push(center, a, a + 1, innerCenter, b + 1, b);
        }
        if (Math.abs(arc-Math.PI*2)>1e-8) for (const j of [0,n]) {
          const a=offset+j,b=inner+a;
          if ((j===0)===top) ix.push(a,b,innerCenter,a,innerCenter,center);
          else ix.push(a,innerCenter,b,a,center,innerCenter);
        }
      } else for (let j = 0; j < n; j++) {
        const a = offset + j, b = inner + a;
        if (top) ix.push(a, b, a + 1, a + 1, b, b + 1);
        else ix.push(a, a + 1, b, a + 1, b + 1, b);
      }
    }
    if (Math.abs(arc - Math.PI * 2) > 1e-8) for (const j of [0, n]) for (let i = 0; i < m - 1; i++) {
      const a = i * stride + j, b = a + stride, ai = inner + a, bi = inner + b;
      if (j === 0) ix.push(a, ai, b, b, ai, bi); else ix.push(a, b, ai, b, bi, ai);
    }
    return buffer(p, ix);
  }
  function curveFor(path) { return new T.CatmullRomCurve3(path.map(p => new T.Vector3(...p)), false, 'centripetal'); }
  function sample(values, fallback, t) {
    if (!values) return fallback;
    const f = t * (values.length - 1), i = Math.min(Math.floor(f), values.length - 2);
    return T.MathUtils.lerp(values[i], values[i + 1], f - i);
  }
  function tube(c) {
    const curve = curveFor(c.path), steps = c.steps || 32, n = c.radialSegments || 8;
    const frames = T.Curve.prototype.computeFrenetFrames.call({getTangentAt:t=>curve.getTangent(t)},steps,false), p = [], ix = [], stride = n + 1;
    for (let i = 0; i <= steps; i++) {
      const center = curve.getPoint(i / steps), r = sample(c.radii, c.radius, i / steps);
      for (let j = 0; j <= n; j++) {
        const a = j / n * Math.PI * 2, v = center.clone().addScaledVector(frames.normals[i], r * Math.cos(a)).addScaledVector(frames.binormals[i], r * Math.sin(a));
        p.push(v.x, v.y, v.z);
      }
    }
    for (let i = 0; i < steps; i++) for (let j = 0; j < n; j++) {
      const a = i * stride + j, b = a + stride; ix.push(a, a + 1, b, a + 1, b + 1, b);
    }
    [0, steps].forEach(i => {
      const center = curve.getPoint(i / steps), ci = p.length / 3; p.push(center.x, center.y, center.z);
      for (let j = 0; j < n; j++) {
        const a = i * stride + j;
        if (i === 0) ix.push(ci, a + 1, a); else ix.push(ci, a, a + 1);
      }
    });
    return buffer(p, ix);
  }
  function ribbon(c) {
    const curve = curveFor(c.path), steps = c.steps || 32, baseNormal = new T.Vector3(...(c.normal || [0, 0, 1])).normalize();
    const depth = c.thickness === undefined ? .003 : c.thickness, p = [], ix = [];
    let previous;
    for (let i = 0; i <= steps; i++) {
      const t = i / steps, center = curve.getPoint(t), tangent = curve.getTangent(t).normalize();
      let side = new T.Vector3().crossVectors(tangent, baseNormal);
      if (side.lengthSq() < 1e-10) side.crossVectors(tangent, Math.abs(tangent.y) < .9 ? new T.Vector3(0, 1, 0) : new T.Vector3(1, 0, 0));
      side.normalize(); if (previous && side.dot(previous) < 0) side.negate(); previous = side;
      const normal = new T.Vector3().crossVectors(side, tangent).normalize(), width = sample(c.widths, c.width, t) / 2;
      // Four corner tracks: front-left, front-right, back-right, back-left.
      [[-1, 1], [1, 1], [1, -1], [-1, -1]].forEach(([s, z]) => {
        const v = center.clone().addScaledVector(side, s * width).addScaledVector(normal, z * depth / 2); p.push(v.x, v.y, v.z);
      });
    }
    for (let i = 0; i < steps; i++) for (let j = 0; j < 4; j++) {
      const a = i * 4 + j, b = i * 4 + (j + 1) % 4, d = a + 4, e = b + 4; ix.push(a, b, d, b, e, d);
    }
    ix.push(0, 2, 1, 0, 3, 2);
    const e = steps * 4; ix.push(e, e + 1, e + 2, e, e + 2, e + 3);
    return buffer(p, ix);
  }
  function panel(c) {
    const shape = new T.Shape(); c.polygon.forEach((p, i) => i ? shape.lineTo(...p) : shape.moveTo(...p)); shape.closePath();
    const depth = c.depth === undefined ? .012 : c.depth, bevel = c.bevel === undefined ? .003 : c.bevel;
    const g = new T.ExtrudeGeometry(shape, {depth, steps: 1, bevelEnabled: bevel > 0, bevelSegments: 3, bevelThickness: bevel, bevelSize: bevel, curveSegments: 20});
    g.translate(0, 0, -depth / 2); g.computeBoundingBox(); return g;
  }
  function ingot(c) {
    const [w, h, d] = c.size, hx = w / 2, hz = d / 2, cut = Math.min(w, d) * .15;
    const poly = [[-hx + cut, -hz], [hx - cut, -hz], [hx, -hz + cut], [hx, hz - cut], [hx - cut, hz], [-hx + cut, hz], [-hx, hz - cut], [-hx, -hz + cut]];
    const p = [], ix = [], g = new T.BufferGeometry(), n = poly.length;
    [[-.5, .94], [-.37, 1], [.31, .87], [.5, .82]].forEach(([y, s]) => poly.forEach(([x, z]) => p.push(x * s, y * h, z * s)));
    // The bottom, bevel band and long tapered side are separately grouped.
    for (let ring = 0; ring < 3; ring++) {
      const start = ix.length;
      for (let j = 0; j < n; j++) {
        const a = ring * n + j, b = ring * n + (j + 1) % n, u = a + n, v = b + n; ix.push(a, u, b, b, u, v);
      }
      g.addGroup(start, ix.length - start, ring === 1 ? 0 : 2);
    }
    let start = ix.length;
    for (let j = 1; j < n - 1; j++) ix.push(0, j, j + 1);
    g.addGroup(start, ix.length - start, 2); start = ix.length;
    for (let j = 1; j < n - 1; j++) ix.push(3 * n, 3 * n + j + 1, 3 * n + j);
    g.addGroup(start, ix.length - start, 1);
    g.setAttribute('position', new T.Float32BufferAttribute(p, 3)); g.setIndex(ix); g.computeVertexNormals(); g.computeBoundingBox(); g.computeBoundingSphere(); return g;
  }
  function geometry(c) {
    if (c.shape === 'loft') return loft(c);
    if (c.shape === 'tube') return tube(c);
    if (c.shape === 'ribbon') return ribbon(c);
    if (c.shape === 'panel') return panel(c);
    if (c.shape === 'ingot') return ingot(c);
    if (c.shape === 'torus') {
      const arc=(c.arc===undefined?360:c.arc)*DEG, n=72, sides=12, stride=sides+1,p=[],ix=[];
      for(let i=0;i<=n;i++)for(let j=0;j<=sides;j++) {
        const a=i/n*arc,b=j/sides*Math.PI*2,r=c.radius+c.tube*Math.cos(b);p.push(r*Math.cos(a),r*Math.sin(a),c.tube*Math.sin(b));
      }
      for(let i=0;i<n;i++)for(let j=0;j<sides;j++){const a=i*stride+j,b=a+stride;ix.push(a,b,a+1,a+1,b,b+1);}
      if(arc<Math.PI*2-1e-8)for(const end of [0,n]) {
        const a=end/n*arc,center=p.length/3;p.push(c.radius*Math.cos(a),c.radius*Math.sin(a),0);
        for(let j=0;j<sides;j++){const v=end*stride+j;if(end===0)ix.push(center,v,v+1);else ix.push(center,v+1,v);}
      }
      return buffer(p,ix);
    }
    if (c.shape === 'box') return new T.BoxGeometry(...c.size);
    if (c.shape === 'ellipsoid') return new T.SphereGeometry(1, 24, 16).scale(...c.radii);
    if (c.shape === 'gem') return new T.OctahedronGeometry(1, 0).scale(...c.radii);
    throw new Error('未实现的 shape ' + c.shape);
  }
  function makeMaterials(spec) {
    const map = {};
    Object.entries(spec.materials).forEach(([key, v]) => {
      const opacity = v.opacity === undefined ? 1 : v.opacity;
      const mat = new T.MeshPhysicalMaterial({
        color: new T.Color(v.pixelArt?'#ffffff':v.color).convertSRGBToLinear(), metalness: v.metalness, roughness: v.roughness,
        opacity, transparent: opacity < 1, depthWrite: opacity >= .96, side: T.DoubleSide,shadowSide:T.FrontSide,
        emissive: new T.Color(v.emissive || '#000000').convertSRGBToLinear(), emissiveIntensity: v.emissiveIntensity || 0,
        clearcoat: v.pixelArt?0:v.roughness < .36 ? .25 : .03, clearcoatRoughness: Math.max(.12, v.roughness),flatShading:!!v.pixelArt
      });
      if(v.pixelArt){
        const a=v.pixelArt,data=new Uint8Array(16*16*4);
        a.rows.forEach((row,y)=>[...row].forEach((c,x)=>{const rgb=a.palette[Number(c)],i=((15-y)*16+x)*4;for(let k=0;k<3;k++)data[i+k]=parseInt(rgb.slice(1+k*2,3+k*2),16);data[i+3]=255;}));
        const tex=new T.DataTexture(data,16,16,T.RGBAFormat);tex.magFilter=T.NearestFilter;tex.minFilter=T.NearestFilter;tex.generateMipmaps=false;tex.wrapS=tex.wrapT=T.RepeatWrapping;tex.encoding=T.sRGBEncoding;tex.needsUpdate=true;mat.map=tex;
      }
      mat.name = v.name; mat.userData.materialKey = key; map[key] = mat;
    });
    return map;
  }
  function componentsGroup(spec, components, materials) {
    const group = new T.Group();
    group.name = spec.name;
    components.forEach(c => {
      const node = new T.Group(); node.name = c.name;
      const meta = {componentId: c.id, role: c.role, materialKey: c.material, shape: c.shape, designId: spec.id};
      node.userData = {...meta};
      const mats = c.shape === 'ingot' ? [materials[c.material], materials[c.topMaterial || c.material], materials[c.edgeMaterial || c.material]] : materials[c.material];
      let geom=geometry(c);
      if(spec.materials[c.material].pixelArt){
        // Face-aligned world-density UVs: one texture pixel per MC model pixel.
        if(geom.index){const old=geom;geom=geom.toNonIndexed();old.dispose();}
        geom.computeVertexNormals();const p=geom.attributes.position,n=geom.attributes.normal,uv=[];
        for(let i=0;i<p.count;i++){const ax=Math.abs(n.getX(i)),ay=Math.abs(n.getY(i)),az=Math.abs(n.getZ(i));uv.push(ay>ax&&ay>az?p.getX(i):ax>az?p.getZ(i):p.getX(i),ay>ax&&ay>az?p.getZ(i):p.getY(i));}
        geom.setAttribute('uv',new T.Float32BufferAttribute(uv,2));
      }
      const mesh = new T.Mesh(geom, mats); mesh.name = c.name; mesh.userData = {...meta}; mesh.castShadow = true; mesh.receiveShadow = true; node.add(mesh);
      if (c.shape === 'ingot') mesh.userData.materialKeys = [c.material, c.topMaterial || c.material, c.edgeMaterial || c.material];
      if (c.shape === 'panel' && c.trimMaterial) {
        const z = (c.depth === undefined ? .012 : c.depth) / 2 + (c.bevel === undefined ? .003 : c.bevel) + .0005;
        // Closed straight segments preserve the authored silhouette (no Catmull overshoot at corners).
        const vertices = c.polygon.map(p => new T.Vector3(p[0], p[1], z)); vertices.push(vertices[0].clone());
        const path = new T.CurvePath();
        for (let i = 0; i < vertices.length - 1; i++) path.add(new T.LineCurve3(vertices[i], vertices[i + 1]));
        const trim = new T.Mesh(new T.TubeGeometry(path, Math.max(24, c.polygon.length * 8), c.trimRadius || .002, 6, true), materials[c.trimMaterial]);
        trim.name = c.name + ' · 嵌边'; trim.userData = {...meta, materialKey: c.trimMaterial, detail: 'trim'}; trim.castShadow = true; node.add(trim);
      }
      if (c.position) node.position.fromArray(c.position);
      if (c.rotation) node.rotation.set(...c.rotation.map(v => v * DEG));
      if (c.scale) node.scale.fromArray(c.scale);
      group.add(node);
    });
    return group;
  }
  function buildDesign(spec, options = {}) {
    validateDesign(spec);
    const materials = makeMaterials(spec), group = componentsGroup(spec, spec.components, materials);
    group.userData = {designId: spec.id, kind: 'design', ownedMaterials: Object.values(materials)};
    if (options.avatar !== false) group.add(buildAvatar());
    return group;
  }
  function buildIngot(spec) {
    validateDesign(spec);
    const materials = makeMaterials(spec), group = componentsGroup(spec, spec.ingot.components, materials);
    group.name = spec.ingot.name; group.userData = {designId: spec.id, kind: 'ingot', ownedMaterials: Object.values(materials)}; return group;
  }

  function buildAvatar() {
    const group=new T.Group();group.name='Minecraft 标准宽臂方块人';group.userData.kind='avatar';
    const materials={skin:new T.MeshStandardMaterial({color:new T.Color('#a18a72').convertSRGBToLinear(),roughness:1}),hair:new T.MeshStandardMaterial({color:new T.Color('#292725').convertSRGBToLinear(),roughness:1}),eye:new T.MeshStandardMaterial({color:0x151b20,roughness:1}),cloth:new T.MeshStandardMaterial({color:new T.Color('#455567').convertSRGBToLinear(),roughness:1})};
    group.userData.ownedMaterials=Object.values(materials);
    function box(name,size,pos,key='skin'){
      const mesh=new T.Mesh(new T.BoxGeometry(...size.map(v=>v/16)),materials[key]);mesh.name=name;mesh.position.set(...pos.map(v=>v/16));mesh.userData={componentId:'avatar_'+name,role:'avatar',materialKey:'avatar_'+key};mesh.castShadow=true;mesh.receiveShadow=true;group.add(mesh);
    }
    box('头颅',[8,8,8],[0,28,0]);box('躯体',[8,12,4],[0,18,0],'cloth');
    for(const sign of [-1,1]){box(sign<0?'左臂':'右臂',[4,12,4],[sign*6,18,0]);box(sign<0?'左腿':'右腿',[4,12,4],[sign*2,6,0],'cloth');box(sign<0?'左眼':'右眼',[1,1,.025],[sign*2,28,4.02],'eye');box(sign<0?'左眉':'右眉',[2,.5,.025],[sign*2,29.25,4.02],'hair');}
    box('方形发顶',[8,.5,8],[0,31.75,0],'hair');box('像素口型',[2,.5,.025],[0,26,4.02],'hair');
    return group;
  }
  function meshStats(group) {
    let meshes = 0, vertices = 0, triangles = 0;
    const ids = new Set(), materials = new Set(), geometries = new Set(), roles = {};
    group.traverse(node => {
      if (!node.isMesh || !node.geometry) return;
      meshes++; const g = node.geometry; vertices += g.attributes.position.count;
      triangles += (g.index ? g.index.count : g.attributes.position.count) / 3;
      if (node.userData.componentId) ids.add(node.userData.componentId);
      roles[node.userData.role || 'unlabelled'] = (roles[node.userData.role || 'unlabelled'] || 0) + 1;
      geometries.add(g); (Array.isArray(node.material) ? node.material : [node.material]).forEach(m => materials.add(m));
    });
    return {meshes, vertices, triangles, components: ids.size, materials: materials.size, geometries: geometries.size, roles};
  }
  function exportOBJ(group) {
    group.updateWorldMatrix(true, true);
    const lines = ['# 五境衣甲设计册 · 真实三维几何', '# Y 向上；Z 正向为人物正面；单位近似米'], v = new T.Vector3(), normal = new T.Vector3();
    let offset = 1;
    const safe = s => String(s || 'mesh').replace(/[\s#]/g, '_');
    group.traverse(node => {
      if (!node.isMesh || !node.geometry) return;
      const g = node.geometry, pos = g.attributes.position, normals = g.attributes.normal, nm = new T.Matrix3().getNormalMatrix(node.matrixWorld);
      lines.push('o ' + safe(node.userData.componentId || node.name), 'g ' + safe(node.userData.role));
      for (let i = 0; i < pos.count; i++) { v.fromBufferAttribute(pos, i).applyMatrix4(node.matrixWorld); lines.push('v ' + v.toArray().map(n => Number(n.toFixed(7))).join(' ')); }
      const uv=g.attributes.uv;
      for(let i=0;i<pos.count;i++)lines.push('vt '+(uv?Number(uv.getX(i).toFixed(7)):0)+' '+(uv?Number(uv.getY(i).toFixed(7)):0));
      for (let i = 0; i < pos.count; i++) {
        if (normals) normal.fromBufferAttribute(normals, i).applyMatrix3(nm).normalize(); else normal.set(0, 1, 0);
        lines.push('vn ' + normal.toArray().map(n => Number(n.toFixed(7))).join(' '));
      }
      const count = g.index ? g.index.count : pos.count, reflected = node.matrixWorld.determinant() < 0;
      let lastKey;
      for (let i = 0; i < count; i += 3) {
        const gi = g.groups.find(part => i >= part.start && i < part.start + part.count), mi = gi ? gi.materialIndex : 0;
        const material = Array.isArray(node.material) ? node.material[mi] : node.material;
        const key = material && (material.userData.materialKey || material.name) || node.userData.materialKey;
        if (key !== lastKey) { lines.push('usemtl ' + safe(key)); lastKey = key; }
        const face = [0,1,2].map(j => offset + (g.index ? g.index.getX(i + j) : i + j));
        if (reflected) [face[1],face[2]] = [face[2],face[1]];
        lines.push('f ' + face.map(n => n + '/' + n + '/' + n).join(' '));
      }
      offset += pos.count;
    });
    return lines.join('\n') + '\n';
  }
  function dispose(group) {
    const geometries = new Set(), materials = new Set(), textures = new Set();
    group.traverse(node => {
      if (node.geometry) geometries.add(node.geometry);
      if (node.material) (Array.isArray(node.material) ? node.material : [node.material]).forEach(m => materials.add(m));
      (node.userData.ownedMaterials || []).forEach(m => materials.add(m));
    });
    materials.forEach(m => Object.values(m).forEach(v => { if (v && v.isTexture) textures.add(v); }));
    geometries.forEach(g => g.dispose()); textures.forEach(t => t.dispose()); materials.forEach(m => m.dispose());
    if (group.parent) group.parent.remove(group); group.clear();
    group.userData.ownedMaterials = [];
    return {geometries: geometries.size, materials: materials.size, textures: textures.size};
  }
  const api = {validateDesign, buildDesign, buildIngot, buildAvatar, meshStats, exportOBJ, dispose};
  root.ArmorEngine = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
