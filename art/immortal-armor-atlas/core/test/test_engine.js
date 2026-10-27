#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),vm=require('node:vm');
const core=path.resolve(__dirname,'..'),repo=path.resolve(core,'../../..');
const hash=value=>require('node:crypto').createHash('sha256').update(value).digest('hex');
globalThis.THREE=require(path.join(repo,'tools/web/three.min.js'));
const T=globalThis.THREE,E=require('../engine.js'),{assemble}=require('../build.js');
const fixture=JSON.parse(fs.readFileSync(path.join(core,'fixture.json'),'utf8'));
const report={date:new Date().toISOString(),threeRevision:T.REVISION,checks:[],failures:[],integration:[],not_verified:['用户审美认可','真实 GPU / 移动设备性能','Minecraft / Gradle / 游戏内模型']};
report.inputs=Object.fromEntries(['engine.js','viewer.html','fixture.json'].map(file=>[file,hash(fs.readFileSync(path.join(core,file)))]));
function check(name,fn){try{fn();report.checks.push(name);}catch(err){report.failures.push({name,error:err.stack});}}
function copy(){return structuredClone(fixture);}
function rejects(name,edit,pattern){check(name,()=>{const spec=copy();edit(spec);assert.throws(()=>E.validateDesign(spec),pattern);});}
function meshes(group){const out=[];group.traverse(n=>{if(n.isMesh)out.push(n);});return out;}
function validGeometry(group){meshes(group).forEach(mesh=>{
  const g=mesh.geometry;assert(g.attributes.position.count>0);assert(g.attributes.normal);
  ['position','normal'].forEach(a=>assert([...g.attributes[a].array].every(Number.isFinite),mesh.name+' '+a));
  const size=g.index?g.index.count:g.attributes.position.count;assert.equal(size%3,0);
  if(g.index)assert([...g.index.array].every(i=>i>=0&&i<g.attributes.position.count));
  assert(mesh.userData.componentId);assert(mesh.userData.role);assert(mesh.userData.materialKey);
});}
function primitive(shape,params){const spec=copy();spec.components[0]={id:'probe',name:'独立几何探针',role:'chest',material:'steel',shape,...params};const group=E.buildDesign(spec,{avatar:false});return {group,mesh:group.children[0].children[0]};}
function volume(mesh){const g=mesh.geometry,p=g.attributes.position,a=new T.Vector3(),b=new T.Vector3(),c=new T.Vector3();let v=0;const count=g.index?g.index.count:p.count;for(let i=0;i<count;i+=3){a.fromBufferAttribute(p,g.index?g.index.getX(i):i);b.fromBufferAttribute(p,g.index?g.index.getX(i+1):i+1);c.fromBufferAttribute(p,g.index?g.index.getX(i+2):i+2);v+=a.dot(b.cross(c))/6;}return v;}
function manifold(mesh){const g=mesh.geometry,p=g.attributes.position,edges=new Map(),count=g.index?g.index.count:p.count;const vertex=i=>{const n=g.index?g.index.getX(i):i;return [p.getX(n),p.getY(n),p.getZ(n)].map(x=>Math.round(x*1e6)).join(',');};for(let i=0;i<count;i+=3){const v=[vertex(i),vertex(i+1),vertex(i+2)];if(new Set(v).size<3)continue;for(let j=0;j<3;j++){const a=v[j],b=v[(j+1)%3],key=[a,b].sort().join('|');const old=edges.get(key)||[0,0];old[0]++;old[1]+=a<b?1:-1;edges.set(key,old);}}const invalid=[...edges.values()].filter(([n,s])=>n!==2||s!==0);assert.equal(invalid.length,0,'非闭合或反向边 '+invalid.length);}
check('冻结七个 API 与 CommonJS/globalThis 一致',()=>{assert.deepEqual(Object.keys(E).sort(),['validateDesign','buildDesign','buildIngot','buildAvatar','meshStats','exportOBJ','dispose'].sort());assert.equal(E,globalThis.ArmorEngine);});
check('fixture 覆盖九类 shape、四件与至少五种材质',()=>{assert(E.validateDesign(fixture));assert.equal(new Set([...fixture.components,...fixture.ingot.components].map(c=>c.shape)).size,9);});
check('buildDesign / buildIngot / buildAvatar 的几何与 Mesh 元数据',()=>{for(const group of [E.buildDesign(fixture,{avatar:true}),E.buildIngot(fixture),E.buildAvatar()]){validGeometry(group);const s=E.meshStats(group);assert(s.vertices>0);assert(s.triangles>0);assert.equal(s.meshes,meshes(group).length);E.dispose(group);}});
check('关闭人台不会留下隐藏人台网格',()=>{const group=E.buildDesign(fixture,{avatar:false});assert(!E.meshStats(group).roles.avatar);E.dispose(group);});
check('人体比例、足底与雕塑式头颅',()=>{const group=E.buildAvatar(),bounds=new T.Box3().setFromObject(group);assert(bounds.min.y>=0);assert(bounds.max.y>=1.79&&bounds.max.y<=1.81);const head=meshes(group).find(n=>n.name==='头颅');const size=new T.Box3().setFromObject(head).getSize(new T.Vector3());assert(size.y>=.24&&size.y<.25);assert(size.x<.20);assert(!meshes(group).some(n=>n.geometry.type==='BoxGeometry'));E.dispose(group);});
check('连续 / 开口 / 扭转 / 波纹 / 倒序 loft 的真实闭合厚度与正向体积',()=>{
  const rings=[{y:0,rx:.20,rz:.13},{y:.18,rx:.17,rz:.11,cx:.01,cz:.02,twist:18,wave:.003},{y:.4,rx:.22,rz:.14,twist:25}];
  for(const extra of [{},{capTop:true},{capBottom:true},{capTop:true,capBottom:true},{startAngle:20,endAngle:290},{startAngle:20,endAngle:290,capTop:true,capBottom:true},{rings:rings.slice().reverse()}]){const {group,mesh}=primitive('loft',{rings,thickness:.004,...extra});validGeometry(group);manifold(mesh);assert(volume(mesh)>0);E.dispose(group);}
});
check('tube 逐点变径与端盖是闭合正体积',()=>{const {group,mesh}=primitive('tube',{path:[[0,0,0],[.03,.1,.01],[.01,.25,.08]],radii:[.01,.025,.006],steps:36,radialSegments:12});validGeometry(group);manifold(mesh);assert(volume(mesh)>0);E.dispose(group);});
check('非等距 Catmull 控制点处的半径与宽度遵循逐点参数',()=>{const path=[[0,0,0],[0,.05,0],[0,.5,0]],radii=[.01,.025,.006],{group,mesh}=primitive('tube',{path,radii,steps:32,radialSegments:8});const pos=mesh.geometry.attributes.position,v=new T.Vector3().fromBufferAttribute(pos,16*9),center=new T.Vector3(...path[1]);assert(Math.abs(v.distanceTo(center)-.025)<1e-6);E.dispose(group);const probe=primitive('ribbon',{path,widths:[.03,.08,.02],thickness:.004,steps:32});const p=probe.mesh.geometry.attributes.position,a=new T.Vector3().fromBufferAttribute(p,16*4),b=new T.Vector3().fromBufferAttribute(p,16*4+1);assert(Math.abs(a.distanceTo(b)-.08)<1e-6);E.dispose(probe.group);});
check('圆环与局部冠环拥有封闭端面和正确面序',()=>{for(const arc of [360,210]){const {group,mesh}=primitive('torus',{radius:.12,tube:.01,arc});manifold(mesh);assert(volume(mesh)>0);E.dispose(group);}});
check('ribbon 宽度、正面、实体侧壁与端面闭合',()=>{for(const normal of [[0,0,1],[0,1,0]]){const {group,mesh}=primitive('ribbon',{path:[[0,0,0],[0,.1,0],[.03,.25,.01]],widths:[.03,.06,.02],normal,thickness:.005});validGeometry(group);manifold(mesh);assert(volume(mesh)>0);E.dispose(group);}});
check('panel 凹轮廓、真实倒角与嵌边材质',()=>{const {group,mesh}=primitive('panel',{polygon:[[-.1,-.1],[.1,-.1],[.1,.1],[0,.02],[-.1,.1]],depth:.012,bevel:.002,trimMaterial:'gold'});validGeometry(group);assert.equal(group.children[0].children.length,2);manifold(mesh);assert(volume(mesh)>0);E.dispose(group);});
check('低扁削角锭有闭合正体积与三个分工材质',()=>{const group=E.buildIngot(fixture),mesh=group.children[0].children[0];manifold(mesh);assert(volume(mesh)>0);assert.equal(mesh.material.length,3);assert(mesh.geometry.groups.some(g=>g.materialIndex===1));assert(mesh.geometry.groups.some(g=>g.materialIndex===2));E.dispose(group);});
check('OBJ 逐顶点应用父组/组件世界矩阵并输出真实三角面及法线',()=>{const group=E.buildDesign(fixture,{avatar:false});group.position.set(1.2,.2,-.4);group.rotation.y=.31;group.scale.set(-1.1,1,.9);const text=E.exportOBJ(group),s=E.meshStats(group),lines=text.split('\n');assert.equal(lines.filter(l=>l.startsWith('v ')).length,s.vertices);assert.equal(lines.filter(l=>l.startsWith('vn ')).length,s.vertices);assert.equal(lines.filter(l=>l.startsWith('f ')).length,s.triangles);const first=meshes(group)[0],v=new T.Vector3().fromBufferAttribute(first.geometry.attributes.position,0).applyMatrix4(first.matrixWorld);const actual=lines.find(l=>l.startsWith('v ')).slice(2).split(' ').map(Number);assert(v.distanceTo(new T.Vector3(...actual))<1e-6);assert(!/NaN|Infinity/.test(text));const vertexCount=s.vertices;for(const line of lines.filter(l=>l.startsWith('f ')))for(const n of line.slice(2).split(' ')){const [vi,ni]=n.split('//').map(Number);assert(vi>=1&&vi<=vertexCount);assert(ni>=1&&ni<=vertexCount);}E.dispose(group);});
check('dispose 对共享几何/材质/纹理恰好释放一次并摘除对象',()=>{const parent=new T.Group(),group=E.buildDesign(fixture,{avatar:true});parent.add(group);const geoms=new Set(),mats=new Set();group.traverse(n=>{if(n.geometry)geoms.add(n.geometry);if(n.material)(Array.isArray(n.material)?n.material:[n.material]).forEach(m=>mats.add(m));(n.userData.ownedMaterials||[]).forEach(m=>mats.add(m));});let geometryEvents=0,materialEvents=0,textureEvents=0;geoms.forEach(g=>g.addEventListener('dispose',()=>geometryEvents++));mats.forEach(m=>m.addEventListener('dispose',()=>materialEvents++));const texture=new T.Texture();texture.addEventListener('dispose',()=>textureEvents++);[...mats].slice(0,2).forEach(m=>{m.map=texture;});const disposed=E.dispose(group);assert.equal(disposed.geometries,geoms.size);assert.equal(geometryEvents,geoms.size);assert.equal(materialEvents,mats.size);assert.equal(textureEvents,1);assert.equal(group.children.length,0);assert.equal(parent.children.length,0);});
rejects('未知 shape 明确报错，不生成 box',s=>{s.components[0].shape='capsule';},/components\[0\]\.shape.*capsule/);
rejects('未知组件字段明确报错',s=>{s.components[0].transmission=1;},/components\[0\]\.transmission/);
rejects('未知顶层字段明确报错',s=>{s.gameplay='no';},/design\.gameplay/);
rejects('未知材质字段明确报错',s=>{s.materials.steel.texture='url';},/materials\.steel\.texture/);
rejects('NaN 变换明确报错',s=>{s.components[0].position=[0,NaN,0];},/position\[1\]/);
rejects('零缩放明确报错',s=>{s.components[0].scale=[0,1,1];},/scale/);
rejects('材质引用与组件位置可定位',s=>{s.components[0].material='missing';},/components\[0\]\.material.*missing/);
rejects('缺失四件之一报错',s=>{s.components=s.components.filter(c=>c.role!=='boots');},/缺少 boots/);
rejects('重复组件 id 报错',s=>{s.components[1].id=s.components[0].id;},/重复组件 id/);
rejects('锭组件跨 role 报错',s=>{s.ingot.components[0].role='head';},/ingot\.components\[0\]\.role/);
rejects('锭高宽比不放宽',s=>{s.ingot.components[0].size[1]=.4;},/高宽比/);
rejects('自交轮廓拒绝',s=>{s.components[0]={id:'bad',name:'坏轮廓',role:'chest',material:'steel',shape:'panel',polygon:[[0,0],[1,1],[0,1],[1,0]]};},/轮廓自交/);
rejects('路径重复点拒绝',s=>{const c=s.components.find(c=>c.shape==='tube');c.path[1]=c.path[0];},/相邻控制点/);
rejects('变径数组与路径不等长拒绝',s=>{const c=s.components.find(c=>c.shape==='tube');c.radii=[.1];},/radii.*等长/);
rejects('同时给固定半径与变径拒绝',s=>{const c=s.components.find(c=>c.shape==='tube');c.radius=.01;},/必须且只能/);
rejects('非法圆周范围拒绝',s=>{s.components[0].endAngle=400;},/endAngle/);
rejects('ribbon 零法线拒绝',s=>{s.components.find(c=>c.shape==='ribbon').normal=[0,0,0];},/normal/);
rejects('材质范围不静默钳制',s=>{s.materials.steel.roughness=3;},/roughness/);
check('自包含 HTML 组装、JSON 安全与浏览器脚本语法',()=>{const spec=copy();spec.description+='</script><img src=x>';const html=assemble([spec]);assert(!/__THREE_JS__|__ENGINE_JS__|__CATALOGUE_JSON__/.test(html));assert(!/<(?:script|link|img)[^>]+(?:src|href)=["']https?:/i.test(html));assert(!html.includes('</script><img'));const scripts=[...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];assert.equal(scripts.length,4);scripts.filter(x=>!x[1].includes('application/json')).forEach((x,i)=>new vm.Script(x[2],{filename:'inline-'+i}));assert.equal(JSON.parse(scripts.find(x=>x[1].includes('application/json'))[2])[0].description,spec.description);});
// One integration snapshot only. Other workers own these inputs; this test never changes them.
const levelDir=path.resolve(core,'../levels');
const levelArgs=process.argv.find(a=>a.startsWith('--levels='));
const selectedLevels=process.argv.includes('--fixture-only')?[]:levelArgs?levelArgs.split('=')[1].split(',').map(Number):[1,2,3,4,5];
for(const level of selectedLevels){
 const file=path.join(levelDir,'level'+level+'.json');
 if(!fs.existsSync(file)){report.integration.push({level,status:'not_available'});continue;}
 try{const raw=fs.readFileSync(file,'utf8'),specs=JSON.parse(raw);assert(Array.isArray(specs)&&specs.length===3,'每境恰好三套');const designs=[];for(const spec of specs){assert.equal(spec.level,level);E.validateDesign(spec);const g=E.buildDesign(spec,{avatar:true}),ingot=E.buildIngot(spec);validGeometry(g);validGeometry(ingot);designs.push({id:spec.id,armor:E.meshStats(g),ingot:E.meshStats(ingot)});E.dispose(g);E.dispose(ingot);}report.integration.push({level,status:'passed',sha256:hash(raw),designs});}catch(err){report.integration.push({level,status:'failed',error:err.stack});}
}
fs.mkdirSync(__dirname,{recursive:true});fs.writeFileSync(path.join(__dirname,'engine-report.json'),JSON.stringify(report,null,2)+'\n');
process.stdout.write(JSON.stringify({passed:report.checks.length,failed:report.failures.length,integration:report.integration.map(({level,status})=>({level,status}))},null,2)+'\n');
if(report.failures.length||report.integration.some(x=>x.status==='failed')){process.stdout.write(JSON.stringify(report.failures,null,2)+'\n');process.exitCode=1;}
