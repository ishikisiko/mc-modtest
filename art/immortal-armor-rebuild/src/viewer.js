'use strict';
// Self-contained renderer. No CDN, fetch, package install, images, or network requests.
const $=id=>document.getElementById(id), esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const canvas=$('canvas'),gl=canvas.getContext('webgl',{alpha:true,antialias:true,preserveDrawingBuffer:true,premultipliedAlpha:false});
const state={index:12,other:0,part:'all',shade:'material',yaw:.36,pitch:.09,zoom:1,fx:true,body:true,spin:false,dark:false,compare:false};
const names={all:'整套',head:'冠',chest:'胸衣',legs:'护腿',boots:'靴',ingot:'锭'};
const cache=new Map();let model=null,otherModel=null,loadToken=0,dirty=true,program,attrs,unifs,stageMesh,last=0,drawCalls=0,lastError=null;
let readyResolve;window.atlasReady=new Promise(r=>readyResolve=r);
function toast(s){$('toast').textContent=s;$('toast').classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').classList.remove('show'),3200)}
function fail(e){lastError=String(e.stack||e);console.error(e);$('loading').classList.remove('hidden');$('load-text').textContent='模型加载失败：'+e.message+'。请使用支持 WebGL 和 DecompressionStream 的浏览器打开本地 HTML。';readyResolve?.(false)}
function hex(s){return [1,3,5].map(i=>Math.pow(parseInt(s.slice(i,i+2),16)/255,2.2))}
function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s}
const vs=`precision highp float;
attribute vec3 aPosition;attribute vec3 aNormal;attribute vec2 aUV;
uniform mat4 uViewProj;uniform vec3 uOffset;
varying vec3 vP;varying vec3 vN;varying vec2 vUV;
void main(){vP=aPosition+uOffset;vN=aNormal;vUV=aUV;gl_Position=uViewProj*vec4(vP,1.);}`;
const fs=`precision highp float;
varying vec3 vP;varying vec3 vN;varying vec2 vUV;
uniform vec3 uColor;uniform vec3 uEye;uniform float uMetal,uRough,uEmission,uAlpha,uKind,uMode,uGround,uGroundSize,uDark;
const float PI=3.14159265;
vec3 fresnel(float h,vec3 f0){return f0+(1.-f0)*pow(clamp(1.-h,0.,1.),5.);}
vec3 light(vec3 N,vec3 V,vec3 L,vec3 radiance,vec3 base,float metal,float rough){
 vec3 H=normalize(V+L);float nl=max(dot(N,L),0.),nv=max(dot(N,V),.001),nh=max(dot(N,H),0.),vh=max(dot(V,H),0.);
 float a=rough*rough,a2=a*a,d=nh*nh*(a2-1.)+1.;float D=a2/max(.0001,PI*d*d);
 float k=(rough+1.);k=k*k*.125;float G=(nv/(nv*(1.-k)+k))*(nl/(nl*(1.-k)+k));
 vec3 F=fresnel(vh,mix(vec3(.04),base,metal));vec3 spec=D*G*F/max(.0001,4.*nv*nl);
 vec3 diffuse=(1.-F)*(1.-metal)*base/PI;return (diffuse+spec)*radiance*nl;
}
vec3 aces(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
 if(uGround>.5){float d=length(vP.xz);float s=max(.10,uGroundSize);float opacity=(1.-smoothstep(s*.7,s*2.3,d))*.30;float shadow=exp(-dot(vP.xz,vP.xz)/(s*s*.17))*.40;vec3 c=mix(vec3(.64,.70,.75),vec3(.055,.085,.11),uDark);c*=1.-shadow;gl_FragColor=vec4(c,opacity);return;}
 if(uMode>1.5&&uMode<2.5){gl_FragColor=vec4(mix(vec3(.10,.16,.20),vec3(.82,.88,.90),uDark),1.);return;}
 if(uMode>2.5){gl_FragColor=vec4(mix(vec3(.20,.30,.38),vec3(.68,.78,.86),uDark),.74);return;}
 vec3 N=normalize(vN);if(!gl_FrontFacing)N=-N;vec3 V=normalize(uEye-vP);vec3 base=uColor;float rough=max(.10,uRough);float metal=uMetal;
 if(uMode>.5){base=vec3(.48,.49,.48);metal=.08;rough=.62;}
 // Subtle analytic weave/polish only; no external texture and no artificial panel seams.
 if(uKind>1.5&&uKind<2.5&&uMode<.5){float weave=sin(vUV.x*620.)*sin(vUV.y*530.);base*=.988+.012*weave;}
 if(uKind>.5&&uKind<1.5&&uMode<.5){base*=.97+.03*sin(vUV.y*34.+vUV.x*9.);}
 vec3 col=light(N,V,normalize(vec3(-2.8,4.0,3.5)),vec3(3.6,3.4,3.1),base,metal,rough);
 col+=light(N,V,normalize(vec3(3.,1.6,2.)),vec3(1.3,1.6,1.9),base,metal,rough);
 col+=light(N,V,normalize(vec3(.7,2.5,-3.)),vec3(3.2,3.6,3.8),base,metal,rough);
 float hemi=.32+.22*(N.y*.5+.5);col+=base*(1.-metal)*hemi;
 // Analytic studio reflections give broad, legible metallic highlights.
 vec3 R=reflect(-V,N);float nv=max(dot(N,V),0.);vec3 F=fresnel(nv,mix(vec3(.04),base,metal));
 float boxA=pow(max(dot(R,normalize(vec3(-.55,.60,.58))),0.),mix(85.,5.,rough));
 float boxB=pow(max(dot(R,normalize(vec3(.78,.30,-.40))),0.),mix(110.,7.,rough));
 vec3 env=vec3(.22,.25,.28)+vec3(1.35,1.25,1.10)*boxA+vec3(.60,.80,1.00)*boxB;
 col+=env*F*(.65+.40*metal);
 if(uKind>1.5&&uKind<2.5)col+=base*pow(1.-nv,4.)*.18;
 if(uMode<.5)col+=base*uEmission;
 col=aces(col*.95);col=pow(col,vec3(1./2.2));
 gl_FragColor=vec4(col,uMode>.5?1.:uAlpha);
}`;
function initGL(){
 if(!gl){softInit();return}program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vs));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fs));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
 attrs={};for(const n of ['Position','Normal','UV'])attrs[n]=gl.getAttribLocation(program,'a'+n);
 unifs={};for(const n of ['ViewProj','Offset','Color','Eye','Metal','Rough','Emission','Alpha','Kind','Mode','Ground','GroundSize','Dark'])unifs[n]=gl.getUniformLocation(program,'u'+n);
 gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.CULL_FACE);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
 stageMesh=upload({name:'展示棚地面',slot:'stage',material:'',positions:[-6,0,-6,6,0,-6,6,0,6,-6,0,6],normals:[0,1,0,0,1,0,0,1,0,0,1,0],uvs:[0,0,1,0,1,1,0,1],indices:[0,2,1,0,3,2]});
}
function upload(mesh){
 const gpu={...mesh};if(!gl){gpu.count=mesh.indices.length;gpu.min=[Infinity,Infinity,Infinity];gpu.max=[-Infinity,-Infinity,-Infinity];for(let i=0;i<mesh.positions.length;i+=3)for(let j=0;j<3;j++){gpu.min[j]=Math.min(gpu.min[j],mesh.positions[i+j]);gpu.max[j]=Math.max(gpu.max[j],mesh.positions[i+j])}gpu.center=gpu.min.map((a,i)=>(a+gpu.max[i])/2);return gpu}function buffer(data,type){let b=gl.createBuffer();gl.bindBuffer(type,b);gl.bufferData(type,data,gl.STATIC_DRAW);return b}
 const v=new Float32Array(mesh.positions),n=new Float32Array(mesh.normals),uv=new Float32Array(mesh.uvs||v.length/3*2);
 gpu.p=buffer(v,gl.ARRAY_BUFFER);gpu.n=buffer(n,gl.ARRAY_BUFFER);gpu.uv=buffer(uv,gl.ARRAY_BUFFER);
 if(v.length/3>65535)throw Error('单网格顶点数超过当前便携渲染器限制');gpu.idx=buffer(new Uint16Array(mesh.indices),gl.ELEMENT_ARRAY_BUFFER);gpu.count=mesh.indices.length;gpu.wire=null;
 gpu.min=[Infinity,Infinity,Infinity];gpu.max=[-Infinity,-Infinity,-Infinity];for(let i=0;i<v.length;i+=3)for(let j=0;j<3;j++){gpu.min[j]=Math.min(gpu.min[j],v[i+j]);gpu.max[j]=Math.max(gpu.max[j],v[i+j])}gpu.center=gpu.min.map((a,i)=>(a+gpu.max[i])/2);return gpu;
}
function dispose(m){if(!gl)return;for(const mesh of m.gpu)for(const k of ['p','n','uv','idx','wire'])if(mesh[k])gl.deleteBuffer(mesh[k])}
async function getModel(i){
 if(cache.has(i)){const m=cache.get(i);cache.delete(i);cache.set(i,m);return m}
 const bytes=Uint8Array.from(atob(PACKED[i]),c=>c.charCodeAt(0));const stream=new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate'));const m=JSON.parse(await new Response(stream).text());m.gpu=m.meshes.map(upload);cache.set(i,m);
 for(const [k,v] of cache){if(cache.size<=3)break;if(k!==state.index&&k!==state.other&&k!==i){dispose(v);cache.delete(k)}}return m;
}
function shown(m,exporting=false){
 return (exporting?m.meshes:m.gpu).filter(x=>{
  if(x.slot==='body'){if(exporting||!state.body||state.part==='ingot')return false;if(state.part==='all')return true;return state.part==='head'&&!x.name.includes('手')&&!x.name.includes('拇指')&&!x.name.includes('颈')}
  if(x.slot==='aura')return state.fx&&state.part==='all';
  if(state.part==='all')return x.slot!=='ingot';return x.slot===state.part;
 });
}
function bounds(ms){const a=[Infinity,Infinity,Infinity],b=[-Infinity,-Infinity,-Infinity];for(const m of ms)for(let i=0;i<3;i++){a[i]=Math.min(a[i],m.min[i]);b[i]=Math.max(b[i],m.max[i])}return {min:a,max:b,center:a.map((x,i)=>(x+b[i])/2),size:a.map((x,i)=>b[i]-x)}}
function multiply(a,b){let o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++){let s=0;for(let k=0;k<4;k++)s+=a[k*4+r]*b[c*4+k];o[c*4+r]=s}return o}
function perspective(fov,aspect,near,far){const f=1/Math.tan(fov/2),o=new Float32Array(16);o[0]=f/aspect;o[5]=f;o[10]=(far+near)/(near-far);o[11]=-1;o[14]=2*far*near/(near-far);return o}
function lookAt(eye,target){const sub=(a,b)=>a.map((v,i)=>v-b[i]),unit=a=>{const l=Math.hypot(...a);return a.map(x=>x/l)},cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);const z=unit(sub(eye,target)),x=unit(cross([0,1,0],z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1])}
function bindMesh(mesh){for(const [name,k,size] of [['Position','p',3],['Normal','n',3],['UV','uv',2]]){gl.bindBuffer(gl.ARRAY_BUFFER,mesh[k]);gl.enableVertexAttribArray(attrs[name]);gl.vertexAttribPointer(attrs[name],size,gl.FLOAT,false,0,0)}}
function drawMesh(mesh,mats,ground=false,offset=0,gs=.6){
 bindMesh(mesh);const mat=mats[mesh.material]||{color:'#aaaaaa',metal:0,rough:1};gl.uniform3fv(unifs.Offset,[0,offset,0]);gl.uniform3fv(unifs.Color,hex(mat.color));gl.uniform1f(unifs.Metal,mat.metal||0);gl.uniform1f(unifs.Rough,mat.rough??.5);gl.uniform1f(unifs.Emission,mat.emission||0);gl.uniform1f(unifs.Alpha,mat.alpha??1);gl.uniform1f(unifs.Kind,mat.kind||0);gl.uniform1f(unifs.Ground,ground?1:0);gl.uniform1f(unifs.GroundSize,gs);
 if(state.shade==='wire'&&!ground){if(!mesh.wire){const out=[];for(let i=0;i<mesh.indices.length;i+=3){const a=mesh.indices[i],b=mesh.indices[i+1],c=mesh.indices[i+2];out.push(a,b,b,c,c,a)}mesh.wire=gl.createBuffer();gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,mesh.wire);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(out),gl.STATIC_DRAW);mesh.wireCount=out.length}gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,mesh.wire);gl.drawElements(gl.LINES,mesh.wireCount,gl.UNSIGNED_SHORT,0)}else{gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,mesh.idx);gl.drawElements(gl.TRIANGLES,mesh.count,gl.UNSIGNED_SHORT,0)}drawCalls++;
}
function renderOne(m,x,y,w,h,shared){
 const ms=shown(m);if(!ms.length)return;const bb=bounds(ms);const aspect=w/h;let target,dist;
 if(state.part==='all'){target=[0,1.19,0];dist=Math.max(2.55/2/Math.tan(.34),1.64/aspect/2/Math.tan(.34))*1.08;}
 else{const b=shared||bb;target=b.center;dist=Math.max(b.size[1],b.size[0]/aspect,b.size[2])*.5/Math.tan(.34)*1.34;dist=Math.max(dist,.32)}
 dist*=state.zoom;const eye=[target[0]+Math.sin(state.yaw)*Math.cos(state.pitch)*dist,target[1]+Math.sin(state.pitch)*dist,target[2]+Math.cos(state.yaw)*Math.cos(state.pitch)*dist];const vp=multiply(perspective(.68,aspect,.025,40),lookAt(eye,target));
 gl.viewport(x,y,w,h);gl.uniformMatrix4fv(unifs.ViewProj,false,vp);gl.uniform3fv(unifs.Eye,eye);gl.uniform1f(unifs.Mode,({material:0,clay:1,silhouette:2,wire:3})[state.shade]);gl.uniform1f(unifs.Dark,state.dark?1:0);
 if(['all','ingot','boots'].includes(state.part)&&state.shade!=='wire'){gl.depthMask(false);drawMesh(stageMesh,{},true,bb.min[1]-.009,state.part==='all'?.72:Math.max(bb.size[0],bb.size[2])*.8);gl.depthMask(true)}
 const opaque=[],transparent=[];for(const mesh of ms){if((m.materials[mesh.material].alpha??1)<1&&state.shade==='material')transparent.push(mesh);else opaque.push(mesh)}
 for(const mesh of opaque)drawMesh(mesh,m.materials);transparent.sort((a,b)=>Math.hypot(...b.center.map((v,i)=>v-eye[i]))-Math.hypot(...a.center.map((v,i)=>v-eye[i])));gl.depthMask(false);for(const mesh of transparent)drawMesh(mesh,m.materials);gl.depthMask(true);
}
function render(){
 if(!model)return;if(!gl){softRender();return}const rect=canvas.getBoundingClientRect();if(rect.width<5||rect.height<5)return;const dpr=Math.min(window.devicePixelRatio||1,1.75);const w=Math.round(rect.width*dpr),h=Math.round(rect.height*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}
 gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);drawCalls=0;
 if(state.compare&&otherModel){const half=Math.floor(w/2);const bb=bounds([...shown(model),...shown(otherModel)]);renderOne(model,0,0,half,h,bb);renderOne(otherModel,half,0,w-half,h,bb)}else renderOne(model,0,0,w,h);
 dirty=false;
}
function tick(t){if(state.spin&&model){state.yaw+=(t-last||16)*.00018;dirty=true}last=t;if(dirty)render();requestAnimationFrame(tick)}
function thumb(s,cls='thumb'){return THUMBS[s.id]?`<img class="${cls}" src="${THUMBS[s.id]}" alt="${s.name}同源模型渲染" loading="lazy">`:`<div class="${cls} thumb-empty">${s.name}</div>`}
function buildMenus(){
 let html='',gallery='';for(let r=1;r<=5;r++){const list=CATALOG.filter(s=>s.level===r),s=list[0];html+=`<div class="side-rank"><div class="rank-title"><b>0${r} / ${esc(s.realm)}</b><span>${esc(s.rank)}</span></div>`;gallery+=`<section class="gallery-row"><div class="gallery-heading"><h2>0${r} · ${esc(s.realm)}</h2><span>${esc(s.rank)}</span></div><div class="gallery-grid">`;
 for(const s of list){const i=CATALOG.indexOf(s);html+=`<button class="set-card" data-index="${i}" aria-label="查看${esc(s.realm+s.name)}">${thumb(s)}<div><div class="set-name"><small>${s.id.slice(0,2)}</small>${esc(s.name)}</div><div class="set-route">${esc(s.route)}<br>${esc(s.ingot)}</div></div></button>`;gallery+=`<button class="gallery-card" data-index="${i}">${thumb(s,'gallery-image')}<div class="gallery-info"><h3><span>${s.id.slice(0,2)}</span>${esc(s.name)}</h3><p>${esc(s.route)}</p><p>${esc(s.ingot)}</p><p>${esc(s.tag)}</p><div class="swatches">${s.colors.map(c=>`<span class="swatch" style="background:${c};height:10px"></span>`).join('')}</div></div></button>`}html+='</div>';gallery+='</div></section>'}
 $('set-list').innerHTML=html;$('gallery-content').innerHTML=gallery;$('compare-select').innerHTML=CATALOG.map((s,i)=>`<option value="${i}">${s.realm} · ${s.name}</option>`).join('');
 document.querySelectorAll('[data-index]').forEach(el=>el.addEventListener('click',()=>{setPage('studio');selectSet(Number(el.dataset.index))}));
}
function detail(){
 const s=CATALOG[state.index],m=s.materials;const keys=['metal','cloth','inner','trim','gem'];$('detail').innerHTML=`<div class="kicker">${s.id.slice(0,2)} / ${esc(s.realm)} · ${esc(s.rank)}</div><h1>${esc(s.name)}</h1><div class="chips"><span class="chip">${esc(s.route)}</span><span class="chip">概念提案</span></div><div class="tag">${esc(s.tag)}</div><div class="swatches">${keys.map(k=>`<div class="swatch" tabindex="0" role="button" data-mat="${k}" style="background:${m[k].color}" title="${esc(m[k].name)}"></div>`).join('')}</div><div class="material-caption" id="material-caption">主护面 / 衣绫 / 内衬 / 嵌边 / 灵髓<br>点击色块查看材质参数</div><details open><summary>设计判断</summary><p>${esc(s.idea)}</p><p><b style="color:var(--gold);font-weight:400">视觉焦点</b><br>${esc(s.focus)}</p></details><details open><summary>四件拆解</summary>${s.parts.map((p,i)=>`<div class="part-note"><b>${['冠 · 头部','胸衣 · 胸部','护腿 · 腿部','靴 · 足部'][i]}</b><p>${esc(p)}</p></div>`).join('')}</details><details><summary>锭与材质</summary><p style="color:var(--gold)">${esc(s.ingot)}</p><p>${esc(s.material)}</p><p>${esc(s.ingotDesign)}</p><p class="small">主材金属度 ${s.metal.toFixed(2)} · 粗糙度 ${s.rough.toFixed(2)}<br>材质数值为本轮美术参数，不是游戏属性。</p></details><details><summary>法相与局部分片</summary><p>${esc(s.effect)}</p><p>${esc(s.fragment)}</p></details><details><summary>玩法方向与模型信息</summary><p>${esc(s.play)}</p><p class="small">本套含人台与锭合计 ${s.stats.meshes} 个网格 / ${s.stats.triangles.toLocaleString()} 个三角面。尚未进行游戏 LOD、骨骼绑定或运行时性能优化。</p></details><div class="notice">本次交付为完整建模设计，不是可安装模组。真实游戏动画、穿模、多人表现与用户审美认可均待验证。</div>`;
 $('detail').querySelectorAll('[data-mat]').forEach(e=>{const update=()=>{const mm=m[e.dataset.mat];$('material-caption').textContent=`${mm.name} · ${mm.color} · 金属度 ${mm.metal.toFixed(2)} / 粗糙度 ${mm.rough.toFixed(2)}`};e.addEventListener('click',update);e.addEventListener('keydown',ev=>{if(ev.key==='Enter')update()})});
 $('stage-kicker').textContent=`${s.id.slice(0,2)} / ${s.realm} · ${s.rank}`;$('stage-name').textContent=state.part==='ingot'?s.ingot:s.name;$('stage-route').textContent=state.part==='ingot'?'共用扁锭形制 · 同套装灵材':s.route;$('stage-badge').textContent=s.realm+' · '+names[state.part];
 const ms=model?shown(model):[];$('stage-stats').textContent=`${ms.length} 个可见网格 / ${ms.reduce((n,m)=>n+m.indices.length/3,0).toLocaleString()} 三角面`;
 document.querySelectorAll('.set-card').forEach(e=>{e.classList.toggle('selected',Number(e.dataset.index)===state.index);e.setAttribute('aria-pressed',Number(e.dataset.index)===state.index?'true':'false')});
 $('label-a').textContent=s.name;$('label-b').textContent=CATALOG[state.other].name;
}
async function selectSet(i,reset=true){const token=++loadToken;state.index=(i+15)%15;$('loading').classList.remove('hidden');try{const m=await getModel(state.index);if(token!==loadToken)return;model=m;if(state.compare)otherModel=await getModel(state.other);if(reset){state.zoom=1;state.yaw=.36;state.pitch=state.part==='ingot'?.64:.09}detail();$('loading').classList.add('hidden');dirty=true;render();return true}catch(e){fail(e);return false}}
function setPart(p){state.part=p;state.zoom=1;state.yaw=.36;state.pitch=p==='ingot'?.65:.09;document.querySelectorAll('[data-part]').forEach(e=>e.classList.toggle('active',e.dataset.part===p));detail();dirty=true;render()}
function setPage(p){document.querySelectorAll('.page').forEach(e=>e.classList.toggle('active',e.id===p));document.querySelectorAll('[data-page]').forEach(e=>e.classList.toggle('active',e.dataset.page===p));dirty=true;setTimeout(render,30)}
function capture(){render();const c=document.createElement('canvas');c.width=canvas.width;c.height=canvas.height;const ctx=c.getContext('2d');const g=ctx.createRadialGradient(c.width*.46,c.height*.24,c.width*.05,c.width*.5,c.height*.42,c.height*.9);if(state.dark){g.addColorStop(0,'#354452');g.addColorStop(1,'#111b25')}else{g.addColorStop(0,'#e4e7e6');g.addColorStop(.5,'#c9d0d3');g.addColorStop(1,'#aebbc4')}ctx.fillStyle=g;ctx.fillRect(0,0,c.width,c.height);ctx.drawImage(canvas,0,0);return c.toDataURL('image/png')}
function save(data,name,type){const url=typeof data==='string'&&data.startsWith('data:')?data:URL.createObjectURL(new Blob([data],{type:type||'application/octet-stream'}));const a=document.createElement('a');a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();if(url.startsWith('blob:'))setTimeout(()=>URL.revokeObjectURL(url),30000)}
function exportGLB(){
 const meshes=shown(model,true),keys=Object.keys(model.materials),materials=keys.map(k=>{const m=model.materials[k],c=[1,3,5].map(i=>parseInt(m.color.slice(i,i+2),16)/255);const out={name:m.name,pbrMetallicRoughness:{baseColorFactor:[...c,m.alpha??1],metallicFactor:m.metal||0,roughnessFactor:m.rough??.5},doubleSided:true};if(m.emission)out.emissiveFactor=c.map(v=>v*m.emission);if((m.alpha??1)<1)out.alphaMode='BLEND';return out});
 const chunks=[],views=[],accessors=[],gm=[],nodes=[];let offset=0;
 function acc(array,type,component,target){const raw=new Uint8Array(array.buffer);const pad=(4-offset%4)%4;if(pad){chunks.push(new Uint8Array(pad));offset+=pad}const vi=views.length;views.push({buffer:0,byteOffset:offset,byteLength:raw.length,target});chunks.push(raw);offset+=raw.length;const width=type==='SCALAR'?1:Number(type.slice(-1)),a={bufferView:vi,componentType:component,count:array.length/width,type};if(type==='VEC3'){a.min=[Infinity,Infinity,Infinity];a.max=[-Infinity,-Infinity,-Infinity];for(let i=0;i<array.length;i++)a.min[i%3]=Math.min(a.min[i%3],array[i]),a.max[i%3]=Math.max(a.max[i%3],array[i])}accessors.push(a);return accessors.length-1}
 for(const m of meshes){const attrs={POSITION:acc(new Float32Array(m.positions),'VEC3',5126,34962),NORMAL:acc(new Float32Array(m.normals),'VEC3',5126,34962)};if(m.uvs)attrs.TEXCOORD_0=acc(new Float32Array(m.uvs),'VEC2',5126,34962);gm.push({name:m.name,primitives:[{attributes:attrs,indices:acc(new Uint32Array(m.indices),'SCALAR',5125,34963),material:keys.indexOf(m.material)}]});nodes.push({name:m.name,mesh:gm.length-1,extras:{slot:m.slot}})}
 const g={asset:{version:'2.0',generator:'仙铸离线设计册'},scene:0,scenes:[{nodes:nodes.map((_,i)=>i)}],nodes,meshes:gm,materials,accessors,bufferViews:views,buffers:[{byteLength:offset}]};let jb=new TextEncoder().encode(JSON.stringify(g));const jp=(4-jb.length%4)%4,bp=(4-offset%4)%4;let out=new Uint8Array(12+8+jb.length+jp+8+offset+bp),dv=new DataView(out.buffer);dv.setUint32(0,0x46546c67,true);dv.setUint32(4,2,true);dv.setUint32(8,out.length,true);dv.setUint32(12,jb.length+jp,true);dv.setUint32(16,0x4e4f534a,true);out.set(jb,20);out.fill(32,20+jb.length,20+jb.length+jp);let pos=20+jb.length+jp;dv.setUint32(pos,offset+bp,true);dv.setUint32(pos+4,0x004e4942,true);pos+=8;for(const chunk of chunks){out.set(chunk,pos);pos+=chunk.length}return out;
}
function exportOBJ(){const meshes=shown(model,true),lines=['# 仙铸: actual source geometry; +Y up; metres'];let off=1;for(const m of meshes){lines.push('o '+m.name.replace(/\s/g,'_'));for(let i=0;i<m.positions.length;i+=3)lines.push('v '+m.positions.slice(i,i+3).join(' '));for(let i=0;i<m.normals.length;i+=3)lines.push('vn '+m.normals.slice(i,i+3).join(' '));for(let i=0;i<m.indices.length;i+=3)lines.push('f '+m.indices.slice(i,i+3).map(n=>`${n+off}//${n+off}`).join(' '));off+=m.positions.length/3}return lines.join('\n')}
function bindUI(){
 document.querySelectorAll('[data-page]').forEach(e=>e.onclick=()=>setPage(e.dataset.page));document.querySelectorAll('[data-part]').forEach(e=>e.onclick=()=>setPart(e.dataset.part));
 document.querySelectorAll('[data-shade]').forEach(e=>e.onclick=()=>{state.shade=e.dataset.shade;document.querySelectorAll('[data-shade]').forEach(x=>x.classList.toggle('active',x===e));dirty=true});
 document.querySelectorAll('[data-view]').forEach(e=>e.onclick=()=>{state.yaw=({front:0,iso:.44,side:Math.PI/2,back:Math.PI})[e.dataset.view];state.pitch=state.part==='ingot'?.65:e.dataset.view==='iso'?.14:.04;dirty=true});
 for(const key of ['fx','body','rotate'])$(key).onchange=e=>{state[key==='rotate'?'spin':key]=e.target.checked;detail();dirty=true};
 $('theme').onclick=()=>{state.dark=!state.dark;$('stage').classList.toggle('dark',state.dark);$('theme').textContent=state.dark?'浅色棚光':'深色棚光';dirty=true};
 const reset=()=>{state.zoom=1;state.yaw=.36;state.pitch=state.part==='ingot'?.65:.09;dirty=true};$('reset').onclick=reset;canvas.ondblclick=reset;
 $('compare-toggle').onclick=async()=>{state.compare=!state.compare;$('compare-toggle').classList.toggle('active',state.compare);$('compare-row').classList.toggle('show',state.compare);$('stage-label').classList.toggle('hidden',state.compare);$('stage-badge').classList.toggle('hidden',state.compare);document.querySelectorAll('.compare-label').forEach(e=>e.classList.toggle('show',state.compare));if(state.compare)otherModel=await getModel(state.other);detail();dirty=true};
 $('compare-select').onchange=async e=>{state.other=Number(e.target.value);otherModel=await getModel(state.other);detail();dirty=true};
 $('export-png').onclick=()=>save(capture(),CATALOG[state.index].id+'-'+state.part+'.png');$('export-glb').onclick=()=>{save(exportGLB(),CATALOG[state.index].id+'-'+state.part+'.glb','model/gltf-binary');toast('已导出真实模型，不含人台')};$('export-obj').onclick=()=>{save(exportOBJ(),CATALOG[state.index].id+'-'+state.part+'.obj','text/plain');toast('OBJ 导出几何与法线；完整包另含 MTL 材质')};
 const pointers=new Map();
 canvas.onpointerdown=e=>{canvas.setPointerCapture(e.pointerId);pointers.set(e.pointerId,[e.clientX,e.clientY])};
 canvas.onpointermove=e=>{if(!pointers.has(e.pointerId))return;const old=pointers.get(e.pointerId);const other=[...pointers.entries()].find(([id])=>id!==e.pointerId)?.[1];
  if(other){const before=Math.hypot(old[0]-other[0],old[1]-other[1]);const after=Math.hypot(e.clientX-other[0],e.clientY-other[1]);if(before>5&&after>5)state.zoom=Math.max(.32,Math.min(3.2,state.zoom*before/after));}
  else{state.yaw-=(e.clientX-old[0])*.008;state.pitch=Math.max(-1.1,Math.min(1.15,state.pitch+(e.clientY-old[1])*.006));}
  pointers.set(e.pointerId,[e.clientX,e.clientY]);dirty=true;};
 canvas.onpointerup=canvas.onpointercancel=e=>pointers.delete(e.pointerId);
 canvas.addEventListener('wheel',e=>{e.preventDefault();state.zoom=Math.max(.32,Math.min(3.2,state.zoom*Math.exp(e.deltaY*.001)));dirty=true},{passive:false});
 document.addEventListener('keydown',e=>{if(['INPUT','SELECT','TEXTAREA'].includes(e.target.tagName))return;if(e.key==='ArrowRight')selectSet(state.index+1);if(e.key==='ArrowLeft')selectSet(state.index-1)});new ResizeObserver(()=>{dirty=true}).observe(canvas);
}
window.atlas={state,select:async(i)=>{setPage('studio');await selectSet(i);render()},part:p=>setPart(p),render,capture,exportGLB,exportOBJ,info:()=>({index:state.index,id:CATALOG[state.index].id,part:state.part,visible:shown(model).length,triangles:shown(model).reduce((s,m)=>s+m.count/3,0),glError:gl?gl.getError():null,renderer:gl?'WebGL':'CPU z-buffer',drawCalls,error:lastError,modelsCached:cache.size})};
(async()=>{try{initGL();buildMenus();bindUI();await selectSet(12);requestAnimationFrame(tick);readyResolve(true)}catch(e){fail(e)}})();
