// 本地文件截图，不启动服务或 Minecraft。依赖位置由环境提供。
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),zlib=require('node:zlib');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const out=path.resolve(process.argv[2]||'out/preview/capital-environment-design-20261029');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
 const report={scope:'真实三维设计几何；软件渲染；非原生 Minecraft',errors:[],captures:[],html_sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(out,'index.html'))).digest('hex')};
 try{
  const scenes=JSON.parse(fs.readFileSync(path.join(out,'manifest.json'),'utf8')).scenes;
  for(let i=0;i<scenes.length;i++){
   const scene=scenes[i],page=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1});
   page.on('pageerror',e=>report.errors.push(e.message));
   await page.goto('file://'+path.join(out,scene.id+'.html'),{waitUntil:'load'});
   await page.waitForFunction(()=>globalThis.CAPITAL_DESIGN?.ready,{},{timeout:60000});
   await page.locator('#loading').waitFor({state:'hidden',timeout:5000});
   if(scene.id.endsWith('_city')){
    const source=JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(out,scene.id+'.json.gz'))));
    const expected=crypto.createHash('sha256').update(JSON.stringify(source.meshes.map(m=>[m.v,m.f]))).digest('hex');
    const actual=await page.evaluate(async()=>{const raw=new TextEncoder().encode(JSON.stringify(CAPITAL_DESIGN.scenes[0].meshes.map(m=>[m.v,m.f])));return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',raw)),b=>b.toString(16).padStart(2,'0')).join('');});
    if(actual!==expected)throw Error('压缩地形回读几何不一致：'+scene.id);
    (report.terrain_identity??=[]).push({scene:scene.id,sha256:actual,match:true});
   }
   for(const view of scene.id.endsWith('_city')?Object.keys(scene.cameras):['overview']){
    await page.evaluate(view=>{CAPITAL_DESIGN.fit(view);CAPITAL_DESIGN.renderer.render(CAPITAL_DESIGN.world,CAPITAL_DESIGN.camera);},view);
    const name=scene.id+'-'+view+'.png';await page.locator('#stage').screenshot({path:path.join(out,name)});
    const digest=crypto.createHash('sha256').update(fs.readFileSync(path.join(out,name))).digest('hex');
    if(report.captures.some(c=>c.sha256===digest))throw Error('不同视角得到同一图片，拒绝空白或遮挡截图：'+name);
    report.captures.push({scene:scene.id,view,file:name,sha256:digest});
   }
   if(i===0){
    await page.setViewportSize({width:390,height:900});
    report.mobile=await page.evaluate(()=>({width:innerWidth,document:document.documentElement.scrollWidth,canvas:document.querySelector('canvas').getBoundingClientRect().width,loaded:CAPITAL_DESIGN.ready}));
   }
   await page.close();
   console.log(scene.id,'captured');
  }
 }finally{await browser.close();fs.writeFileSync(path.join(out,'capture-report.json'),JSON.stringify(report,null,2)+'\n');}
 if(report.errors.length)throw Error(report.errors.join('\n'));
})().catch(e=>{console.error(e);process.exitCode=1;});
