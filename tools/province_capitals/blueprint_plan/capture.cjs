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
  await page.waitForFunction(()=>globalThis.CAPITAL_PLAN?.ready,{},{timeout:30000});
  const source=JSON.parse(fs.readFileSync(path.join(out,'plan.json'),'utf8'));
  assert.equal(await page.evaluate(()=>CAPITAL_PLAN.data.source_fingerprint),source.source_fingerprint);
  await page.screenshot({path:path.join(out,'plan.png'),fullPage:true});report.captures.push('plan.png');
  const garden=source.gardens.find(g=>g.path.length>1)||source.gardens[0];
  assert.ok(garden,'完整蓝图须提供可检查的庭院');
  await page.evaluate(id=>CAPITAL_PLAN.select(id),garden.id);
  assert.equal(await page.locator('#selectedName').innerText(),garden.name||garden.id);
  const rise=Math.abs(garden.path[0][1]-garden.path.at(-1)[1]);
  assert.ok((await page.locator('#selectedConnection').innerText()).includes(`高差 ${rise} 格`));
  await page.locator('#spaceMode').click();
  await page.waitForFunction(()=>CAPITAL_PLAN.renderer?.info.render.calls>0);
  report.webgl=await page.evaluate(()=>({calls:CAPITAL_PLAN.renderer.info.render.calls,triangles:CAPITAL_PLAN.renderer.info.render.triangles}));
  assert.ok(report.webgl.triangles>10000);
  await page.locator('#stage').screenshot({path:path.join(out,'space.png')});report.captures.push('space.png');
  assert.ok(source.districts.length,'完整蓝图须提供街坊定位');
  await page.locator('#district').selectOption(source.districts[0].id);
  await page.locator('#stage').screenshot({path:path.join(out,'district-space.png')});report.captures.push('district-space.png');
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
