(() => {
'use strict';
const root=document.getElementById('astra-houses'), canvas=document.getElementById('astra-canvas'), labels=document.getElementById('astra-labels');
const layerInput=document.getElementById('astra-layer'), namesInput=document.getElementById('astra-names'), routeInput=document.getElementById('astra-route');
const gl=canvas.getContext('webgl',{alpha:true,antialias:true,preserveDrawingBuffer:true});
if(!gl){document.getElementById('astra-error').hidden=false;return;}
const palettes=[
 ['草方块','#71825b','grass'],['泥土','#806044','noise'],['砂砾','#aaa28d','noise'],['泥土径','#9b8968','noise'],
 ['去皮云杉木','#80613d','log'],['云杉木板','#a27f4f','plank'],['方解石','#d8d4bb','stone'],['深板岩瓦','#46515a','tile'],
 ['石砖','#929084','brick'],['红色陶瓦','#a75a42','noise'],['深色橡木','#4b382b','plank'],['白桦木板','#c6ad79','plank'],
 ['平滑砂岩','#cdbb8e','stone'],['氧化的切制铜','#578e7e','copper'],['凝灰岩','#828577','stone'],['玻璃','#a7c8bf','glass'],
 ['床褥','#a94e42','cloth'],['枕头','#e0d8bc','cloth'],['树叶','#4a6d42','leaf'],['耕地','#74543a','furrow'],
 ['灰色地毯','#747d79','cloth'],['木门','#715332','door'],['红砖','#9e664d','brick'],['熔炉','#666963','furnace'],
 ['箱子','#ac7c36','chest'],['麦田','#aea052','leaf'],['深色原木','#5a4532','log'],['金合欢木','#aa7453','log'],['水','#598c99','water'],['书册','#a58460','book'],['陶罐','#bc8a68','stone'],['白花叶','#b1b8a0','bloom']
];
const MAT=Object.fromEntries(palettes.map((p,i)=>[p[0],i]));
function rgb(hex){return [parseInt(hex.slice(1,3),16),parseInt(hex.slice(3,5),16),parseInt(hex.slice(5,7),16)];}
function hash(a,b,c=0){let n=Math.imul(a+37,374761393)^Math.imul(b+19,668265263)^Math.imul(c+1,1442695041);n=Math.imul(n^(n>>>13),1274126177);return ((n^(n>>>16))>>>0)/4294967295;}
const atlas=document.createElement('canvas');atlas.width=128;atlas.height=64;const ac=atlas.getContext('2d');
palettes.forEach(([name,color,type],id)=>{const base=rgb(color),tx=id%8*16,ty=Math.floor(id/8)*16;
 for(let y=0;y<16;y++)for(let x=0;x<16;x++){
  let d=(hash(x,y,id)-.5)*20,alpha=255;
  if(type==='plank'){if(y%4===0)d-=22;if((x+(Math.floor(y/4)%2)*8)%16===0)d-=20;d+=(hash(x,Math.floor(y/2),id)-.5)*12;}
  if(type==='log'){d+=(hash(x,0,id)-.5)*38;if(x===1||x===14)d-=17;}
  if(type==='brick'){if(y%8===0||(x+Math.floor(y/8)*8)%16===0)d-=28;}
  if(type==='tile'){if(y%4===0)d-=20;if((x+Math.floor(y/4)*4)%8===0)d-=18;}
  if(type==='copper'){if(x===0||y===0)d-=28;if(x===1||y===1)d+=14;d+=(hash(Math.floor(x/3),Math.floor(y/3),id)-.5)*24;}
  if(type==='stone')d+=(hash(Math.floor(x/4),Math.floor(y/4),id)-.5)*14;
  if(type==='grass'||type==='leaf')d+=(hash(Math.floor(x/2),Math.floor(y/2),id)-.5)*26;
  if(type==='furrow'&&y%4<2)d-=22;
  if(type==='glass'){alpha=x===0||y===0||x===15||y===15?155:55;d=x===y||x===y+1?32:0;}
  if(type==='water'){alpha=205;d=(hash(x,Math.floor(y/3),id)-.5)*15;}if(type==='book'){d=x%3===0?-30:((x%5)*9-15);if(y===1||y===14)d=-35;}if(type==='bloom'){d=(hash(x,y,id)-.5)*65;}if(type==='cloth')d=(hash(x,y,id)-.5)*9;
  if(type==='door'){if(x===1||x===14||y===1||y===14)d-=25;if((x===3||x===12)&&y>4&&y<12)d-=12;}
  if(type==='furnace'&&x>3&&x<12&&y>3&&y<9)d-=62;
  if(type==='chest'){if(y===5||x===0||x===15)d-=45;if(x>6&&x<9&&y>4&&y<9)d+=45;}
  ac.fillStyle=`rgba(${base.map(v=>Math.max(0,Math.min(255,v+d))).join(',')},${alpha/255})`;ac.fillRect(tx+x,ty+y,1,1);
 }
});
const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,atlas);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
function shader(type,src){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function program(v,f){const p=gl.createProgram();gl.attachShader(p,shader(gl.VERTEX_SHADER,v));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,f));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
const vs=`attribute vec3 aP;attribute vec3 aN;attribute vec2 aUV;attribute float aM;uniform mat4 uVP;uniform mat4 uLight;varying vec3 vN;varying vec3 vP;varying vec2 vUV;varying float vM;varying vec4 vL;void main(){vN=aN;vP=aP;vUV=aUV;vM=aM;vL=uLight*vec4(aP,1.);gl_Position=uVP*vec4(aP,1.);}`;
const fs=`precision highp float;uniform sampler2D uTex;uniform sampler2D uShadow;uniform float uShadowOn;varying vec3 vN;varying vec3 vP;varying vec2 vUV;varying float vM;varying vec4 vL;float depth(vec4 c){return dot(c,vec4(1.,1./255.,1./65025.,1./16581375.));}void main(){float m=floor(vM+.1);vec2 tile=vec2(mod(m,8.),floor(m/8.));vec2 uv=(tile*16.+floor(fract(vUV)*16.)+.5)/vec2(128.,64.);vec4 c=texture2D(uTex,uv);vec3 n=normalize(vN);float sun=max(0.,dot(n,normalize(vec3(-.55,.85,.4))));vec3 l=vL.xyz/vL.w*.5+.5;float shade=1.;if(uShadowOn>.5&&l.x>0.&&l.x<1.&&l.y>0.&&l.y<1.){float s=0.;float bias=.0013+.001*(1.-sun);for(int i=0;i<2;i++)for(int j=0;j<2;j++){float z=depth(texture2D(uShadow,l.xy+(vec2(float(i),float(j))-.5)/1536.));s+=(l.z-bias>z)?0.55:1.;}shade=s*.25;}float light=.40+.48*sun*shade+.12*max(0.,n.y);float grain=.985+.03*fract(sin(dot(floor(vP),vec3(12.9898,78.233,45.164)))*43758.5453);gl_FragColor=vec4(c.rgb*light*grain,c.a);}`;
const main=program(vs,fs);const dep=program(`attribute vec3 aP;uniform mat4 uVP;void main(){gl_Position=uVP*vec4(aP,1.);}`,`precision highp float;vec4 pack(float v){vec4 r=fract(v*vec4(1.,255.,65025.,16581375.));r-=r.yzww*vec4(1./255.,1./255.,1./255.,0.);return r;}void main(){gl_FragColor=pack(gl_FragCoord.z);}`);
const size=1536, shadowTex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,shadowTex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,size,size,0,gl.RGBA,gl.UNSIGNED_BYTE,null);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
const sfbo=gl.createFramebuffer(),drb=gl.createRenderbuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,sfbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,shadowTex,0);gl.bindRenderbuffer(gl.RENDERBUFFER,drb);gl.renderbufferStorage(gl.RENDERBUFFER,gl.DEPTH_COMPONENT16,size,size);gl.framebufferRenderbuffer(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.RENDERBUFFER,drb);const shadowOK=gl.checkFramebufferStatus(gl.FRAMEBUFFER)===gl.FRAMEBUFFER_COMPLETE;gl.bindFramebuffer(gl.FRAMEBUFFER,null);
const sub=(a,b)=>a.map((v,i)=>v-b[i]),dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],norm=a=>{let l=Math.hypot(...a);return a.map(v=>v/l);};
function mul(a,b){let o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o;}
function look(eye,target,up=[0,1,0]){const f=norm(sub(target,eye)),r=norm(cross(f,up)),u=cross(r,f);return new Float32Array([r[0],u[0],-f[0],0,r[1],u[1],-f[1],0,r[2],u[2],-f[2],0,-dot(r,eye),-dot(u,eye),dot(f,eye),1]);}
function ortho(l,r,b,t,n,f){return new Float32Array([2/(r-l),0,0,0,0,2/(t-b),0,0,0,0,-2/(f-n),0,-(r+l)/(r-l),-(t+b)/(t-b),-(f+n)/(f-n),1]);}
function perspective(fov,a,n,f){const q=1/Math.tan(fov/2);return new Float32Array([q/a,0,0,0,0,q,0,0,0,0,(f+n)/(n-f),-1,0,0,2*f*n/(n-f),0]);}
const world=p=>[p[0],p[1],-p[2]];
const lightVP=mul(ortho(-65,65,-65,65,1,210),look([-35,100,65],[32,0,-28]));
const designs=window.HouseDesigns;
let house=0,layer='full',fullPlot=false,viewName='overview',pending=true,eye=[],target=[],span=65,isPlan=false,isPerson=false;
let visibleBoxes=[],opaqueCount=0,glassCount=0,shadowDirty=true;
const opaqueBuffer=gl.createBuffer(),glassBuffer=gl.createBuffer();
function emitBox(b,out){
 const {x0,x1,y0,y1,z0,z1}=b,mat=MAT[b.mat];
 if(mat===undefined)throw new Error('未知材质：'+b.mat);
 const faces=[[[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[0,0,-1],x1-x0,y1-y0],[[x1,y0,z1],[x0,y0,z1],[x0,y1,z1],[x1,y1,z1],[0,0,1],x1-x0,y1-y0],[[x0,y0,z1],[x0,y0,z0],[x0,y1,z0],[x0,y1,z1],[-1,0,0],z1-z0,y1-y0],[[x1,y0,z0],[x1,y0,z1],[x1,y1,z1],[x1,y1,z0],[1,0,0],z1-z0,y1-y0],[[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1],[0,1,0],x1-x0,z1-z0],[[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0],[0,-1,0],x1-x0,z1-z0]];
 for(const f of faces){const uv=[[0,0],[f[5],0],[f[5],f[6]],[0,f[6]]];for(const i of [0,1,2,0,2,3])out.push(f[i][0],f[i][1],-f[i][2],f[4][0],f[4][1],-f[4][2],uv[i][0],uv[i][1],mat);}
}
function routeBoxes(){
 const out=[],d=designs[house];
 if(!routeInput.checked||fullPlot)return out;
 for(let ri=0;ri<d.routes.length;ri++){
  const route=d.routes[ri];
  for(let i=0;i<route.points.length-1;i++){
   const a=route.points[i],b=route.points[i+1],n=Math.ceil(Math.hypot(b[0]-a[0],b[2]-a[2])/.7);
   for(let k=0;k<=n;k++){
    let x=a[0]+(b[0]-a[0])*k/n,y=a[1]+(b[1]-a[1])*k/n,z=a[2]+(b[2]-a[2])*k/n;
    const possible=d.boxes.filter(v=>(v.kind==='floor'||v.kind==='stair')&&x>=v.x0&&x<v.x1&&z>=v.z0&&z<v.z1&&Math.abs(v.y1-y)<.65).map(v=>v.y1);if(possible.length)y=possible.sort((p,q)=>Math.abs(p-y)-Math.abs(q-y))[0];
    if(layer==='first'&&y>=d.cut)continue;
    out.push({x0:x-.14,x1:x+.14,y0:y+.065,y1:y+.14,z0:z-.14,z1:z+.14,mat:['枕头','氧化的切制铜','床褥','陶罐'][ri%4],kind:'route'});
   }
  }
 }
 return out;
}
function upload(){
 const d=designs[house],op=[],tr=[];visibleBoxes=[];
 for(const original of [...d.boxes,...routeBoxes()]){
  if(original.kind==='plot'&&!fullPlot)continue;
  if(layer!=='full'&&original.roof)continue;
  if(layer==='first'&&original.y0>=d.cut)continue;
  let b={...original};
  if(layer==='first')b.y1=Math.min(b.y1,d.cut);
  if(!fullPlot&&b.kind==='terrain'){b.x0=d.bounds[0]-1;b.x1=d.bounds[1]+1;b.z1=d.bounds[2]+1;}
  if(!fullPlot&&b.kind==='boundary'&&b.y1<=.11){b.x0=Math.max(b.x0,d.bounds[0]);b.x1=Math.min(b.x1,d.bounds[1]);}
  if(b.x1<=b.x0||b.y1<=b.y0)continue;
  visibleBoxes.push(b);emitBox(b,b.mat==='玻璃'||b.mat==='水'?tr:op);
 }
 gl.bindBuffer(gl.ARRAY_BUFFER,opaqueBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(op),gl.STATIC_DRAW);opaqueCount=op.length/9;
 gl.bindBuffer(gl.ARRAY_BUFFER,glassBuffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(tr),gl.STATIC_DRAW);glassCount=tr.length/9;
 shadowDirty=true;pending=true;refreshLabels();refreshText();
}
function refreshText(){
 const d=designs[house];
 document.getElementById('astra-summary').textContent=d.reason;
 document.getElementById('astra-question').textContent='请在 3D 中判断：'+d.question;
 document.getElementById('astra-space-note').textContent=`宅院 ${d.size} 格，坐标 x=${d.bounds[0]}～${d.bounds[1]}、z=0～${d.bounds[2]}；北侧连续留白 ${d.reserve} 格。普通门洞至少 2×3 格，楼梯净宽至少 3 格，每级进深 1 格、升高半格。`;
 const list=document.getElementById('astra-room-list');list.replaceChildren();for(const r of d.rooms){const p=document.createElement('div');p.textContent=`${r.name} · ${r.bounds[1]-r.bounds[0]}×${r.bounds[3]-r.bounds[2]} · y=${r.y}`;list.append(p);}
 root.querySelectorAll('[data-house]').forEach(b=>b.setAttribute('aria-pressed',String(+b.dataset.house===house)));
 root.querySelectorAll('[data-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.view===viewName)));
 const layerName={full:'完整建筑',roofless:'已移除屋面',first:`一层剖切 y≤${d.cut}`}[layer];
 document.getElementById('astra-mode').textContent=fullPlot?`宅田 64×72 · 北侧留白 ${d.reserve} · 北 ↑`:`${d.name} · ${layerName}${isPerson?' · 视点高 1.7 格':''}`;
 layerInput.value=layer;
}
function refreshLabels(){
 labels.replaceChildren();const d=designs[house],items=[];
 if(fullPlot){items.push({text:`连续留白 ${d.reserve} 格`,p:[32,.1,(d.bounds[2]+72)/2],dim:true},{text:'宅田东西宽 64 格',p:[32,0,1],dim:true},{text:'南北长 72 格',p:[62,0,36],dim:true},{text:`宅院 ${d.size}`,p:[32,Math.max(d.upperY,7),23],dim:true},{text:'北 ↑  z=72',p:[32,0,70],dim:true});}
 else if(namesInput.checked){const level=layer==='first'?1:(viewName==='upper'||layer==='roofless')?2:1;for(const r of d.rooms){if(r.level!==level)continue;items.push({text:r.name,p:r.p});}}
 for(const r of items){const el=document.createElement('span');el.className='room-label'+(r.dim?' dimension':'');el.textContent=r.text;el._p=r.p;labels.append(el);}pending=true;
}
function attrs(p,buffer){gl.bindBuffer(gl.ARRAY_BUFFER,buffer);for(const [name,n,off] of [['aP',3,0],['aN',3,12],['aUV',2,24],['aM',1,32]]){const l=gl.getAttribLocation(p,name);if(l<0)continue;gl.enableVertexAttribArray(l);gl.vertexAttribPointer(l,n,gl.FLOAT,false,36,off);}}
function project(p,m){const q=[p[0],p[1],-p[2],1],v=[0,0,0,0];for(let r=0;r<4;r++)for(let c=0;c<4;c++)v[r]+=m[c*4+r]*q[c];return [v[0]/v[3],v[1]/v[3],v[2]/v[3],v[3]];}
let frameCount=0;
function render(){
 if(!pending)return;pending=false;frameCount++;
 const rect=canvas.getBoundingClientRect(),dpr=Math.min(window.devicePixelRatio||1,2),w=Math.max(1,Math.round(rect.width*dpr)),h=Math.max(1,Math.round(rect.height*dpr));if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.CULL_FACE);
 if(shadowOK&&shadowDirty){gl.bindFramebuffer(gl.FRAMEBUFFER,sfbo);gl.viewport(0,0,size,size);gl.clearColor(1,1,1,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.disable(gl.BLEND);gl.useProgram(dep);attrs(dep,opaqueBuffer);gl.uniformMatrix4fv(gl.getUniformLocation(dep,'uVP'),false,lightVP);gl.drawArrays(gl.TRIANGLES,0,opaqueCount);gl.bindFramebuffer(gl.FRAMEBUFFER,null);shadowDirty=false;}
 gl.viewport(0,0,w,h);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
 const aspect=w/h,view=look(world(eye),world(target),isPlan?[0,0,-1]:[0,1,0]);
 const fit=Math.max(span,(isPlan?fullPlot?70:60:span*1.15)/aspect);
 const proj=isPerson?perspective(72*Math.PI/180,aspect,.08,400):ortho(-fit*aspect/2,fit*aspect/2,-fit/2,fit/2,.1,400),vp=mul(proj,view);
 gl.useProgram(main);gl.uniformMatrix4fv(gl.getUniformLocation(main,'uVP'),false,vp);gl.uniformMatrix4fv(gl.getUniformLocation(main,'uLight'),false,lightVP);
 gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,tex);gl.uniform1i(gl.getUniformLocation(main,'uTex'),0);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,shadowTex);gl.uniform1i(gl.getUniformLocation(main,'uShadow'),1);gl.uniform1f(gl.getUniformLocation(main,'uShadowOn'),shadowOK?1:0);
 gl.disable(gl.BLEND);attrs(main,opaqueBuffer);gl.drawArrays(gl.TRIANGLES,0,opaqueCount);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);attrs(main,glassBuffer);gl.drawArrays(gl.TRIANGLES,0,glassCount);gl.depthMask(true);
 const occupied=[];
 for(const e of labels.children){
  const p=project(e._p,vp);e.style.display=p[2]<-1||p[2]>1||p[3]<0?'none':'';if(e.style.display==='none')continue;
  const ew=e.offsetWidth,eh=e.offsetHeight,px=Math.max(ew/2+3,Math.min(rect.width-ew/2-3,(p[0]*.5+.5)*rect.width)),py=(-p[1]*.5+.5)*rect.height;
  let yy=py;for(const delta of [0,-26,26,-52,52,-78,78]){const y=py+delta;if(occupied.every(o=>Math.abs(o.x-px)>(o.w+ew)/2+3||Math.abs(o.y-y)>(o.h+eh)/2+3)){yy=y;break;}}
  occupied.push({x:px,y:yy,w:ew,h:eh});e.style.left=px+'px';e.style.top=yy+'px';
 }
 window.__housePreview={house:designs[house].name,houseIndex:house,layer,fullPlot,view:viewName,boxes:visibleBoxes.length,totalBoxes:designs[house].boxes.length,vertices:opaqueCount+glassCount,eye:[...eye],target:[...target],span,frameCount,canvas:[w,h],webglError:gl.getError(),shadow:shadowOK};
}
let restoring=false;
function save(){if(!restoring&&window.openai?.setWidgetState){try{const p=window.openai.setWidgetState({modelContent:{方案:designs[house].name,层次:layer,视角:viewName},privateContent:{house,layer,viewName}});if(p?.catch)p.catch(()=>{});}catch(e){}}}
function preset(v){
 const d=designs[house],c=d.cameras[v];viewName=v;fullPlot=v==='plot';isPlan=v==='plan'||fullPlot;isPerson=v==='person'||v==='gallery';eye=[...c.eye];target=[...c.target];span=c.span||d.cameras.overview.span;
 if(v==='overview'||v==='person'||v==='gallery'||v==='plot'){layer='full';namesInput.checked=false;}
 else if(v==='plan'){layer='first';namesInput.checked=true;}else{layer='roofless';namesInput.checked=true;}
 upload();save();
}
root.querySelectorAll('[data-house]').forEach(b=>b.addEventListener('click',()=>{house=+b.dataset.house;preset('overview');}));
root.querySelectorAll('[data-view]').forEach(b=>b.addEventListener('click',()=>preset(b.dataset.view)));
layerInput.addEventListener('change',()=>{layer=layerInput.value;upload();save();});namesInput.addEventListener('change',refreshLabels);routeInput.addEventListener('change',upload);
let pointers=new Map(),drag=null,pinch=null;
canvas.addEventListener('contextmenu',e=>e.preventDefault());
canvas.addEventListener('pointerdown',e=>{canvas.focus({preventScroll:true});canvas.setPointerCapture(e.pointerId);pointers.set(e.pointerId,[e.clientX,e.clientY]);drag={x:e.clientX,y:e.clientY,pan:e.button===2||e.shiftKey};if(pointers.size===2){const p=[...pointers.values()];pinch=Math.hypot(p[1][0]-p[0][0],p[1][1]-p[0][1]);}});
canvas.addEventListener('pointermove',e=>{
 if(!pointers.has(e.pointerId))return;pointers.set(e.pointerId,[e.clientX,e.clientY]);
 if(pointers.size===2){const p=[...pointers.values()],dist=Math.hypot(p[1][0]-p[0][0],p[1][1]-p[0][1]);if(pinch&&dist){if(isPerson){const f=norm(sub(target,eye)),move=(pinch-dist)*.04;eye=eye.map((a,i)=>a+f[i]*move);target=target.map((a,i)=>a+f[i]*move);}else span=Math.max(10,Math.min(160,span*pinch/dist));}pinch=dist;pending=true;return;}
 if(!drag)return;const dx=e.clientX-drag.x,dy=e.clientY-drag.y;drag.x=e.clientX;drag.y=e.clientY;
 if(drag.pan||e.shiftKey){let dir=norm(sub(target,eye)),right=norm(cross([dir[0],dir[1],-dir[2]],[0,1,0]));if(!Number.isFinite(right[0]))right=[1,0,0];const sc=(isPerson?15:span)/canvas.clientHeight,move=[-right[0]*dx*sc,isPerson?0:dy*sc,right[2]*dx*sc];if(isPlan){move[1]=0;move[2]=dy*sc;}eye=eye.map((a,i)=>a+move[i]);target=target.map((a,i)=>a+move[i]);}
 else if(isPerson){const v=sub(target,eye),r=Math.hypot(...v),yaw=Math.atan2(v[0],v[2])-dx*.006,pitch=Math.max(-1.4,Math.min(1.4,Math.asin(v[1]/r)+dy*.006));target=[eye[0]+r*Math.cos(pitch)*Math.sin(yaw),eye[1]+r*Math.sin(pitch),eye[2]+r*Math.cos(pitch)*Math.cos(yaw)];}
 else{const v=sub(eye,target),r=Math.hypot(...v),yaw=Math.atan2(v[0],-v[2])-dx*.006,pitch=Math.max(.04,Math.min(1.53,Math.asin(v[1]/r)+dy*.006));eye=[target[0]+r*Math.cos(pitch)*Math.sin(yaw),target[1]+r*Math.sin(pitch),target[2]-r*Math.cos(pitch)*Math.cos(yaw)];isPlan=false;}
 viewName='custom';pending=true;refreshText();
});
function end(e){pointers.delete(e.pointerId);drag=null;pinch=null;}canvas.addEventListener('pointerup',end);canvas.addEventListener('pointercancel',end);
canvas.addEventListener('wheel',e=>{e.preventDefault();if(isPerson){const f=norm(sub(target,eye)),s=-e.deltaY*.012;eye=eye.map((a,i)=>a+f[i]*s);target=target.map((a,i)=>a+f[i]*s);}else span=Math.max(10,Math.min(160,span*Math.exp(e.deltaY*.001)));pending=true;},{passive:false});
canvas.addEventListener('keydown',e=>{
 if(!isPerson||!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','w','a','s','d'].includes(e.key))return;e.preventDefault();
 const dx=target[0]-eye[0],dz=target[2]-eye[2],l=Math.hypot(dx,dz),f=[dx/l,0,dz/l],r=[f[2],0,-f[0]],amount=e.shiftKey?2:.8;
 const v=e.key==='ArrowUp'||e.key==='w'?f:e.key==='ArrowDown'||e.key==='s'?f.map(x=>-x):e.key==='ArrowRight'||e.key==='d'?r:r.map(x=>-x);
 eye=eye.map((a,i)=>a+v[i]*amount);target=target.map((a,i)=>a+v[i]*amount);pending=true;
});
window.__setHouseView=(i,v='overview',l)=>{house=i;preset(v);if(l){layer=l;upload();}render();return window.__housePreview;};
window.__houseGeometry=()=>({design:designs[house],visibleBoxes});
window.__setCamera=(e,t,s)=>{eye=[...e];target=[...t];if(s)span=s;isPlan=false;viewName='custom';pending=true;render();};
function restore(s){const p=s?.privateContent||s;if(!p)return;restoring=true;try{if(Number.isInteger(p.house)&&p.house>=0&&p.house<3)house=p.house;preset(designs[house].cameras[p.viewName]?p.viewName:'overview');if(['full','roofless','first'].includes(p.layer)){layer=p.layer;upload();}}finally{restoring=false;}}
preset('overview');restore(window.openai?.widgetState);window.addEventListener('openai:set_globals',e=>restore(e.detail?.globals?.widgetState));new ResizeObserver(()=>{pending=true;}).observe(canvas);
function loop(){render();requestAnimationFrame(loop);}requestAnimationFrame(loop);
})();
