// Offline model preview checks; never launches Minecraft.
import {createRequire} from 'node:module';
import {writeFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_CORE||'playwright-core');
const out=resolve(process.argv[2]||'out/preview/reference-courtyard');
const url=process.argv[3]||'http://10.3.0.5:8765/reference-courtyard/';
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE||undefined,args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist','--use-gl=angle']});
try{
  const page=await browser.newPage({viewport:{width:1280,height:1000},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url);await page.waitForFunction(()=>window.__ready===true);
  const info=await page.evaluate(()=>window.__previewInfo);
  for(const view of info.views){
    await page.getByRole('button',{name:({overview:'庭院全貌',reference:'临水门亭',gate:'月洞门',corridor:'回廊内部',alcove:'圆门侧室',cut:'去顶看布局'})[view],exact:true}).click();
    await page.locator('#scene').screenshot({path:join(out,view+'.png')});
  }
  await page.getByLabel('显示池水').uncheck();
  if(await page.evaluate(()=>meshes.some(m=>m.userData.part==='water'&&m.visible)))throw Error('water visibility failed');
  await page.getByLabel('显示屋顶').check();
  if(await page.evaluate(()=>meshes.some(m=>m.userData.part==='roof'&&!m.visible)))throw Error('roof visibility failed');
  await page.setViewportSize({width:390,height:844});
  const mobileOverflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
  if(errors.length||mobileOverflow)throw Error(JSON.stringify({errors,mobileOverflow}));
  writeFileSync(join(out,'browser-check.json'),JSON.stringify({...info,errors,mobileOverflow,toggles:true,renderer:'offline WebGL / SwiftShader, not Minecraft'},null,2)+'\n');
  console.log(JSON.stringify({...info,errors,mobileOverflow}));
}finally{await browser.close();}
