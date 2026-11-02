const $=id=>document.getElementById(id);
const canvas=$('scene'),stage=$('stage');
const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:false});
renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));
renderer.outputEncoding=THREE.sRGBEncoding;
renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=.95;
const scene=new THREE.Scene();scene.background=new THREE.Color('#c6d7d3');
const camera=new THREE.PerspectiveCamera(42,1,.2,1800);
const controls=new THREE.OrbitControls(camera,canvas);controls.enableDamping=true;controls.maxPolarAngle=Math.PI*.495;controls.minDistance=8;controls.maxDistance=850;
scene.add(new THREE.HemisphereLight('#ffffff','#596d65',.8));
const sun=new THREE.DirectionalLight('#fff3df',1);sun.position.set(-80,240,-110);scene.add(sun);
const materials={};for(const [key,color] of Object.entries(DATA.palette)){materials[key]=new THREE.MeshLambertMaterial({color:new THREE.Color(color).convertSRGBToLinear(),side:THREE.DoubleSide});}
const [W,H,D]=DATA.dimensions,raw=Uint8Array.from(atob(DATA.terrain),c=>c.charCodeAt(0)),heightview=new DataView(raw.buffer);
const ground=(x,z)=>heightview.getInt16((z*W+x)*2,true)+1;
function terrainMesh(side){const positions=[],indices=[];
 const quad=p=>{const i=positions.length/3;positions.push(...p.flat());indices.push(i,i+1,i+2,i,i+2,i+3);};
 for(let z=0;z<D;z++)for(let x=0;x<W;x++){
  const y=ground(x,z);
  if(!side){quad([[x,y,z],[x,y,z+1],[x+1,y,z+1],[x+1,y,z]]);continue;}
  for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]){const nx=x+dx,nz=z+dz,ny=nx>=0&&nx<W&&nz>=0&&nz<D?ground(nx,nz):0;if(ny>=y)continue;
   if(dx){const xx=x+(dx>0?1:0);quad([[xx,ny,z],[xx,y,z],[xx,y,z+1],[xx,ny,z+1]]);}
   else{const zz=z+(dz>0?1:0);quad([[x,ny,zz],[x+1,ny,zz],[x+1,y,zz],[x,y,zz]]);}
  }
 }
 const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setIndex(indices);geometry.computeVertexNormals();return new THREE.Mesh(geometry,materials[side?'earth':'ground']);
}
const terrain=new THREE.Group();terrain.add(terrainMesh(false),terrainMesh(true));scene.add(terrain);
const box=new THREE.BoxGeometry(1,1,1),dummy=new THREE.Object3D(),groups=new Map();
for(const b of DATA.boxes){const key=b[2]+'|'+b[3];if(!groups.has(key))groups.set(key,[]);groups.get(key).push(b);}
for(const items of groups.values()){const mesh=new THREE.InstancedMesh(box,materials[items[0][2]],items.length);for(let i=0;i<items.length;i++){dummy.position.fromArray(items[i][0]);dummy.scale.fromArray(items[i][1]);dummy.updateMatrix();mesh.setMatrixAt(i,dummy.matrix);}mesh.instanceMatrix.needsUpdate=true;scene.add(mesh);}
const routeLayer=new THREE.Group();scene.add(routeLayer);routeLayer.visible=false;
for(const r of DATA.routes){const points=r.centers.map(p=>new THREE.Vector3(p[0]+.5,p[1]+.15,p[2]+.5));routeLayer.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points),new THREE.LineBasicMaterial({color:'#edc879',depthTest:false})));}
const labels=[];for(const b of DATA.buildings){const node=document.createElement('span');node.className='label';node.textContent=b.name;$('labels').append(node);labels.push({node,p:new THREE.Vector3(b.origin[0]+b.size[0]/2,b.origin[1]+b.size[1]+2,b.origin[2]+b.size[2]/2)});}
let showLabels=true;
function setView(v){$('stage').style.display='block';$('plan').style.display='none';camera.position.fromArray(v.eye);controls.target.fromArray(v.target);controls.update();document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===v.id));}
for(const view of DATA.views){const b=document.createElement('button');b.textContent=view.name;b.dataset.view=view.id;b.onclick=()=>setView(view);$('views').append(b);}
function button(text,fn){const b=document.createElement('button');b.textContent=text;b.onclick=fn;$('views').append(b);return b;}
button('平面蓝图',()=>{$('stage').style.display='none';$('plan').style.display='block';document.querySelectorAll('[data-view]').forEach(b=>b.classList.remove('active'));});
button('显隐地名',()=>{showLabels=!showLabels;$('labels').style.display=showLabels?'block':'none';});
button('查看动线',()=>{routeLayer.visible=!routeLayer.visible;});
for(const a of DATA.anchors){const b=document.createElement('button');b.textContent=a.name;b.onclick=()=>{const [x,y,z]=a.position;setView({id:'anchor',eye:[x+17,y+14,z-23],target:[x,y+4,z]});};$('anchors').append(b);}
for(const note of DATA.design){const p=document.createElement('p');p.textContent=note;$('design').append(p);}
$('status').textContent=`设计试点 · ${DATA.buildings.length}栋现有组件 · ${DATA.routes.length}条道路 · ${DATA.checks.anchors}处玩法锚点 · 离线路径检查通过｜游戏与玩家验收未进行`;
function makePlan(){const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox',`-5 -8 ${W+12} ${D+16}`);svg.setAttribute('role','img');svg.setAttribute('aria-label','宗门总平面与标高');
 const add=(tag,attrs,text)=>{const n=document.createElementNS(ns,tag);for(const [k,v] of Object.entries(attrs))n.setAttribute(k,String(v));if(text)n.textContent=text;svg.append(n);};
 for(let z=0;z<D;z+=2)for(let x=0;x<W;x+=2){const h=ground(x,z);add('rect',{x,y:D-z-2,width:2,height:2,fill:`hsl(163 12% ${21+h*.39}%)`});}
 for(const r of DATA.routes)add('polyline',{points:r.centers.map(p=>`${p[0]},${D-p[2]}`).join(' '),fill:'none',stroke:r.kind==='arch_bridge'?'#e6c58b':'#b7c8c1','stroke-width':r.width,'stroke-linejoin':'round'});
 for(const b of DATA.buildings){add('rect',{x:b.origin[0],y:D-b.origin[2]-b.size[2],width:b.size[0],height:b.size[2],fill:'#c3d6cb',stroke:'#384d46','stroke-width':.8});add('text',{x:b.origin[0]+b.size[0]/2,y:D-b.origin[2]+5,'text-anchor':'middle',class:'labeltext'},`${b.name} · ${b.entry[1]}格`);}
 add('text',{x:4,y:8,class:'labeltext'},'山内 ↑　入山口 ↓');$('plan').append(svg);
}
makePlan();
function resize(){const width=stage.clientWidth,height=stage.clientHeight;if(width&&height){renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();}}
new ResizeObserver(resize).observe(stage);resize();setView(DATA.views[0]);
$('loading').style.display='none';window.__sectPreviewReady=true;
function animate(){requestAnimationFrame(animate);if(stage.style.display==='none')return;controls.update();renderer.render(scene,camera);for(const label of labels){const p=label.p.clone().project(camera);label.node.style.display=p.z<1?'block':'none';label.node.style.left=(p.x*.5+.5)*stage.clientWidth+'px';label.node.style.top=(-p.y*.5+.5)*stage.clientHeight+'px';}}
animate();
