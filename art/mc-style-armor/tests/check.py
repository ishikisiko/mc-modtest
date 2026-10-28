"""Focused geometry/export/UI checks. Does not assert Minecraft runtime compatibility."""
from pathlib import Path
import sys,json,math,base64
from collections import Counter
from PIL import Image
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'src'))
from build import DATA,make_armor,BONES,box_faces,corners_world,rotate,world
catalog=json.loads((ROOT/'catalog.json').read_text())
assert len(catalog['models'])==15
assert Counter(m['brief']['level'] for m in catalog['models'])=={1:3,2:3,3:3,4:3,5:3}
geometry=[]
for m in catalog['models']:
 assert all(b['size']==[8,8,8] for b in m['profiles']['standard'] if b['name']=='展示头部')
 assert all(b['size']==[8,12,4] for b in m['profiles']['standard'] if b['name']=='展示躯干')
 for profile,boxes in m['profiles'].items():
  arm=next(b for b in boxes if b['name']=='展示手臂_right_arm');assert arm['size']==([3,12,4] if profile=='slim' else [4,12,4])
  for b in boxes:
   assert b['bone'] in BONES
   assert len(b['size'])==3 and all(v>0 and math.isfinite(v) for v in b['size'])
   assert all(math.isfinite(v) for v in b['from'])
   for _,r in box_faces(b):assert all(0<=v<=512 for v in r),(m['id'],b['name'],r)
   for vs,_ in corners_world(b):assert all(math.isfinite(x) for v in vs for x in v)
  bb=json.loads((ROOT/'models'/m['id']/f'armor-{profile}.bbmodel').read_text())
  assert all(e['type']=='cube' for e in bb['elements'])
  ids={e['uuid'] for e in bb['elements']};assert len(ids)==len(bb['elements'])
  assert {c for group in bb['outliner'] for c in group['children']}==ids
  # Geometry parity: BBModel bounds and rotations reconstruct the same cuboid vertices.
  lookup={b['name']:b for b in boxes if b['slot'] not in ('skin','ingot')}
  for e in bb['elements']:
   b=lookup[e['name']];actual=set()
   for x in [e['from'][0],e['to'][0]]:
    for y in [e['from'][1],e['to'][1]]:
     for z in [e['from'][2],e['to'][2]]:
      q=rotate([v-e['origin'][i] for i,v in enumerate([x,y,z])],e['rotation']);actual.add(tuple(q[i]+e['origin'][i] for i in range(3)))
   expected={tuple(p) for vs,_ in corners_world(b) for p in vs}
   assert len(actual)==8 and len(expected)==8
   assert all(min(math.dist(a,e) for e in expected)<2e-6 for a in actual),(m['id'],e['name'])
  assert bb['textures'][0]['source']==m['texture']
 for name in ['armor','ingot']:
  obj=(ROOT/'models'/m['id']/f'{name}.obj').read_text();assert 'usemtl pixel_atlas' in obj and 'vt ' in obj
  assert 'map_Kd texture.png' in (ROOT/'models'/m['id']/f'{name}.mtl').read_text()
 spec=json.loads((ROOT/'models'/m['id']/'armor.authored.json').read_text());mats=json.loads((ROOT/'models'/m['id']/'materials.json').read_text())
 assert spec['enabled'] is False
 assert {i['slot'] for i in spec['items']}=={'head','chest','legs','feet'}
 assert all(b['material'] in mats for i in spec['items'] for b in i['boxes'])
 geometry.append({'id':m['id'],'native_rig':True,'both_profiles':True,'bbmodel_geometry_parity':True,'uv_bounds':True,'image':list(Image.open(ROOT/'models'/m['id']/'texture.png').size)})
(ROOT/'tests/geometry.json').write_text(json.dumps(geometry,ensure_ascii=False,indent=2))
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 page=b.new_page(viewport={'width':1440,'height':1000});errors=[];requests=[]
 page.on('pageerror',lambda e:errors.append(str(e)));page.on('request',lambda r:requests.append(r.url))
 page.set_content((ROOT/'index.html').read_text());page.wait_for_function('window.ready===true')
 results=[]
 for i in range(15):
  for profile in ['standard','slim']:
   for part in ['all','head','chest','legs','feet','ingot']:
    st=page.evaluate('async o=>await studio.set(o)',{'index':i,'profile':profile,'part':part,'pose':'standing','yaw':-.35,'pitch':.6 if part=='ingot' else .12,'zoom':1})
    assert st['faces']>0 and st['cuboids']>0
    results.append(st)
  for pose in ['walk','raised','crouch']:
   st=page.evaluate('async o=>await studio.set(o)',{'index':i,'profile':'standard','part':'all','pose':pose,'pitch':.12});assert st['faces']>0;results.append(st)
  print('checked',catalog['models'][i]['id'],flush=True)
 # Exercise actual UI controls and downloads rather than API-only mode switches.
 page.evaluate('async()=>await studio.set({index:12,part:"all",profile:"standard",pose:"standing",yaw:-.48,pitch:.13,zoom:1})')
 page.get_by_role('button',name='十五套总览').click();assert page.locator('.tile').count()==15
 assert page.locator('.tile img').evaluate_all('(imgs)=>imgs.every(i=>i.complete&&i.naturalWidth>0)')
 page.locator('.tile').nth(14).click();assert page.locator('#setname').inner_text().startswith('无相')
 page.get_by_role('button',name='查看贴图').click();assert page.locator('#textureview').is_visible()
 page.get_by_role('button',name='查看贴图').click()
 page.locator('#shade').select_option('clay');page.wait_for_timeout(150)
 page.locator('#shade').select_option('texture');page.wait_for_timeout(150)
 for button in ['下载 BBModel','下载 PNG 贴图','保存当前截图']:
  with page.expect_download() as dl:page.get_by_role('button',name=button).click()
  assert dl.value.suggested_filename
 page.evaluate('window.scrollTo(0,0)');page.screenshot(path=str(ROOT/'previews/interface-desktop.png'))
 page.set_viewport_size({'width':430,'height':932});page.wait_for_timeout(150)
 assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
 page.screenshot(path=str(ROOT/'previews/interface-mobile.png'),full_page=True)
 assert not errors,errors
 assert not [u for u in requests if u.startswith(('http:','https:'))],requests
 (ROOT/'tests/browser.json').write_text(json.dumps({'combinations':len(results),'results':results,'errors':errors,'external_requests':requests,'gallery_15_loaded':True,'downloads':3,'mobile_no_horizontal_overflow':True,'file_navigation':'Not tested: browser policy blocks file://. Exact HTML loaded with set_content, with no external requests.'},ensure_ascii=False,indent=2))
 b.close()
print('PASS: 15 sets, 30 armor BBModels + 15 ingots, 225 browser combinations, 3 downloads')
