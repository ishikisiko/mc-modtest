#!/usr/bin/env node
'use strict';
// Parent-owned delivery assembly: never changes author inputs or game resources.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const atlas=__dirname,repo=path.resolve(atlas,'../..');
globalThis.THREE=require(path.join(repo,'tools/web/three.min.js'));
const E=require('./core/engine.js'),{assemble}=require('./core/build.js');
const F=require('./core/effects.js'),effectCatalogue=require('./effects/catalogue.js');
const {encodePixelPNG}=require('./core/pixel_png.js');
const sha=buf=>crypto.createHash('sha256').update(buf).digest('hex');
const json=(p,data)=>{fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(data,null,2)+'\n');};
const materialMTL=materials=>Object.entries(materials).map(([key,m])=>{
 const rgb=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16)/255),c=rgb(m.color),e=rgb(m.emissive||'#000000').map(x=>x*(m.emissiveIntensity||0));
 return ['newmtl '+key,'Kd '+(m.pixelArt?'1 1 1':c.join(' ')),'Ks '+[.04+.7*m.metalness,.04+.7*m.metalness,.04+.7*m.metalness].join(' '),'Ns '+Math.min(1000,Math.max(1,2/Math.max(.05,m.roughness)**2-2)),'d '+(m.opacity??1),'Pr '+m.roughness,'Pm '+m.metalness,'Ke '+e.join(' '),'illum 2',...(m.pixelArt?['map_Kd textures/'+key+'.png']:[])].join('\n');
}).join('\n\n')+'\n';
function meshAudit(group,label){
 group.updateMatrixWorld(true);let meshes=0,triangles=0;const roles=new Set();
 group.traverse(n=>{if(!n.isMesh)return;meshes++;roles.add(n.userData.role);const g=n.geometry;
 for(const key of ['position','normal']){const a=g.getAttribute(key);if(!a)throw Error(label+' lacks '+key);for(const v of a.array)if(!Number.isFinite(v))throw Error(label+' has nonfinite '+key);}
 const pos=g.getAttribute('position');if(g.index)for(const i of g.index.array)if(i<0||i>=pos.count)throw Error(label+' bad index');triangles+=(g.index?g.index.count:pos.count)/3;
 });
 if(!meshes||!triangles)throw Error(label+' empty model');const b=new THREE.Box3().setFromObject(group);return {meshes,triangles,bounds:{min:b.min.toArray(),max:b.max.toArray()},roles:[...roles]};
}
function geometrySignature(d){const normalize=cs=>cs.map(c=>Object.fromEntries(Object.entries(c).filter(([k])=>!['id','name','material','trimMaterial','topMaterial','edgeMaterial'].includes(k))));return sha(JSON.stringify({armor:normalize(d.components),ingot:normalize(d.ingot.components)}));}
function build(out,options={}){
 out=path.resolve(out);fs.mkdirSync(out,{recursive:true});const inputs={},catalogue=[],realms=['炼气','筑基','金丹','元婴','化神'];
 for(let level=1;level<=5;level++){
  const file=path.join(atlas,'levels','level'+level+'.json'),raw=fs.readFileSync(file),arr=JSON.parse(raw);inputs[path.relative(repo,file)]=sha(raw);
  if(!Array.isArray(arr)||arr.length!==3)throw Error('第'+level+'境必须恰好三套');
  for(const d of arr){if(d.level!==level||d.realm!==realms[level-1])throw Error(d.id+' realm mismatch');E.validateDesign(d);const prose=[d.description,d.silhouette,d.materialStory,d.progression,d.segmentation,...Object.values(d.parts).map(p=>p.description),d.ingot.description].join('');const han=(prose.match(/[\u3400-\u9fff]/g)||[]).length;if(han<500)throw Error(d.id+' detailed description too short: '+han);catalogue.push(d);}
 }
 if(new Set(catalogue.map(d=>d.id)).size!==15)throw Error('重复设计ID');
 for(const d of catalogue){if(!effectCatalogue[d.id])throw Error(d.id+' missing effect profile');F.validate(effectCatalogue[d.id],d);}
 const signatureSet=new Set(catalogue.map(geometrySignature));if(signatureSet.size!==15)throw Error('存在几何相同而只改配色的设计');
 const records=[];let models=0;
 for(const d of catalogue){
  const folder=path.join(out,'models',d.id);fs.mkdirSync(folder,{recursive:true});json(path.join(folder,'design.json'),d);fs.writeFileSync(path.join(folder,'materials.mtl'),materialMTL(d.materials));
  fs.mkdirSync(path.join(folder,'textures'),{recursive:true});for(const [key,m]of Object.entries(d.materials))if(m.pixelArt)fs.writeFileSync(path.join(folder,'textures',key+'.png'),encodePixelPNG(m.pixelArt));
  const armor=E.buildDesign(d,{avatar:false}),ingot=E.buildIngot(d),a=meshAudit(armor,d.id+'/armor'),i=meshAudit(ingot,d.id+'/ingot');
  const exportModel=(group,name)=>{const obj='mtllib materials.mtl\n'+E.exportOBJ(group);if(!obj.includes('\nf '))throw Error(d.id+'/'+name+' missing faces');fs.writeFileSync(path.join(folder,name+'.obj'),obj);models++;};
  exportModel(armor,'armor');exportModel(ingot,'ingot');
  for(const role of ['head','chest','legs','boots']){
   const part=new THREE.Group();for(const child of armor.children)if(child.userData.role===role)part.add(child.clone(true));meshAudit(part,d.id+'/'+role);exportModel(part,role);part.clear();
  }
  records.push({id:d.id,name:d.name,level:d.level,realm:d.realm,materials:Object.keys(d.materials).length,components:d.components.length,ingotComponents:d.ingot.components.length,geometrySignature:geometrySignature(d),armor:a,ingot:i});E.dispose(armor);E.dispose(ingot);
 }
 json(path.join(out,'catalogue.json'),catalogue);const thumbnails=options.thumbnailsFile?JSON.parse(fs.readFileSync(options.thumbnailsFile,'utf8')):null;assemble(catalogue,path.join(out,'index.html'),{thumbnails});assemble(catalogue,path.join(out,'atlas-inline.html'),{compact:true,thumbnails});
 const compactSize=fs.statSync(path.join(out,'atlas-inline.html')).size;if(compactSize>512000)throw Error('内联 HTML 超出工具限制：'+compactSize);
 const handbook=['# 五境十五套 · 完整设计说明','',...Array.from({length:5},(_,i)=>fs.readFileSync(path.join(atlas,'levels','level'+(i+1)+'-design.md'),'utf8'))].join('\n\n');fs.writeFileSync(path.join(out,'完整设计说明.md'),handbook);
 const source=path.join(out,'source'),sourceAtlas=path.join(source,'art/immortal-armor-atlas');fs.mkdirSync(sourceAtlas,{recursive:true});
 for(const folder of ['levels','authors','checks','effects','reviews'])fs.cpSync(path.join(atlas,folder),path.join(sourceAtlas,folder),{recursive:true,filter:p=>!p.includes('__pycache__')&&!p.endsWith('.pyc')});
 fs.mkdirSync(path.join(sourceAtlas,'core'),{recursive:true});for(const file of ['engine.js','effects.js','viewer.html','build.js','pixel_png.js','README.md','fixture.json'])fs.copyFileSync(path.join(atlas,'core',file),path.join(sourceAtlas,'core',file));
 fs.mkdirSync(path.join(sourceAtlas,'core/test'),{recursive:true});for(const file of ['test_engine.js','test_viewer.js','test_effects.js'])fs.copyFileSync(path.join(atlas,'core/test',file),path.join(sourceAtlas,'core/test',file));
 for(const file of ['BRIEF.md','MC_BRIEF.md','CONTRACT.md','README.md','delivery.js','capture.js','taixu-preview.js','design-preview.js'])fs.copyFileSync(path.join(atlas,file),path.join(sourceAtlas,file));
 fs.mkdirSync(path.join(source,'tools/web'),{recursive:true});for(const file of ['three.min.js','README.md'])fs.copyFileSync(path.join(repo,'tools/web',file),path.join(source,'tools/web',file));
 for(const file of ['core/engine.js','core/effects.js','effects/catalogue.js','core/viewer.html','core/build.js','delivery.js'])inputs['art/immortal-armor-atlas/'+file]=sha(fs.readFileSync(path.join(atlas,file)));
 let commit=null,branch=null;try{commit=cp.execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8',stdio:['ignore','pipe','ignore']}).trim();branch=cp.execFileSync('git',['branch','--show-current'],{cwd:repo,encoding:'utf8',stdio:['ignore','pipe','ignore']}).trim();}catch{}
 const report={sourceCommit:commit,branch,inputs,expectedDesigns:15,designs:records,objModels:models,geometryUnique:signatureSet.size,inlineBytes:compactSize,scope:'十五套真实三维锭材质与防具设计；非已实装模组',not_verified:['用户审美认可','游戏内运行与适配','真实设备FPS','跨设备广泛兼容']};json(path.join(out,'delivery.json'),report);
 json(path.join(out,'动态配置.json'),effectCatalogue);
 fs.writeFileSync(path.join(out,'动态说明.md'),['# 五境动态','', '动效可关闭或暂停；减少动态偏好时默认关闭。灵材锭保持静态，OBJ为静态原形，动态参数另见动态配置.json。','', '| 境界 | 衣甲 | 动态 |','|---|---|---|',...catalogue.map(d=>'| '+d.realm+' | '+d.name+' | '+effectCatalogue[d.id].title+'：'+effectCatalogue[d.id].description+' |'),'','这些动态属于离线三维设计预览，尚未实装Minecraft。'].join('\n'));
 fs.writeFileSync(path.join(out,'使用说明.txt'),'五境十五套锭与防具设计\n\n打开 index.html 即可离线查看，无需服务器。atlas-inline.html 是同内容的压缩内联版。\n每套可切换整套、头饰、胸衣、下装、靴履和锭，拖动旋转、滚轮缩放，并查看详细设计和材质。\n完整设计说明.md 收录十五套详细说明；models/ 含每套整套、四件与锭 OBJ、MTL和作者数据。\nOBJ保存真实几何与基本材质；完整物理材质效果以HTML和design.json为准。\n这是设计交付，不是可安装mod；游戏适配与用户审美接受未验证。\n');
 const sums=[];function walk(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory())walk(p);else if(e.name!=='SHA256SUMS'&&!e.name.endsWith('.zip'))sums.push(sha(fs.readFileSync(p))+'  '+path.relative(out,p));}}walk(out);fs.writeFileSync(path.join(out,'SHA256SUMS'),sums.sort().join('\n')+'\n');
 process.stdout.write(JSON.stringify({out,designs:catalogue.length,OBJ:models,inlineBytes:compactSize,sourceCommit:commit})+'\n');return report;
}
module.exports={build,meshAudit,materialMTL};
if(require.main===module){const args=process.argv.slice(2);if(args[0]!=='--out'||!args[1]){console.error('用法：node art/immortal-armor-atlas/delivery.js --out OUT');process.exitCode=1;}else{try{build(args[1],{thumbnailsFile:args.includes('--thumbnails')?args[args.indexOf('--thumbnails')+1]:null});}catch(e){console.error(e.stack);process.exitCode=1;}}}
