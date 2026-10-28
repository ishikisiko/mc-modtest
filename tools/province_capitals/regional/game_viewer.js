/* 所有几何来自 owning generator；本文件只负责交互显示，不运行检查。 */
'use strict';
const $=id=>document.getElementById(id);
const canvas=$('city-canvas'),stage=$('stage');
const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:false,powerPreference:'high-performance'});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.5));
renderer.outputColorSpace=THREE.SRGBColorSpace;
renderer.toneMapping=THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure=1.15;
renderer.shadowMap.enabled=true;
renderer.shadowMap.type=THREE.PCFSoftShadowMap;
const world=new THREE.Scene();
world.background=new THREE.Color('#d3dfdc');
world.fog=new THREE.Fog('#d3dfdc',470,1050);
const camera=new THREE.PerspectiveCamera(43,1,0.12,1600);
const hemi=new THREE.HemisphereLight('#eef4e2','#677d69',2.15);
world.add(hemi);
const sun=new THREE.DirectionalLight('#fff0d3',3.25);
sun.position.set(-80,260,130);
sun.target.position.set(112,15,136);
sun.castShadow=true;
sun.shadow.mapSize.set(1536,1536);
Object.assign(sun.shadow.camera,{left:-210,right:210,top:210,bottom:-210,near:1,far:650});
sun.shadow.bias=-0.00035;
sun.shadow.normalBias=0.12;
world.add(sun,sun.target);
const ground=new THREE.Mesh(new THREE.PlaneGeometry(3000,3000),new THREE.MeshStandardMaterial({color:'#b8c9bb',roughness:1}));
ground.rotation.x=-Math.PI/2;ground.position.set(112,-0.4,136);ground.receiveShadow=true;world.add(ground);
// 外海背景与天空、灯光相同，是展示情境；模型本体闭包络由 manifest 单独给出。
const seaBackdrop=new THREE.Mesh(new THREE.PlaneGeometry(1800,2200),new THREE.MeshStandardMaterial({color:'#3d8c98',roughness:0.83}));
seaBackdrop.rotation.x=-Math.PI/2;seaBackdrop.position.set(1124,5.78,136);seaBackdrop.receiveShadow=true;world.add(seaBackdrop);
const boxGeometry=new THREE.BoxGeometry(1,1,1);
const dummy=new THREE.Object3D();
const raycaster=new THREE.Raycaster();
const pointer=new THREE.Vector2();
const clock=new THREE.Clock();
let sceneRoot=null,current=null,selected=null,labelNodes=[],pickables=[],materials=[],textures=[];
const state={city:0,mode:'panorama',target:new THREE.Vector3(),radius:350,theta:0.7,phi:0.7,yaw:0,pitch:0,
  labels:true,roofs:true,envelope:false,scale:true,drag:null,keys:new Set(),moved:false};

function textureFor(key,color){
  if(!['wall','stone','wood','wood_light','roof','roof_edge','ground','earth','road'].includes(key))return null;
  const c=document.createElement('canvas');c.width=c.height=32;
  const ctx=c.getContext('2d');ctx.fillStyle=color;ctx.fillRect(0,0,32,32);
  let seed=13+key.length*71;
  const rand=()=>{seed=(seed*1664525+1013904223)>>>0;return seed/4294967296;};
  for(let i=0;i<90;i++){
    const light=rand()>0.5;
    ctx.fillStyle=light?'rgba(255,255,240,.07)':'rgba(15,28,22,.06)';
    const x=Math.floor(rand()*16)*2,y=Math.floor(rand()*16)*2;
    ctx.fillRect(x,y,2+Math.floor(rand()*3),1+Math.floor(rand()*2));
  }
  if(key==='roof'||key==='roof_edge'){
    ctx.fillStyle='rgba(15,25,25,.14)';for(let x=0;x<32;x+=8)ctx.fillRect(x,0,1,32);
    ctx.fillStyle='rgba(225,235,220,.08)';for(let y=0;y<32;y+=16)ctx.fillRect(0,y,32,1);
  }
  if(key==='stone'||key==='road'){
    ctx.fillStyle='rgba(20,30,20,.10)';ctx.fillRect(0,15,32,1);ctx.fillRect(0,31,32,1);
    ctx.fillRect(16,0,1,16);ctx.fillRect(7,16,1,16);
  }
  if(key==='wood'||key==='wood_light'){
    ctx.fillStyle='rgba(25,18,10,.12)';for(let x=0;x<32;x+=8)ctx.fillRect(x,0,1,32);
  }
  const tex=new THREE.CanvasTexture(c);tex.colorSpace=THREE.SRGBColorSpace;
  tex.magFilter=THREE.NearestFilter;tex.minFilter=THREE.NearestFilter;
  tex.wrapS=tex.wrapT=THREE.RepeatWrapping;textures.push(tex);return tex;
}

