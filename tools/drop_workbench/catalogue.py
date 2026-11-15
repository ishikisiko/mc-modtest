"""从当前模组资源及精确版本的Minecraft缓存建立中文物品目录。"""
from __future__ import annotations
import argparse,hashlib,json,re,shutil,zipfile
from pathlib import Path
from .categories import classify
from .loot import native_loot
ROOT=Path(__file__).resolve().parents[2]
ASSETS='https://assets.ishiki333hhh.win/drop-workbench/'

class Assets:
    def __init__(self,root,client):self.root=root;self.client=zipfile.ZipFile(client)
    def read(self,name):
        path=self.root/'src/main/resources'/name
        if path.is_file():return path.read_bytes()
        try:return self.client.read(name)
        except KeyError:return None
    def model(self,id):
        ns,name=id.split(':',1);raw=self.read(f'assets/{ns}/models/{name}.json')
        return json.loads(raw) if raw else {}
    def texture(self,id,seen=None):
        seen=set() if seen is None else seen
        if id in seen:return None
        seen.add(id);ns,name=id.split(':',1);model=self.model(id)
        if model.get('loader')=='neoforge:separate_transforms':
            gui=model.get('perspectives',{}).get('gui',{})
            if isinstance(gui,str):return self.texture(gui,seen)
            model=gui if isinstance(gui,dict) and gui else model.get('base',model)
        textures=model.get('textures',{})
        for key in ['layer0','particle','all','side','front','top',*textures]:
            texture=textures.get(key)
            if not texture:continue
            for _ in range(8):
                if texture.startswith('#'):texture=textures.get(texture[1:],'');continue
                break
            if not texture or texture.startswith('#'):continue
            if ':' not in texture:texture='minecraft:'+texture
            tns,tname=texture.split(':',1);data=self.read(f'assets/{tns}/textures/{tname}.png')
            if data:return data
        parent=model.get('parent')
        if parent:
            if ':' not in parent:parent='minecraft:'+parent
            result=self.texture(parent,seen)
            if result:return result
        if name.startswith('item/'):
            return self.read(f'assets/{ns}/textures/{name}.png') or self.read(f'assets/{ns}/textures/block/{name[5:]}.png')
        return None

