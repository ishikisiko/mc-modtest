#!/usr/bin/env node
// Production input export. Executes the original layout/field, never the whole-city voxel builder.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';
import { buildPlan } from '../../../web/yunshan-city/src/world/plan.js';
import { createField, KIND, FT, poolLevelAt, quayAt } from '../../../web/yunshan-city/src/world/field.js';
import { SITE, POOLS } from '../../../web/yunshan-city/src/world/geo.js';
import { TM } from '../../../web/yunshan-city/src/world/materials.js';
import { makeCatalog } from '../../../web/yunshan-city/src/voxel/catalog.js';
import { buildTree } from '../../../web/yunshan-city/src/voxel/trees.js';
import { PAL } from '../../../web/yunshan-city/src/voxel/palette.js';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const spec = JSON.parse(readFileSync(resolve(ROOT, 'blueprints/province_capitals/central/game-spec.json')));
const preview = JSON.parse(readFileSync(resolve(ROOT, spec.height_profile_source)));
const out = resolve(process.argv[process.argv.indexOf('--out') + 1]);
mkdirSync(out, { recursive: true });
function adapt(y) {
  const knots = preview.height_proposal.knots;
  if (y <= knots[0][0]) return knots[0][1];
  for (let i=1;i<knots.length;i++) if (y<=knots[i][0]) {
    const a=knots[i-1],b=knots[i];return a[1]+(b[1]-a[1])*(y-a[0])/(b[0]-a[0]);
  }
  return preview.height_proposal.max_ground_y;
}
console.log('读取原作 buildPlan；以一格精度生成正式地形输入（不运行测试）');
const plan=buildPlan(spec.seed), defs={...makeCatalog(spec.seed).defs,...plan.uniques};
const field=createField({...plan,features:plan.features.filter(f=>f.kind!==KIND.WALL&&f.kind!==KIND.PALWALL)});
const {width,depth,source_offset:offset}=spec.bounds;
const heights=Buffer.alloc(width*depth*2), waters=Buffer.alloc(width*depth*2), mats=Buffer.alloc(width*depth);
const scratch=new Float64Array(4);
let min=32767,max=-32768;
for(let z=0;z<depth;z++) {
  for(let x=0;x<width;x++) {
    const i=z*width+x;
    field.sample(x+offset[0]+0.5,z+offset[2]+0.5,1,scratch);
    // Integer Y denotes a block, not the standing air above it.
    const water=scratch[1]===TM.WATER||scratch[1]===TM.FOAM;
    const level=Math.round(adapt(scratch[0]))-1;
    const top=water?level-spec.waterworks.depth:Math.floor(adapt(scratch[0]))-1;
    heights.writeInt16LE(top,i*2);waters.writeInt16LE(water?level:-32768,i*2);mats[i]=scratch[1];
    min=Math.min(min,top);max=Math.max(max,top);
  }
  if(z%240===0)console.log(`地形行 ${z}/${depth}`);
}
for(const [name,buf] of [['terrain',heights],['water',waters],['materials',mats]])
  writeFileSync(resolve(out,`${name}.bin.gz`),gzipSync(buf,{level:9,mtime:0}));

// Only the small, dedicated original tree generator is sampled. Each unique tree is
// converted DOWN to one-block cells once; no 0.2m city or 125x volume is constructed.
const treeModels={};
for(const def of Object.values(defs).filter(d=>d.kind==='tree')) {
  const model=buildTree(def),cells=new Map();
  for(let y=model.y0;y<model.y1;y++)for(let z=model.z0;z<model.z1;z++)for(let x=model.x0;x<model.x1;x++) {
    const m=model.get(x,y,z);if(!m)continue;
    const cell=[Math.floor((x+0.5)/5),Math.max(0,Math.floor(y/5)),Math.floor((z+0.5)/5)];
    const key=cell.join(','),wood=m===PAL.BARK||m===PAL.BARK_G||m===PAL.BAMBOO;
    const old=cells.get(key);if(!old||wood)cells.set(key,[...cell,wood?1:0]);
  }
  treeModels[def.id]={species:def.species,cells:[...cells.values()]};
}
const buildings=[],trees=[],lamps=[],omitted={};
plan.instances.forEach((inst,i)=>{
  const def=defs[inst.id];if(!def)return;
  const row={...inst,source_instance_index:i};
  if(spec.templates.kind_builders[def.kind])buildings.push(row);
  else if(def.kind==='tree')trees.push(row);
  else if(def.kind==='lamp')lamps.push(row);
  else omitted[def.kind]=(omitted[def.kind]||0)+1;
});
const paths=[];
const byPid=new Map();
for(const f of plan.features)if(f.t===FT.SEG&&f.pid&&[TM.ROAD,TM.STEPS,TM.STONEPATH,TM.MARBLE,TM.WOOD].includes(f.top)) {
  if(!byPid.has(f.pid))byPid.set(f.pid,{id:`source_path_${f.pid}`,width:f.hw*2,material:f.top,points:[[f.ax,f.ay,f.az]]});
  byPid.get(f.pid).points.push([f.bx,f.by,f.bz]);
}
paths.push(...byPid.values());
for(const side of [-1,1])paths.push({id:`quay_${side}`,width:5,material:TM.STEPS,
  points:plan.paths.river.map(([x,z])=>[x+side*11.5,quayAt(z),z])});
// Outlet quays also have explicit paths through the water gate.
for(const side of [-1,1])paths.push({id:`outlet_quay_${side}`,width:3,material:TM.ROAD,
  points:[[26+side*9,40,346],[36+side*9,40,370],[44+side*9,40,392],[48+side*9,40,420]]});
const walls=plan.features.filter(f=>f.kind===KIND.WALL||f.kind===KIND.PALWALL);
function riverX(z){
  const pts=plan.paths.river;
  for(let i=0;i<pts.length-1;i++)if((z-pts[i][1])*(z-pts[i+1][1])<=0){const a=pts[i],b=pts[i+1],t=(z-a[1])/(b[1]-a[1]);return a[0]+(b[0]-a[0])*t;}
  return pts.reduce((a,b)=>Math.abs(a[1]-z)<Math.abs(b[1]-z)?a:b)[0];
}
const falls=[{id:'feiyun',x:SITE.fallX,z:-392,width:20,top:SITE.fallLipY,bottom:SITE.poolY}];
for(let i=0;i<POOLS.length-1;i++)falls.push({id:`river_drop_${i+1}`,x:riverX(POOLS[i].zN),z:POOLS[i].zN,width:14,top:POOLS[i+1].level,bottom:POOLS[i].level});
falls.push({id:'south_outlet',x:50,z:440,width:12,top:38,bottom:-205});
const result={schema:1,defs,buildings,trees,lamps,tree_models:treeModels,paths,walls,falls,pools:POOLS,
  site:SITE,river:plan.paths.river,omitted,terrain_range:[min,max],lot_count:plan.lotCount,
  note:'正式导出输入；非预览exchange；source原坐标，Python统一归零。'};
writeFileSync(resolve(out,'source.json'),JSON.stringify(result));
console.log(JSON.stringify({buildings:buildings.length,trees:trees.length,lamps:lamps.length,lots:plan.lotCount,omitted}));
