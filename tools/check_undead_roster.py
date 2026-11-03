"""检查地域怪物的数据、生成归属、UV 和攻击帧；不启动游戏。"""
from __future__ import annotations
import json
from pathlib import Path
from PIL import Image
from tools.gen_undead_roster import ROOT,catalogue,validate,outputs
from tools.beastgen.build import Built,same_file

def check(contract_schema=None):
    c=validate(catalogue());errors=[]
    for path,data in outputs().items():
        if not same_file(path,data):errors.append('STALE '+str(path.relative_to(ROOT)))
    for v in c['variants']:
        b=Built(v['id'])
        for p,data in b.outputs().items():
            if not same_file(p,data):errors.append('STALE '+str(p.relative_to(ROOT)))
        used={(t.u,t.v) for t in b.texels}
        for y,row in enumerate(b.texture):
            for x,p in enumerate(row):
                if p[3] not in (0,255) or bool(p[3])!=((x,y) in used):errors.append(v['id']+' UV alpha mismatch');break
        clips={a.name:a for a in b.clips}
        for action in v['moves']:
            clip=clips[action['animation']]
            if clip.loop or abs(clip.length-action['total_ticks']/20)>1e-6:errors.append(v['id']+' attack duration differs')
            times={round(k.time*20) for k in clip.keys}
            if not set(action['strike_ticks'])<=times:errors.append(v['id']+' missing attack pose at damage tick')
        # Schema validation is built from the single authoritative catalogue, not a second parameter file.
        from jsonschema import Draft202012Validator
        schema_path=Path(contract_schema) if contract_schema else None
        if schema_path is not None:
            contract={'schema_version':1,'project':c['project'],'entity':{'id':v['id'],'resource_location':'myvillage:'+v['id'],'display_name':{'zh_cn':v['name'],'en_us':v['name']},'kind':v['family'],'base_class':'BeastEntity','mob_category':'MONSTER','temperament':'hostile'},'physical':v['physical'],'attributes':v['attributes'],'behavior':{'goals':[],'target_goals':[],'combat':{'source':'catalogue.moves'}},'state':{'synced':[{'name':n} for n in c['contract']['synced']],'persisted':c['contract']['persisted']},'rendering':{'route':'vanilla','base_texture':'myvillage:textures/entity/'+v['id']+'/'+v['id']+'.png','final_texture_size':dict(zip(('width','height'),b.model.texture_size)),'bones':[n.name for n in b.model.bones],'animations':[{'name':n.name} for n in b.clips]},'acceptance':c['contract']['acceptance']}
            errors.extend(v['id']+' contract '+e.message for e in Draft202012Validator(json.loads(schema_path.read_text())).iter_errors(contract))
    for family in ['corpse','archer']:
        folder=ROOT/'art/entities/undead_roster'/family
        im=Image.open(folder/'material_tones.png').convert('RGBA');mask=Image.open(folder/'uv_islands_mask.png').convert('L')
        if im.size!=mask.size:errors.append('material dimensions '+family)
        if len({p[:3] for p in im.getdata() if p[3]})>32:errors.append('material palette '+family)
        if any(bool(p[3])!=(m>0) for p,m in zip(im.getdata(),mask.getdata())):errors.append('material outside UV '+family)
    if errors:raise ValueError('\n'.join(errors))
    print('undead roster: 18 variants, data/assets/UV/timing checks passed')
if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--contract-schema');a=p.parse_args();check(a.contract_schema)
