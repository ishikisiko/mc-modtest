#!/usr/bin/env node
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {chromium}=require(process.env.ARMOR_PLAYWRIGHT_PACKAGE||'/home/ubuntu/.cache/uv/archive-v0/tJ0GIKoi7c5vpcgP/lib/python3.13/site-packages/playwright/driver/package');
const executablePath=process.env.ARMOR_CHROMIUM||'/home/ubuntu/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome';
const args=process.argv.slice(2),get=(k,def)=>args.includes(k)?args[args.indexOf(k)+1]:def;
const file=path.resolve(get('--file','')),out=path.resolve(get('--out','reports/immortal-armor-atlas/capture')),expected=Number(get('--expected','15'));
fs.mkdirSync(out,{recursive:true});
const report={file,inputSHA256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),expected,checks:[],captures:[],errors:[],warnings:[],not_verified:['用户审美认可','游戏内适配','真实硬件帧率']};
const tick=page=>page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
(async()=>{let browser;try{
 browser=await chromium.launch({executablePath,headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
 const page=await browser.newPage({viewport:{width:1000,height:1100},deviceScaleFactor:1});page.on('pageerror',e=>report.errors.push(e.message));page.on('console',m=>{if(m.type()==='warning')report.warnings.push(m.text());if(m.type()==='error')report.errors.push(m.text());});
 await page.goto('file://'+file,{waitUntil:'load',timeout:60000});await page.waitForFunction(()=>globalThis.__ARMOR_ATLAS__?.stats.ready,{timeout:60000});
 const count=await page.evaluate(()=>__ARMOR_ATLAS__.catalogue.length);if(count!==expected)throw Error('Expected '+expected+' designs, got '+count);
 await page.waitForFunction(n=>__ARMOR_ATLAS__.stats.thumbnailCount===n||__ARMOR_ATLAS__.stats.errors.length>0,expected,{timeout:180000});const startupErrors=await page.evaluate(()=>__ARMOR_ATLAS__.stats.errors);if(startupErrors.length)throw Error(JSON.stringify(startupErrors));report.checks.push('all real model thumbnails ready');
 const thumbs=await page.evaluate(async()=>{const imgs=[...document.querySelectorAll('.tile img')],result={};for(let i=0;i<imgs.length;i++){const c=document.createElement('canvas');c.width=imgs[i].naturalWidth;c.height=imgs[i].naturalHeight;c.getContext('2d').drawImage(imgs[i],0,0);result[__ARMOR_ATLAS__.catalogue[i].id]=c.toDataURL('image/jpeg',.84);}return result;});
 fs.writeFileSync(path.join(out,'thumbnails.json'),JSON.stringify(thumbs));
 const inlineImages=await page.evaluate(()=>{const result={};[...document.querySelectorAll('.tile img')].forEach((img,i)=>{const c=document.createElement('canvas');c.width=img.naturalWidth;c.height=img.naturalHeight;c.getContext('2d').drawImage(img,0,0);result[__ARMOR_ATLAS__.catalogue[i].id]=c.toDataURL('image/jpeg',.68);});return result;});
 const designs=await page.evaluate(()=>__ARMOR_ATLAS__.catalogue),hash=v=>crypto.createHash('sha256').update(v).digest('hex'),core=path.join(__dirname,'core');
 fs.writeFileSync(path.join(out,'thumbnails-inline.json'),JSON.stringify({images:inlineImages,designHashes:Object.fromEntries(designs.map(d=>[d.id,hash(JSON.stringify(d))])),engineHash:hash(fs.readFileSync(path.join(core,'engine.js'))),viewerHash:hash(fs.readFileSync(path.join(core,'viewer.html'))),sourceHtmlHash:report.inputSHA256}));for(const [id,uri]of Object.entries(thumbs))fs.writeFileSync(path.join(out,id+'-thumb.jpg'),Buffer.from(uri.split(',')[1],'base64'));
 const manifest=await page.evaluate(()=>__ARMOR_ATLAS__.catalogue.map(d=>({id:d.id,name:d.name,realm:d.realm,level:d.level,style:d.style})));
 for(let i=0;i<expected;i++){
  const d=manifest[i];await page.evaluate(index=>{__ARMOR_ATLAS__.selectDesign(index);__ARMOR_ATLAS__.setMode('all');},i);await page.getByRole('button',{name:'四分之三',exact:true}).click();await tick(page);await page.locator('#stage').screenshot({path:path.join(out,d.id+'-iso.png')});
  await page.getByRole('button',{name:'背面',exact:true}).click();await tick(page);await page.locator('#stage').screenshot({path:path.join(out,d.id+'-back.png')});
  await page.evaluate(()=>__ARMOR_ATLAS__.setMode('ingot'));await page.getByRole('button',{name:'四分之三',exact:true}).click();await tick(page);await page.locator('#stage').screenshot({path:path.join(out,d.id+'-ingot.png')});
  const stats=await page.evaluate(()=>({model:__ARMOR_ATLAS__.getGeometryStats(),viewer:__ARMOR_ATLAS__.stats}));if(stats.viewer.errors.length)throw Error(JSON.stringify(stats.viewer.errors));report.captures.push({...d,views:['iso','back','ingot'],ingotGeometry:stats.model});process.stdout.write('captured '+d.id+'\n');
 }
 report.checks.push('all designs iso/back/ingot');report.viewer=await page.evaluate(()=>__ARMOR_ATLAS__.stats);report.status=report.errors.length?'failed':'pass';
}catch(e){report.status='failed';report.errors.push(e.stack);process.exitCode=1;}finally{if(browser)await browser.close();fs.writeFileSync(path.join(out,'capture-report.json'),JSON.stringify(report,null,2)+'\n');process.stdout.write(JSON.stringify({status:report.status,captured:report.captures.length,errors:report.errors})+'\n');}})();
