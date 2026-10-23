#!/usr/bin/env python3
"""Cross-module closure checks for the six ordinary encounters and their actual production uses."""
from __future__ import annotations
import json
from pathlib import Path
from tools.gen_wilderness_roster import ROOT,catalogue,outputs
from tools.validate_custom_entities import read_png_rgba,validate

def check(root=ROOT):
    errors=[];c=catalogue();res=root/'src/main/resources'
    generated=outputs(root)
    for p,data in generated.items():
        if not p.exists() or p.read_bytes()!=data:errors.append('stale:'+str(p.relative_to(root)))
    ar=json.loads((res/'data/myvillage/alchemy/rules.json').read_text());fr=json.loads((res/'data/myvillage/forging/rules.json').read_text())
    for m in c['mobs']:
        id=m['id'];pill=m['pill_id'];trait=m['trait_id'];material='myvillage:'+m['material_id']
        recipe=next((r for r in ar['recipes'] if r['id']==pill),None)
        if not recipe or recipe['ingredients']!=[material,*m['alchemy_herbs']]:errors.append('alchemy closure:'+id)
        if fr['traits'].get(trait,{}).get('costs')!={material:m['trait_cost']}:errors.append('forging closure:'+id)
        if not any(trait in r['traits'] for r in fr['recipes']):errors.append('unselectable trait:'+trait)
        tex=res/f'assets/myvillage/textures/entity/{id}/{id}.png';w,h,rgba=read_png_rgba(tex)
        if set(rgba[3::4])-{0,255}:errors.append('nonbinary alpha:'+id)
        art=root/'art/entities'/id
        if not (art/(id+'.bbmodel')).exists():errors.append('missing model source:'+id)
        truth=json.loads((art/'uv_truth.json').read_text())
        if truth['size']!=[w,h]:errors.append('UV size:'+id)
        for f in truth['faces']:
            x,y,fw,fh=f['rect']
            if x<0 or y<0 or x+fw>w or y+fh>h:errors.append('UV bounds:'+id+':'+f['id'])
        if not (art/'texture_provenance.json').exists():errors.append('missing texture provenance:'+id)
        loot=json.loads((res/f'data/myvillage/loot_table/entities/{id}.json').read_text())
        entries=[e for pool in loot['pools'] for e in pool['entries']]
        if not any(e['name']==material for e in entries):errors.append('missing loot material:'+id)
    errors.extend(validate(root)['errors'])
    return errors

if __name__=='__main__':
    problems=check();print('\n'.join(problems) if problems else 'wilderness roster closure: PASS');raise SystemExit(bool(problems))
