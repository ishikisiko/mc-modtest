#!/usr/bin/env node
'use strict';
// New MC coordinate contract. Legacy 271-point smooth-body checks remain historical.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const atlas=path.resolve(__dirname,'..'),repo=path.resolve(atlas,'../..');
globalThis.THREE=require(path.join(repo,'tools/web/three.min.js'));const T=THREE,E=require('../core/engine.js');
const arg=process.argv.find(a=>a.startsWith('--levels=')),levels=(arg?arg.split('=')[1]:'1,2,3,4,5').split(',').map(Number);
const report={contract:'MC 8x8x8 head / 8x12x4 torso / 4x12x4 limbs; pixel textures',checks:[],failures:[],inputs:{},not_verified:['用户审美认可','游戏动作与碰撞','真实设备帧率']};
function check(name,pass,detail){report.checks.push({name,pass,detail});if(!pass)report.failures.push(name);}
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
report.inputs.engine=hash(fs.readFileSync(path.join(atlas,'core/engine.js')));
for(const n of levels){
 const file=path.join(atlas,'levels','level'+n+'.json'),bytes=fs.readFileSync(file);report.inputs['level'+n]=hash(bytes);
 for(const d of JSON.parse(bytes)){
  E.validateDesign(d);check(d.id+'/hard-surface',d.components.every(c=>['box','panel'].includes(c.shape)&&!(c.bevel>0)));
  check(d.id+'/pixel-materials',Object.values(d.materials).every(m=>[16,32].includes(m.pixelArt?.rows.length)));
  const g=E.buildDesign(d,{avatar:true});g.updateMatrixWorld(true);
  const embroidery=d.components.find(c=>c.id==='orn_back');
  if(embroidery){const p=embroidery.position,hs=new T.Raycaster(new T.Vector3(p[0],p[1],-4),new T.Vector3(0,0,1)).intersectObject(g,true);check(d.id+'/embroidery-visible',hs[0]?.object.userData.componentId==='orn_back',{first:hs[0]?.object.userData.componentId});}
  function sample(x,y,back=false,face=false){
   const hs=new T.Raycaster(new T.Vector3(x/16,y/16,back?-4:4),new T.Vector3(0,0,back?1:-1)).intersectObject(g,true);
   check(d.id+'/'+(face?'face':back?'back':'front')+'/'+x+'/'+y,!!hs.length&&(face?hs[0].object.userData.role==='avatar':hs[0].object.userData.role!=='avatar'),hs[0]?{id:hs[0].object.userData.componentId,role:hs[0].object.userData.role}:null);
  }
  for(const back of [false,true]){
   for(const x of [-3,0,3])for(const y of [13.5,17,21,23])sample(x,y,back);
   for(const x of [-6,6])for(const y of [16,20,23])sample(x,y,back);
   for(const x of [-2,2])for(const y of [1,3,6,9,11])sample(x,y,back);
  }
  for(const x of [-2,0,2])sample(x,27,false,true);
  const armor=E.buildDesign(d,{avatar:false}),b=new T.Box3().setFromObject(armor),size=b.getSize(new T.Vector3());
  check(d.id+'/bounds',b.min.y>=-.001&&b.max.y<=2.8&&size.x<=2.25,{min:b.min.toArray(),max:b.max.toArray()});
  armor.traverse(m=>{if(!m.isMesh)return;const uv=m.geometry.attributes.uv;check(d.id+'/uv/'+m.userData.componentId,!!uv&&uv.count===m.geometry.attributes.position.count&&[...uv.array].every(Number.isFinite));check(d.id+'/nearest/'+m.userData.componentId,!!m.material.map&&m.material.map.magFilter===T.NearestFilter&&m.material.map.minFilter===T.NearestFilter);});
  const obj=E.exportOBJ(armor);check(d.id+'/obj-uv',obj.split('\n').filter(l=>l.startsWith('vt ')).length===E.meshStats(armor).vertices);
  E.dispose(g);E.dispose(armor);
 }
}
report.status=report.failures.length?'failed':'pass';const out=path.join(repo,'reports/immortal-armor-atlas/minecraft/checks/fit-'+levels.join('-')+'.json');fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,failures:report.failures,report:out}));if(report.failures.length)process.exitCode=1;
