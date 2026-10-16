// Optional offline screenshots. Uses repository-vendored graphics, no Minecraft.
// PLAYWRIGHT_CORE and CHROMIUM_EXECUTABLE may point to an existing installation.
import {createRequire} from 'node:module';
import {writeFileSync,readFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_CORE||'playwright-core');
const out=resolve(process.argv[2]||'out/preview/yunshan-functional-v1');
const base=(process.argv[3]||'http://10.3.0.5:8765/yunshan-functional-v1').replace(/\/$/,'');
const report=JSON.parse(readFileSync(join(out,'batch-report.json'),'utf8'));
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE||undefined,
  args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist','--use-gl=angle']});
const results=[];
try{
  for(const b of report.buildings){
    const role=b.role;
    const page=await browser.newPage({viewport:{width:1200,height:1000},deviceScaleFactor:1});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(`${base}/${role}/index.html`);
    await page.waitForFunction(()=>window.__ready===true);
    for(const view of ['full','front','cut','rear','entry']){
      await page.evaluate(v=>setView(v),view);
      await page.locator('#scene').screenshot({path:join(out,role,`${view}.png`)});
    }
    if(role==='inn'){
      await page.evaluate(()=>setView('upper'));
      await page.locator('#scene').screenshot({path:join(out,role,'upper.png')});
    }
    await page.setViewportSize({width:390,height:844});
    const mobileOverflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
    const info=await page.evaluate(()=>window.__previewInfo);
    if(errors.length||mobileOverflow)throw Error(JSON.stringify({role,errors,mobileOverflow}));
    results.push({role,views:role==='inn'?6:5,errors,mobileOverflow,...info});
    console.log(`${role}: ${results.at(-1).views} views, ${info.instancedBatches} material batches`);
    await page.close();
  }
  const page=await browser.newPage({viewport:{width:1200,height:1000}});
  await page.goto(`${base}/index.html`);
  await page.screenshot({path:join(out,'overview.png'),fullPage:true});
  const missingImages=await page.evaluate(()=>[...document.images].filter(i=>!i.complete||i.naturalWidth===0).map(i=>i.src));
  if(missingImages.length)throw Error('missing images: '+missingImages.join(','));
  await page.setViewportSize({width:390,height:844});
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
  if(overflow)throw Error('overview has mobile horizontal overflow');
  writeFileSync(join(out,'browser-check.json'),JSON.stringify({results,overview:{missingImages,overflow}},null,2)+'\n');
}finally{await browser.close();}
