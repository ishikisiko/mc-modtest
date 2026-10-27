#!/usr/bin/env node
'use strict';
// Exercise the delivered catalogue, not copies of the engine fixture.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {chromium}=require(process.env.ARMOR_PLAYWRIGHT_PACKAGE||'/home/ubuntu/.cache/uv/archive-v0/tJ0GIKoi7c5vpcgP/lib/python3.13/site-packages/playwright/driver/package');
const executablePath=process.env.ARMOR_CHROMIUM||'/home/ubuntu/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome';
const file=path.resolve(process.argv[2]),out=path.resolve(process.argv[3]);
fs.mkdirSync(out,{recursive:true});
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const report={input:file,inputSHA256:sha(fs.readFileSync(file)),checks:[],designs:[],errors:[],network:[],not_verified:['用户审美认可','真实设备帧率','游戏适配与动作']};
const tick=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
(async()=>{let browser;try{
 browser=await chromium.launch({executablePath,headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
 const page=await browser.newPage({viewport:{width:1280,height:1050},deviceScaleFactor:1});
 page.on('pageerror',e=>report.errors.push(e.message));
 page.on('console',m=>{if(m.type()==='error')report.errors.push(m.text());});
 page.on('request',r=>{if(/^https?:/.test(r.url()))report.network.push(r.url());});
 await page.goto('file://'+file,{waitUntil:'load',timeout:60000});
 await page.waitForFunction(()=>globalThis.__ARMOR_ATLAS__?.stats.ready,null,{timeout:60000});
 await page.waitForFunction(()=>__ARMOR_ATLAS__.stats.thumbnailCount===15,null,{timeout:60000});
 const catalogue=await page.evaluate(()=>__ARMOR_ATLAS__.catalogue);
 assert.equal(catalogue.length,15);assert.equal(new Set(catalogue.map(d=>d.id)).size,15);
 report.catalogueSHA256=sha(JSON.stringify(catalogue));
 assert.equal(await page.locator('.tile img').count(),15);
 report.checks.push('十五套真实设计及同源缩略图加载');
 for(let realm=0;realm<5;realm++){
  await page.locator('#realms button').nth(realm).click();
  assert.equal(await page.locator('#styles button').count(),3);
  for(let style=0;style<3;style++){
   await page.locator('#styles button').nth(style).click();
   const index=realm*3+style,d=catalogue[index];
   assert.equal(await page.locator('#design-name').textContent(),d.name);
   const modes=[];
   for(const mode of ['all','head','chest','legs','boots','ingot']){
    await page.locator('#modes [data-mode="'+mode+'"]').click();await tick(page);
    const s=await page.evaluate(()=>({index:__ARMOR_ATLAS__.state.index,radius:__ARMOR_ATLAS__.state.radius,geometry:__ARMOR_ATLAS__.getGeometryStats()}));
    assert.equal(s.index,index);assert(s.geometry.triangles>0);assert(Number.isFinite(s.radius)&&s.radius>0);
    if(mode==='all')assert(s.geometry.roles.avatar>0);else assert.deepEqual(Object.keys(s.geometry.roles),[mode]);
    modes.push({mode,triangles:s.geometry.triangles,meshes:s.geometry.meshes});
   }
   report.designs.push({id:d.id,modes});
  }
 }
 report.checks.push('十五套各六种模式：整套、四件和独立锭，共九十次实际构建');
 for(const angle of ['front','side','back','three']){
  await page.locator('[data-angle="'+angle+'"]').click();await tick(page);
  assert(await page.evaluate(()=>__ARMOR_ATLAS__.state.phi<1.2),'锭的标准视角应能俯看顶面纹样');
 }
 await page.locator('#reset').click();assert(await page.evaluate(()=>__ARMOR_ATLAS__.state.phi<1.2));
 await page.locator('#modes [data-mode="all"]').click();
 for(const angle of ['front','side','back','three']){
  await page.locator('[data-angle="'+angle+'"]').click();await tick(page);
  assert.equal(await page.locator('[data-angle="'+angle+'"]').getAttribute('aria-pressed'),'true');
 }
 const canvas=page.locator('#stage canvas'),box=await canvas.boundingBox();
 const before=await page.evaluate(()=>__ARMOR_ATLAS__.state.theta);
 await page.mouse.move(box.x+box.width*.45,box.y+box.height*.5);await page.mouse.down();
 await page.mouse.move(box.x+box.width*.60,box.y+box.height*.55,{steps:6});await page.mouse.up();
 assert.notEqual(await page.evaluate(()=>__ARMOR_ATLAS__.state.theta),before);
 const radius=await page.evaluate(()=>__ARMOR_ATLAS__.state.radius);await page.mouse.wheel(0,80);await tick(page);
 assert.notEqual(await page.evaluate(()=>__ARMOR_ATLAS__.state.radius),radius);
 await canvas.focus();await page.keyboard.press('ArrowLeft');
 report.checks.push('正侧背视角、锭顶面、拖动、缩放和键盘操作');
 await page.locator('#show-realm').click();assert.equal(await page.locator('.matrix-row:visible').count(),1);
 await page.locator('#show-all').click();assert.equal(await page.locator('.matrix-row:visible').count(),5);
 await page.locator('.tile').nth(9).click();assert.equal(await page.evaluate(()=>__ARMOR_ATLAS__.state.index),9);
 report.checks.push('矩阵筛选与选款同步');
 await page.getByText('几何信息与模型下载',{exact:true}).click();
 for(const [selector,extension]of [['#download-obj','.obj'],['#download-json','.json']]){
  const pending=page.waitForEvent('download');await page.locator(selector).click();const dl=await pending;
  assert(dl.suggestedFilename().startsWith(catalogue[9].id));assert(dl.suggestedFilename().endsWith(extension));
  const target=path.join(out,'download'+extension);await dl.saveAs(target);const data=fs.readFileSync(target,'utf8');
  if(extension==='.obj')assert(data.includes('\nv ')&&data.includes('\nf '));else assert.deepEqual(JSON.parse(data),catalogue[9]);
 }
 report.checks.push('所选真实模型 OBJ 和设计 JSON 下载');
 await page.locator('#reset').click();await tick(page);
 await page.screenshot({path:path.join(out,'desktop.png'),fullPage:true});
 await page.setViewportSize({width:390,height:900});await tick(page);
 const dimensions=await page.evaluate(()=>({client:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth,canvas:document.querySelector('#stage canvas').getBoundingClientRect().width}));
 assert(dimensions.scroll<=dimensions.client+1);assert(dimensions.canvas>=380);
 await page.screenshot({path:path.join(out,'mobile.png'),fullPage:true});report.dimensions=dimensions;
 report.checks.push('390 像素窄屏无横向溢出');
 report.viewer=await page.evaluate(()=>__ARMOR_ATLAS__.stats);
 assert.equal(report.viewer.errors.length,0);assert.equal(report.viewer.liveDesignGroups,1);
 assert.equal(report.network.length,0);assert.equal(report.errors.length,0);
 report.status='pass';
}catch(e){report.status='failed';report.failure=e.stack;process.exitCode=1;}finally{
 if(browser)await browser.close();fs.writeFileSync(path.join(out,'interaction-report.json'),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify({status:report.status,designs:report.designs.length,modes:report.designs.reduce((s,d)=>s+d.modes.length,0),checks:report.checks.length,failure:report.failure}));
}})();
