#!/usr/bin/env node
'use strict';
// Explicit realm batches share one exact HTML. Captures use fixed effect times;
// a separate live-clock check proves the animation is actually running.
const fs=require('fs'),path=require('path'),assert=require('assert/strict'),crypto=require('crypto');
const {chromium}=require(process.env.ARMOR_PLAYWRIGHT_PACKAGE||'/home/ubuntu/.cache/uv/archive-v0/tJ0GIKoi7c5vpcgP/lib/python3.13/site-packages/playwright/driver/package');
const file=path.resolve(process.argv[2]),out=path.resolve(process.argv[3]),level=Number(process.argv[4]||3),lifecycle=process.argv.includes('--lifecycle');
fs.mkdirSync(out,{recursive:true});const hash=x=>crypto.createHash('sha256').update(x).digest('hex');
const report={input:file,inputSHA256:hash(fs.readFileSync(file)),level,selected:[],completed:[],unrun:[],checks:[],errors:[],network:[],not_verified:['用户审美认可','游戏内适配与动态穿插','真实设备帧率']};
const tick=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
(async()=>{let browser;try{
 browser=await chromium.launch({executablePath:process.env.ARMOR_CHROMIUM||'/home/ubuntu/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
 const page=await browser.newPage({viewport:{width:1000,height:1100},deviceScaleFactor:1});page.on('pageerror',e=>report.errors.push(e.message));page.on('console',m=>{if(m.type()==='error')report.errors.push(m.text());});page.on('request',r=>{if(/^https?:/.test(r.url()))report.network.push(r.url());});
 await page.goto('file://'+file);await page.waitForFunction(()=>globalThis.__ARMOR_ATLAS__?.stats.ready);await page.evaluate(()=>__ARMOR_ATLAS__.freezeEffects(0));
 await page.waitForFunction(()=>__ARMOR_ATLAS__.stats.thumbnailCount===__ARMOR_ATLAS__.catalogue.length);
 const selected=await page.evaluate(n=>__ARMOR_ATLAS__.catalogue.map((d,index)=>({id:d.id,level:d.level,index})).filter(d=>d.level===n),level);assert.equal(selected.length,3);report.selected=selected.map(d=>d.id);
 for(const d of selected){
  await page.evaluate(i=>{__ARMOR_ATLAS__.selectDesign(i);__ARMOR_ATLAS__.setMode('all');__ARMOR_ATLAS__.setEffects(false);},d.index);
  const baseline=await page.evaluate(()=>__ARMOR_ATLAS__.exportStaticOBJ());
  await page.locator('[data-angle="three"]').click();await page.locator('#stage').screenshot({path:path.join(out,d.id+'-static-front.png')});
  await page.locator('[data-angle="back"]').click();await page.locator('#stage').screenshot({path:path.join(out,d.id+'-static-back.png')});
  await page.evaluate(()=>{__ARMOR_ATLAS__.setEffects(true);__ARMOR_ATLAS__.freezeEffects(2.1);});
  await page.locator('[data-angle="three"]').click();await page.locator('#stage').screenshot({path:path.join(out,d.id+'-dynamic-front.png')});
  await page.locator('[data-angle="back"]').click();await page.locator('#stage').screenshot({path:path.join(out,d.id+'-dynamic-back-2.png')});
  await page.evaluate(()=>__ARMOR_ATLAS__.freezeEffects(4.3));await page.locator('#stage').screenshot({path:path.join(out,d.id+'-dynamic-back-4.png')});
  const fx=await page.evaluate(()=>__ARMOR_ATLAS__.getEffectStats());assert(fx.particles>0);assert.equal(fx.time,4.3);assert.equal(await page.evaluate(()=>__ARMOR_ATLAS__.exportStaticOBJ()),baseline,'动态中导出必须仍是原始静态几何');
  const modes=[];for(const mode of ['head','chest','legs','boots','ingot','all']){await page.evaluate(mode=>{__ARMOR_ATLAS__.setMode(mode);__ARMOR_ATLAS__.freezeEffects(3);},mode);await tick(page);const x=await page.evaluate(()=>({geometry:__ARMOR_ATLAS__.getGeometryStats(),effects:__ARMOR_ATLAS__.getEffectStats()}));assert(x.geometry.triangles>0);if(mode!=='all')assert.deepEqual(Object.keys(x.geometry.roles),[mode]);if(mode==='ingot')assert(!x.effects.particles);modes.push({mode,...x.effects});}
  await page.evaluate(()=>__ARMOR_ATLAS__.setEffects(false));assert.equal(await page.evaluate(()=>__ARMOR_ATLAS__.exportStaticOBJ()),baseline);
  report.completed.push({id:d.id,effects:fx,modes});console.log('completed '+d.id);
 }
 report.checks.push('三套静态与固定时刻动态画面','六种模式按部件限制特效','动效开关前后静态OBJ一致');
 if(lifecycle){
  await page.evaluate(()=>{const a=__ARMOR_ATLAS__;a.selectDesign(a.catalogue.findIndex(d=>d.id==='huashen_taixu'));a.setMode('all');a.setEffects(true);a.freezeEffects(1);});
  const at=await page.evaluate(()=>__ARMOR_ATLAS__.getEffectStats().time);await page.locator('#effects-pause').click();await page.waitForFunction(t=>__ARMOR_ATLAS__.getEffectStats().time>t+.35,at,{timeout:15000});
  await page.locator('#effects-pause').click();const paused=await page.evaluate(()=>__ARMOR_ATLAS__.getEffectStats().time);await page.waitForTimeout(250);assert.equal(await page.evaluate(()=>__ARMOR_ATLAS__.getEffectStats().time),paused);
  await page.locator('#effects-pause').click();await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));await page.waitForFunction(()=>!__ARMOR_ATLAS__.getEffectStats().stageVisible);const hidden=await page.evaluate(()=>__ARMOR_ATLAS__.getEffectStats().time);await page.waitForTimeout(300);assert.equal(await page.evaluate(()=>__ARMOR_ATLAS__.getEffectStats().time),hidden);
  await page.evaluate(()=>window.scrollTo(0,0));await page.waitForFunction(()=>__ARMOR_ATLAS__.getEffectStats().stageVisible);await page.waitForFunction(t=>__ARMOR_ATLAS__.getEffectStats().time>t,hidden);await page.evaluate(()=>__ARMOR_ATLAS__.freezeEffects(2));
  const initial=await page.evaluate(()=>__ARMOR_ATLAS__.getMemory());for(let i=0;i<15;i++){await page.evaluate(i=>{__ARMOR_ATLAS__.selectDesign(i);__ARMOR_ATLAS__.freezeEffects(2);},i);await tick(page);}
  await page.evaluate(()=>{__ARMOR_ATLAS__.selectDesign(12);__ARMOR_ATLAS__.freezeEffects(2);});await tick(page);const final=await page.evaluate(()=>__ARMOR_ATLAS__.getMemory());assert(final.geometries<=initial.geometries+1,JSON.stringify({initial,final}));assert.equal(final.textures,initial.textures);report.memory={initial,final};
  await page.setViewportSize({width:390,height:900});await tick(page);const dimensions=await page.evaluate(()=>({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}));assert(dimensions.scroll<=dimensions.width+1);await page.screenshot({path:path.join(out,'mobile.png'),fullPage:true});
  const reduced=await browser.newPage({viewport:{width:900,height:900},reducedMotion:'reduce'});await reduced.goto('file://'+file);await reduced.waitForFunction(()=>globalThis.__ARMOR_ATLAS__?.stats.ready);assert.equal(await reduced.evaluate(()=>__ARMOR_ATLAS__.state.effects),false);await reduced.close();
  report.checks.push('真实时间推进、暂停保持、离屏停转与返回恢复','十五套切换后GPU资源回落','390像素窄屏与减少动态偏好');
 }
 assert.equal(report.errors.length,0);assert.equal(report.network.length,0);report.status='pass';
}catch(e){report.status='failed';report.failure=e.stack;process.exitCode=1;}finally{
 report.unrun=report.selected.filter(id=>!report.completed.some(d=>d.id===id));if(browser)await browser.close();fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,selected:report.selected.length,completed:report.completed.length,unrun:report.unrun,failure:report.failure}));
}})();