function makeMaterials(data){
  const map={};
  for(const [key,value] of Object.entries(data)){
    const texture=textureFor(key,value.color);
    map[key]=new THREE.MeshStandardMaterial({color:texture?'#ffffff':value.color,map:texture,
      roughness:key==='water'?0.70:0.98,metalness:0,flatShading:['roof','roof_edge','stone','wood'].includes(key),side:THREE.DoubleSide});
    if(key==='water'){map[key].transparent=true;map[key].opacity=0.92;map[key].depthWrite=false;}
    materials.push(map[key]);
  }
  return map;
}

function keyFor(item){return [item.layer,item.m,item.detail||'detailed'].join('|');}

function unpackGame(data){
  const bytes=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
  if(data.packed_boxes){
    const pack=data.packed_boxes,raw=bytes(pack.data),view=new DataView(raw.buffer);
    data.boxes=[];const n=pack.count,previous=[0,0,0,0,0,0];
    for(let i=0;i<n;i++){
      const v=[];for(let j=0;j<6;j++){previous[j]+=view.getInt16((j*n+i)*2,true);v.push(previous[j]/32);}
      data.boxes.push({p:v.slice(0,3),s:v.slice(3),q:0,m:pack.materials[view.getUint8(12*n+i)],layer:pack.layers[view.getUint8(13*n+i)],tag:pack.tags[view.getUint16(14*n+i*2,true)],detail:'detailed'});
    }
    delete data.packed_boxes;
  }
  if(data.voxel_terrain){
    const field=data.voxel_terrain,raw=bytes(field.data),view=new DataView(raw.buffer),w=field.width,d=field.depth;
    const vertices=[],faces=[],sides=[],sideFaces=[];
    const h=(x,z)=>view.getInt16((z*w+x)*2,true)+1;
    const quad=(points,side=false)=>{const vs=side?sides:vertices,fs=side?sideFaces:faces,n=vs.length;vs.push(...points);fs.push([n,n+1,n+2],[n,n+2,n+3]);};
    for(let z=0;z<d;z++){
      for(let x=0;x<w;){const y=h(x,z);let end=x+1;while(end<w&&h(end,z)===y)end++;quad([[x,y,z],[x,y,z+1],[end,y,z+1],[end,y,z]]);x=end;}
      for(let x=0;x<w;x++){
        const y=h(x,z);
        for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]){
          const nx=x+dx,nz=z+dz,ny=nx>=0&&nx<w&&nz>=0&&nz<d?h(nx,nz):0;if(ny>=y)continue;
          if(dx){const xx=x+(dx>0?1:0);quad([[xx,ny,z],[xx,y,z],[xx,y,z+1],[xx,ny,z+1]],true);}
          else{const zz=z+(dz>0?1:0);quad([[x,ny,zz],[x+1,ny,zz],[x+1,y,zz],[x,y,zz]],true);}
        }
      }
    }
    data.meshes.push({v:vertices,f:faces,m:'ground',layer:'terrain',tag:'heightfield',detail:'detailed'},
                     {v:sides,f:sideFaces,m:'earth',layer:'terrain',tag:'heightfield_sides',detail:'detailed'});
    delete data.voxel_terrain;
  }
}

