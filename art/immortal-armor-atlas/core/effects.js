/* Bounded preview effects. No geometry exports, network, timers or game state. */
(function(global){
'use strict';
const T=global.THREE,P=1/16,TAU=Math.PI*2;
const finite=(x,name)=>{if(typeof x!=='number'||!Number.isFinite(x))throw Error(name+' must be finite');};
const range=(x,a,b,name)=>{finite(x,name);if(x<a||x>b)throw Error(name+' out of range');};
function vector(a,name){if(!Array.isArray(a)||a.length!==3)throw Error(name+' must be xyz');a.forEach(v=>range(v,-64,64,name));}
function color(c){if(!/^#[0-9a-f]{6}$/i.test(c))throw Error('invalid effect color');}
function validate(config,design){
 if(!config)return;range(config.level,1,5,'level');if(config.level!==design.level)throw Error('effect realm mismatch');
 const ids=new Set(design.components.map(c=>c.id)),moving=new Set();
 const targets=a=>{if(!Array.isArray(a)||!a.length)throw Error('empty targets');for(const id of a)if(!ids.has(id))throw Error('unknown effect target '+id);};
 for(const name of ['flows','motions','glows','arcs'])if(!Array.isArray(config[name]))throw Error('missing '+name);
 let particles=0;
 for(const f of config.flows){if(!['head','chest','legs','boots'].includes(f.role))throw Error('invalid effect role');if(!Array.isArray(f.path)||f.path.length<2||f.path.length>24)throw Error('invalid flow path');f.path.forEach(p=>vector(p,'path'));range(f.count,1,16,'count');if(!Number.isInteger(f.count))throw Error('fractional count');particles+=f.count;range(f.size,.05,.7,'size');range(f.period,2,30,'period');range(f.opacity,0,.85,'opacity');if(f.trail!==undefined)range(f.trail,0,.15,'trail');color(f.color);if(!['spark','star','seal','leaf'].includes(f.shape))throw Error('unknown particle shape');}
 if(particles>[6,12,18,26,32][config.level-1])throw Error('particle budget exceeded');
 if(config.arcs.length>3||config.motions.length>3||config.glows.length>4)throw Error('effect layer budget exceeded');
 for(const m of config.motions){targets(m.targets);for(const id of m.targets){if(moving.has(id))throw Error('overlapping motion target '+id);moving.add(id);}vector(m.pivot,'pivot');vector(m.lift,'lift');if(m.lift.some(v=>Math.abs(v)>.75))throw Error('motion lift too large');if(!['x','y','z'].includes(m.axis))throw Error('invalid motion axis');range(m.angle,-8,8,'angle');range(m.period,4,30,'period');}
 for(const g of config.glows){targets(g.targets);color(g.color);range(g.min,0,.5,'glow min');range(g.max,g.min,.5,'glow max');range(g.period,3,30,'period');}
 for(const a of config.arcs){if(!['head','chest','legs','boots'].includes(a.role))throw Error('invalid arc role');if(!Array.isArray(a.points)||a.points.length<2||a.points.length>20)throw Error('invalid arc');a.points.forEach(p=>vector(p,'arc'));color(a.color);range(a.period,3,30,'arc period');range(a.duration,.2,a.period*.5,'arc duration');range(a.phase,0,1,'arc phase');range(a.opacity,0,.85,'arc opacity');}
 return {particles,motions:config.motions.length,arcs:config.arcs.length};
}
function shape(kind,size){
 const s=size*P;if(kind==='spark')return new T.BoxGeometry(s,s,s);
 if(kind==='seal'){
  const frame=new T.Shape();frame.moveTo(-s,-s);frame.lineTo(s,-s);frame.lineTo(s,s);frame.lineTo(-s,s);frame.closePath();
  const hole=new T.Path();hole.moveTo(-s*.63,-s*.63);hole.lineTo(-s*.63,s*.63);hole.lineTo(s*.63,s*.63);hole.lineTo(s*.63,-s*.63);hole.closePath();frame.holes.push(hole);
  const mark=new T.Shape();mark.moveTo(-s*.13,-s*.45);mark.lineTo(s*.13,-s*.45);mark.lineTo(s*.13,s*.12);mark.lineTo(s*.45,s*.12);mark.lineTo(s*.45,s*.35);mark.lineTo(-s*.13,s*.35);mark.closePath();
  return new T.ShapeGeometry([frame,mark]);
 }
 const p=kind==='star'?[[0,1],[.23,.23],[1,0],[.23,-.23],[0,-1],[-.23,-.23],[-1,0],[-.23,.23]]:
         kind==='leaf'?[[0,1],[.6,.25],[.4,-.6],[0,-1],[-.4,-.6],[-.6,.25]]:[[0,1],[1,0],[0,-1],[-1,0]];
 const g=new T.Shape();p.forEach(([x,y],i)=>i?g.lineTo(x*s,y*s):g.moveTo(x*s,y*s));g.closePath();return new T.ShapeGeometry(g);
}
function pathPoint(path,t,out){
 const lengths=[];let total=0;for(let i=1;i<path.length;i++){const d=path[i].distanceTo(path[i-1]);lengths.push(d);total+=d;}
 let d=t*total;for(let i=0;i<lengths.length;i++){if(d<=lengths[i]||i===lengths.length-1)return out.copy(path[i]).lerp(path[i+1],lengths[i]?d/lengths[i]:0);d-=lengths[i];}
 return out.copy(path[0]);
}
function create(root,config,mode='all'){
 const group=new T.Group();group.name='衣甲灵效';group.userData.kind='armor-effects';
 const originalOrder=root.children.slice(),motions=[],glows=[],flows=[],arcs=[],ownedMaterials=new Set(),ownedGeometries=new Set();
 let disposed=false,time=0;const roleOK=r=>mode==='all'||mode===r;
 const nodes=new Map(root.children.filter(n=>n.userData.componentId).map(n=>[n.userData.componentId,n]));
 const material=(c,opacity)=>{const m=new T.MeshBasicMaterial({color:new T.Color(c).convertSRGBToLinear(),transparent:true,opacity,depthWrite:false,depthTest:true,side:T.DoubleSide,toneMapped:false});ownedMaterials.add(m);return m;};
 if(config&&mode!=='ingot'){
  for(const m of config.motions){const selected=m.targets.map(id=>nodes.get(id)).filter(Boolean);if(!selected.length)continue;const pivot=new T.Group();pivot.position.fromArray(m.pivot.map(v=>v*P));root.add(pivot);const saved=selected.map(n=>({n,position:n.position.clone(),quaternion:n.quaternion.clone(),scale:n.scale.clone()}));for(const n of selected){root.remove(n);pivot.add(n);n.position.sub(pivot.position);}motions.push({spec:m,pivot,saved,base:pivot.position.clone()});}
  for(const g of config.glows){const entries=[];for(const id of g.targets){const node=nodes.get(id);if(!node)continue;node.traverse(mesh=>{if(!mesh.isMesh||Array.isArray(mesh.material))return;const original=mesh.material,clone=original.clone();clone.emissive=new T.Color(g.color).convertSRGBToLinear();clone.emissiveIntensity=g.min;mesh.material=clone;ownedMaterials.add(clone);entries.push({mesh,original,clone});});}if(entries.length)glows.push({spec:g,entries});}
  for(const f of config.flows){if(!roleOK(f.role))continue;const geometry=shape(f.shape,f.size);ownedGeometries.add(geometry);const nodes=[],tails=[];for(let i=0;i<f.count;i++){
   const mesh=new T.Mesh(geometry,material(f.color,0));mesh.castShadow=false;mesh.receiveShadow=false;mesh.renderOrder=1;group.add(mesh);nodes.push(mesh);
   if(f.trail){const geom=new T.BufferGeometry();geom.setAttribute('position',new T.Float32BufferAttribute(new Float32Array(7*3),3));const colors=[],c=new T.Color(f.color).convertSRGBToLinear();for(let j=0;j<7;j++)colors.push(c.r*j/6,c.g*j/6,c.b*j/6);geom.setAttribute('color',new T.Float32BufferAttribute(colors,3));const mat=new T.LineBasicMaterial({vertexColors:true,transparent:true,opacity:0,depthWrite:false,depthTest:true,blending:T.AdditiveBlending,toneMapped:false});const tail=new T.Line(geom,mat);tail.frustumCulled=false;ownedMaterials.add(mat);ownedGeometries.add(geom);group.add(tail);tails.push(tail);}
  }flows.push({spec:f,nodes,tails,path:f.path.map(p=>new T.Vector3(...p.map(v=>v*P)))});}
  for(const a of config.arcs){if(!roleOK(a.role))continue;const geometry=new T.BufferGeometry().setFromPoints(a.points.map(p=>new T.Vector3(...p.map(v=>v*P))));ownedGeometries.add(geometry);const mat=new T.LineBasicMaterial({color:new T.Color(a.color).convertSRGBToLinear(),transparent:true,opacity:0,depthWrite:false,depthTest:true,toneMapped:false});ownedMaterials.add(mat);const line=new T.Line(geometry,mat);line.renderOrder=1;group.add(line);arcs.push({spec:a,line});}
 }
 const stats=()=>({particles:flows.reduce((n,f)=>n+f.nodes.length,0),trails:flows.reduce((n,f)=>n+f.tails.length,0),motions:motions.length,glows:glows.length,arcs:arcs.length,materials:ownedMaterials.size,geometries:ownedGeometries.size,time,disposed});
 function update(seconds){if(disposed)return;finite(seconds,'effect time');time=seconds;
  motions.forEach(({spec,pivot,base},i)=>{const wave=Math.sin(seconds/spec.period*TAU+i*.83);pivot.rotation[spec.axis]=spec.angle*Math.PI/180*wave;pivot.position.copy(base).addScaledVector(new T.Vector3(...spec.lift.map(v=>v*P)),wave);});
  glows.forEach(({spec,entries},i)=>{const phase=.5-.5*Math.cos(seconds/spec.period*TAU+i*.7),strength=spec.min+(spec.max-spec.min)*phase;entries.forEach(e=>e.clone.emissiveIntensity=strength);});
  flows.forEach(({spec,nodes,tails,path},j)=>nodes.forEach((mesh,i)=>{const p=((seconds/spec.period+i/nodes.length+j*.17)%1+1)%1;pathPoint(path,p,mesh.position);mesh.material.opacity=spec.opacity*Math.sin(p*Math.PI)**1.3;mesh.rotation.z=spec.shape==='leaf'?.4*Math.sin(p*TAU+i):0;mesh.rotation.y=spec.shape==='spark'?seconds*.2:0;mesh.scale.setScalar(.65+.35*Math.sin(p*Math.PI));
   if(tails[i]){const tail=tails[i],pos=tail.geometry.attributes.position,v=new T.Vector3();for(let k=0;k<7;k++){pathPoint(path,Math.max(0,p-spec.trail*(1-k/6)),v);pos.setXYZ(k,v.x,v.y,v.z);}pos.needsUpdate=true;tail.material.opacity=mesh.material.opacity*.72;}
  }));
  arcs.forEach(({spec,line})=>{const phase=((seconds/spec.period-spec.phase)%1+1)%1*spec.period;line.material.opacity=phase<spec.duration?spec.opacity*Math.sin(phase/spec.duration*Math.PI)**2:0;});
  root.updateMatrixWorld(true);group.updateMatrixWorld(true);
 }
 function dispose(){if(disposed)return;disposed=true;for(const {entries}of glows)for(const e of entries)e.mesh.material=e.original;
  for(const {pivot,saved}of motions){for(const {n,position,quaternion,scale}of saved){pivot.remove(n);root.add(n);n.position.copy(position);n.quaternion.copy(quaternion);n.scale.copy(scale);}root.remove(pivot);pivot.clear();}
  const order=new Map(originalOrder.map((n,i)=>[n,i]));root.children.sort((a,b)=>(order.get(a)??999)-(order.get(b)??999));
  ownedGeometries.forEach(g=>g.dispose());ownedMaterials.forEach(m=>m.dispose());if(group.parent)group.parent.remove(group);group.clear();root.updateMatrixWorld(true);
 }
 update(0);return {group,update,dispose,stats};
}
const api={validate,create};global.ArmorEffects=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(globalThis);
