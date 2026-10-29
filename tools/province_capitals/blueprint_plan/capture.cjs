'use strict';
// 本地文件检查；不启动服务，不使用原生游戏替代品作验收。
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const out=path.resolve(process.argv[2]||'out/preview/lingyue-blueprint-plan-20261030');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader']});
 const report={scope:'蓝图与三维预览界面检查；非 Minecraft 运行验证',errors:[],captures:[]};
 try{
  const page=await browser.newPage({viewport:{width:1100,height:1000},deviceScaleFactor:1});
  page.on('pageerror',e=>report.errors.push(e.message));
  await page.goto('file://'+path.join(out,'index.html'));
  await page.waitForFunction(()=>globalThis.LINGYUE_PLAN?.ready,{},{timeout:30000});
  const source=JSON.parse(fs.readFileSync(path.join(out,'plan.json'),'utf8'));
  assert.equal(await page.evaluate(()=>LINGYUE_PLAN.data.source_fingerprint),source.source_fingerprint);
  await page.screenshot({path:path.join(out,'plan.png'),fullPage:true});report.captures.push('plan.png');
  await page.evaluate(()=>LINGYUE_PLAN.select('lingyue_garden_07'));
  assert.match(await page.locator('#selectedName').innerText(),/坡间药圃/);
  assert.match(await page.locator('#selectedConnection').innerText(),/高差 6 格/);
  await page.locator('#spaceMode').click();
  await page.waitForFunction(()=>LINGYUE_PLAN.renderer?.info.render.calls>0);
  report.webgl=await page.evaluate(()=>({calls:LINGYUE_PLAN.renderer.info.render.calls,triangles:LINGYUE_PLAN.renderer.info.render.triangles}));
  assert.ok(report.webgl.triangles>10000);
  await page.locator('#stage').screenshot({path:path.join(out,'space.png')});report.captures.push('space.png');
  await page.locator('#district').selectOption('mid_w');
  await page.locator('#stage').screenshot({path:path.join(out,'mid-west-space.png')});report.captures.push('mid-west-space.png');
  await page.setViewportSize({width:390,height:1000});
  await page.locator('#planMode').click();
  await page.locator('#district').selectOption('all');
  report.mobile=await page.evaluate(()=>({width:innerWidth,document:document.documentElement.scrollWidth,canvas:document.querySelector('#map').getBoundingClientRect().width}));
  assert.ok(report.mobile.document<=report.mobile.width);
  await page.screenshot({path:path.join(out,'mobile.png'),fullPage:true});report.captures.push('mobile.png');
  assert.deepEqual(report.errors,[]);report.status='pass';
 }finally{await browser.close();fs.writeFileSync(path.join(out,'capture-report.json'),JSON.stringify(report,null,2)+'\n');}
 console.log(JSON.stringify(report));
})().catch(e=>{console.error(e);process.exitCode=1;});