function loadGeometry(data){
  unpackGame(data);
  if(data.terrain_mesh_from_heightfield){
    const field=data.terrain_heightfield,[nx,nz]=field.size,step=field.resolution,vertices=[],faces=[];
    for(let z=0;z<nz;z++)for(let x=0;x<nx;x++)vertices.push([x*step,field.heights[z*nx+x],z*step]);
    for(let z=0;z<nz-1;z++)for(let x=0;x<nx-1;x++){
      const a=z*nx+x;faces.push([a,a+nx,a+1],[a+1,a+nx,a+nx+1]);
    }
    data.meshes.unshift({v:vertices,f:faces,m:'ground',layer:'terrain',tag:'continuous_terrain',detail:'detailed'});
    delete data.terrain_mesh_from_heightfield;
  }
  if(sceneRoot){
    world.remove(sceneRoot);
    sceneRoot.traverse(o=>{if(o.geometry&&o.geometry!==boxGeometry)o.geometry.dispose();});
    for(const m of materials)m.dispose();for(const t of textures)t.dispose();
  }
  materials=[];textures=[];pickables=[];
  sceneRoot=new THREE.Group();world.add(sceneRoot);
  const mat=makeMaterials(data.materials),boxGroups=new Map(),meshGroups=new Map();
  for(const item of data.boxes){const key=keyFor(item);if(!boxGroups.has(key))boxGroups.set(key,[]);boxGroups.get(key).push(item);}
  for(const [key,items] of boxGroups){
    const first=items[0],mesh=new THREE.InstancedMesh(boxGeometry,mat[first.m],items.length);
    mesh.userData={layer:first.layer,detail:first.detail,tags:items.map(i=>i.tag),base:mat[first.m]};
    for(let i=0;i<items.length;i++){
      const item=items[i];dummy.position.fromArray(item.p);dummy.scale.fromArray(item.s);
      dummy.rotation.set(0,-item.q*Math.PI/2,0);dummy.updateMatrix();mesh.setMatrixAt(i,dummy.matrix);
    }
    mesh.instanceMatrix.needsUpdate=true;mesh.computeBoundingSphere();
    mesh.castShadow=!['water','scale','road'].includes(first.layer);mesh.receiveShadow=true;
    if(first.layer==='water')mesh.renderOrder=1;
    sceneRoot.add(mesh);pickables.push(mesh);
  }
  for(const item of data.meshes){
    const key=keyFor(item);
    if(!meshGroups.has(key))meshGroups.set(key,{first:item,positions:[],indices:[],tags:[]});
    const group=meshGroups.get(key),offset=group.positions.length/3;
    for(const v of item.v)group.positions.push(...v);
    for(const face of item.f){group.indices.push(face[0]+offset,face[1]+offset,face[2]+offset);group.tags.push(item.tag);}
  }
  for(const group of meshGroups.values()){
    const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(group.positions,3));
    geometry.setIndex(group.indices);geometry.computeVertexNormals();geometry.computeBoundingSphere();
    const mesh=new THREE.Mesh(geometry,mat[group.first.m]);
    mesh.userData={layer:group.first.layer,detail:group.first.detail,tags:group.tags,base:mat[group.first.m]};
    mesh.castShadow=group.first.layer==='roof';mesh.receiveShadow=true;
    if(group.first.layer==='water')mesh.renderOrder=1;
    sceneRoot.add(mesh);pickables.push(mesh);
  }
  const envelopeMat=new THREE.MeshStandardMaterial({color:'#9fa99f',roughness:1,side:THREE.DoubleSide});materials.push(envelopeMat);
  sceneRoot.userData.envelopeMat=envelopeMat;
  applyLayers();
}

function applyLayers(){
  if(!sceneRoot)return;
  sceneRoot.traverse(o=>{
    if(!o.isMesh)return;
    o.visible=(o.userData.layer!=='roof'||state.roofs)&&(o.userData.layer!=='scale'||state.scale);
    o.material=state.envelope&&o.userData.detail==='envelope'?sceneRoot.userData.envelopeMat:o.userData.base;
  });
}

function setView(name){
  state.mode=name;
  const preset=current.cameras[name];
  camera.position.fromArray(preset.eye);state.target.fromArray(preset.target);
  const delta=camera.position.clone().sub(state.target);
  state.radius=delta.length();state.theta=Math.atan2(delta.x,delta.z);state.phi=Math.acos(delta.y/state.radius);
  const forward=state.target.clone().sub(camera.position).normalize();
  state.yaw=Math.atan2(forward.x,forward.z);state.pitch=Math.asin(forward.y);
  camera.lookAt(state.target);
  document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===name));
  $('mode-help').textContent=name==='street'?'自由街景 · 拖拽转头 · 点击画面后 WASD 行走，Q/E 升降 · 不模拟碰撞':'拖动环绕 · 滚轮缩放 · 右键或 Shift 拖动平移 · 点击建筑查看';
}

function focusBuilding(building,move=true){
  selected=building;
  const card=$('selection');card.replaceChildren();
  const title=document.createElement('strong');title.textContent=building.name;
  const body=document.createElement('span');body.textContent=building.note;
  const status=document.createElement('div');status.textContent='正式资源方块轮廓 · 游戏未验收'+'｜入口 '+building.entrance.map(v=>Math.round(v*10)/10).join(' / ');
  card.append(title,body,status);
  if(move){
    const directions=[[0,1],[-1,0],[0,-1],[1,0]],d=directions[building.rotation_quarters%4];
    const e=building.entrance;
    const distance=building.role==='landmark'?17:building.role==='gate'?12:8;
    camera.position.set(e[0]+d[0]*distance,e[1]+(building.role==='landmark'?6:2.3),e[2]+d[1]*distance);
    state.target.set(building.position[0],building.position[1]+3.1,building.position[2]);
    state.mode='street';
    const forward=state.target.clone().sub(camera.position).normalize();state.yaw=Math.atan2(forward.x,forward.z);state.pitch=Math.asin(forward.y);
    camera.lookAt(state.target);
    document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view==='street'));
    $('mode-help').textContent='自由街景 · 拖拽转头 · 点击画面后 WASD 行走，Q/E 升降 · 不模拟碰撞';
  }
}

