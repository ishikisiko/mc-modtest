from pathlib import Path
import argparse,json,base64
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--all',action='store_true');a.add_argument('--start',type=int,default=0);a.add_argument('--end',type=int,default=15);args=a.parse_args()
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 page=b.new_page(viewport={'width':1440,'height':1000},device_scale_factor=1)
 errors=[];page.on('pageerror',lambda err:errors.append(str(err)))
 page.set_content((ROOT/'index.html').read_text(),wait_until='load');page.wait_for_function('window.ready===true')
 page.screenshot(path=str(ROOT/'previews/interface-first.png'))
 ids=page.evaluate('DATA.models.map(m=>m.id)')
 for i in range(args.start,args.end) if args.all else [0,5,12]:
  for view,yaw in [('front',-.35),('back',3.14159)]:
   st=page.evaluate('async o=>await studio.set(o)',{'index':i,'yaw':yaw,'pitch':.12,'zoom':1,'part':'all','pose':'standing'})
   (ROOT/'previews'/f'{ids[i]}-{view}.png').write_bytes(base64.b64decode(page.evaluate('canvas.toDataURL().split(",")[1]')))
  if args.all:
   page.evaluate('async()=>await studio.set({part:"ingot",yaw:-.5,pitch:.65,zoom:1})')
   (ROOT/'previews'/f'{ids[i]}-ingot.png').write_bytes(base64.b64decode(page.evaluate('canvas.toDataURL().split(",")[1]')))
  print(ids[i],st,flush=True)
 print('errors:',errors)
 b.close()
