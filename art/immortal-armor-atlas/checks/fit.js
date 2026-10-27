#!/usr/bin/env node
'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const atlas=path.resolve(__dirname,'..'),repo=path.resolve(atlas,'../..');globalThis.THREE=require(path.join(repo,'tools/web/three.min.js'));const E=require('../core/engine.js'),T=THREE;
const all=[1,2,3].flatMap(n=>JSON.parse(fs.readFileSync(path.join(atlas,'levels','level'+n+'.json'),'utf8')));
const out=process.argv[2]||path.join(repo,'reports/immortal-armor-atlas/review/fit-repair.json');
const report={scope:'独立审美复核指出的固定几何缺陷；不是全角度穿戴验收',checks:[],failed:[],inputs:{}};
for(const n of [1,2,3])report.inputs['level'+n]=crypto.createHash('sha256').update(fs.readFileSync(path.join(atlas,'levels','level'+n+'.json'))).digest('hex');
report.inputs.engine=crypto.createHash('sha256').update(fs.readFileSync(path.join(atlas,'core/engine.js'))).digest('hex');
function hit(g,x,y,side){return new T.Raycaster(new T.Vector3(x,y,side==='back'?-2:2),new T.Vector3(0,0,side==='back'?1:-1)).intersectObject(g,true).map(h=>({id:h.object.userData.componentId,role:h.object.userData.role,z:h.point.z}));}
function record(name,ok,data){report.checks.push({name,pass:ok,...data});if(!ok)report.failed.push(name);}
for(const id of ['zhuji_baiqing_lingyun','zhuji_yuheng_yizhuang','zhuji_wuyao_yeque','jindan_jinwu','jindan_zixiao','qi_moqing']){
 const d=all.find(d=>d.id===id),g=E.buildDesign(d,{avatar:true});g.updateMatrixWorld(true);
 if(id.startsWith('zhuji'))for(const side of ['front','back'])for(const sign of [-1,1])for(const x of [.19,.20,.21])for(const y of [1.44,1.46,1.48]){
  const hs=hit(g,sign*x,y,side);record(id+'/shoulder/'+side+'/'+sign*x+'/'+y,!!hs.length&&hs[0].role!=='avatar',{point:[sign*x,y],first:hs[0]});
 }
 if(id.startsWith('jindan'))for(const side of ['front','back'])for(const x of [-.05,0,.04])for(const y of [.94,.96,.97]){
  const hs=hit(g,x,y,side);record(id+'/hip/'+side+'/'+x+'/'+y,!!hs.length&&hs[0].role!=='avatar',{point:[x,y],first:hs[0]});
 }
 if(id==='qi_moqing')for(const x of [-.15,-.14,.14,.15])for(const y of [.90,.94,.97]){
  const hs=hit(g,x,y,'back').filter(h=>['mq_longback','mq_back_lining','mq_trouser_l','mq_trouser_r','mq_hip'].includes(h.id));
  record(id+'/back-layers/'+x+'/'+y,hs[0]?.id==='mq_longback',{point:[x,y],hits:hs.slice(0,8)});
 }
 E.dispose(g);
}
report.status=report.failed.length?'failed':'pass';fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,failed:report.failed}));if(report.failed.length)process.exitCode=1;
