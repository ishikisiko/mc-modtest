"""Load the self-contained document, exercise real geometry views, capture previews.
Uses Playwright + a Chromium executable, no Internet or Minecraft runtime.
"""
import asyncio,base64,io,json,sys,time
from pathlib import Path
from PIL import Image,ImageOps
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
async def main(start=0,end=15):
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  page=await browser.new_page(viewport={'width':1500,'height':1050},device_scale_factor=1.4)
  errors=[];requests=[];results=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('request',lambda r:requests.append(r.url))
  await page.set_content((ROOT/'index.html').read_text(),wait_until='load',timeout=60000)
  assert await page.evaluate('atlasReady'), 'atlas init failed'
  catalog=json.loads((ROOT/'src'/'catalog.json').read_text())
  for i in range(start,end):
   sid=catalog[i]['id'];await page.evaluate('(i)=>atlas.select(i)',i)
   for part in ['all','head','chest','legs','boots','ingot']:
    await page.evaluate('(p)=>atlas.part(p)',part)
    info=await page.evaluate('atlas.info()');assert info['visible']>0 and info['triangles']>0 and info['error'] is None and info['glError'] in [None,0],info
    results.append(info)
    if part in ['all','ingot']:
     raw=await page.evaluate('atlas.capture()');im=Image.open(io.BytesIO(base64.b64decode(raw.split(',')[1]))).convert('RGB')
     dest=ROOT/'previews'/(sid+('-ingot' if part=='ingot' else '')+'.png');im.save(dest)
     if part=='all':
      thumb=ImageOps.fit(im,(450,600),method=Image.Resampling.LANCZOS,centering=(.5,.5));thumb.save(ROOT/'previews'/(sid+'.webp'),quality=88)
   await page.evaluate("()=>{atlas.part('all');atlas.state.yaw=Math.PI+.28;atlas.render()}")
   raw=await page.evaluate('atlas.capture()');Image.open(io.BytesIO(base64.b64decode(raw.split(',')[1]))).convert('RGB').save(ROOT/'previews'/(sid+'-back.png'))
   print(sid,results[-1]['renderer'],'six views + rear captured',flush=True)
  report=dict(status='passed',document_loading='set_content: same self-contained HTML; managed Chromium blocks file navigation',network_requests=requests,js_errors=errors,views=results)
  assert not errors and not requests,report
  (ROOT/'tests'/f'browser-report-{start:02d}-{end:02d}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
  await browser.close()
if __name__=='__main__':asyncio.run(main(int(sys.argv[1]) if len(sys.argv)>1 else 0,int(sys.argv[2]) if len(sys.argv)>2 else 15))
