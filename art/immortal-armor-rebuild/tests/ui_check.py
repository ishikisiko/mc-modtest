"""Extra UI / model-export checks against the actual self-contained HTML."""
import asyncio,base64,json,struct
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
async def main():
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  page=await browser.new_page(viewport={'width':1500,'height':1050},device_scale_factor=1)
  errors=[];requests=[];steps=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.on('request',lambda r:requests.append(r.url))
  await page.set_content((ROOT/'index.html').read_text(),wait_until='load',timeout=60000)
  assert await page.evaluate('atlasReady')
  await page.evaluate('atlas.select(12)')
  for mode in ['material','clay','silhouette','wire']:
   await page.locator(f'[data-shade="{mode}"]').click();await page.evaluate('atlas.render()')
   assert await page.evaluate('atlas.state.shade')==mode;steps.append('shade_'+mode)
  await page.locator('[data-shade="material"]').click()
  for view in ['front','side','back','iso']:
   await page.locator(f'[data-view="{view}"]').click();await page.evaluate('atlas.render()');steps.append('camera_'+view)
  for el,flag in [('fx','fx'),('body','body'),('rotate','spin')]:
   await page.locator('#'+el).uncheck();assert not await page.evaluate('atlas.state.'+flag)
   await page.locator('#'+el).check();assert await page.evaluate('atlas.state.'+flag);steps.append('toggle_'+el)
  await page.locator('#rotate').uncheck()
  await page.locator('#theme').click();assert await page.evaluate('atlas.state.dark')
  await page.locator('#theme').click();assert not await page.evaluate('atlas.state.dark');steps.append('theme_both')
  await page.locator('#compare-toggle').click();await page.locator('#compare-select').select_option('14')
  await page.wait_for_timeout(500);await page.evaluate('atlas.render()')
  assert await page.evaluate('atlas.state.compare && atlas.state.other===14');steps.append('same_light_compare')
  await page.screenshot(path=str(ROOT/'previews'/'interface-compare.png'))
  await page.locator('#compare-toggle').click()
  await page.locator('#reset').click();await page.evaluate('atlas.render()')
  # Exercise export construction, then check the binary independently. No claim
  # is made about managed-browser download permission or destination handling.
  await page.evaluate("atlas.part('head')")
  encoded=await page.evaluate("()=>{const b=atlas.exportGLB();let s='';for(let i=0;i<b.length;i+=8192)s+=String.fromCharCode(...b.subarray(i,i+8192));return btoa(s)}")
  b=base64.b64decode(encoded);magic,version,length=struct.unpack_from('<III',b)
  assert magic==0x46546c67 and version==2 and length==len(b)
  n,typ=struct.unpack_from('<II',b,12);g=json.loads(b[20:20+n]);assert typ==0x4e4f534a and len(g['meshes'])>0
  assert all(x['extras']['slot']=='head' for x in g['nodes']);steps.append('browser_GLB_export_header_and_slot')
  obj=await page.evaluate('atlas.exportOBJ()');assert '\nv ' in obj and '\nf ' in obj;steps.append('browser_OBJ_export')
  png=await page.evaluate('atlas.capture()');assert base64.b64decode(png.split(',')[1])[:8]==b'\x89PNG\r\n\x1a\n';steps.append('browser_PNG_export')
  await page.evaluate("atlas.part('all')");await page.screenshot(path=str(ROOT/'previews'/'interface-desktop.png'))
  assert await page.locator('img').count()==30
  assert await page.evaluate('Array.from(document.images).every(i=>i.complete && i.naturalWidth>0)');steps.append('30_embedded_thumbnails_decode')
  for name in ['gallery','rules','studio']:
   await page.locator(f'[data-page="{name}"]').click();assert await page.locator('#'+name).is_visible();steps.append('page_'+name)
  box=await page.locator('#canvas').bounding_box()
  yaw=await page.evaluate('atlas.state.yaw');await page.mouse.move(box['x']+box['width']*.5,box['y']+box['height']*.5);await page.mouse.down();await page.mouse.move(box['x']+box['width']*.5+50,box['y']+box['height']*.5+10);await page.mouse.up()
  assert await page.evaluate('atlas.state.yaw')!=yaw;steps.append('pointer_orbit')
  z=await page.evaluate('atlas.state.zoom');await page.mouse.wheel(0,100);await page.wait_for_timeout(150);assert await page.evaluate('atlas.state.zoom')!=z;steps.append('wheel_zoom')
  await page.set_viewport_size({'width':390,'height':844});await page.wait_for_timeout(200)
  mobile=[]
  for name in ['studio','gallery','rules']:
   await page.locator(f'[data-page="{name}"]').click();await page.evaluate('atlas.render()')
   sizes=await page.evaluate('({width:innerWidth,document:document.documentElement.scrollWidth})');assert sizes['document']<=sizes['width']+1,sizes;mobile.append({'page':name,**sizes})
   if name=='studio':await page.screenshot(path=str(ROOT/'previews'/'interface-mobile.png'),full_page=True)
  steps.append('390px_three_pages_no_horizontal_overflow')
  assert not errors and not requests,(errors,requests)
  report={'status':'passed','steps':steps,'step_count':len(steps),'mobile':mobile,'js_errors':errors,'network_requests':requests,'renderer':await page.evaluate('atlas.info().renderer'),'download_button_OS_save':'not_verified; export bytes checked','webgl_path':'not_verified; browser exposes no context'}
  (ROOT/'tests'/'ui-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False,indent=2))
  await browser.close()
if __name__=='__main__':asyncio.run(main())
