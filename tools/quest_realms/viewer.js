/* 离线地图审阅器；所有场景与关卡关系从作者导出数据读取。 */
(async()=>{
'use strict';
const $=id=>document.getElementById(id),canvas=$('canvas'),stage=$('stage');
function localScript(file){return new Promise((resolve,reject)=>{const s=document.createElement('script');s.src=file;s.onload=resolve;s.onerror=()=>reject(new Error('本地数据加载失败：'+file));document.head.append(s)})}
const params=new URLSearchParams(location.search);
const index=location.protocol==='file:'?(await localScript('index.data.js'),window.QUEST_REALM_INDEX):await fetch('index.json',{cache:'no-store'}).then(r=>r.json());
const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:false,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio||1,1));renderer.outputEncoding=THREE.sRGBEncoding;
renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=.98;
renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;renderer.shadowMap.autoUpdate=false;renderer.localClippingEnabled=true;
const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(48,1,index.rules.near,index.rules.far);
const controls=new THREE.OrbitControls(camera,canvas);controls.enableDamping=true;controls.dampingFactor=.09;controls.maxPolarAngle=Math.PI*.495;controls.maxDistance=1100;controls.minDistance=1;
const hemi=new THREE.HemisphereLight(0xdce9e7,0x515848,.7);scene.add(hemi);
const sun=new THREE.DirectionalLight(0xffe9bc,1.2);sun.position.set(-160,300,-120);sun.castShadow=true;
Object.assign(sun.shadow.camera,{left:-230,right:230,top:230,bottom:-230,near:1,far:900});sun.shadow.mapSize.set(1024,1024);sun.shadow.bias=-.0003;sun.shadow.normalBias=.4;scene.add(sun);scene.add(sun.target);
const fill=new THREE.DirectionalLight(0xbfd6de,.32);fill.position.set(180,180,150);scene.add(fill);
const geometryUnit=new THREE.BoxGeometry(1,1,1).toNonIndexed(),UP=new THREE.Vector3(0,1,0);
let root,meshList=[],current,phase=0,cut=false,hideRoof=false,showRoutes=false,routeGroup,planLevel=-1,tour=null,busy=false,needsRender=true;
const textures={},cache=new Map(),pending=new Map();
const texLoader=new THREE.TextureLoader();
await Promise.all(Object.entries(index.palette).map(async([key,p])=>{
 const t=await new Promise((res,rej)=>texLoader.load(p.url,res,undefined,rej));
 t.wrapS=t.wrapT=THREE.RepeatWrapping;t.magFilter=THREE.NearestFilter;t.minFilter=THREE.NearestMipmapNearestFilter;t.encoding=THREE.sRGBEncoding;
 textures[key]=t;
}));