function setupLabels(){
  $('labels').replaceChildren();labelNodes=[];
  for(const item of current.labels){
    const node=document.createElement('div');node.className='label '+item.kind;node.textContent=item.text;
    $('labels').append(node);labelNodes.push({node,item,point:new THREE.Vector3(...item.position)});
  }
}

function loadCity(index){
  state.city=index;current=SCENES[index];selected=null;
  seaBackdrop.visible=current.presentation_context.open_east_sea;
  loadGeometry(current);setupLabels();setView('panorama');
  $('city-name').textContent=current.name;$('city-summary').textContent=current.summary;
  const cov=current.manifest.coverage;
  $('stats').replaceChildren();
  for(const [value,label] of [[cov.houses,'栋完整方块民居'],[cov.detailed_houses,'栋含生活设施'],[cov.service_anchors,'类服务锚点'],[current.manifest.size[0]+'×'+current.manifest.size[2],'格 · 城形范围']]){
    const div=document.createElement('div');div.className='stat';const num=document.createElement('strong');num.textContent=value;
    const small=document.createElement('span');small.textContent=label;div.append(num,small);$('stats').append(div);
  }
  $('anchors').replaceChildren();
  for(const building of current.manifest.buildings.filter(b=>b.role!=='house'&&b.role!=='warehouse')){
    const button=document.createElement('button');button.textContent=building.name;button.addEventListener('click',()=>focusBuilding(building));$('anchors').append(button);
  }
  $('pending').replaceChildren();
  for(const text of current.manifest.unimplemented){const li=document.createElement('li');li.textContent=text;$('pending').append(li);}
  $('selection').textContent='点击建筑或右侧位置，可近看入口与沿街关系。屋顶开关可查看内部楼板、床位与设施。';
  $('feature-view').textContent='地域重点';
  document.querySelectorAll('[data-city]').forEach(b=>b.classList.toggle('active',Number(b.dataset.city)===index));
}

SCENES.forEach((scene,index)=>{
  const button=document.createElement('button');button.textContent=scene.name;button.dataset.city=index;
  button.addEventListener('click',()=>loadCity(index));$('city-tabs').append(button);
});
document.querySelectorAll('[data-view]').forEach(button=>button.addEventListener('click',()=>setView(button.dataset.view)));
for(const [id,key] of [['toggle-labels','labels'],['toggle-roofs','roofs'],['toggle-envelope','envelope'],['toggle-scale','scale']]){
  $(id).addEventListener('click',()=>{state[key]=!state[key];$(id).setAttribute('aria-pressed',String(state[key]));applyLayers();});
}
function download(data,suffix){
  const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));
  const a=document.createElement('a');a.href=url;a.download=current.city_id+'-'+suffix+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
$('download-manifest').addEventListener('click',()=>download(current.manifest,'manifest'));
$('download-scene').addEventListener('click',()=>download(current,'geometry'));

