#!/usr/bin/env node
// Preview export only. No voxel builders, validators, tests, world writes or servers.
import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import { dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { gzipSync } from 'node:zlib';
import { execFileSync } from 'node:child_process';
import { buildPlan } from '../../../web/yunshan-city/src/world/plan.js';
import { createField, FT, KIND, poolLevelAt, quayAt } from '../../../web/yunshan-city/src/world/field.js';
import { createGeo, SITE, POOLS } from '../../../web/yunshan-city/src/world/geo.js';
import { makeCatalog, V } from '../../../web/yunshan-city/src/voxel/catalog.js';
import { TM } from '../../../web/yunshan-city/src/world/materials.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../../..');
const OUTPUT = resolve(ROOT, 'out/preview/province-capitals/central');
const BLUEPRINT = resolve(ROOT, 'blueprints/province_capitals/central');
const spec = JSON.parse(await readFile(resolve(BLUEPRINT, 'preview-spec.json'), 'utf8'));
const capitalCatalog = JSON.parse(await readFile(resolve(ROOT, 'blueprints/province_capitals/catalog.json'), 'utf8'));
const capitalDefinition = capitalCatalog.capitals.find(row => row.city_id === spec.city_id);
const { sampling: grid, height_proposal: proposal } = spec;
const round = (x) => Math.round(x * 1000) / 1000;
const xyz = (p) => [round(p[0]), round(p[2]), round(p[1])];
const hash = (s) => createHash('sha256').update(s).digest('hex');
const json = (x) => JSON.stringify(x, null, 2) + '\n';
const rel = (p) => relative(ROOT, p).replaceAll('\\', '/');
const zip64 = (s) => gzipSync(s, { level: 9, mtime: 0 }).toString('base64');

