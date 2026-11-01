// Orthographic textured voxel renderer. No WebGL, external scripts, or game process.
const compressed=Uint8Array.from(atob('__DATA__'),c=>c.charCodeAt(0));
const DATA=await new Response(new Blob([compressed]).stream().pipeThrough(new DecompressionStream('gzip'))).json();
const images={};
function pixels(kind){
  const c=document.createElement('canvas');c.width=c.height=16;const x=c.getContext('2d');
  const base=kind==='grass'?[109,137,70]:[77,106,56];
  for(let j=0;j<16;j++)for(let i=0;i<16;i++){
    const n=((i*37+j*19+i*j*13)%17)-8;x.fillStyle=`rgb(${base.map(v=>v+n).join(',')})`;x.fillRect(i,j,1,1);
  }return c;
}
for(const key of ['grass','leaves'])images[key]=pixels(key);
await Promise.all(Object.entries(DATA.textures).map(([key,url])=>new Promise((resolve,reject)=>{
  const im=new Image();im.onload=()=>{images[key]=im;resolve()};im.onerror=reject;im.src=url;
})));
const averages={};
for(const [key,im] of Object.entries(images)){
  const c=document.createElement('canvas');c.width=c.height=1;const x=c.getContext('2d');x.drawImage(im,0,0,1,1);
  const p=x.getImageData(0,0,1,1).data;averages[key]=`rgb(${p[0]},${p[1]},${p[2]})`;
}
const normals=[[0,1,0],[1,0,0],[-1,0,0],[0,0,1],[0,0,-1]];
function quad(v,normal,texture){return {v,normal,texture,c:v[0].map((_,i)=>v.reduce((s,p)=>s+p[i],0)/4)}}
function makeFaces(model){
  const faces=[],grid=new Map(model.columns.map(c=>[`${c[0]},${c[1]}`,c]));
  for(const [x,z,h,top,side,role] of model.columns){
    faces.push(quad([[x,h,z],[x+1,h,z],[x+1,h,z+1],[x,h,z+1]],0,top));
    for(const [dx,dz,n] of [[1,0,1],[-1,0,2],[0,1,3],[0,-1,4]]){
      const low=grid.get(`${x+dx},${z+dz}`)?.[2]??0;
      if(low>=h)continue;
      let v;
      if(dx===1)v=[[x+1,low,z],[x+1,low,z+1],[x+1,h,z+1],[x+1,h,z]];
      if(dx===-1)v=[[x,low,z+1],[x,low,z],[x,h,z],[x,h,z+1]];
      if(dz===1)v=[[x+1,low,z+1],[x,low,z+1],[x,h,z+1],[x+1,h,z+1]];
      if(dz===-1)v=[[x,low,z],[x+1,low,z],[x+1,h,z],[x,h,z]];
      // Tile vertical cut faces in block-height strips, not one stretched texture.
      for(let bottom=low;bottom<h;bottom+=1){
        const top=Math.min(h,bottom+1),slice=v.map(p=>[p[0],p[1]===low?bottom:top,p[2]]);
        faces.push(quad(slice,n,side));
      }
    }
  }
  for(const [x,y,z,w,h,d,t] of model.boxes){
    faces.push(quad([[x,y+h,z],[x+w,y+h,z],[x+w,y+h,z+d],[x,y+h,z+d]],0,t));
    faces.push(quad([[x+w,y,z],[x+w,y,z+d],[x+w,y+h,z+d],[x+w,y+h,z]],1,t));
    faces.push(quad([[x,y,z+d],[x,y,z],[x,y+h,z],[x,y+h,z+d]],2,t));
    faces.push(quad([[x+w,y,z+d],[x,y,z+d],[x,y+h,z+d],[x+w,y+h,z+d]],3,t));
    faces.push(quad([[x,y,z],[x+w,y,z],[x+w,y+h,z],[x,y+h,z]],4,t));
  }
  return faces;
}
const meshes=DATA.models.map(makeFaces);
const standard={yaw:-.72,pitch:.78,zoom:1,target:[0,3.5,17.5]};
let camera={...standard,target:[...standard.target]},selected=DATA.selected,sync=false;
function basis(cam){
  const s=Math.sin(cam.yaw),c=Math.cos(cam.yaw),p=Math.sin(cam.pitch),q=Math.cos(cam.pitch);
  return {right:[c,0,-s],up:[-s*p,q,-c*p],eye:[s*q,p,c*q]};
}
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
function draw(canvas,index,cam){
  const rect=canvas.getBoundingClientRect();if(!rect.width)return;
  const dpr=Math.min(window.devicePixelRatio||1,2),w=Math.round(rect.width*dpr),h=Math.round(rect.height*dpr);
  if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}
  const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;
  const gradient=ctx.createLinearGradient(0,0,0,h);gradient.addColorStop(0,'#e8eee7');gradient.addColorStop(1,'#d1d9c4');
  ctx.fillStyle=gradient;ctx.fillRect(0,0,w,h);
  const b=basis(cam),scale=Math.min(w/44,h/36)*cam.zoom;
  const project=p=>{const a=p.map((v,i)=>v-cam.target[i]);return [w*.5+dot(a,b.right)*scale,h*.53-dot(a,b.up)*scale]};
  const visible=meshes[index].filter(f=>dot(normals[f.normal],b.eye)>.001).map(f=>[dot(f.c,b.eye),f]).sort((a,b)=>a[0]-b[0]);
  for(const [,f] of visible){
    const v=f.v.map(project),key=DATA.keys[f.texture],tex=images[key];
    const sw=tex.width,sh=tex.height;
    // A subpixel underfill avoids false white grid lines between coplanar blocks.
    const center=v.reduce((a,p)=>[a[0]+p[0]/4,a[1]+p[1]/4],[0,0]);
    const padded=v.map(p=>{const d=Math.hypot(p[0]-center[0],p[1]-center[1]);return p.map((q,i)=>q+(q-center[i])*.55/Math.max(1,d))});
    ctx.fillStyle=averages[key];ctx.beginPath();ctx.moveTo(...padded[0]);for(let i=1;i<4;i++)ctx.lineTo(...padded[i]);ctx.closePath();ctx.fill();
    ctx.save();ctx.beginPath();ctx.moveTo(...v[0]);for(let i=1;i<4;i++)ctx.lineTo(...v[i]);ctx.closePath();ctx.clip();
    ctx.transform((v[1][0]-v[0][0])/sw,(v[1][1]-v[0][1])/sw,(v[3][0]-v[0][0])/sh,(v[3][1]-v[0][1])/sh,...v[0]);
    ctx.drawImage(tex,0,0);ctx.restore();
    const shade=[0,.18,.30,.12,.26][f.normal];
    if(shade){ctx.fillStyle=`rgba(19,30,21,${shade})`;ctx.beginPath();ctx.moveTo(...v[0]);for(let i=1;i<4;i++)ctx.lineTo(...v[i]);ctx.closePath();ctx.fill()}
  }
}
const cards=[...document.querySelectorAll('.sample')],detail=document.querySelector('#detail');
function render(){
  cards.forEach((card,i)=>{card.setAttribute('aria-pressed',i===selected?'true':'false');draw(card.querySelector('canvas'),i,sync?camera:standard)});
  draw(detail,selected,camera);
}
function select(i){
  selected=i;const s=DATA.models[i].style;
  document.querySelector('#selected').textContent=`${s.letter} · ${s.name}　${s.width} 格通行`;
  document.querySelector('#tradeoff').textContent=s.tradeoff;render();
}
cards.forEach((card,i)=>card.addEventListener('click',()=>select(i)));
document.querySelectorAll('[data-view]').forEach(button=>button.addEventListener('click',()=>{
  const views={overview:standard,near:{yaw:-.45,pitch:.87,zoom:2.2,target:[0,3.5,10]},top:{yaw:0,pitch:1.54,zoom:1,target:[0,3.5,17.5]},slope:{yaw:-1.12,pitch:.36,zoom:1.85,target:[0,5,22]}};
  camera={...views[button.dataset.view],target:[...views[button.dataset.view].target]};render();
}));
document.querySelector('#compare').addEventListener('click',e=>{sync=!sync;e.currentTarget.textContent=sync?'固定四段视角':'同步旋转四段';render()});
let pointers=new Map(),previous=null;
detail.addEventListener('pointerdown',e=>{detail.setPointerCapture(e.pointerId);pointers.set(e.pointerId,[e.clientX,e.clientY]);previous=pointers.size===1?[e.clientX,e.clientY]:null});
detail.addEventListener('pointermove',e=>{
  if(!pointers.has(e.pointerId))return;
  const before=[...pointers.values()];pointers.set(e.pointerId,[e.clientX,e.clientY]);
  if(pointers.size===2){const after=[...pointers.values()];const distance=p=>Math.hypot(p[0][0]-p[1][0],p[0][1]-p[1][1]);camera.zoom=Math.max(.7,Math.min(3.5,camera.zoom*distance(after)/Math.max(1,distance(before))))}
  else if(previous){camera.yaw+=(e.clientX-previous[0])*.008;camera.pitch=Math.max(.2,Math.min(1.54,camera.pitch+(e.clientY-previous[1])*.006));previous=[e.clientX,e.clientY]}
  render();
});
for(const event of ['pointerup','pointercancel'])detail.addEventListener(event,e=>{pointers.delete(e.pointerId);previous=null});
detail.addEventListener('wheel',e=>{e.preventDefault();camera.zoom=Math.max(.7,Math.min(3.5,camera.zoom*Math.exp(-e.deltaY*.001)));render()},{passive:false});
new ResizeObserver(render).observe(document.querySelector('main'));
select(selected);window.roadPreview={ready:true,models:DATA.models.map(m=>({id:m.style.id,checks:m.checks})),select,render,get selected(){return selected},get camera(){return camera}};