def build(root=ROOT,client=None,assets_cache=None,out=None):
    client=client or Path.home()/'.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'
    assets_cache=assets_cache or Path.home()/'.gradle/caches/neoformruntime/assets'
    out=out or root/'reports/drop-workbench/site';out.mkdir(parents=True,exist_ok=True);(out/'icons').mkdir(exist_ok=True)
    reader=Assets(root,client);index=json.loads((assets_cache/'indexes/17.json').read_text());entry=index['objects']['minecraft/lang/zh_cn.json'];zh=assets_cache/'objects'/entry['hash'][:2]/entry['hash']
    raw=zh.read_bytes()
    if hashlib.sha1(raw).hexdigest()!=entry['hash']:raise ValueError('Minecraft中文资源哈希不符')
    langs={'minecraft':json.loads(raw),'yuanxian':json.loads((root/'src/main/resources/assets/yuanxian/lang/zh_cn.json').read_text())}
    model_names={'minecraft':{n.split('/')[-1][:-5] for n in reader.client.namelist() if n.startswith('assets/minecraft/models/item/') and n.endswith('.json')},'yuanxian':{p.stem for p in (root/'src/main/resources/assets/yuanxian/models/item').glob('*.json')}}
    items=[]
    extra_names=json.loads((root/'tools/drop_workbench/labels.json').read_text())
    direct_ids=set(re.findall(r'\bITEMS\.register(?:Item|SimpleItem|SimpleBlockItem)?\(\s*"([a-z0-9_]+)"',(root/'src/main/java/com/example/myvillage/item/ModItems.java').read_text()))
    def icon(id):
        blob=reader.texture(id)
        if blob is None and id=='yuanxian:item/cultivation_handbook':blob=reader.texture('minecraft:item/book')
        if not blob:return None
        name=hashlib.sha256(blob).hexdigest()[:24]+'.png';p=out/'icons'/name
        if not p.exists():p.write_bytes(blob)
        return ASSETS+'icons/'+name
    for ns in langs:
        for name in sorted(model_names[ns]):
            label=langs[ns].get('item.'+ns+'.'+name) or langs[ns].get('block.'+ns+'.'+name)
            if not label:
                parent=reader.model(ns+':item/'+name).get('parent','')
                if isinstance(parent,str) and parent.startswith(ns+':block/'):
                    label=langs[ns].get('block.'+ns+'.'+parent.split(':block/',1)[1])
            if not label and ns=='yuanxian' and name in direct_ids:label=extra_names.get(ns+':'+name,ns+':'+name)
            if not label or name=='air':continue
            if name.startswith('music_disc_') or name.endswith('_smithing_template'):
                desc=langs[ns].get('item.'+ns+'.'+name+'.desc')
                if desc:label+=' · '+desc
            items.append({'id':ns+':'+name,'name':label,'namespace':ns,'icon':icon(ns+':item/'+name),'is_block':'block.'+ns+'.'+name in langs[ns]})
    entities={}
    for ns in langs:
        for key,label in langs[ns].items():
            if key.startswith('entity.'+ns+'.'):
                id=ns+':'+key[len('entity.'+ns+'.'):]
                if id=='minecraft:player' or '.' in id.split(':')[1]:continue
                entities[id]={'id':id,'name':label,'namespace':ns}
    defaults=json.loads((root/'src/main/resources/data/yuanxian/drops/monster_drops.json').read_text())
    for id in defaults['entities']:
        entities.setdefault(id,{'id':id,'name':id,'namespace':id.split(':')[0]})
    # 注册类的取值用于限制真实实体ID，避免将翻译子键视为实体。
    for ns in list(langs):
        if ns=='minecraft':
            names={n.split('/')[-1][:-5] for n in reader.client.namelist() if n.startswith('data/minecraft/loot_table/entities/') and n.endswith('.json')}
        else:names={p.stem for p in (root/'src/main/resources/data/yuanxian/loot_table/entities').glob('*.json')}
        entities={id:v for id,v in entities.items() if v['namespace']!=ns or id.split(':')[1] in names or id in defaults['entities']}
    categories=classify(root,reader,items)
    unsupported=native_loot(reader,langs,entities)
    portraits=root/'reports/drop-workbench/portraits'
    if (portraits/'manifest.json').exists():
        views=json.loads((portraits/'manifest.json').read_text())
        for id,v in entities.items():
            if id in views:v['portrait']=views[id]
        shutil.copytree(portraits/'entities',out/'entities',dirs_exist_ok=True)
        with zipfile.ZipFile(out/'creature-views.zip','w') as archive:
            archive.writestr('图片说明.txt','由 Minecraft 1.21.1 与当前源仙模组的实际模型、贴图生成。每种生物提供三个角度的离线3D预览，显示一种代表外观；游戏中的装备、动作及外观变种会随状态变化。这些图片用于辨认生物。\n')
            for id,v in entities.items():
                for view in v.get('portrait',{}).get('views',[]):
                    file=view['url'].split('/')[-1].split('?')[0];archive.write(portraits/'entities'/file,('原版' if v['namespace']=='minecraft' else '源仙')+'/'+v['name']+'__'+id.split(':')[1]+'/'+view['name']+'.png')
    result={'version':2,'minecraft_version':'1.21.1','items':items,'categories':categories,'entities':sorted(entities.values(),key=lambda v:(v['namespace'],v['id'])),'regions':[{'id':id,'name':name} for id,name in [('donghuang','东荒'),('lingyue','灵岳'),('beiyuan','北原'),('donghai','东海'),('ximo','西漠'),('nanjiang','南疆'),('moyu','魔域'),('zhongzhou','中州')]],'realms':[],'provenance':{'minecraft_client_sha256':hashlib.file_digest(client.open('rb'),'sha256').hexdigest(),'minecraft_language_sha1':entry['hash'],'mod_language_sha256':hashlib.sha256((root/'src/main/resources/assets/yuanxian/lang/zh_cn.json').read_bytes()).hexdigest(),'selection':'当前物品模型及其物品名称、BlockItem父方块名称','loot_unrecognized':unsupported}}
    realm_names={'mortal':'凡人','qi_refining':'炼气','foundation_establishment':'筑基','golden_core':'金丹','nascent_soul':'元婴','spirit_transformation':'化神'}
    for value in json.loads((root/'src/main/resources/data/yuanxian/stats/rules.json').read_text())['realms']:
        id=value['id'];name=id.split(':')[1]
        result['realms'].append({'id':id,'name':realm_names.get(name,name),'order':value['order'],'stages':value['stages']})
    result['realms'].sort(key=lambda v:v['order']);(out/'catalogue.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n')
    for path in (root/'web/drop-workbench').iterdir():
        if path.suffix in ('.html','.css','.js'):shutil.copy2(path,out/path.name)
    print(json.dumps({'items':len(items),'by_namespace':{ns:sum(v['namespace']==ns for v in items) for ns in langs},'entities':len(entities),'icons':len(list((out/'icons').glob('*.png'))),'output':str(out)},ensure_ascii=False))
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--client',type=Path);p.add_argument('--assets-cache',type=Path);p.add_argument('--out',type=Path);a=p.parse_args();build(a.root,a.client,a.assets_cache,a.out)
if __name__=='__main__':main()