function adapt(y) {
  const k = proposal.knots;
  if (y <= k[0][0]) return k[0][1];
  for (let i = 1; i < k.length; i++) {
    if (y <= k[i][0]) {
      const t = (y - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
      return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * t;
    }
  }
  return proposal.max_ground_y;
}

// buildPlan only builds the deterministic layout and instance list, never voxel volumes.
console.log('生成中州完整规划与低密度地形白模（交付生成；不执行测试）');
const plan = buildPlan(spec.seed);
const geo = createGeo(spec.seed);
const defs = { ...makeCatalog(spec.seed).defs, ...plan.uniques };
// Wall ribbons are exported separately so the 4-block ground grid cannot turn them into spikes.
const earthPlan = { ...plan, features: plan.features.filter(f => f.kind !== KIND.WALL && f.kind !== KIND.PALWALL) };
const earth = createField(earthPlan);
const scratch = new Float64Array(4);
const ground = (x, z) => earth.sample(x, z, grid.cell, scratch)[0];
const nx = Math.round((grid.x_max - grid.x_min) / grid.cell) + 1;
const nz = Math.round((grid.z_max - grid.z_min) / grid.cell) + 1;
const heights = [], materials = [];
let minY = Infinity, maxY = -Infinity, minNatural = Infinity, maxNatural = -Infinity;
for (let j = 0; j < nz; j++) {
  for (let i = 0; i < nx; i++) {
    const x = grid.x_min + i * grid.cell, z = grid.z_min + j * grid.cell;
    earth.sample(x, z, grid.cell, scratch);
    const y = scratch[0];
    heights.push(Math.round(y / grid.quantum));
    materials.push(scratch[1]);
    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
    const natural = geo.height(x, z, grid.cell);
    minNatural = Math.min(minNatural, natural); maxNatural = Math.max(maxNatural, natural);
  }
}

// Height proxies are formula envelopes read from buildings.js / landmarks.js / roof.js.
// They are not exact occupied-voxel bounds; no original high-resolution builder is invoked.
function shapeFor(def) {
  let w = (def.w || 0) * V, d = (def.d || 0) * V, body = 5, roof = 2, shape = 'hall';
  let note = '原作 authored 占地 + 连续体量高度包络';
  const floors = def.floors || def.stories || 1;
  switch (def.kind) {
    case 'house': {
      body = (2 + (def.style === 'hui' ? 18 : 16) * floors) * V;
      roof = (Math.round((def.d - 6) * 0.36) + 8) * V;
      shape = 'house'; break;
    }
    case 'shop': body = 33 * V; roof = (Math.round((def.d + 3) * 0.33) + 8) * V; shape = 'shop'; break;
    case 'tea': body = (3 + 17 + (floors - 1) * 18) * V; roof = 5; break;
    case 'court': body = 6; roof = 4; shape = 'court'; break;
    case 'pavilion': {
      const radius = def.r * V;
      w = d = (def.r + 8) * 2 * V;
      body = (4 + (def.double ? 32 : 20)) * V;
      roof = (Math.round((def.r + (def.double ? 4 : 8)) * (def.double ? 1.05 : 0.95)) + 9) * V;
      shape = 'pavilion'; note += `；${def.sides || 6} 边亭，半径 ${round(radius)}`; break;
    }
    case 'hall': {
      const rank = def.rank || 1, tiers = def.platform || 1;
      const plinth = tiers * (rank >= 3 ? 6 : rank === 2 ? 5 : 4);
      body = (plinth + (rank >= 3 ? 36 : rank === 2 ? 28 : 22) + (rank >= 2 ? 3 : 2) + (def.double ? (rank >= 3 ? 23 : 21) : 0)) * V;
      roof = Math.max(3, d * 0.32) + 2;
      break;
    }
    case 'lou': {
      let y = def.grand ? 12 : 6;
      for (let f = 0; f < floors; f++) y += (def.grand ? (f === 0 ? 30 : 24 - f) : f === 0 ? 24 : 18) + (def.grand ? 3 : 2) + (f < floors - 1 ? 7 : 0);
      body = (y + (def.grand ? 20 : 0)) * V;
      roof = Math.max(4, d * 0.32) + spec.whitebox.landmark_height_allowance;
      shape = 'tower'; break;
    }
    case 'pagoda': {
      let y = 8;
      for (let f = 0; f < floors; f++) y += (f === 0 ? 34 : Math.round(20 - f * 0.5)) + 5;
      const rTop = Math.round(def.r * (1 - 0.034 * floors));
      body = y * V; roof = (rTop + 7 + 40) * V + spec.whitebox.landmark_height_allowance;
      w = d = (def.r + 12) * 2 * V;
      shape = 'pagoda'; break;
    }
    case 'citygate': body = (Math.max(30, def.baseH || 45) + 56) * V; roof = 7; shape = 'gate'; break;
    case 'watergate': body = (Math.max(30, def.baseH || 40) + 18) * V; roof = 4; shape = 'gate'; break;
    case 'palgate': body = 59 * V; roof = 8; shape = 'gate'; break;
    case 'drumtower': body = 86 * V; roof = 7; shape = 'tower'; break;
    case 'walltower': w = d = (def.w || 50) * V; body = 6; roof = 4; shape = 'tower'; break;
    // buildArchBridge spans local X, and buildPlan places it with rot=1.
    // Keep that source orientation; a separate logical cross-river path is a proposal below.
    case 'archbridge': w = def.span * V; d = def.width * V; body = 0.8; roof = def.span * V * 0.11; shape = 'bridge'; break;
    case 'langqiao': w = def.span * V; d = def.width * V; body = 5; roof = 2.5; shape = 'bridge'; break;
    default: return null;
  }
  return { kind: shape, width: round(w), depth: round(d), body_height: round(body), roof_height: round(roof), height: round(body + roof), floors, height_status: 'formula_envelope_not_voxel_bounds', note };
}
const names = { gate_south:'正阳门', gate_water:'水门', pal_gate:'宫门', pal_main:'太和殿', pal_rear:'后殿', pal_side:'侧殿', pal_gatehall:'过殿', pal_corner:'宫角亭', guanpu:'观瀑阁', summit_ting:'揽云亭', trail_ting:'山径亭', basin_ting:'瀑潭亭', lake_ting:'湖心亭', pillar_ting:'城外石柱亭', pagoda:'凌霄塔', west_tower:'西岭楼', drum_tower:'市集鼓楼', wall_tower:'城墙敌楼' };
const buildings = [], counts = {}, templateCounts = {};
for (let i = 0; i < plan.instances.length; i++) {
  const inst = plan.instances[i], def = defs[inst.id];
  if (!def) continue;
  const shape = shapeFor(def);
  if (!shape) continue;
  const n = templateCounts[inst.id] = (templateCounts[inst.id] || 0) + 1;
  const id = `central_${inst.id}_${String(n).padStart(3, '0')}`;
  counts[def.kind] = (counts[def.kind] || 0) + 1;
  const position = [inst.x, inst.y, inst.z];
  // Enemy towers sit on walls; retain the 9-block wall offset when remapping earth.
  const offset = def.kind === 'walltower' ? 9 : 0;
  const adaptedBase = adapt(inst.y - offset) + offset;
  const worldW = inst.rot & 1 ? shape.depth : shape.width;
  const worldD = inst.rot & 1 ? shape.width : shape.depth;
  const probes = [[0,0],[-.5,-.5],[-.5,.5],[.5,-.5],[.5,.5]].map(([u,v]) => ground(inst.x + u * worldW, inst.z + v * worldD));
  const supportBottom = Math.min(inst.y - spec.whitebox.minimum_foundation_depth, ...probes);
  buildings.push({
    id, name: names[inst.id] || (def.kind === 'house' ? `民居 ${n}` : def.kind === 'shop' ? `商铺 ${n}` : '茶楼'),
    role: def.kind === 'house' ? 'house' : def.kind === 'shop' ? 'shop' : ['citygate','watergate','palgate'].includes(def.kind) ? 'gate' : shape.kind === 'bridge' ? 'bridge' : 'landmark',
    template: `yunshan_preview/${inst.id}`, position, rotation_quarters: (4 - inst.rot) % 4,
    source_rotation_quarters: inst.rot, author_origin: [0,0,0], rotation_transform: 'exchange_q=(4-source_rot)%4; Three.rotation.y=-q*PI/2',
    source_template: inst.id, source_kind: def.kind, source_instance_index: i,
    source_definition: def, shape,
    adapted_position: [inst.x, round(adaptedBase), inst.z],
    support: { raw_bottom: round(supportBottom), adapted_bottom: round(Math.min(adapt(supportBottom), adaptedBase - spec.whitebox.minimum_foundation_depth)), wall_offset: offset },
    scale: [1,1,1], status: 'preview_mass_only'
  });
}

const pathGroups = new Map();
for (let i = 0; i < plan.features.length; i++) {
  const f = plan.features[i];
  if (f.t === FT.SEG && f.pid) {
    const arr = pathGroups.get(f.pid) || [];
    arr.push({ ...f, source_feature_index: i }); pathGroups.set(f.pid, arr);
  }
}
const roads = [];
const sameStart = (a,b) => a && b && Math.hypot(a[0]-b[0],a[1]-b[1]) < 0.01;
for (const [pid, segs] of pathGroups) {
  const f = segs[0];
  if (![TM.ROAD, TM.STEPS, TM.STONEPATH, TM.MARBLE, TM.WOOD].includes(f.top)) continue;
  let id = `source_path_${pid}`, name = '原作阶巷 / 平台通道';
  if (sameStart([f.ax,f.az],plan.paths.avenue[0])) { id='avenue'; name='御街 · 南门—市集—宫前'; }
  else if (sameStart([f.ax,f.az],plan.paths.approach[0])) { id='approach'; name='南门外登城折返阶'; }
  else if (sameStart([f.ax,f.az],plan.paths.summit[0])) { id='cliff_lower'; name='瀑潭—观瀑阁崖阶'; }
  else if (f.az < -380 && f.top === TM.STEPS) { id = `summit_${pid}`; name = '观瀑阁—上台地—揽云峰'; }
  else if (f.top === TM.WOOD) { id='lake_causeway'; name='镜湖九曲栈道'; }
  else if (f.top === TM.MARBLE || (f.top === TM.STEPS && f.side === TM.MARBLE)) { id=`imperial_stair_${pid}`; name='宫城御阶'; }
  const points = [[f.ax,f.ay,f.az],...segs.map(s=>[s.bx,s.by,s.bz])].map(p=>p.map(round));
  roads.push({ id, name, kind: f.top === TM.STEPS ? 'stair' : f.top === TM.WOOD ? 'bridge' : 'street', width: f.hw*2, points, source_pid: pid, source_feature_indices: segs.map(s=>s.source_feature_index), status:'source_path_not_block_walkability', adapted_points:points.map(([x,y,z])=>[x,round(adapt(y)),z]) });
}
// Actual river quays are encoded in the source field, not in paths.lanes.
for (const side of [-1,1]) {
  const points = plan.paths.river.map((p,i,a)=>{
    const before=a[Math.max(0,i-1)], after=a[Math.min(a.length-1,i+1)];
    const dx=after[0]-before[0], dz=after[1]-before[1], length=Math.hypot(dx,dz)||1;
    return [round(p[0]+side*(-dz/length)*11.5),round(quayAt(p[1])),round(p[1])];
  });
  roads.push({id:`river_quay_${side<0?'east':'west'}`,name:side<0?'东河岸步道':'西河岸步道',kind:'stair',width:4,points,adapted_points:points.map(([x,y,z])=>[x,round(adapt(y)),z]),status:'derived_from_source_field_quay_not_collision_checked'});
}
for (const [i, z0, z1, y] of [[1,-56,-110,80],[2,-110,-186,96],[3,-186,-224,112]]) {
  const points=[[-22,y,z0],[-22,y,z1]];
  roads.push({id:`imperial_way_${i}`,name:`宫城第 ${i} 台御道`,kind:'street',width:6,points,adapted_points:points.map(([x,h,z])=>[x,round(adapt(h)),z]),status:'source_marble_strip_may_cross_hall_mass'});
}

const water = [], walls = [], surfaces = [];
function splitRiverFeature(f) {
  let ts=[0,1];
  if (f.bz !== f.az) for (const p of POOLS) { const t=(p.zN-f.az)/(f.bz-f.az); if(t>0&&t<1)ts.push(t); }
  // The outlet's source MIN cliff starts at z=440; expose its fall, not a floating channel.
  if(Math.max(f.az,f.bz)>440) { const t=(440-f.az)/(f.bz-f.az);if(t>0&&t<1)ts.push(t); }
  ts.sort((a,b)=>a-b);
  for(let i=0;i<ts.length-1;i++) {
    const a=ts[i], b=ts[i+1], zm=f.az+(f.bz-f.az)*(a+b)/2;
    if(zm>440)continue;
    const level=poolLevelAt(zm);
    water.push({kind:'channel',width:f.hw*2,points:[[round(f.ax+(f.bx-f.ax)*a),level,round(f.az+(f.bz-f.az)*a)],[round(f.ax+(f.bx-f.ax)*b),level,round(f.az+(f.bz-f.az)*b)]]});
  }
}
for(let i=0;i<plan.features.length;i++) {
  const f=plan.features[i];
  if(f.kind===KIND.WALL||f.kind===KIND.PALWALL) {
    walls.push({kind:f.kind===KIND.WALL?'city_wall':'palace_wall',width:f.hw*2,points:[[f.ax,f.ay,f.az],[f.bx,f.by,f.bz]].map(p=>p.map(round)),height:f.kind===KIND.WALL?9:5.2,source_feature_index:i});
  } else if(f.top===TM.WATER || f.top===TM.FOAM) {
    if(f.t===FT.SEG && f.kind===KIND.RIVER)splitRiverFeature(f);
    else if(f.t===FT.SEG)water.push({kind:'channel',width:f.hw*2,points:[[f.ax,f.ay,f.az],[f.bx,f.by,f.bz]].map(p=>p.map(round))});
    else if(f.t===FT.ELL)water.push({kind:'lake',x:f.cx,z:f.cz,rx:f.rx,rz:f.rz,y:f.y});
    else water.push({kind:'rect',xz:[f.x0,f.z0,f.x1,f.z1],y:f.y});
  } else if(f.t!==FT.SEG && [TM.COURT,TM.PLAZA,TM.PADDY].includes(f.top) && f.mode===0) {
    surfaces.push({kind:f.t===FT.ELL?'ellipse':'rect',x:f.cx,z:f.cz,rx:f.rx,rz:f.rz,xz:f.t===FT.RECT?[f.x0,f.z0,f.x1,f.z1]:undefined,y:f.y,material:f.top,source_feature_index:i});
  }
}
function riverX(z) {
  const pts=plan.paths.river;
  for(let i=0;i<pts.length-1;i++)if((z-pts[i][1])*(z-pts[i+1][1])<=0){const t=(z-pts[i][1])/(pts[i+1][1]-pts[i][1]);return pts[i][0]+t*(pts[i+1][0]-pts[i][0]);}
  return pts.reduce((a,b)=>Math.abs(a[1]-z)<Math.abs(b[1]-z)?a:b)[0];
}
const falls=[{id:'feiyun',name:'飞云瀑',x:SITE.fallX,z:plan.site.fallZ,width:20,top:SITE.fallLipY,bottom:SITE.poolY}];
for(let i=0;i<POOLS.length-1;i++)falls.push({id:`river_drop_${i}`,name:`河道第 ${i+1} 处跌水`,x:round(riverX(POOLS[i].zN)),z:POOLS[i].zN,width:14,top:POOLS[i+1].level,bottom:POOLS[i].level});
falls.push({id:'south_outlet',name:'城南出水瀑',x:50,z:440,width:12,top:38,bottom:-205});
falls.forEach(f=>{f.raw_drop=round(f.top-f.bottom);f.adapted_drop=round(adapt(f.top)-adapt(f.bottom));});

const d1Baseline=JSON.parse(await readFile(resolve(ROOT,'blueprints/yunshan/baseline/d1_columns.json'),'utf8'));
const d1Cfg=JSON.parse(await readFile(resolve(ROOT,'blueprints/yunshan/district_realization_v2.json'),'utf8'));
const [dx0,dz0,dx1,dz1]=d1Baseline.window;
const d1={source_window:[dx0,dz0,dx1,dz1],source_size:[dx1-dx0,d1Cfg.y_top-d1Cfg.y_base+1,dz1-dz0],packed_size:[Math.ceil((dx1-dx0)/16)*16,d1Cfg.y_top-d1Cfg.y_base+1,Math.ceil((dz1-dz0)/16)*16],packed_window:[dx0,dz0,dx0+Math.ceil((dx1-dx0)/16)*16,dz0+Math.ceil((dz1-dz0)/16)*16],note:'仅为现有 D1 街区在原作坐标中的大致对照；旧版街巷和四馆曾另行设计，不能逐栋等同本次原作实例。'};

const entries=buildings.filter(b=>b.role==='gate').map(b=>({id:`entry_${b.source_template}`,position:[b.position[0],b.position[1],b.position[2]+b.shape.depth/2],adapted_position:[b.adapted_position[0],b.adapted_position[1],b.adapted_position[2]+b.shape.depth/2],facing:'south',building_id:b.id,status:b.source_template==='gate_water'?'water_entry_not_pedestrian':'preview_anchor'}));
const used=new Set(), services=[];
for(const item of spec.services_proposal){
  const options=buildings.filter(b=>!used.has(b.id)&&(item.source_template?b.source_template===item.source_template:b.source_kind===item.source_kind));
  options.sort((a,b)=>item.near?Math.hypot(a.position[0]-item.near[0],a.position[2]-item.near[1])-Math.hypot(b.position[0]-item.near[0],b.position[2]-item.near[1]):a.id.localeCompare(b.id));
  if(!options.length)continue;
  const b=options[0];used.add(b.id);
  services.push({id:`central_service_${item.id}`,name:item.name,role:item.role,building_id:b.id,position:[...b.position],adapted_position:[...b.adapted_position],status:'proposed_role_only_not_original_service_not_runtime_bound'});
}
const sourceFiles=[
  'blueprints/province_capitals/catalog.json',
  'web/yunshan-city/src/world/plan.js','web/yunshan-city/src/world/field.js','web/yunshan-city/src/world/geo.js','web/yunshan-city/src/world/geom.js','web/yunshan-city/src/world/noise.js','web/yunshan-city/src/world/materials.js','web/yunshan-city/src/voxel/catalog.js','web/yunshan-city/src/voxel/buildings.js','web/yunshan-city/src/voxel/landmarks.js','web/yunshan-city/src/voxel/roof.js','web/yunshan-city/src/render/structures.js',
  'blueprints/yunshan/baseline/d1_columns.json','blueprints/yunshan/district_realization_v2.json',
  'blueprints/province_capitals/central/preview-spec.json','tools/province_capitals/central/export.mjs','tools/province_capitals/central/viewer.js','tools/province_capitals/central/viewer.html',
  'tools/web/three.min.js','tools/web/OrbitControls.js','web/yunshan-city/vendor/THREE-LICENSE.txt'
];
const sourceHashes={};for(const p of sourceFiles)sourceHashes[p]=hash(await readFile(resolve(ROOT,p)));
const source={kind:'derived_from_actual_buildPlan',ref:spec.source.plan,preserved_ref:spec.source.preserved_ref,preserved_tip:spec.source.preserved_tip,workspace_head:execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim(),seed:spec.seed,sha256:sourceHashes};
const xmin=Math.min(...plan.cityPoly.map(p=>p[0])), xmax=Math.max(...plan.cityPoly.map(p=>p[0])), zmin=Math.min(...plan.cityPoly.map(p=>p[1])), zmax=Math.max(...plan.cityPoly.map(p=>p[1]));
const rawBuildingTop=Math.max(...buildings.map(b=>b.position[1]+b.shape.height));
const adaptedBuildingTop=Math.max(...buildings.map(b=>b.adapted_position[1]+b.shape.height));
const rawTop=Math.max(maxY,rawBuildingTop);
const adaptedTop=Math.max(adapt(maxY),adaptedBuildingTop);
const bounds={min:[grid.x_min,round(minY-12),grid.z_min],max:[grid.x_max,round(rawTop),grid.z_max]};
const adaptedBounds={min:[grid.x_min,round(adapt(minY)-12),grid.z_min],max:[grid.x_max,round(adaptedTop),grid.z_max]};
const districts=spec.districts.map(d=>({id:d.id,name:d.name,role:d.role,bounds:{min:[d.xz[0],round(minY),d.xz[1]],max:[d.xz[2],round(rawTop),d.xz[3]]},bounds_note:'水平功能审阅分区，允许交叠；不是行政归属或运行时独占分片。'}));
const heightfield={x0:grid.x_min,z0:grid.z_min,cell:grid.cell,nx,nz,quantum:grid.quantum,heights,materials,layout:'x_fastest_row_major',kind:'sampled_source_earth_without_walls',raw_min_y:round(minY),raw_max_y:round(maxY),natural_min_y:round(minNatural),natural_max_y:round(maxNatural),note:'墙单独导出；场地改造、建筑 pad、河道、御阶仍按原作 createField，地形用采样足迹抑制微小噪声。'};
const exchange={schema:1,city_id:spec.city_id,region_id:spec.region_id,stage:'preview',units:'block',coordinate_system:'+X east, +Y up, +Z south; 1 authored metre = 1 horizontal block',bounds_semantics:'closed_geometric_envelope',bounds_note:'包括连续地形侧壁和底面、支承与白模屋顶；不包括文字、机位、线框和 Y=319 参考标尺。',bounds,source,terrain:{kind:'heightfield_or_terraces',data:'terrain-original.json',data_path_base:'manifest_directory',cell:grid.cell,height_proposal:proposal},districts,roads,buildings,entries,services,water:{surfaces:water,falls,pools:POOLS},walls,surfaces,proposed_links:spec.proposed_links,d1_comparison:d1,activation:'not_activated_preview_only',evidence:spec.evidence,interface_notes:[
  '原始 original 与适配 adapted 是独立 profile、manifest、layout_id 与 SHA-256；各布局 position / points / bounds 仅属于本 profile，高度不可混用。',
  'exchange rotation_quarters=q 表示 +X→+Z 的正四分之一圈，绕 author_origin 再平移且不归零；Three.js 使用 -q*PI/2。原作实际渲染 r=1:(x,z)→(z,-x)，故 q=(4-source_rot)%4，并保留 source_rotation_quarters。',
  'bounds 是 closed_geometric_envelope，不是含首尾的体素索引；terrain.data 均相对各自 manifest 文件目录。source.ref 与 source.sha256 的路径相对 repository_root。',
  'source 的水门不是步行入口；service 仅候选语义岗位，原作商铺没有客栈/药铺/锻坊的既定服务绑定。',
  '全城 road / building 清单没有套用现有 D1 的 512 水平、128 高度等旧限制；接入前须新资源契约和空间索引。',
  '原作桥梁注释称 rot=1 沿 X，buildArchBridge 的几何 span 实际已沿本地 X；本白模忠实保留 source rotation，并将桥位逻辑连接单独标为待协调，未悄悄改原作朝向。'
]};
const bridgeIssues=buildings.filter(b=>b.shape.kind==='bridge').map(b=>({building_id:b.id,source_template:b.source_template,source_rotation_quarters:b.source_rotation_quarters,exchange_rotation_quarters:b.rotation_quarters,note:'源桥长轴与 rot=1 注释可能相反；需确定预览/正式几何旋转约定。'}));
const labels=[...plan.labels.map(l=>({text:l.text,position:l.pos,base_y:l.text==='揽云亭'?SITE.summit.y:l.text==='观瀑阁'?plan.site.ledge.y:l.text==='太和殿'?96:l.text==='凌霄塔'?plan.pagoda.y:l.text==='镜湖'?38:l.text==='宫城'?80:l.text==='正阳门'?buildings.find(b=>b.source_template==='gate_south').position[1]:l.pos[1]})),{text:'市集',position:[-44,60,176],base_y:48},{text:'D1 对照范围',position:[dx0+45,90,dz1+6],base_y:45},{text:'上游台地',position:[-15,SITE.upperY+12,-620],base_y:SITE.upperY}];
const stats={source_instances:plan.instances.length,source_features:plan.features.length,source_lots:plan.lotCount,building_instances:buildings.length,by_source_kind:counts,source_trees_omitted:plan.treeCount,roads:roads.length,wall_segments:walls.length,water_pools:POOLS.length,waterfalls:falls.length,terrain_vertices:nx*nz,terrain_triangles:(nx-1)*(nz-1)*2,scene_width:grid.x_max-grid.x_min,scene_depth:grid.z_max-grid.z_min,city_outline_bounds_xz:[round(xmin),round(zmin),round(xmax),round(zmax)],city_outline_width:round(xmax-xmin),city_outline_depth:round(zmax-zmin),original_summit_ground_y:SITE.summit.y,original_summit_label_y:plan.labels.find(l=>l.text==='揽云亭').pos[1],adapted_summit_ground_y:round(adapt(SITE.summit.y)),raw_terrain_y:[round(minY),round(maxY)],adapted_terrain_y:[round(adapt(minY)),round(adapt(maxY))],raw_geometric_top_y:round(rawTop),adapted_geometric_top_y:round(adaptedTop),raw_whitebox_top_y:round(rawBuildingTop),adapted_whitebox_top_y:round(adaptedBuildingTop),adapted_whitebox_roof_clearance:round(proposal.highest_solid_y-adaptedBuildingTop),d1_packed_size:d1.packed_size};
const sourcePointToAdapted=([x,y,z])=>[x,round(adapt(y)),z];
function makeProfile(profileId) {
  const isAdapted=profileId==='adapted';
  const layout={...exchange,profile_id:profileId,profile:{id:profileId,height_strategy:isAdapted?proposal.id:'source_identity',status:isAdapted?'proposal_not_activated':'source_reference_only'},manifest_ref:`manifest-${profileId}.json`,path_bases:{terrain_data:'manifest_directory',manifest_ref:'manifest_directory',source_ref:'repository_root',source_sha256_keys:'repository_root'},bounds:isAdapted?adaptedBounds:bounds};
  layout.terrain={...exchange.terrain,data:`terrain-${profileId}.json`,height_profile_id:profileId};
  layout.layout_family_id=capitalDefinition.layout_id;
  layout.appearance_id=capitalDefinition.appearance_id;
  layout.buildings=buildings.map(b=>{
    const {adapted_position,support,...rest}=b;
    return {...rest,position:isAdapted?adapted_position:b.position,source_position:b.position,support:{bottom:isAdapted?support.adapted_bottom:support.raw_bottom,source_bottom:support.raw_bottom,wall_offset:support.wall_offset}};
  });
  layout.roads=roads.map(r=>{const {adapted_points,...rest}=r;return {...rest,points:isAdapted?adapted_points:r.points,source_points:r.points};});
  const anchors=items=>items.map(a=>{const {adapted_position,...rest}=a;return {...rest,position:isAdapted?adapted_position:a.position,source_position:a.position};});
  layout.entries=anchors(entries);layout.services=anchors(services);
  if(isAdapted){
    layout.districts=districts.map(d=>({...d,bounds:{min:[d.bounds.min[0],adaptedBounds.min[1],d.bounds.min[2]],max:[d.bounds.max[0],adaptedBounds.max[1],d.bounds.max[2]]}}));
    layout.walls=walls.map(w=>({...w,source_points:w.points,points:w.points.map(([x,y,z])=>[x,round(adapt(y-w.height)+w.height),z])}));
    layout.surfaces=surfaces.map(s=>({...s,y:round(adapt(s.y)),source_y:s.y}));
    layout.proposed_links=spec.proposed_links.map(l=>({...l,points:l.points.map(sourcePointToAdapted),source_points:l.points}));
    layout.water={surfaces:water.map(w=>w.points?{...w,points:w.points.map(sourcePointToAdapted),source_points:w.points}:{...w,y:round(adapt(w.y)),source_y:w.y}),falls:falls.map(f=>({...f,top:round(adapt(f.top)),bottom:round(adapt(f.bottom)),source_top:f.top,source_bottom:f.bottom,profile_drop:f.adapted_drop})),pools:POOLS.map(p=>({...p,level:round(adapt(p.level)),source_level:p.level}))};
  }
  const layoutSha=hash(JSON.stringify(layout));layout.layout_sha256=layoutSha;layout.layout_id=`${spec.city_id}/${profileId}/${layoutSha.slice(0,16)}`;
  const terrain=isAdapted?{...heightfield,quantum:0.001,heights:heightfield.heights.map(v=>Math.round(adapt(v*heightfield.quantum)*1000)),height_profile_id:profileId,source_y_range:[round(minY),round(maxY)],profile_min_y:round(adapt(minY)),profile_max_y:round(adapt(maxY)),closure_bottom_y:adaptedBounds.min[1]}:{...heightfield,height_profile_id:profileId,profile_min_y:round(minY),profile_max_y:round(maxY),closure_bottom_y:bounds.min[1]};
  return {layout,terrain,identity:{profile_id:profileId,layout_id:layout.layout_id,layout_sha256:layoutSha,manifest:`manifest-${profileId}.json`,layout:`layout-${profileId}.json`,terrain:`terrain-${profileId}.json`,paths_relative_to:'manifest_directory'}};
}
const profiles={original:makeProfile('original'),adapted:makeProfile('adapted')};
const profileIdentities=Object.fromEntries(Object.entries(profiles).map(([k,v])=>[k,v.identity]));
const scene={spec,source,stats,heightfield,buildings,roads,walls,water,falls,surfaces,city_poly:plan.cityPoly.map(p=>p.map(round)),labels,views:plan.views,summit_path:plan.paths.summit.map(xyz),proposed_links:spec.proposed_links,services,d1,districts,bridge_issues:bridgeIssues,profiles:profileIdentities};
await mkdir(OUTPUT,{recursive:true});
await mkdir(resolve(OUTPUT,'vendor'),{recursive:true});
// Pack only the embedded copy. Readable scene/terrain JSON remains fully expanded.
// Row-relative int16 height deltas keep the offline HTML small without reducing samples.
const packedHeights=Buffer.alloc(heights.length*2);
for(let i=0;i<heights.length;i++){const previous=i%nx?heights[i-1]:i>=nx?heights[i-nx]:0;packedHeights.writeInt16LE(heights[i]-previous,i*2);}
const embeddedScene={...scene,source:undefined,views:undefined,
  buildings:buildings.map(({source_definition,...rest})=>rest),
  roads:roads.map(({adapted_points,source_feature_indices,...rest})=>rest),
  heightfield:{...heightfield,heights:undefined,heights_delta_i16_le_base64:packedHeights.toString('base64')}};
const payload=JSON.stringify(embeddedScene);
const viewer=await readFile(resolve(HERE,'viewer.js'),'utf8');
const three=await readFile(resolve(ROOT,'tools/web/three.min.js'),'utf8');
const orbit=await readFile(resolve(ROOT,'tools/web/OrbitControls.js'),'utf8');
let html=await readFile(resolve(HERE,'viewer.html'),'utf8');
html=html.replace('__THREE_GZIP_BASE64__',zip64(three)).replace('__ORBIT_GZIP_BASE64__',zip64(orbit)).replace('__SCENE_GZIP_BASE64__',zip64(payload)).replace('__VIEWER_GZIP_BASE64__',zip64(viewer));
await writeFile(resolve(OUTPUT,'index.html'),html);
await writeFile(resolve(OUTPUT,'scene.json'),json(scene));
await writeFile(resolve(OUTPUT,'heightfield.json'),json(heightfield));
await writeFile(resolve(OUTPUT,'exchange.json'),json(profiles.original.layout));
for(const [id,p] of Object.entries(profiles)){
  await writeFile(resolve(OUTPUT,p.identity.layout),json(p.layout));
  await writeFile(resolve(OUTPUT,p.identity.terrain),json(p.terrain));
  const files={};for(const name of [p.identity.layout,p.identity.terrain]){const bytes=await readFile(resolve(OUTPUT,name));files[name]={bytes:bytes.length,sha256:hash(bytes)};}
  await writeFile(resolve(OUTPUT,p.identity.manifest),json({schema:1,city_id:spec.city_id,region_id:spec.region_id,stage:'preview',profile_id:id,layout_id:p.identity.layout_id,layout_sha256:p.identity.layout_sha256,bounds_semantics:'closed_geometric_envelope',bounds:p.layout.bounds,layout:p.identity.layout,terrain:{kind:'heightfield_or_terraces',data:p.identity.terrain,data_path_base:'manifest_directory'},path_bases:p.layout.path_bases,height_strategy:p.layout.profile,source,evidence:spec.evidence,files}));
}
await copyFile(resolve(ROOT,'tools/web/three.min.js'),resolve(OUTPUT,'vendor/three.min.js'));
await copyFile(resolve(ROOT,'tools/web/OrbitControls.js'),resolve(OUTPUT,'vendor/OrbitControls.js'));
await copyFile(resolve(ROOT,'web/yunshan-city/vendor/THREE-LICENSE.txt'),resolve(OUTPUT,'vendor/THREE-LICENSE.txt'));
const manifest={schema:1,city_id:spec.city_id,region_id:spec.region_id,name:spec.name,stage:'preview',kind:'profile_index',profiles:profileIdentities,path_bases:{profiles:'manifest_directory',files:'manifest_directory',source_ref:'repository_root',source_sha256_keys:'repository_root'},bounds_semantics:'closed_geometric_envelope',generated_at:new Date().toISOString(),generator:'tools/province_capitals/central/export.mjs',command:'node tools/province_capitals/central/export.mjs',source,stats,bounds,adapted_bounds:adaptedBounds,height_proposal:proposal,scope:'完整原作地形/道路/水系/城墙/建筑实例的三维白模；非正式体素或游戏资源',sampling:{...grid,nx,nz},d1_comparison:d1,bridge_orientation_issues:bridgeIssues,interface_notes:exchange.interface_notes,rebuild_items:spec.rebuild_items,evidence:spec.evidence,files:{},handoff:{preview_html:rel(resolve(OUTPUT,'index.html')),exchange_json:'out/preview/province-capitals/central/layout-original.json',adapted_exchange_json:'out/preview/province-capitals/central/layout-adapted.json',documentation:'docs/province-capitals-central-preview.md',display_owner:'parent_agent_html_preview_html_render',production_activation:'not_activated',worlds_modified:false}};
for(const name of ['index.html','scene.json','heightfield.json','exchange.json','layout-original.json','layout-adapted.json','terrain-original.json','terrain-adapted.json','manifest-original.json','manifest-adapted.json','vendor/three.min.js','vendor/OrbitControls.js','vendor/THREE-LICENSE.txt']){const bytes=await readFile(resolve(OUTPUT,name));manifest.files[name]={bytes:bytes.length,sha256:hash(bytes)};}
await writeFile(resolve(OUTPUT,'manifest.json'),json(manifest));
const doc=`# 中州云山巨城：完整版三维白模与主世界高度提案\n\n本批仅为可审阅预览，未激活正式生成器、未改版本或打包、未写玩家世界。所有测试均为 \`not_run\`；玩家外观、步行、FPS 与多人均为 \`not_verified\`。后续测试交 Terra xhigh。\n\n## 来源与范围\n\n直接执行当前父工作区 \`web/yunshan-city/src/world/plan.js\` 的 \`buildPlan(${spec.seed})\`，读取 \`createGeo\` 和 \`createField\`；未执行原作 0.2 米高精度体素重建。保留分支为 \`${spec.source.preserved_ref}\`，tip \`${spec.source.preserved_tip}\`；当前输入文件 SHA-256 逐项记录在 manifest。\n\n场景 ${stats.scene_width}×${stats.scene_depth} 方块，X=[${grid.x_min},${grid.x_max}]、Z=[${grid.z_min},${grid.z_max}]；城内轮廓包围盒约 ${stats.city_outline_width}×${stats.city_outline_depth}，后者不含山顶和城外地标。水平 1 原作米=1 方块。地形采样 ${grid.cell} 格，${nx}×${nz}=${stats.terrain_vertices} 顶点，${stats.terrain_triangles} 个表面三角形；地形封闭侧壁与底面形成连续山基。原作细墙、道路、水面和建筑 pad 独立生成，避免采样吞掉细街。\n\n保留 ${counts.house||0} 民居、${counts.shop||0} 商铺、${counts.tea||0} 茶楼，以及城门、宫城各殿、寺塔、城墙敌楼、观瀑阁、揽云亭、湖心亭、山径亭、城外石柱亭和桥，共 ${buildings.length} 个建筑/桥占位。原作全部 ${plan.lotCount} 个常规 lot 均按真实实例保留；原树木 ${plan.treeCount} 株、人物、船与灯具未铺成高精细模型。\n\n正阳门→御街→市集→宫前，三进宫城、七级水池/跌水→镜湖→水门出水、北瀑潭→观瀑阁→上台地→揽云峰均可查看。原作 tour 含飞行；宫后至瀑潭和观瀑阁阶梯间的缺口在图中用橙色虚线说明，不宣称已可步行。\n\n现有 D1 原参考窗口 [${d1.source_window}]，源尺寸 ${d1.source_size.join('×')}，按 16 格对齐后 ${d1.packed_size.join('×')}，在全城南门至市集西侧用青框标示。D1 保留四馆的旧版资源与这里的原作实例不是逐栋对应关系；本批没有放大 D1 冒充整城。\n\n## 两种高度模式\n\n原始模式：地形 Y=${stats.raw_terrain_y.join('～')}；峰台 ${SITE.summit.y}、揽云亭原标记 ${stats.original_summit_label_y}；白模保守屋顶最高约 ${stats.raw_whitebox_top_y}。不能称作当前主世界可 1:1 竖向容纳。\n\n适配模式仅为 \`minecraft:overworld\` 提案，不建新维度、不改变主世界全局高度。采用下面分段线性映射，38～134 原作城区高差保持 1:1，上部山体与台地压缩；建筑整体只重放基座，不压扁楼体。墙上敌楼保留 9 格支承偏移。所有原始水平位置和四分之一旋转保留。\n\n| 原作地表 Y | 提案地表 Y |\n|---:|---:|\n${proposal.knots.map(k=>`| ${k[0]} | ${k[1]} |`).join('\n')}\n\n本场景适配采样地形 Y=${stats.adapted_terrain_y.join('～')}，峰台 Y=${stats.adapted_summit_ground_y}；当前白模屋顶最高约 Y=${stats.adapted_whitebox_top_y}，距最高可放实体方块 Y=${proposal.highest_solid_y} 约 ${stats.adapted_whitebox_roof_clearance} 格。长期预算为最高地表 ${proposal.max_ground_y}+未来建筑 ${proposal.future_building_allowance}+顶空 ${proposal.ceiling_clearance}=${proposal.highest_solid_y}，不是把山顶简单压到 319。若未来山顶建筑超过 32 格，必须再降低峰台或调整该地标，不可直接上移整城。\n\n| 水工位置 | 原落差 | 提案落差 |\n|---|---:|---:|\n${falls.map(f=>`| ${f.name} | ${f.raw_drop} | ${f.adapted_drop} |`).join('\n')}\n\n以上落差仅用于白模；未模拟流体。实际主世界要先选定全城 1200×1440 及外缘约 128 格过渡带的连续保留区，绑定绝对 Y 后进行山基填挖与边界融合；不能把已经固定到顶空的方案任意加 Y 偏移。海平面、洞穴、现有结构避让、材料开销及区块生成预算均未落地。\n\n## 生成与预览\n\n从父工作区执行：\n\n\`\`\`sh\nnode tools/province_capitals/central/export.mjs\n\`\`\`\n\n这条命令只导出本批预览、交换 JSON、manifest 和本说明，不运行 tests/validators/lint/check/Gradle/MC/云端工作。输出脚本仅写本代理拥有的四个路径。页面是自包含 HTML，内嵌本地 Three.js、OrbitControls 和 gzip 场景数据，无 CDN、无 fetch、无服务。也附可读 JSON 和 vendor 文件。现代浏览器须有 WebGL 与 DecompressionStream。父代理统一执行 html_preview/html_render；本代理未运行浏览器或渲染测试。\n\n- HTML：\`out/preview/province-capitals/central/index.html\`\n- manifest：\`out/preview/province-capitals/central/manifest.json\`\n- 全场景：\`out/preview/province-capitals/central/scene.json\`\n- 地形：\`out/preview/province-capitals/central/heightfield.json\`\n- 契约交换：\`out/preview/province-capitals/central/layout-original.json\`\n- authored 提案：\`blueprints/province_capitals/central/preview-spec.json\`\n\n页面提供原始/适配高度、全景/俯视/城门/御街/宫城/瀑布/山顶/D1 机位、旋转平移缩放、图层、建筑点选、层高与尺度说明、顶高参考和原作步道逐点巡览。巡览只用于空间审阅，摄像机不是角色或碰撞模拟。\n\n## 接口偏差与未完成项\n\n${exchange.interface_notes.map(x=>'- '+x).join('\n')}\n\n${spec.rebuild_items.map(x=>'- '+x).join('\n')}\n\n地标高度是源码公式的保守包络；原作建筑 roof/gate 细部、真实内室和方块碰撞均未重建。source 中桥长轴与旋转注释存在偏差，已在 manifest 的 bridge_orientation_issues 单列；不得将本白模当作已可遍历的桥梁证据。\n\n## 后续整城资源化与 NPC 就近投影建议\n\n1. 先确认全城轮廓、尺度、台地映射与水工设计，再按南门镜湖坊、市集、两坡住区、宫城、瀑潭崖阶、上台地与峰顶分批资源化。每个正式建筑族按 groups.py→modset/catalogue→roof/motif 注册表接入；预览 template ID 不直接注册为游戏资源。\n2. 整城规划与高度映射版本冻结为一份来源，资源按区块柱或有界街区切片、空间索引；每片维护边界道路、水口、基础连接锚点，保证跨片连续。不沿用 D1 的全城尺寸上限，也不在玩家接近时重算整个 buildPlan。\n3. 常驻人物身份、职业、资产和工作计划留在 WorldSim 命簿，由现有 facade 修改；场景只维护 district/building/place 锚点与可见半径。多玩家用同一人物唯一投影租约，近场实体化、远场冷模拟，离场回收投影而不复制身份。\n4. NPC 导航按街区道路图和高度量化后的入口/楼梯连接分层寻路；只有附近区块已生成且真实入口有效时才投影在安全锚点，水门、石柱亭、未补阶梯均不能先假设可达。服务语义需重新确认后绑定已有权威交易/补给流程。\n5. 下轮任何检查与游戏证据由 Terra xhigh 承担；仍需确认源身份、街区切片、顶空、实际步行/水系边界、NPC 租约、多人及性能。当前全部 not_run / not_verified 不因生成文件而变为通过。\n`;
const profileDoc=`\n## 协调补充的采纳\n\ncity_id 已统一为 \`capital_zhongzhou\`。bounds 采用 \`closed_geometric_envelope\`，包含连续山基底面和真实白模几何，不把最大值减一。terrain.data 相对各自 manifest 所在目录；其余路径基准在 path_bases 明确。\n\n| 高度 profile | manifest | 布局 | 布局身份 |\n|---|---|---|---|\n${Object.entries(profiles).map(([id,p])=>`| ${id} | ${p.identity.manifest} | ${p.identity.layout} | ${p.identity.layout_id} |`).join('\n')}\n\n两份 manifest、布局 JSON 与 terrain JSON 均在 \`out/preview/province-capitals/central/\`。原始交换入口为 \`layout-original.json\`，适配交换入口为 \`layout-adapted.json\`。各自 position/points/bounds 只有该 profile 的高度，source_position/source_points 保留来源；不能混用布局身份。\n\n原作 \`render/structures.js:114\` 的实际 r=1 变换为 \`(x,z)→(z,-x)\`；已转换 \`exchange q=(4-source_rot)%4\`，并保留 \`source_rotation_quarters\`、\`author_origin=[0,0,0]\`，不自动归零。页面按 \`rotation.y=-q*Math.PI/2\` 使用。桥长轴与原注释的偏差单列披露，不通过改旋转掩盖。\n\n这些都是交付生成和来源说明，不构成任何新增检查；测试仍全部 not_run。\n`;
await writeFile(resolve(ROOT,'docs/province-capitals-central-preview.md'),doc+profileDoc);
console.log(JSON.stringify({generated_only:true,preview_html:resolve(OUTPUT,'index.html'),manifest:resolve(OUTPUT,'manifest.json'),exchange:resolve(OUTPUT,'layout-original.json'),stats,html_bytes:Buffer.byteLength(html),tests:'not_run',player_visual:'not_verified'},null,2));
