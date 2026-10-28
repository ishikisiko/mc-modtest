#!/usr/bin/env node
'use strict';
const fs=require('fs'),path=require('path'),assert=require('assert/strict'),crypto=require('crypto');
const atlas=path.resolve(__dirname,'../..'),repo=path.resolve(atlas,'../..');
global.THREE=require(path.join(repo,'tools/web/three.min.js'));
const T=THREE,E=require('../engine.js'),F=require('../effects.js'),data=require('../../effects/catalogue.js');
const report={checks:[],designs:[],failures:[],inputs:{}};
for(const f of ['core/effects.js','effects/catalogue.js'])report.inputs[f]=crypto.createHash('sha256').update(fs.readFileSync(path.join(atlas,f))).digest('hex');
function check(name,fn){try{fn();report.checks.push(name);}catch(e){report.failures.push({name,error:e.stack});}}
const designs=[];for(let n=1;n<=5;n++){const key='levels/level'+n+'.json',raw=fs.readFileSync(path.join(atlas,key));report.inputs[key]=crypto.createHash('sha256').update(raw).digest('hex');designs.push(...JSON.parse(raw));}
check('每套都有独立作者配置，ID与境界完整对应',()=>assert.deepEqual(Object.keys(data).sort(),designs.map(d=>d.id).sort()));
for(const d of designs){check(d.id+' / 动态变换有限、脸手露出、关闭恢复与资源归属',()=>{
 F.validate(data[d.id],d);const root=E.buildDesign(d,{avatar:true}),before=E.exportOBJ(root),originals=new Map();
 root.traverse(m=>{if(m.isMesh)originals.set(m,m.material);});
 let textureDisposals=0;const textures=new Set([...originals.values()].map(m=>m.map).filter(Boolean));textures.forEach(t=>t.addEventListener('dispose',()=>textureDisposals++));
 const fx=F.create(root,data[d.id]);const scene=new T.Group();scene.add(root,fx.group);
 for(const seconds of [0,1.7,4.8,9.1,13.4]){
  fx.update(seconds);root.updateMatrixWorld(true);root.traverse(n=>assert(n.matrixWorld.elements.every(Number.isFinite)));
  fx.group.traverse(n=>{assert(n.matrixWorld.elements.every(Number.isFinite));if(n.material?.opacity!==undefined)assert(n.material.opacity>=0&&n.material.opacity<=.85);});
  for(const [x,y]of [[-2,27],[0,27],[2,27],[-6,13.4],[6,13.4]]){
   const hits=new T.Raycaster(new T.Vector3(x/16,y/16,4),new T.Vector3(0,0,-1)).intersectObject(root,true);assert.equal(hits[0]?.object.userData.role,'avatar',d.id+' face/hand at '+x+','+y);
  }
 }
 const count=fx.stats();fx.dispose();fx.dispose();assert.equal(textureDisposals,0,'借用的模型贴图不能随特效释放');
 originals.forEach((mat,mesh)=>assert.equal(mesh.material,mat));assert.equal(E.exportOBJ(root),before,'关闭动效必须恢复全部组件与导出次序');
 assert.equal(fx.group.children.length,0);E.dispose(root);assert.equal(textureDisposals,textures.size,'模型最终释放自身贴图');report.designs.push({id:d.id,...count});
 });
 check(d.id+' / 四件筛选及锭不带特效',()=>{for(const mode of ['head','chest','legs','boots','ingot']){
  const root=mode==='ingot'?E.buildIngot(d):E.buildDesign(d,{avatar:false});
  if(mode!=='ingot')root.children.slice().forEach(n=>{if(n.userData.role!==mode){root.remove(n);n.traverse(m=>m.geometry?.dispose());}});
  const fx=F.create(root,data[d.id],mode);fx.update(3);if(mode==='ingot')assert.equal(fx.group.children.length,0);
  const expected=data[d.id].flows.filter(f=>f.role===mode).reduce((n,f)=>n+f.count,0);assert.equal(fx.stats().particles,expected);
  fx.dispose();E.dispose(root);
 }});
}
check('悬圭与金铭轴在整个摆动中保持刚性相对位置',()=>{
 const d=designs.find(d=>d.id==='huashen_xuanji'),root=E.buildDesign(d,{avatar:false});root.updateMatrixWorld(true);
 const pairs=[-1,1].map(sign=>{const a=root.children.find(n=>n.userData.componentId==='suspended_tablet_'+sign),b=root.children.find(n=>n.userData.componentId==='tablet_gold_axis_'+sign);return {a,b,relative:new T.Matrix4().copy(a.matrixWorld).invert().multiply(b.matrixWorld)};});
 const fx=F.create(root,data[d.id]);try{for(const t of [1.9,4.2,8.7]){fx.update(t);for(const {a,b,relative}of pairs){const actual=new T.Matrix4().copy(a.matrixWorld).invert().multiply(b.matrixWorld);assert(actual.elements.every((v,i)=>Math.abs(v-relative.elements[i])<1e-12),'悬圭铭轴脱位');}}}finally{fx.dispose();E.dispose(root);}
});
const sample=designs.find(d=>d.id==='jindan_zixiao');
for(const [name,edit,match]of [
 ['未知组件拒绝',c=>c.motions[0].targets=['missing'],/unknown effect target/],
 ['高频雷闪拒绝',c=>c.arcs[0].period=.1,/arc period/],
 ['超范围摆动拒绝',c=>c.motions[0].angle=80,/angle/],
 ['粒子预算超出拒绝',c=>c.flows=Array(8).fill(c.flows[0]),/budget/],
 ['非有限坐标拒绝',c=>c.flows[0].path[0][0]=Infinity,/finite/],
 ['重复运动归属拒绝',c=>c.motions.push(c.motions[0]),/overlapping/]
])check(name,()=>{const c=structuredClone(data[sample.id]);edit(c);assert.throws(()=>F.validate(c,sample),match);});
report.status=report.failures.length?'failed':'pass';const out=path.join(repo,'reports/immortal-armor-atlas/dynamic-v5/effects-unit.json');fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,failures:report.failures}));if(report.failures.length)process.exitCode=1;