function condition(test,done){
 if(!test)return true;
 if(test.startsWith('not_all:'))return !test.slice(8).split(',').every(t=>done.has(t));
 return test.startsWith('!')?!done.has(test.slice(1)):done.has(test);
}
function reached(done){
 const found=new Set([current.spec.entry]);let changed=true;
 while(changed){changed=false;for(const e of current.spec.edges){if(!e.requires.every(t=>done.has(t)))continue;
  if(found.has(e.a)&&!found.has(e.b)){found.add(e.b);changed=true}if(found.has(e.b)&&!found.has(e.a)){found.add(e.a);changed=true}
 }}return found;
}
function material(key){
 const def=index.palette[key],tint=def.tint||new THREE.Color(def.color).lerp(new THREE.Color('#ffffff'),.3);
 return new THREE.MeshStandardMaterial({map:textures[key],color:tint,roughness:key==='water'?.3:key==='bronze'?.55:.93,metalness:['bronze','iron','jade'].includes(key)?.4:0,
  transparent:!!def.transparent,opacity:def.transparent||1,depthWrite:!def.transparent,vertexColors:true,
  emissive:def.emissive?def.color:0x000000,emissiveIntensity:def.emissive||0});
}
function faceKey(b,face){
 const axis=[0,0,1,1,2,2][face],positive=face%2===0,other=[0,1,2].filter(a=>a!==axis);
 return [axis,b[axis]+(positive?b[axis+3]:0),...other.flatMap(a=>[b[a],b[a+3]])].join(',');
}
async function geometry(model){
 const faceUse=new Map(),hidden=new Set(),data=model.boxes;
 // 精确重合的相邻面在这里消去，山体保留隧道和洞室的内部表面。
 for(let i=0;i<data.length;i++){
  const b=data[i];if(b[8]||index.palette[model.materials[b[6]]].transparent)continue;
  for(let f=0;f<6;f++){
   const key=faceKey(b,f),other=faceUse.get(key);
   if(other&&other[1]%2!==f%2){hidden.add(other[0]*6+other[1]);hidden.add(i*6+f);faceUse.delete(key)}else faceUse.set(key,[i,f]);
  }
 }
 const buckets=new Map(),pos=geometryUnit.attributes.position.array,norm=geometryUnit.attributes.normal.array;
 const tmp=new THREE.Color();
 for(let i=0;i<data.length;i++){
  const b=data[i],key=[b[6],b[7],b[8]].join('|');
  let g=buckets.get(key);if(!g){g={positions:[],normals:[],uvs:[],colors:[],mat:model.materials[b[6]],layer:b[7],condition:b[8]};buckets.set(key,g)}
  const factor=.94+((Math.floor(b[0])*13+Math.floor(b[2])*29)%13)*.008;
  tmp.setRGB(factor,factor,factor);
  for(let f=0;f<6;f++){
   if(hidden.has(i*6+f))continue;
   for(let j=f*6;j<f*6+6;j++){
    const nx=norm[j*3],ny=norm[j*3+1],nz=norm[j*3+2];
    const x=b[0]+(pos[j*3]+.5)*b[3],y=b[1]+(pos[j*3+1]+.5)*b[4],z=b[2]+(pos[j*3+2]+.5)*b[5];
    g.positions.push(x,y,z);g.normals.push(nx,ny,nz);
    g.uvs.push((nx?z:x)/2,(ny?z:y)/2);
    const shade=ny>0?1:ny<0?.65:nx<0?.86:nz<0?.92:.8;
    g.colors.push(tmp.r*shade,tmp.g*shade,tmp.b*shade);
   }
  }
  if(i%3500===0)await new Promise(r=>setTimeout(r,0));
 }
 const group=new THREE.Group(),list=[];
 for(const g of buckets.values()){
  const geo=new THREE.BufferGeometry();
  geo.setAttribute('position',new THREE.Float32BufferAttribute(g.positions,3));geo.setAttribute('normal',new THREE.Float32BufferAttribute(g.normals,3));geo.setAttribute('uv',new THREE.Float32BufferAttribute(g.uvs,2));geo.setAttribute('color',new THREE.Float32BufferAttribute(g.colors,3));
  geo.computeBoundingSphere();const mesh=new THREE.Mesh(geo,material(g.mat));mesh.castShadow=!['water','ground','geology','path'].includes(g.layer);mesh.receiveShadow=true;
  mesh.userData={layer:g.layer,condition:g.condition};group.add(mesh);list.push(mesh);
 }
 return {group,list,faces:meshFaces(list)};
}
function meshFaces(list){return list.reduce((n,m)=>n+m.geometry.attributes.position.count/6,0)}
function clear(){
 if(root){scene.remove(root);for(const m of meshList){m.geometry.dispose();m.material.dispose()}}
 if(routeGroup){scene.remove(routeGroup);routeGroup.traverse(o=>{o.geometry?.dispose();o.material?.dispose()})}
 $('labels').replaceChildren();stopTour();
}
function vec(p){return new THREE.Vector3(...p)}
function resize(){const w=stage.clientWidth,h=stage.clientHeight;if(w&&h){renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();needsRender=true}}
function setCamera(c,player=false){
 stopTour();hideRoof=false;cut=false;updateVisibility();
 controls.maxPolarAngle=player?Math.PI-.02:Math.PI*.495;
 camera.position.copy(vec(c.eye));controls.target.copy(vec(c.target));camera.fov=player?65:48;camera.updateProjectionMatrix();controls.update();
 $('view-name').textContent=c.name;$('hint').textContent=player?'玩家高度视角 · 山体与屋顶完整显示':'拖动旋转 · 滚轮缩放 · 右键平移';
}
function overview(){
 const c=current.spec.cameras[0];setCamera(c);if(c.cut){cut=true;updateVisibility()}
}
function nodeView(n){
 const route=current.routes.find(r=>r.a===n.id)||current.routes.find(r=>r.b===n.id);
 let p=route?.points[1]||[n.pos[0]+15,n.pos[1],n.pos[2]+15];
 if(route?.b===n.id)p=route.points.at(-2);
 setCamera({name:n.name,eye:[n.pos[0],n.pos[1]+index.rules.player_eye,n.pos[2]],target:[p[0],p[1]+2.2,p[2]]},true);
}
function updateVisibility(){
 if(!current)return;const done=new Set(current.spec.phases[phase].done);
 const frontPlane=new THREE.Plane(new THREE.Vector3(0,0,1),-current.spec.size[2]*.48);
 for(const m of meshList){m.visible=condition(m.userData.condition,done)&&!(hideRoof&&['roof','ceiling'].includes(m.userData.layer));
  m.material.clippingPlanes=cut?[frontPlane]:[];
 }
 if(routeGroup)routeGroup.visible=showRoutes;
 renderer.shadowMap.needsUpdate=true;needsRender=true;
 $('section').classList.toggle('active',cut);$('roof').classList.toggle('active',hideRoof);$('routes').classList.toggle('active',showRoutes);
}
function buildRoutes(){
 if(routeGroup){scene.remove(routeGroup);routeGroup.traverse(o=>{o.geometry?.dispose();o.material?.dispose()})}
 routeGroup=new THREE.Group();const done=new Set(current.spec.phases[phase].done);
 for(const e of current.routes){
  const open=e.requires.every(t=>done.has(t));
  const line=new THREE.Line(new THREE.BufferGeometry().setFromPoints(e.points.map(p=>vec([p[0],p[1]+.55,p[2]]))),open?new THREE.LineBasicMaterial({color:e.kind==='bridge'?0x82d4d0:0xf5cf88,depthTest:false}):new THREE.LineDashedMaterial({color:0xad928a,dashSize:3,gapSize:2,depthTest:false}));line.renderOrder=20;line.computeLineDistances();routeGroup.add(line);
 }
 scene.add(routeGroup);routeGroup.visible=showRoutes;
 needsRender=true;
}
function setPhase(i){
 phase=i;const p=current.spec.phases[i],done=new Set(p.done),accessible=reached(done);
 for(const b of $('phases').children)b.classList.toggle('active',Number(b.dataset.phase)===i);
 $('phase-note').textContent=p.name+'。当前可达地点 '+accessible.size+' 处。此处切换的是地图设计状态。';
 for(const b of $('nodes').children)b.classList.toggle('locked',!accessible.has(b.dataset.id));
 buildRoutes();updateVisibility();drawPlan();window.__realmState={id:current.spec.id,phase:i,reachable:[...accessible],cut,hideRoof};
}
function makePlanLevels(){
 const heights=current.spec.nodes.map(n=>n.pos[1]);const min=Math.min(...heights),max=Math.max(...heights),step=(max-min+1)/3;
 current.planLevels=[[-1000,1000,'全部楼层'],[min,min+step,'下层'],[min+step,min+step*2,'中层'],[min+step*2,max+1,'上层']];
 $('plan-levels').replaceChildren();for(let i=0;i<4;i++){const b=document.createElement('button');b.textContent=current.planLevels[i][2];b.classList.toggle('active',i===0);b.onclick=()=>{planLevel=i-1;for(const x of b.parentElement.children)x.classList.toggle('active',x===b);drawPlan()};$('plan-levels').append(b)}
}
function escape(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function drawPlan(){
 if(!current)return;const [w,,d]=current.spec.size,done=new Set(current.spec.phases[phase].done),level=current.planLevels?.[planLevel+1]||[-1000,1000];
 let svg='';
 for(const s of current.spec.structures){
  const [x,y,z]=s.pos,[ww,h,dd]=s.size;if(y>=level[1]||y+h<level[0])continue;
  svg+=`<rect x="${x-ww/2}" y="${z-dd/2}" width="${ww}" height="${dd}" fill="var(--accent)" opacity=".23" stroke="var(--muted)" stroke-width=".4"/>`;
 }
 for(const e of current.routes){
  if(!e.points.some(p=>p[1]>=level[0]&&p[1]<level[1]))continue;
  const open=e.requires.every(t=>done.has(t)),color=e.kind==='bridge'?'var(--info)':'var(--warning)';
  svg+=`<polyline points="${e.points.map(p=>p[0]+','+p[2]).join(' ')}" fill="none" stroke="${color}" stroke-width="1.8" opacity="${open?.9:.35}" ${open?'':'stroke-dasharray="4 3"'}/>`;
 }
 for(const n of current.spec.nodes){
  if(n.pos[1]<level[0]||n.pos[1]>=level[1])continue;
  svg+=`<g data-node="${n.id}" style="cursor:pointer"><circle cx="${n.pos[0]}" cy="${n.pos[2]}" r="2.6" fill="var(--primary)"/><text x="${n.pos[0]+4}" y="${n.pos[2]-3}" fill="var(--foreground)" stroke="var(--secondary)" stroke-width="1.4" paint-order="stroke" font-size="4.4">${escape(n.name)}</text></g>`;
 }
 $('map-plan').innerHTML=`<svg viewBox="0 0 ${w} ${d}" role="img" aria-label="${escape(current.spec.name)}的分层路线">${svg}</svg>`;
 for(const el of $('map-plan').querySelectorAll('[data-node]'))el.onclick=()=>{nodeView(current.spec.nodes.find(n=>n.id===el.dataset.node));$('plan-panel').hidden=true};
}
async function select(id){
 if(busy)return;busy=true;$('loading').style.display='grid';$('loading').textContent='正在构造地形、建筑与内部空间';
 try{
  const m=cache.get(id)||(location.protocol==='file:'?(await localScript(id+'.data.js'),window.QUEST_REALM_MAPS[id]):await fetch(id+'.json',{cache:'no-store'}).then(r=>{if(!r.ok)throw new Error('地图数据加载失败');return r.json()}));cache.set(id,m);clear();current=m;
  const built=await geometry(m);root=built.group;meshList=built.list;scene.add(root);
  const a=m.spec.atmosphere;scene.background=new THREE.Color(a.sky);scene.fog=new THREE.FogExp2(a.fog,.00125);sun.color.set(a.sun);hemi.color.set(a.ambient);
  sun.target.position.set(m.spec.size[0]/2,20,m.spec.size[2]/2);sun.position.set(m.spec.size[0]/2-160,300,m.spec.size[2]/2-120);sun.target.updateMatrixWorld();
  $('name').textContent=m.spec.name;$('subtitle').textContent=m.spec.subtitle+' · '+m.spec.size[0]+' × '+m.spec.size[2]+' 格';$('brief').textContent=m.spec.brief;
  for(const b of $('library').children)b.classList.toggle('active',b.dataset.id===id);
  $('composition').replaceChildren();for(const text of m.spec.composition){const li=document.createElement('li');li.textContent=text;$('composition').append(li)}
  $('metrics').innerHTML=[`${m.checks.nodes} 处探索地点`,`${m.checks.cycles} 处路线回环`,`${m.checks.optional_areas} 处可选支路`].map(t=>'<span>'+t+'</span>').join('');
  $('nodes').replaceChildren();for(const n of m.spec.nodes){const b=document.createElement('button');b.textContent=n.name;b.dataset.id=n.id;if(n.role==='optional')b.classList.add('optional');b.title=n.text||n.name;b.onclick=()=>nodeView(n);$('nodes').append(b)}
  $('cameras').replaceChildren();m.spec.cameras.forEach((c,i)=>{const b=document.createElement('button');b.textContent=c.name;b.onclick=()=>{if(i===0)overview();else setCamera(c,true)};$('cameras').append(b)});
  $('phases').replaceChildren();m.spec.phases.forEach((p,i)=>{const b=document.createElement('button');b.dataset.phase=i;b.innerHTML=`<b>阶段 ${i+1}</b>${escape(p.name)}`;b.onclick=()=>setPhase(i);$('phases').append(b)});
  planLevel=-1;makePlanLevels();setPhase(0);overview();resize();$('loading').style.display='none';
  window.__realmReady=true;window.__realmCheck={id,boxes:m.boxes.length,faces:built.faces,materials:meshList.length};
  params.set('map',id);if(location.protocol!=='file:')history.replaceState(null,'','?'+params.toString());
 }catch(e){$('loading').textContent='预览加载失败：'+e.message;console.error(e)}finally{busy=false}
}
function shortest(start,end,done){
 const q=[start],seen=new Map([[start,null]]);
 while(q.length){const id=q.shift();if(id===end)break;for(const e of current.routes){if(!e.requires.every(t=>done.has(t)))continue;const other=e.a===id?e.b:e.b===id?e.a:null;if(other&&!seen.has(other)){seen.set(other,{previous:id,edge:e});q.push(other)}}}
 if(!seen.has(end))return [];const result=[];let p=end;while(seen.get(p)){const hop=seen.get(p),points=hop.edge.a===hop.previous?hop.edge.points:[...hop.edge.points].reverse();result.unshift(points);p=hop.previous}return result.flat();
}
function startTour(){
 const spec=current.spec,done=new Set(),points=[];let node=spec.entry;
 for(const a of spec.actions){points.push(...shortest(node,a.node,done));done.add(a.id);node=a.node}points.push(...shortest(node,spec.entry,done));
 if(points.length<2)return;setPhase(spec.phases.length-1);cut=false;hideRoof=false;updateVisibility();controls.enabled=false;
 tour={points:points.map(p=>vec([p[0],p[1]+index.rules.player_eye,p[2]])),segment:0,t:0};camera.fov=65;camera.updateProjectionMatrix();$('stop').hidden=false;$('tour').hidden=true;$('view-name').textContent='沿地图主路漫游';$('hint').textContent='镜头按作者路线移动 · 可随时停止';
}
function stopTour(){if(tour){const direction=new THREE.Vector3();camera.getWorldDirection(direction);controls.target.copy(camera.position).addScaledVector(direction,12);controls.maxPolarAngle=Math.PI-.02;$('view-name').textContent='路线漫游已停止';$('hint').textContent='拖动旋转 · 滚轮缩放 · 右键平移'}tour=null;controls.enabled=true;needsRender=true;$('stop').hidden=true;$('tour').hidden=false}
function stepTour(dt){
 if(!tour)return;let a=tour.points[tour.segment],b=tour.points[tour.segment+1];if(!b){stopTour();return}
 const len=a.distanceTo(b);tour.t+=dt*8/Math.max(.1,len);
 if(tour.t>=1){tour.t=0;tour.segment++;return}
 camera.position.lerpVectors(a,b,tour.t);let target=b.clone();if(len<2&&tour.points[tour.segment+2])target=tour.points[tour.segment+2].clone();target.y+=.1;camera.lookAt(target);
}
$('overview').onclick=overview;$('section').onclick=()=>{cut=!cut;updateVisibility()};$('roof').onclick=()=>{hideRoof=!hideRoof;updateVisibility()};$('routes').onclick=()=>{showRoutes=!showRoutes;updateVisibility()};$('plan').onclick=()=>{$('plan-panel').hidden=!$('plan-panel').hidden;drawPlan()};$('tour').onclick=startTour;$('stop').onclick=stopTour;
$('fullscreen').onclick=()=>{if(document.fullscreenElement)document.exitFullscreen();else stage.requestFullscreen?.()};
for(const map of index.maps){const b=document.createElement('button');b.dataset.id=map.id;b.innerHTML='<strong>'+escape(map.name)+'</strong><small>'+escape(map.subtitle)+'</small>';b.onclick=()=>select(map.id);$('library').append(b)}
new ResizeObserver(resize).observe(stage);document.addEventListener('fullscreenchange',resize);
controls.addEventListener('change',()=>{needsRender=true});
let last=performance.now(),lastDraw=0;function animate(now){requestAnimationFrame(animate);const dt=Math.min(.1,(now-last)/1000);last=now;if(tour){stepTour(dt);needsRender=true}else controls.update();if(needsRender&&root&&now-lastDraw>1000/12){lastDraw=now;renderer.render(scene,camera);needsRender=false}}requestAnimationFrame(animate);
if(params.has('capture'))document.body.classList.add('capture');
window.REALM_REVIEW={select,phase:setPhase,overview,node:id=>nodeView(current.spec.nodes.find(n=>n.id===id)),camera:i=>setCamera(current.spec.cameras[i],i>0),
 state:()=>({id:current?.spec.id,phase,cut,hideRoof,busy,touring:!!tour}),model:()=>current,
 audit:()=>({id:current.spec.id,phase,boxes:current.boxes.length,camera:camera.position.toArray(),target:controls.target.toArray(),visibleMeshes:meshList.filter(m=>m.visible).length,
  conditions:meshList.filter(m=>m.visible&&m.userData.condition).map(m=>m.userData.condition),
  reachable:[...reached(new Set(current.spec.phases[phase].done))],
  viewport:{width:document.documentElement.clientWidth,scrollWidth:document.documentElement.scrollWidth},
  water:current.boxes.filter(b=>b[7]==='water'&&b[8]&&condition(b[8],new Set(current.spec.phases[phase].done))).map(b=>({height:b[1]+b[4],condition:b[8]}))})};
await select(index.maps.some(m=>m.id===params.get('map'))?params.get('map'):'water_shrine');
})().catch(e=>{document.getElementById('loading').textContent='预览初始化失败：'+e.message;console.error(e)});
