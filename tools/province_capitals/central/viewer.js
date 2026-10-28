// This script is embedded by export.mjs. All geometry is derived from its scene payload.
function startWhitebox(data) {
  const T=globalThis.THREE, $=id=>document.getElementById(id);
  const wrap=$('view'), h=data.heightfield, spec=data.spec, proposal=spec.height_proposal;
  if(h.heights_delta_i16_le_base64){
    const bytes=Uint8Array.from(atob(h.heights_delta_i16_le_base64),c=>c.charCodeAt(0)),values=new DataView(bytes.buffer);h.heights=[];
    for(let i=0;i<h.nx*h.nz;i++){const previous=i%h.nx?h.heights[i-1]:i>=h.nx?h.heights[i-h.nx]:0;h.heights.push(previous+values.getInt16(i*2,true));}
  }
  let mode='original', preset='overview', camera, controls, cityGroup, selected=null, tour=false, progress=0;
  const layerState={terrain:true,buildings:true,roads:true,walls:true,water:true,route:true,d1:true,labels:true,ceiling:false,wire:false};
  const scene=new T.Scene();scene.background=new T.Color('#e8eee8');
  scene.add(new T.HemisphereLight(0xffffff,0x87978a,0.9));
  const sunlight=new T.DirectionalLight(0xfff9ee,0.95);sunlight.position.set(-650,1100,500);scene.add(sunlight);
  scene.add(new T.AmbientLight(0xffffff,0.14));
  const renderer=new T.WebGLRenderer({antialias:true,alpha:false,powerPreference:'high-performance'});
  renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.75));renderer.outputEncoding=T.sRGBEncoding;
  renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=0.88;wrap.append(renderer.domElement);
  const raycaster=new T.Raycaster(), mouse=new T.Vector2(), pickMeshes=[];
  const matrix=new T.Matrix4(), quaternion=new T.Quaternion(), vector=new T.Vector3(), scale=new T.Vector3();
  const labels=[], groups={}, dynamicGeometries=[], dynamicMaterials=[];
  const boxGeometry=new T.BoxGeometry(1,1,1);
  const towerGeometry=new T.CylinderGeometry(.35,.5,1,8);
  const coneGeometry=new T.ConeGeometry(.5,1,8);
  const roofGeometry=new T.BufferGeometry();
  const roofPoints=[[-.5,0,-.5],[.5,0,-.5],[.5,0,.5],[-.5,0,.5],[-.5,1,0],[.5,1,0]];
  const roofFaces=[0,5,1,0,4,5,4,2,5,4,3,2,0,3,4,1,5,2,0,2,3,0,1,2];
  roofGeometry.setAttribute('position',new T.Float32BufferAttribute(roofFaces.flatMap(i=>roofPoints[i]),3));roofGeometry.computeVertexNormals();
  const white=new T.MeshStandardMaterial({color:'#eeeae0',roughness:1,flatShading:true});
  const roofMat=new T.MeshStandardMaterial({color:'#929e92',roughness:1,flatShading:true});
  const landmarkMat=new T.MeshStandardMaterial({color:'#f8f6ed',roughness:1,flatShading:true});
  const foundationMat=new T.MeshStandardMaterial({color:'#c9cfc4',roughness:1});
  let selectionBox;

  function mapY(y) {
    if(mode==='original')return y;
    const k=proposal.knots;if(y<=k[0][0])return k[0][1];
    for(let i=1;i<k.length;i++)if(y<=k[i][0])return k[i-1][1]+(k[i][1]-k[i-1][1])*(y-k[i-1][0])/(k[i][0]-k[i-1][0]);
    return proposal.max_ground_y;
  }
  function rawGround(x,z) {
    const u=Math.max(0,Math.min(h.nx-1.00001,(x-h.x0)/h.cell)),v=Math.max(0,Math.min(h.nz-1.00001,(z-h.z0)/h.cell));
    const i=Math.floor(u),j=Math.floor(v),a=u-i,b=v-j,k=j*h.nx+i;
    return ((h.heights[k]*(1-a)+h.heights[k+1]*a)*(1-b)+(h.heights[k+h.nx]*(1-a)+h.heights[k+h.nx+1]*a)*b)*h.quantum;
  }
  function p3(p,lift=0){return [p[0],mapY(p[1])+lift,p[2]];}
  function material(color,extra={}){const mat=new T.MeshStandardMaterial({color,roughness:1,side:T.DoubleSide,...extra});dynamicMaterials.push(mat);return mat;}
  function geometry(coords,colors){const g=new T.BufferGeometry();g.setAttribute('position',new T.Float32BufferAttribute(coords,3));if(colors)g.setAttribute('color',new T.Float32BufferAttribute(colors,3));g.computeVertexNormals();dynamicGeometries.push(g);return g;}
  function tri(out,a,b,c){out.push(...a,...b,...c);}
  function quad(out,a,b,c,d){tri(out,a,b,c);tri(out,a,c,d);}
  function addMesh(group,coords,mat){if(!coords.length)return;const m=new T.Mesh(geometry(coords),mat);group.add(m);return m;}
  function ribbon(out,points,width,lift=.32,mapper=p=>p3(p,lift)) {
    for(let i=0;i<points.length-1;i++){
      const a=mapper(points[i]),b=mapper(points[i+1]),dx=b[0]-a[0],dz=b[2]-a[2],L=Math.hypot(dx,dz)||1;
      const ox=-dz/L*width/2,oz=dx/L*width/2;
      quad(out,[a[0]+ox,a[1],a[2]+oz],[b[0]+ox,b[1],b[2]+oz],[b[0]-ox,b[1],b[2]-oz],[a[0]-ox,a[1],a[2]-oz]);
    }
  }
  function line(points,color,parent,dashed=false,width=1) {
    const g=new T.BufferGeometry().setFromPoints(points.map(p=>new T.Vector3(...p)));dynamicGeometries.push(g);
    const mat=dashed?new T.LineDashedMaterial({color,dashSize:5,gapSize:3,depthTest:false}):new T.LineBasicMaterial({color,linewidth:width});dynamicMaterials.push(mat);
    const result=new T.Line(g,mat);if(dashed)result.computeLineDistances();parent.add(result);return result;
  }
  function addLabel(text,raw,baseY=raw[1],className='') {
    const el=document.createElement('div');el.className='scene-label '+className;el.textContent=text;$('labels').append(el);labels.push({el,raw,baseY});
  }
  function createTerrain() {
    const n=h.nx*h.nz,positions=new Float32Array(n*3),colors=new Float32Array(n*3),indices=[];
    const rock=new T.Color('#687a6d'),green=new T.Color('#809177'),town=new T.Color('#a7af9a'),stone=new T.Color('#8f9c89');
    const tint=new T.Color();
    for(let j=0;j<h.nz;j++)for(let i=0;i<h.nx;i++){
      const k=j*h.nx+i,y=h.heights[k]*h.quantum;
      positions[k*3]=h.x0+i*h.cell;positions[k*3+1]=mapY(y);positions[k*3+2]=h.z0+j*h.cell;
      const a=h.heights[j*h.nx+Math.max(0,i-1)]*h.quantum,b=h.heights[j*h.nx+Math.min(h.nx-1,i+1)]*h.quantum;
      const c=h.heights[Math.max(0,j-1)*h.nx+i]*h.quantum,d=h.heights[Math.min(h.nz-1,j+1)*h.nx+i]*h.quantum;
      const slope=Math.hypot(b-a,d-c)/(2*h.cell),m=h.materials[k];
      tint.copy([7,8,11,15,20,23].includes(m)?stone:m===24?town:green).lerp(rock,Math.min(.8,slope*.24));
      colors[k*3]=tint.r;colors[k*3+1]=tint.g;colors[k*3+2]=tint.b;
      if(i<h.nx-1&&j<h.nz-1){const a0=k,b0=k+1,c0=k+h.nx,d0=c0+1;indices.push(a0,c0,b0,b0,c0,d0);}
    }
    const g=new T.BufferGeometry();g.setAttribute('position',new T.BufferAttribute(positions,3));g.setAttribute('color',new T.BufferAttribute(colors,3));g.setIndex(indices);g.computeVertexNormals();dynamicGeometries.push(g);
    const mat=material('#ffffff',{vertexColors:true,flatShading:true});const mesh=new T.Mesh(g,mat);groups.terrain.add(mesh);
    const wire=new T.Mesh(g,new T.MeshBasicMaterial({color:'#506758',wireframe:true,transparent:true,opacity:.14,depthWrite:false}));dynamicMaterials.push(wire.material);wire.position.y=.12;groups.wire.add(wire);
    const side=[],bottom=mapY(h.raw_min_y)-12;
    const edge=[];for(let i=0;i<h.nx;i++)edge.push(i);for(let j=1;j<h.nz;j++)edge.push(j*h.nx+h.nx-1);for(let i=h.nx-2;i>=0;i--)edge.push((h.nz-1)*h.nx+i);for(let j=h.nz-2;j>0;j--)edge.push(j*h.nx);
    for(let i=0;i<edge.length;i++){
      const a=edge[i],b=edge[(i+1)%edge.length],p=[positions[a*3],positions[a*3+1],positions[a*3+2]],q=[positions[b*3],positions[b*3+1],positions[b*3+2]];
      quad(side,p,[p[0],bottom,p[2]],[q[0],bottom,q[2]],q);
    }
    quad(side,[h.x0,bottom,h.z0],[h.x0,bottom,h.z0+(h.nz-1)*h.cell],[h.x0+(h.nx-1)*h.cell,bottom,h.z0+(h.nz-1)*h.cell],[h.x0+(h.nx-1)*h.cell,bottom,h.z0]);
    addMesh(groups.terrain,side,material('#a7b09f'));
  }
  function createSurfaces() {
    const coords=[],side=[];
    for(const f of data.surfaces){
      const y=mapY(f.y)+.09,edge=[];
      if(f.kind==='rect'){const [x0,z0,x1,z1]=f.xz;edge.push([x0,y,z0],[x0,y,z1],[x1,y,z1],[x1,y,z0]);}
      else for(let i=0;i<40;i++){const a=i/40*Math.PI*2;edge.push([f.x+Math.cos(a)*f.rx,y,f.z+Math.sin(a)*f.rz]);}
      const center=edge.reduce((a,b)=>[a[0]+b[0]/edge.length,y,a[2]+b[2]/edge.length],[0,y,0]);
      for(let i=0;i<edge.length;i++){const a=edge[i],b=edge[(i+1)%edge.length];tri(coords,center,b,a);const ya=Math.min(y-1.2,mapY(rawGround(a[0],a[2]))),yb=Math.min(y-1.2,mapY(rawGround(b[0],b[2])));quad(side,a,[a[0],ya,a[2]],[b[0],yb,b[2]],b);}
    }
    addMesh(groups.terrain,coords,material('#9da992'));addMesh(groups.terrain,side,foundationMat);
  }
  function createBuildings() {
    const buckets=new Map();pickMeshes.length=0;
    function inst(geo,mat,pos,size,b){const key=geo.uuid+mat.uuid;let bucket=buckets.get(key);if(!bucket){bucket={geo,mat,rows:[]};buckets.set(key,bucket);}bucket.rows.push({pos,size,b});}
    for(const b of data.buildings){
      const s=b.shape,[x,base,z]=mode==='original'?b.position:b.adapted_position,w=s.width,d=s.depth;
      const bottom=mode==='original'?b.support.raw_bottom:b.support.adapted_bottom;
      if(s.kind!=='bridge')inst(boxGeometry,foundationMat,[x,(bottom+base)/2,z],[w,Math.max(.2,base-bottom),d],b);
      const mat=b.role==='house'?white:landmarkMat;
      if(s.kind==='gate'){
        const opening=Math.min(w*.32,7),pier=(w-opening)/2;
        const r=-b.rotation_quarters*Math.PI/2;
        const left=-(opening+pier)/2,right=(opening+pier)/2;
        const shifted=u=>[x+Math.cos(r)*u,base+s.body_height/2,z-Math.sin(r)*u];
        inst(boxGeometry,mat,shifted(left),[pier,s.body_height,d],b);inst(boxGeometry,mat,shifted(right),[pier,s.body_height,d],b);
        inst(boxGeometry,mat,[x,base+s.body_height*.85,z],[opening,s.body_height*.3,d],b);
        inst(roofGeometry,roofMat,[x,base+s.body_height,z],[w,s.roof_height,d],b);
      } else if(s.kind==='tower'||s.kind==='pagoda'||s.kind==='pavilion'){
        inst(towerGeometry,mat,[x,base+s.body_height/2,z],[w,s.body_height,d],b);
        inst(coneGeometry,roofMat,[x,base+s.body_height+s.roof_height/2,z],[w,s.roof_height,d],b);
      } else if(s.kind==='bridge'){
        inst(boxGeometry,mat,[x,base+.5,z],[w,1,d],b);
        if(b.source_kind==='langqiao'){inst(boxGeometry,landmarkMat,[x,base+s.body_height/2,z],[w*.8,s.body_height,d*.35],b);inst(roofGeometry,roofMat,[x,base+s.body_height,z],[w,s.roof_height,d],b);}
      } else {
        inst(boxGeometry,mat,[x,base+s.body_height/2,z],[w*.88,s.body_height,d*.88],b);
        inst(roofGeometry,roofMat,[x,base+s.body_height,z],[w,s.roof_height,d],b);
      }
    }
    for(const bucket of buckets.values()){
      const mesh=new T.InstancedMesh(bucket.geo,bucket.mat,bucket.rows.length);mesh.userData.rows=bucket.rows;
      bucket.rows.forEach((row,i)=>{vector.set(...row.pos);scale.set(...row.size);quaternion.setFromAxisAngle(new T.Vector3(0,1,0),-row.b.rotation_quarters*Math.PI/2);matrix.compose(vector,quaternion,scale);mesh.setMatrixAt(i,matrix);});
      mesh.instanceMatrix.needsUpdate=true;mesh.frustumCulled=false;groups.buildings.add(mesh);pickMeshes.push(mesh);
    }
  }
  function createRoads() {
    const main=[],lanes=[],steps=[];
    for(const road of data.roads)ribbon(road.id==='avenue'||road.id.startsWith('imperial_')?main:road.kind==='stair'?steps:lanes,road.points,road.width,.35);
    addMesh(groups.roads,main,material('#c6af7e'));addMesh(groups.roads,lanes,material('#bbc2b6'));addMesh(groups.roads,steps,material('#c6bca0'));
    const outline=data.city_poly.map(([x,z])=>[x,mapY(rawGround(x,z))+.7,z]);outline.push(outline[0]);line(outline,'#7a8d7e',groups.walls);
    for(const road of data.roads.filter(r=>r.id==='cliff_lower'||r.id.startsWith('summit_')))line(road.points.map(p=>p3(p,1.2)),'#b9781c',groups.route);
    for(const link of data.proposed_links)line(link.points.map(p=>p3(p,1.4)),'#d58d22',groups.route,true);
  }
  function createWalls() {
    const city=[],palace=[];
    for(const f of data.walls){
      const arr=f.kind==='city_wall'?city:palace,[a,b]=f.points,dx=b[0]-a[0],dz=b[2]-a[2],L=Math.hypot(dx,dz)||1,ox=-dz/L*f.width/2,oz=dx/L*f.width/2;
      const ya=mapY(a[1]-f.height)+f.height,yb=mapY(b[1]-f.height)+f.height;
      const ba=Math.min(ya-f.height,mapY(rawGround(a[0],a[2]))),bb=Math.min(yb-f.height,mapY(rawGround(b[0],b[2])));
      const p=[a[0]+ox,ya,a[2]+oz],q=[b[0]+ox,yb,b[2]+oz],r=[b[0]-ox,yb,b[2]-oz],s=[a[0]-ox,ya,a[2]-oz];
      quad(arr,p,q,r,s);quad(arr,p,[p[0],ba,p[2]],[q[0],bb,q[2]],q);quad(arr,s,r,[r[0],bb,r[2]],[s[0],ba,s[2]]);
    }
    addMesh(groups.walls,city,material('#a7b1a3'));addMesh(groups.walls,palace,material('#c4bca8'));
  }
  function createWater() {
    const surfaces=[],fallFaces=[];
    for(const f of data.water){
      if(f.kind==='channel')ribbon(surfaces,f.points,f.width,.18);
      else if(f.kind==='rect'){const [x0,z0,x1,z1]=f.xz,y=mapY(f.y)+.18;quad(surfaces,[x0,y,z0],[x0,y,z1],[x1,y,z1],[x1,y,z0]);}
      else {const y=mapY(f.y)+.18,center=[f.x,y,f.z];for(let i=0;i<64;i++){const a=i/64*Math.PI*2,b=(i+1)/64*Math.PI*2;tri(surfaces,center,[f.x+Math.cos(b)*f.rx,y,f.z+Math.sin(b)*f.rz],[f.x+Math.cos(a)*f.rx,y,f.z+Math.sin(a)*f.rz]);}}
    }
    for(const f of data.falls){const y0=mapY(f.bottom),y1=mapY(f.top),x0=f.x-f.width/2,x1=f.x+f.width/2;quad(fallFaces,[x0,y0,f.z],[x1,y0,f.z],[x1,y1,f.z],[x0,y1,f.z]);}
    addMesh(groups.water,surfaces,material('#79b4c5',{metalness:0,roughness:.7}));addMesh(groups.water,fallFaces,material('#b4d8df',{roughness:.8}));
  }
  function createReference() {
    function rect(window,color,dash){const [x0,z0,x1,z1]=window,coords=[],ends=[[x0,z0],[x1,z0],[x1,z1],[x0,z1],[x0,z0]];for(let i=0;i<4;i++){const a=ends[i],b=ends[i+1],n=Math.ceil(Math.hypot(b[0]-a[0],b[1]-a[1])/4);for(let k=0;k<=n;k++){const x=a[0]+(b[0]-a[0])*k/n,z=a[1]+(b[1]-a[1])*k/n;coords.push([x,mapY(rawGround(x,z))+1.6,z]);}}line(coords,color,groups.d1,dash);}
    rect(data.d1.source_window,'#168eab',false);rect(data.d1.packed_window,'#168eab',true);
    const limit=proposal.highest_solid_y;line([[-320,limit,-740],[320,limit,-740],[320,limit,430],[-320,limit,430],[-320,limit,-740]],'#7f9cac',groups.ceiling,true);
    for(const x of [-320,320])line([[x,60,-740],[x,limit,-740]],'#9eb4bb',groups.ceiling);
    const scaleY=mapY(rawGround(-245,475))+3;line([[-245,scaleY,475],[-145,scaleY,475]],'#4c6658',groups.reference);
    for(const x of [-245,-195,-145])line([[x,scaleY-2,475],[x,scaleY+2,475]],'#4c6658',groups.reference);
    addLabel('100 格 / 100 米',[-195,rawGround(-195,475)+6,475],rawGround(-195,475),'scale');
  }
  function rebuild() {
    if(cityGroup)scene.remove(cityGroup);dynamicGeometries.splice(0).forEach(g=>g.dispose());dynamicMaterials.splice(0).forEach(m=>m.dispose());
    labels.splice(0).forEach(l=>l.el.remove());cityGroup=new T.Group();scene.add(cityGroup);
    for(const key of ['terrain','buildings','roads','walls','water','route','d1','ceiling','wire','reference']){groups[key]=new T.Group();cityGroup.add(groups[key]);}
    createTerrain();createSurfaces();createBuildings();createRoads();createWalls();createWater();createReference();
    for(const l of data.labels)addLabel(l.text,l.position,l.base_y,l.text.includes('D1')?'d1':'');
    Object.keys(groups).forEach(key=>{groups[key].visible=layerState[key]!==false;});
    if(selected)showSelected(selected);updateInfo();
  }
  function makeControls(cam) {
    if(controls)controls.dispose();camera=cam;controls=new T.OrbitControls(camera,renderer.domElement);controls.enableDamping=false;controls.screenSpacePanning=true;controls.minDistance=8;controls.maxDistance=4000;controls.maxPolarAngle=Math.PI*.49;controls.addEventListener('change',()=>{updateLabels();});
  }
  const presets={overview:{pos:[920,860,1120],target:[0,125,-110]},gate:{pos:[-82,90,480],target:[-30,55,370]},avenue:{pos:[-158,180,340],target:[-35,63,145]},palace:{pos:[170,235,25],target:[-22,110,-140]},waterfall:{pos:[-115,250,-170],target:[-6,183,-370]},tower:{pos:[-45,245,-300],target:[30,203,-377]},summit:{pos:[300,480,-385],target:[206,410,-520]},d1:{pos:[-255,235,500],target:[-45,62,275]},north:{pos:[460,650,-1050],target:[0,130,-40]}};
  function setCamera(name) {
    tour=false;$('tour-toggle').textContent='巡览';preset=name;
    const aspect=Math.max(1,wrap.clientWidth)/Math.max(1,wrap.clientHeight);
    if(name==='top'){
      const span=Math.max(820,640/aspect),cam=new T.OrthographicCamera(-span*aspect,span*aspect,span,-span,1,6000);cam.position.set(0,1800,-120);cam.up.set(0,0,-1);makeControls(cam);controls.enableRotate=false;controls.target.set(0,mapY(100),-120);
    }else{
      const p=presets[name]||presets.overview,cam=new T.PerspectiveCamera(43,aspect,.3,9000),dy=mapY(p.target[1])-p.target[1];cam.position.set(p.pos[0],p.pos[1]+dy,p.pos[2]);makeControls(cam);controls.target.set(p.target[0],mapY(p.target[1]),p.target[2]);
    }
    controls.update();document.querySelectorAll('[data-camera]').forEach(b=>b.classList.toggle('active',b.dataset.camera===name));resize();
  }
  function updateLabels() {
    if(!camera)return;
    const w=wrap.clientWidth,hh=wrap.clientHeight;
    for(const item of labels){
      if(!layerState.labels||(item.className==='d1'&&!layerState.d1)){item.el.style.display='none';continue;}
      const p=new T.Vector3(item.raw[0],mapY(item.baseY)+(item.raw[1]-item.baseY),item.raw[2]),distance=p.distanceTo(camera.position);p.project(camera);
      const x=(p.x*.5+.5)*w,y=(-p.y*.5+.5)*hh;
      const visible=p.z>=-1&&p.z<=1&&x>24&&x<w-24&&y>45&&y<hh-30&&(distance<1600||preset==='top'||preset==='overview'||preset==='north');
      item.el.style.display=visible?'block':'none';if(visible){item.el.style.left=x+'px';item.el.style.top=y+'px';}
      if(item.el.classList.contains('d1')&&!layerState.d1)item.el.style.display='none';
    }
  }
  function updateInfo() {
    const raw=mode==='original',s=data.stats,peak=raw?s.original_summit_ground_y:s.adapted_summit_ground_y;
    $('mode-note').textContent=raw?'原作高度 · 峰台 404 / 标记 418 · 超出当前主世界':'主世界高度提案 · 山体分段映射 / 建筑保持尺度 · 未激活';
    $('profile-info').textContent=(raw?'原始高度 profile':'主世界适配 profile')+' · '+(data.profiles?.[mode]?.layout_id||'独立身份见 manifest');
    const top=raw?s.raw_whitebox_top_y:s.adapted_whitebox_top_y;
    const rows=[['峰台地表',`Y=${peak}`],['白模屋顶包络',`Y≈${top}`],['当前主世界实体顶高','Y=319'],['未来规划地表上限','Y=280'],['建筑 / 顶空预留','32 格 / 7 格']];
    $('height-info').replaceChildren(...rows.map(([a,b])=>{const tr=document.createElement('tr');for(const t of [a,b]){const td=document.createElement('td');td.textContent=t;tr.append(td);}return tr;}));
  }
  function showSelected(b) {
    selected=b;if(selectionBox){cityGroup.remove(selectionBox);selectionBox.geometry.dispose();selectionBox.material.dispose();selectionBox=null;}
    const p=mode==='original'?b.position:b.adapted_position,s=b.shape,w=b.rotation_quarters%2?s.depth:s.width,d=b.rotation_quarters%2?s.width:s.depth;
    const box=new T.Box3(new T.Vector3(p[0]-w/2,p[1],p[2]-d/2),new T.Vector3(p[0]+w/2,p[1]+s.height,p[2]+d/2));
    selectionBox=new T.Box3Helper(box,0x5f917c);cityGroup.add(selectionBox);
    $('selection-title').textContent=b.name+' · '+b.source_template;
    $('selection-info').textContent=`原作实例 #${b.source_instance_index}；原基座 (${b.position.join(', ')})，提案 Y=${b.adapted_position[1]}。占地 ${s.width}×${s.depth}，高度包络 ${s.height} 格，建筑尺度 1:1:1。source rot=${b.source_rotation_quarters} → exchange q=${b.rotation_quarters}；围绕作者原点，未归零。仅白模，非可碰撞模板。`;
  }
  function resize() {
    const w=Math.max(1,wrap.clientWidth),hh=Math.max(1,wrap.clientHeight);renderer.setSize(w,hh,false);if(!camera)return;
    if(camera.isPerspectiveCamera)camera.aspect=w/hh;else{const span=Math.max(820,640/(w/hh));camera.left=-span*w/hh;camera.right=span*w/hh;camera.top=span;camera.bottom=-span;}camera.updateProjectionMatrix();updateLabels();
  }
  let down;
  renderer.domElement.addEventListener('pointerdown',e=>{down=[e.clientX,e.clientY];if(tour){tour=false;$('tour-toggle').textContent='巡览';controls.enabled=true;}});
  renderer.domElement.addEventListener('pointerup',e=>{
    if(!down||Math.hypot(e.clientX-down[0],e.clientY-down[1])>5||e.button!==0||!layerState.buildings)return;
    const rect=renderer.domElement.getBoundingClientRect();mouse.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);raycaster.setFromCamera(mouse,camera);
    const hit=raycaster.intersectObjects(pickMeshes,false)[0];if(hit&&hit.instanceId!==undefined)showSelected(hit.object.userData.rows[hit.instanceId].b);
  });
  document.querySelectorAll('[data-mode]').forEach(button=>button.addEventListener('click',()=>{
    if(mode===button.dataset.mode)return;mode=button.dataset.mode;document.querySelectorAll('[data-mode]').forEach(b=>b.classList.toggle('active',b===button));rebuild();setCamera(preset);
  }));
  document.querySelectorAll('[data-camera]').forEach(b=>b.addEventListener('click',()=>setCamera(b.dataset.camera)));
  document.querySelectorAll('[data-layer]').forEach(input=>input.addEventListener('change',()=>{const key=input.dataset.layer;layerState[key]=input.checked;if(groups[key])groups[key].visible=input.checked;updateLabels();}));
  function tourPosition(t) {
    const path=data.summit_path,idx=Math.min(path.length-1.001,t*(path.length-1)),i=Math.floor(idx),u=idx-i;
    return path[i].map((v,k)=>v+(path[Math.min(i+1,path.length-1)][k]-v)*u);
  }
  function observePath(t) {
    if(camera.isOrthographicCamera)setCamera('tower');
    const p=tourPosition(t),ahead=tourPosition(Math.min(1,t+.025));
    camera.position.set(p[0]+6,mapY(p[1])+5,p[2]+6);controls.target.set(ahead[0],mapY(ahead[1])+2,ahead[2]);controls.update();updateLabels();
  }
  $('tour-progress').addEventListener('input',e=>{progress=Number(e.target.value)/1000;tour=false;$('tour-toggle').textContent='巡览';observePath(progress);controls.enabled=true;});
  $('tour-toggle').addEventListener('click',()=>{if(camera.isOrthographicCamera)setCamera('tower');tour=!tour;if(progress>=1)progress=0;$('tour-toggle').textContent=tour?'暂停':'巡览';controls.enabled=!tour;});
  $('house-count').textContent=data.stats.by_source_kind.house;$('shop-count').textContent=data.stats.by_source_kind.shop;
  $('scope-width').textContent=data.stats.scene_width;$('scope-depth').textContent=data.stats.scene_depth;
  $('sampling-info').textContent=`${data.stats.building_instances} 个建筑 / 桥实例；${h.cell} 格地形采样。D1 只占南门西侧街区，青框并未放大。`;
  $('rebuild-list').replaceChildren(...spec.rebuild_items.map(text=>{const li=document.createElement('li');li.textContent=text;return li;}));
  $('rotation-note').textContent='旋转：已按原作实际 (x,z)→(z,−x) 转换为 exchange q=(4−source_rot)%4；Three.js 使用 rotation.y=−qπ/2。桥梁源 span 与 rot 注释仍有偏差，须在正式资源化时协调。';
  rebuild();setCamera('overview');new ResizeObserver(resize).observe(wrap);$('loading').remove();
  let last=performance.now();
  function frame(now){requestAnimationFrame(frame);const dt=Math.min(.08,(now-last)/1000);last=now;if(tour){progress=Math.min(1,progress+dt/80);$('tour-progress').value=Math.round(progress*1000);observePath(progress);if(progress>=1){tour=false;controls.enabled=true;$('tour-toggle').textContent='巡览';}}renderer.render(scene,camera);updateLabels();}
  requestAnimationFrame(frame);
}