canvas.addEventListener('contextmenu',e=>e.preventDefault());
canvas.addEventListener('pointerdown',e=>{
  canvas.focus();canvas.setPointerCapture(e.pointerId);state.drag={x:e.clientX,y:e.clientY,sx:e.clientX,sy:e.clientY,pan:e.button===2||e.shiftKey};state.moved=false;
});
canvas.addEventListener('pointermove',e=>{
  if(!state.drag)return;
  const dx=e.clientX-state.drag.x,dy=e.clientY-state.drag.y;
  state.drag.x=e.clientX;state.drag.y=e.clientY;
  if(Math.hypot(e.clientX-state.drag.sx,e.clientY-state.drag.sy)>4)state.moved=true;
  if(state.mode==='street'){
    state.yaw-=dx*0.005;state.pitch=Math.max(-1.25,Math.min(1.25,state.pitch-dy*0.005));
  }else if(state.drag.pan){
    const factor=state.radius/600;
    const right=new THREE.Vector3(Math.cos(state.theta),0,-Math.sin(state.theta));
    const ahead=new THREE.Vector3(Math.sin(state.theta),0,Math.cos(state.theta));
    state.target.addScaledVector(right,-dx*factor).addScaledVector(ahead,-dy*factor);
  }else{
    state.theta-=dx*0.005;state.phi=Math.max(0.025,Math.min(1.52,state.phi-dy*0.005));
  }
});
canvas.addEventListener('pointerup',e=>{
  if(state.drag&&!state.moved&&e.button===0){
    const rect=canvas.getBoundingClientRect();pointer.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
    raycaster.setFromCamera(pointer,camera);
    for(const hit of raycaster.intersectObjects(pickables,false)){
      if(!hit.object.visible)continue;
      const data=hit.object.userData,tag=data.tags[hit.instanceId!==undefined?hit.instanceId:hit.faceIndex];
      const building=current.manifest.buildings.find(b=>b.id===tag);
      if(building){focusBuilding(building,false);break;}
      if(data.layer==='terrain')break;
    }
  }
  state.drag=null;
});
canvas.addEventListener('pointercancel',()=>state.drag=null);
canvas.addEventListener('wheel',e=>{
  e.preventDefault();
  if(state.mode==='street'){
    const forward=new THREE.Vector3(Math.sin(state.yaw),0,Math.cos(state.yaw));camera.position.addScaledVector(forward,-e.deltaY*0.035);
  }else state.radius=Math.max(15,Math.min(820,state.radius*Math.exp(e.deltaY*0.001)));
},{passive:false});
canvas.addEventListener('keydown',e=>{
  if(['KeyW','KeyA','KeyS','KeyD','KeyQ','KeyE','ShiftLeft','ShiftRight','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.code)){state.keys.add(e.code);e.preventDefault();}
});
window.addEventListener('keyup',e=>state.keys.delete(e.code));
canvas.addEventListener('blur',()=>state.keys.clear());

const sizeObserver=new ResizeObserver(()=>{
  const width=stage.clientWidth,height=stage.clientHeight;renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();
});sizeObserver.observe(stage);

function animate(){
  requestAnimationFrame(animate);
  const dt=Math.min(0.05,clock.getDelta());
  if(state.mode==='street'){
    const speed=(state.keys.has('ShiftLeft')||state.keys.has('ShiftRight')?24:9)*dt;
    const f=new THREE.Vector3(Math.sin(state.yaw),0,Math.cos(state.yaw)),right=new THREE.Vector3(-Math.cos(state.yaw),0,Math.sin(state.yaw));
    if(state.keys.has('KeyW')||state.keys.has('ArrowUp'))camera.position.addScaledVector(f,speed);
    if(state.keys.has('KeyS')||state.keys.has('ArrowDown'))camera.position.addScaledVector(f,-speed);
    if(state.keys.has('KeyD')||state.keys.has('ArrowRight'))camera.position.addScaledVector(right,speed);
    if(state.keys.has('KeyA')||state.keys.has('ArrowLeft'))camera.position.addScaledVector(right,-speed);
    if(state.keys.has('KeyQ'))camera.position.y-=speed;
    if(state.keys.has('KeyE'))camera.position.y+=speed;
    camera.position.y=Math.max(1.85,camera.position.y);
    const aim=new THREE.Vector3(Math.sin(state.yaw)*Math.cos(state.pitch),Math.sin(state.pitch),Math.cos(state.yaw)*Math.cos(state.pitch));
    camera.lookAt(camera.position.clone().add(aim));
  }else{
    camera.position.set(state.target.x+state.radius*Math.sin(state.phi)*Math.sin(state.theta),state.target.y+state.radius*Math.cos(state.phi),state.target.z+state.radius*Math.sin(state.phi)*Math.cos(state.theta));
    camera.lookAt(state.target);
  }
  camera.updateMatrixWorld();
  for(const entry of labelNodes){
    const p=entry.point.clone().project(camera);
    const tooFar=state.mode==='street'&&entry.point.distanceTo(camera.position)>95;
    const visible=state.labels&&p.z>-1&&p.z<1&&Math.abs(p.x)<0.98&&Math.abs(p.y)<0.98&&!tooFar&&(entry.item.kind!=='scale'||state.scale);
    entry.node.style.display=visible?'block':'none';
    if(visible){entry.node.style.left=((p.x*0.5+0.5)*stage.clientWidth)+'px';entry.node.style.top=((-p.y*0.5+0.5)*stage.clientHeight)+'px';}
  }
  renderer.render(world,camera);
}
loadCity(0);$('loading').remove();animate();
