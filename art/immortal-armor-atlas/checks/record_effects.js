#!/usr/bin/env node
'use strict';
// Record the live viewer clock; this is browser design evidence, not game FPS.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const {chromium}=require(process.env.ARMOR_PLAYWRIGHT_PACKAGE||'/home/ubuntu/.cache/uv/archive-v0/tJ0GIKoi7c5vpcgP/lib/python3.13/site-packages/playwright/driver/package');
const file=path.resolve(process.argv[2]),out=path.resolve(process.argv[3]);fs.mkdirSync(out,{recursive:true});
const selected=[['jindan_zixiao','three'],['yuanying_xinghe','back'],['yuanying_jiuse','back'],['yuanying_qingdi','back'],['huashen_taixu','back-oblique'],['huashen_xuanji','back-oblique'],['huashen_dari','back']];
const report={inputSHA256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),selected:selected.map(x=>x[0]),completed:[],errors:[],not_verified:['游戏内特效与手感','真实设备帧率','用户审美认可']};
(async()=>{let browser,context;try{
 browser=await chromium.launch({executablePath:process.env.ARMOR_CHROMIUM||'/home/ubuntu/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
 context=await browser.newContext({viewport:{width:760,height:880},recordVideo:{dir:out,size:{width:760,height:880}}});const page=await context.newPage(),started=Date.now();page.on('pageerror',e=>report.errors.push(e.message));
 await page.goto('file://'+file);await page.waitForFunction(()=>globalThis.__ARMOR_ATLAS__?.stats.ready);await page.evaluate(()=>__ARMOR_ATLAS__.freezeEffects(0));await page.waitForFunction(()=>__ARMOR_ATLAS__.stats.thumbnailCount===15);
 report.stageBox=await page.locator('#stage').boundingBox();
 for(const [id,view]of selected){
  await page.evaluate(id=>{const a=__ARMOR_ATLAS__;a.selectDesign(a.catalogue.findIndex(d=>d.id===id));a.setMode('all');a.setEffects(true);a.freezeEffects(0);},id);
  await page.locator('[data-angle="'+(view==='back-oblique'?'back':view)+'"]').click();if(view==='back-oblique')await page.evaluate(()=>{__ARMOR_ATLAS__.state.theta=Math.PI-.55;window.dispatchEvent(new Event('resize'));});
  await page.locator('#effects-pause').click();const start=(Date.now()-started)/1000;await page.waitForTimeout(6200);
  const effectTime=await page.evaluate(()=>__ARMOR_ATLAS__.getEffectStats().time);assert(effectTime>4,'live animation must advance');report.completed.push({id,view,start,end:(Date.now()-started)/1000,effectTime});console.log('recorded '+id);
 }
 assert.equal(report.errors.length,0);report.status='pass';const video=page.video();await page.close();await context.close();context=null;report.video=await video.path();
}catch(e){report.status='failed';report.failure=e.stack;process.exitCode=1;}finally{if(context)await context.close();if(browser)await browser.close();report.unrun=report.selected.filter(id=>!report.completed.some(d=>d.id===id));fs.writeFileSync(path.join(out,'recording.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));}})();
