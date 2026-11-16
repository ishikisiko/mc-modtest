// 仅导出已有离线三维模型的画面，不启动Minecraft或额外服务。
import {createRequire} from 'node:module';
import {mkdirSync,writeFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_CORE||'/home/ubuntu/.t3/runtime/versions/0.1.6/node_modules/playwright-core');
const out=resolve(process.argv[2]),url=process.argv[3];mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE||'/home/ubuntu/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome',args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
const started=Date.now();
try{
 const page=await browser.newPage({viewport:{width:1100,height:1000},deviceScaleFactor:1});
 const errors=[],consoleMessages=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',e=>consoleMessages.push({level:e.type(),text:e.text()}));
 await page.goto(url,{waitUntil:'domcontentloaded',timeout:60000});console.log('文档加载',Date.now()-started,'ms');
 await page.waitForFunction(()=>window.__ready===true,undefined,{timeout:60000});console.log('模型就绪',Date.now()-started,'ms');
 const info=await page.evaluate(()=>window.__homeInfo);
 const captures=['overview','court','cut','plot',...(info.views.includes('door')?['door']:[])];
 for(const key of captures){
  await page.evaluate(key=>window.setView(key),key);
  await page.locator('#scene').screenshot({path:join(out,key+'.png'),timeout:60000});console.log('画面已导出',key);
 }
 if(info.views.includes('door')){
  await page.locator('#doors').check();
  await page.locator('#scene').screenshot({path:join(out,'door-open.png'),timeout:60000});
  const visibleDoorStates=await page.evaluate(()=>document.querySelector('#doors').checked);
  if(!visibleDoorStates)throw new Error('双开门预览开关没有生效');
 }
 await page.setViewportSize({width:390,height:844});
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
 if(errors.length||overflow)throw new Error(JSON.stringify({errors,overflow}));
 const report={status:'captured_for_review',...(await page.evaluate(()=>window.__homeInfo)),errors,consoleMessages,mobile_horizontal_overflow:overflow,game_runtime:'not_run'};
 writeFileSync(join(out,'capture-check.json'),JSON.stringify(report,null,2));console.log('画面检查完成');
}finally{await browser.close()}
