'use strict';
const $=id=>document.getElementById(id),stage=$('stage'),canvas=$('view');
const renderer=new THREE.WebGLRenderer({canvas,antialias:true,powerPreference:'high-performance',preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));
renderer.outputEncoding=THREE.sRGBEncoding;
renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=.95;
renderer.physicallyCorrectLights=true;
renderer.shadowMap.enabled=false;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
const world=new THREE.Scene();world.background=new THREE.Color('#d7dfce');
const camera=new THREE.PerspectiveCamera(42,1,.1,2000);
const orbit=new THREE.OrbitControls(camera,canvas);orbit.enableDamping=false;orbit.maxPolarAngle=Math.PI*.49;orbit.minDistance=2;orbit.maxDistance=1100;
world.add(new THREE.HemisphereLight('#fff4d9','#64796f',.85));
const sun=new THREE.DirectionalLight('#fff0d5',2.2);sun.position.set(-100,260,140);sun.castShadow=true;sun.shadow.mapSize.set(1536,1536);sun.shadow.camera.left=-250;sun.shadow.camera.right=250;sun.shadow.camera.top=250;sun.shadow.camera.bottom=-250;sun.shadow.camera.far=650;sun.shadow.bias=-.0003;world.add(sun,sun.target);
const floor=new THREE.Mesh(new THREE.PlaneGeometry(2600,2600),new THREE.MeshStandardMaterial({color:new THREE.Color('#b2bea0').convertSRGBToLinear(),roughness:1}));floor.rotation.x=-Math.PI/2;floor.position.y=-1.05;floor.receiveShadow=true;world.add(floor);
const sea=new THREE.Mesh(new THREE.PlaneGeometry(1600,1700),new THREE.MeshStandardMaterial({color:new THREE.Color('#438d98').convertSRGBToLinear(),roughness:1}));sea.rotation.x=-Math.PI/2;sea.position.set(1024,5.9,136);world.add(sea);sea.visible=false;
const boxGeo=new THREE.BoxGeometry(1,1,1),dummy=new THREE.Object3D(),ray=new THREE.Raycaster();
let group=null,current=null,currentIndex=0,materials=[],textures=[],picks=[],dragStart=null;
const hidden=new Set();
const viewNames={overview:'全景',close:'核心近看',human:'人物高度',garden:'生活庭院',gate:'城门',wall:'城防与水门'};
const layerSets={landmark:['landmark'],garden:['garden','green','props','cultivation','frontage'],walls:['walls'],context:['context'],scale:['scale']};
const layerNames={landmark:'主地标',garden:'庭园与小设施',walls:'完整城防',context:'民居尺度参照',scale:'人物尺度'};
let dirty=true;function render(){dirty=true;}
orbit.addEventListener('change',render);
function texture(name,color){
 if(['water','fire','amber','human','ice'].includes(name))return null;
 const c=document.createElement('canvas');c.width=c.height=32;const ctx=c.getContext('2d');ctx.fillStyle=color;ctx.fillRect(0,0,32,32);
 let seed=36+name.length;for(let n=0;n<80;n++){seed=(seed*1664525+1013904223)>>>0;const x=(seed>>>16)%16*2;seed=(seed*1664525+1013904223)>>>0;const y=(seed>>>16)%16*2;ctx.fillStyle=n%2?'rgba(250,245,210,.035)':'rgba(14,30,23,.035)';ctx.fillRect(x,y,2,2);}
 const t=new THREE.CanvasTexture(c);t.magFilter=THREE.NearestFilter;t.minFilter=THREE.NearestFilter;t.encoding=THREE.sRGBEncoding;textures.push(t);return t;
}
function unpack(s){
 if(s.voxel_terrain){
  const field=s.voxel_terrain,raw=Uint8Array.from(atob(field.data),c=>c.charCodeAt(0)),view=new DataView(raw.buffer),w=field.width,d=field.depth;
  const vertices=[],faces=[],sides=[],sideFaces=[];
  const h=(x,z)=>view.getInt16((z*w+x)*2,true)+1;
  const quad=(points,side=false)=>{const v=side?sides:vertices,f=side?sideFaces:faces,n=v.length;v.push(...points);f.push([n,n+1,n+2],[n,n+2,n+3]);};
  for(let z=0;z<d;z++){
   for(let x=0;x<w;){const y=h(x,z);let end=x+1;while(end<w&&h(end,z)===y)end++;quad([[x,y,z],[x,y,z+1],[end,y,z+1],[end,y,z]]);x=end;}
   for(let x=0;x<w;x++){const y=h(x,z);for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]){const nx=x+dx,nz=z+dz,ny=nx>=0&&nx<w&&nz>=0&&nz<d?h(nx,nz):0;if(ny>=y)continue;if(dx){const xx=x+(dx>0?1:0);quad([[xx,ny,z],[xx,y,z],[xx,y,z+1],[xx,ny,z+1]],true);}else{const zz=z+(dz>0?1:0);quad([[x,ny,zz],[x+1,ny,zz],[x+1,y,zz],[x,y,zz]],true);}}}
  }
  s.meshes.push({v:vertices,f:faces,m:'ground',layer:'terrain',tag:'heightfield'},{v:sides,f:sideFaces,m:'earth',layer:'terrain',tag:'heightfield_sides'});delete s.voxel_terrain;
 }
 if(!s.packed)return;
 const p=s.packed,raw=Uint8Array.from(atob(p.data),c=>c.charCodeAt(0)),v=new DataView(raw.buffer),n=p.count,prev=[0,0,0,0,0,0];s.boxes=[];
 for(let i=0;i<n;i++){const a=[];for(let k=0;k<6;k++){prev[k]+=v.getInt32((k*n+i)*4,true);a.push(prev[k]/1000);}s.boxes.push({p:a.slice(0,3),s:a.slice(3),m:p.materials[v.getUint8(24*n+i)],layer:p.layers[v.getUint8(25*n+i)],tag:p.tags[v.getUint16(26*n+2*i,true)]});}delete s.packed;
}
function fit(name){
 const p=current.cameras[name]||current.cameras.overview;camera.position.fromArray(p.eye);orbit.target.fromArray(p.target);
 // 窄窗只缩放全景，保持实际人物视点不被自动抬高。
 if(name==='overview'&&camera.aspect<1.2){const delta=camera.position.clone().sub(orbit.target);camera.position.copy(orbit.target).add(delta.multiplyScalar(1.2/camera.aspect));}
 orbit.update();document.querySelectorAll('[data-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.view===name)));render();
}
function layers(){
 group.traverse(o=>{if(o.isMesh)o.visible=!hidden.has(o.userData.layer);});render();
}
function controls(){
 $('views').replaceChildren();for(const name of Object.keys(current.cameras)){const b=document.createElement('button');b.dataset.view=name;b.textContent=viewNames[name]||name;b.onclick=()=>fit(name);$('views').append(b);}
 $('layers').replaceChildren();const present=new Set([...current.boxes,...current.meshes].map(b=>b.layer));
 for(const [key,list]of Object.entries(layerSets)){if(!list.some(x=>present.has(x)))continue;const label=document.createElement('label'),input=document.createElement('input');input.type='checkbox';input.checked=true;input.onchange=()=>{list.forEach(x=>input.checked?hidden.delete(x):hidden.add(x));layers();};label.append(input,document.createTextNode(layerNames[key]));$('layers').append(label);}
 const label=document.createElement('label'),input=document.createElement('input');input.type='checkbox';input.checked=renderer.shadowMap.enabled;input.onchange=()=>{renderer.shadowMap.enabled=input.checked;materials.forEach(m=>m.needsUpdate=true);render();};label.append(input,document.createTextNode('投影（较耗绘制性能）'));$('layers').append(label);
}
function load(index){
 currentIndex=index;current=SCENES[index];unpack(current);hidden.clear();
 if(group){world.remove(group);group.traverse(o=>{if(o.geometry&&o.geometry!==boxGeo)o.geometry.dispose();});materials.forEach(m=>m.dispose());textures.forEach(t=>t.dispose());}
 materials=[];textures=[];picks=[];group=new THREE.Group();world.add(group);
 const mats={};for(const [k,s]of Object.entries(current.materials)){const map=texture(k,s.color);const m=new THREE.MeshStandardMaterial({map,color:map?'#ffffff':new THREE.Color(s.color).convertSRGBToLinear(),roughness:1,metalness:0});mats[k]=m;materials.push(m);}
 const batches=new Map();for(const b of current.boxes){const key=b.layer+'|'+b.m;if(!batches.has(key))batches.set(key,[]);batches.get(key).push(b);}
 for(const rows of batches.values()){const first=rows[0],m=new THREE.InstancedMesh(boxGeo,mats[first.m],rows.length);m.userData={layer:first.layer,tags:rows.map(b=>b.tag)};
  rows.forEach((b,i)=>{dummy.position.fromArray(b.p);dummy.scale.fromArray(b.s);dummy.rotation.set(0,0,0);dummy.updateMatrix();m.setMatrixAt(i,dummy.matrix);});m.instanceMatrix.needsUpdate=true;m.castShadow=!['terrain','path','road'].includes(first.layer);m.receiveShadow=true;m.frustumCulled=false;group.add(m);picks.push(m);
 }
 for(const b of current.meshes){const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(b.v.flat(),3));g.setIndex(b.f.flat());g.computeVertexNormals();const m=new THREE.Mesh(g,mats[b.m]);m.userData={layer:b.layer,tag:b.tag};m.receiveShadow=true;group.add(m);}
 sea.visible=current.mode==='city'&&current.region==='donghai';floor.position.y=current.mode==='city'?-.1:-1.05;
 const target=current.cameras.overview.target;sun.target.position.fromArray(target);sun.position.set(target[0]-130,target[1]+240,target[2]+110);
 $('name').textContent=current.name;$('kind').textContent=current.region_name+' · '+current.kind;$('intent').textContent=current.intent;$('function').textContent=current.function;
 $('stats').textContent=current.mode==='city'?`${current.garden_count} 处生活院落 · ${current.public_object_count} 处公共设施 · ${current.frontage_count} 处门前陈设；为新构图腾出 ${current.removed_for_design.length} 栋旧体量的位置。`:'人物高 1.8 格，旁侧民居用于判断巨物的真实比例。';
 $('scope').textContent=current.scope+' 原生游戏、实际功能与玩家接受未验证。';$('selected').textContent='可点击物件定位其名称。';
 controls();navigation();fit('overview');$('loading').hidden=true;globalThis.CAPITAL_DESIGN.ready=true;render();
}
function button(parent,text,active,fn){const b=document.createElement('button');b.textContent=text;b.setAttribute('aria-pressed',String(active));b.onclick=fn;parent.append(b);}
function navigation(){
 $('modes').replaceChildren();$('cities').replaceChildren();
 SCENES.forEach((s,i)=>{if(s.mode==='city')button($('modes'),s.region_name+'整城样板',currentIndex===i,()=>load(i));});
 const hero=SCENES.findIndex(s=>s.mode==='landmark');if(hero>=0)button($('modes'),'八州独立地标',current.mode==='landmark',()=>load(hero));
 const badge=document.createElement('span');badge.className='badge';badge.textContent='设计方向审阅 · 未接入游戏';$('modes').append(badge);
 if(current.mode==='landmark')SCENES.forEach((s,i)=>{if(s.mode==='landmark')button($('cities'),s.region_name,currentIndex===i,()=>load(i));});
}
canvas.addEventListener('pointerdown',e=>dragStart=[e.clientX,e.clientY]);canvas.addEventListener('pointerup',e=>{if(!dragStart||Math.hypot(e.clientX-dragStart[0],e.clientY-dragStart[1])>5)return;const r=canvas.getBoundingClientRect();ray.setFromCamera(new THREE.Vector2((e.clientX-r.left)/r.width*2-1,1-(e.clientY-r.top)/r.height*2),camera);const hits=ray.intersectObjects(picks).filter(h=>h.object.visible);if(hits[0])$('selected').textContent=hits[0].object.userData.tags[hits[0].instanceId];});
new ResizeObserver(()=>{const w=stage.clientWidth,h=stage.clientHeight;renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();render();}).observe(stage);
function animate(){requestAnimationFrame(animate);if(dirty){dirty=false;renderer.render(world,camera);}}animate();
globalThis.CAPITAL_DESIGN={renderer,camera,world,scenes:SCENES,load,fit,render,ready:false};
renderer.setSize(stage.clientWidth,stage.clientHeight,false);camera.aspect=stage.clientWidth/stage.clientHeight;camera.updateProjectionMatrix();
load(0);
