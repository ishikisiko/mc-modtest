'use strict';
// 将离线三维模型渲染为审阅图；不启动服务器，不作为 Minecraft 运行证据。
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const out=path.resolve(process.argv[2]);
const metadata=JSON.parse(fs.readFileSync(path.join(out,'index.json'),'utf8'));
(async()=>{
 const report={scope:'offline_model_render',status:'running',captures:{},phases:[],errors:[],minecraft_runtime:'not_run',owner_visual:'not_verified'};
 fs.mkdirSync(path.join(out,'images'),{recursive:true});
 let browser;
 try{
  for(const map of metadata.maps){
   browser=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
   const page=await browser.newPage({viewport:{width:1280,height:800},deviceScaleFactor:1});
   page.on('pageerror',error=>report.errors.push(error.message));
   await page.goto('file://'+path.join(out,'index.html')+'?capture=1&map='+map.id);
   await page.waitForFunction(id=>window.__realmReady&&REALM_REVIEW.state().id===id&&!REALM_REVIEW.state().busy,map.id,{timeout:30000});
   const spec=await page.evaluate(()=>REALM_REVIEW.model().spec);
   const shots=[];
   report.captures[map.id]=shots;
   for(let i=0;i<spec.cameras.length;i++){
    await page.evaluate(i=>REALM_REVIEW.camera(i),i);
    await page.waitForTimeout(180);
    const audit=await page.evaluate(()=>REALM_REVIEW.audit());
    spec.cameras[i].eye.forEach((value,axis)=>assert.ok(Math.abs(audit.camera[axis]-value)<.05,'固定镜头被控制器移位'));
    const file='images/'+map.id+'-'+i+'.png';
    await page.screenshot({path:path.join(out,file),timeout:20000});
    shots.push({file,name:spec.cameras[i].name,camera:audit.camera,hide_roof:await page.evaluate(()=>REALM_REVIEW.state().hideRoof)});
   }
   for(let i=0;i<spec.phases.length;i++){
    await page.evaluate(i=>REALM_REVIEW.phase(i),i);
    const audit=await page.evaluate(()=>REALM_REVIEW.audit());
    assert.equal(audit.phase,i);
    report.phases.push({map:map.id,phase:i,visible_meshes:audit.visibleMeshes,reachable:audit.reachable.length});
   }
   await page.evaluate(()=>REALM_REVIEW.overview());
   const overview=await page.evaluate(()=>REALM_REVIEW.state());
   assert.equal(overview.hideRoof,!!spec.cameras[0].hide_roof);
   await page.evaluate(()=>REALM_REVIEW.camera(1));
   assert.equal(await page.evaluate(()=>REALM_REVIEW.state().hideRoof),false,'玩家视角必须恢复岩顶和屋顶');
   console.log(map.id+'：'+shots.length+'个固定视角与'+spec.phases.length+'个展示阶段完成');
   if(map.id===metadata.maps[0].id){
  await page.evaluate(()=>document.body.classList.remove('capture'));
  await page.setViewportSize({width:1100,height:1000});
  await page.waitForTimeout(200);
  const desktop=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));
  assert.ok(desktop.scroll<=desktop.width);
  await page.screenshot({path:path.join(out,'images/desktop.png'),fullPage:true});
  await page.locator('#actors').click();
  await page.locator('#actors').click();
  await page.setViewportSize({width:390,height:900});
  const mobile=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));
  assert.ok(mobile.scroll<=mobile.width);
  await page.screenshot({path:path.join(out,'images/mobile.png'),fullPage:true});
  report.desktop=desktop;report.mobile=mobile;
   }
   await browser.close();browser=null;
  }
  assert.deepEqual(report.errors,[]);
  report.status='passed';
 }catch(error){
  report.status='failed';report.failure=error.message;throw error;
 }finally{
  if(browser)await browser.close();
  fs.writeFileSync(path.join(out,'render-check.json'),JSON.stringify(report,null,2)+'\n');
 }
})().catch(error=>{console.error(error);process.exitCode=1});
