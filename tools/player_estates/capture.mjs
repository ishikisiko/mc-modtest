// Capture the actual offline block model; never launches Minecraft.
import {createRequire} from 'node:module';
import {mkdirSync,writeFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_CORE||'/home/ubuntu/.t3/runtime/versions/0.1.6/node_modules/playwright-core');
const out=resolve(process.argv[2]||'out/preview/gray-courtyard');
const url=process.argv[3]||'https://assets.ishiki333hhh.win/previews/gray-courtyard-blueprint-20261111/index.html';
mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE||'/home/ubuntu/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome',args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
try{
  const page=await browser.newPage({viewport:{width:1280,height:1000},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url,{waitUntil:'domcontentloaded',timeout:60000});
  await page.waitForFunction(()=>window.__ready===true,undefined,{timeout:120000});
  console.log('native block scene ready',await page.evaluate(()=>window.__estateInfo));
  await page.locator('#scene').evaluate(el=>{el.style.height='760px';dispatchEvent(new Event('resize'))});
  for(const key of ['overview','front','court','cut']){
    await page.evaluate(k=>window.setView(k),key);
    await page.locator('#scene').screenshot({path:join(out,key+'.png'),timeout:60000});
    console.log('captured',key);
  }
  await page.evaluate(()=>window.setView('overview'));
  await page.getByLabel('显示屋顶').uncheck();
  await page.getByLabel('显示树木').uncheck();
  await page.setViewportSize({width:390,height:844});
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
  if(errors.length||overflow)throw new Error(JSON.stringify({errors,overflow}));
  const report={...(await page.evaluate(()=>window.__estateInfo)),errors,mobile_horizontal_overflow:overflow,views_captured:['overview','front','court','cut'],game_runtime:'not_verified'};
  writeFileSync(join(out,'browser-check.json'),JSON.stringify(report,null,2));
  console.log('preview check',JSON.stringify(report));
}finally{await browser.close()}
