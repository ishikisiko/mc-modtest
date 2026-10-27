#!/usr/bin/env node
'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const atlas=path.resolve(__dirname,'..'),repo=path.resolve(atlas,'../..');globalThis.THREE=require(path.join(repo,'tools/web/three.min.js'));const E=require('../core/engine.js'),T=THREE;
const levels=[1,2,3,4,5];
const all=levels.flatMap(n=>JSON.parse(fs.readFileSync(path.join(atlas,'levels','level'+n+'.json'),'utf8')));
const out=process.argv[2]||path.join(repo,'reports/immortal-armor-atlas/review/fit-repair.json');
const report={scope:'独立审美复核指出的固定几何缺陷；不是全角度穿戴验收',checks:[],failed:[],inputs:{}};
for(const n of levels)report.inputs['level'+n]=crypto.createHash('sha256').update(fs.readFileSync(path.join(atlas,'levels','level'+n+'.json'))).digest('hex');
report.inputs.engine=crypto.createHash('sha256').update(fs.readFileSync(path.join(atlas,'core/engine.js'))).digest('hex');
function hit(g,x,y,side){return new T.Raycaster(new T.Vector3(x,y,side==='back'?-2:2),new T.Vector3(0,0,side==='back'?1:-1)).intersectObject(g,true).map(h=>({id:h.object.userData.componentId,role:h.object.userData.role,z:h.point.z}));}
function record(name,ok,data){report.checks.push({name,pass:ok,...data});if(!ok)report.failed.push(name);}
for(const id of ['zhuji_baiqing_lingyun','zhuji_yuheng_yizhuang','zhuji_wuyao_yeque','jindan_jinwu','jindan_zixiao','qi_moqing','huashen_taixu','huashen_xuanji','yuanying_jiuse']){
 const d=all.find(d=>d.id===id),g=E.buildDesign(d,{avatar:true});g.updateMatrixWorld(true);
 if(id.startsWith('zhuji')||id==='huashen_xuanji')for(const side of ['front','back'])for(const sign of [-1,1])for(const x of [.19,.20,.21])for(const y of [1.44,1.46,1.48]){
  const hs=hit(g,sign*x,y,side);record(id+'/shoulder/'+side+'/'+sign*x+'/'+y,!!hs.length&&hs[0].role!=='avatar',{point:[sign*x,y],first:hs[0]});
 }
 if(id.startsWith('jindan'))for(const side of ['front','back'])for(const x of [-.05,0,.04])for(const y of [.94,.96,.97]){
  const hs=hit(g,x,y,side);record(id+'/hip/'+side+'/'+x+'/'+y,!!hs.length&&hs[0].role!=='avatar',{point:[x,y],first:hs[0]});
 }
 if(id==='qi_moqing')for(const x of [-.15,-.14,.14,.15])for(const y of [.90,.94,.97]){
  const hs=hit(g,x,y,'back').filter(h=>['mq_longback','mq_back_lining','mq_trouser_l','mq_trouser_r','mq_hip'].includes(h.id));
  record(id+'/back-layers/'+x+'/'+y,hs[0]?.id==='mq_longback',{point:[x,y],hits:hs.slice(0,8)});
 }
 if(id==='huashen_taixu')for(const x of [-.17,-.15,-.14,.14,.15,.17])for(const y of [.88,.92,.94,.96]){
  const hs=hit(g,x,y,'back').filter(h=>['t_back_skirt','t_trouser_l','t_trouser_r','t_hip'].includes(h.id));
  record(id+'/back-layers/'+x+'/'+y,hs[0]?.id==='t_back_skirt',{point:[x,y],hits:hs.slice(0,8)});
 }
 if(id==='yuanying_jiuse'){
  for(const side of [-1,1])for(const dx of [-.015,0,.015])for(const y of [.48,.55,.62,.69]){
   const x=side*.095+dx,tag=side<0?'l':'r';
   const hs=hit(g,x,y,'front').filter(h=>['g_greave_'+tag,'g_trouser_'+tag].includes(h.id));
   record(id+'/shin-layers/'+x+'/'+y,hs[0]?.id==='g_greave_'+tag,{point:[x,y],hits:hs.slice(0,8)});
  }
  for(const x of [-.20,-.18,-.14,0,.14,.18,.20])for(const y of [1.39,1.43,1.46]){
   const hs=hit(g,x,y,'front').filter(h=>['g_body_shell','g_body_inner','g_shoulder_inner'].includes(h.id));
   record(id+'/chest-layers/'+x+'/'+y,hs[0]?.id==='g_body_shell',{point:[x,y],hits:hs.slice(0,8)});
  }
  for(const x of [-.15,-.13,0,.13,.15])for(const y of [.981,.995]){
   const hs=hit(g,x,y,'front').filter(h=>['g_belt','g_body_shell','g_hip_shell','g_hip_inner'].includes(h.id));
   record(id+'/belt-layers/'+x+'/'+y,hs[0]?.id==='g_belt',{point:[x,y],hits:hs.slice(0,8)});
  }
 }
 E.dispose(g);
}
report.status=report.failed.length?'failed':'pass';fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,failed:report.failed}));if(report.failed.length)process.exitCode=1;
