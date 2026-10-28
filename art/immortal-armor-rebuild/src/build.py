#!/usr/bin/env python3
"""Build the offline design atlas and portable geometry. Requires Python + NumPy.
All published previews and exported models use these same authored meshes.
"""
import base64,gzip,hashlib,json,sys,zlib
from pathlib import Path
import numpy as np
from catalog import DATA,REALMS,RANKS
from shapes import make_set
from geometry import save_glb,save_obj
ROOT=Path(__file__).resolve().parents[1]

def compact(obj):return json.dumps(obj,ensure_ascii=False,separators=(',',':'))

def main():
    models=[];packed=[];checks=[]
    for s in DATA:
        s['realm']=REALMS[s['level']-1];s['rank']=RANKS[s['level']-1]
        model=make_set(s);meshes=model['meshes'];checks_one=[]
        for m in meshes:
            v=np.array(m['positions']).reshape(-1,3);f=np.array(m['indices']).reshape(-1,3);n=np.array(m['normals']).reshape(-1,3)
            assert np.isfinite(v).all() and np.isfinite(n).all()
            assert len(f)>0 and f.min()>=0 and f.max()<len(v)
            assert np.all(np.linalg.norm(np.cross(v[f[:,1]]-v[f[:,0]],v[f[:,2]]-v[f[:,0]]),axis=1)>1e-12),m['name']
            checks_one.append(dict(name=m['name'],slot=m['slot'],vertices=len(v),triangles=len(f)))
        for slot in ['head','chest','legs','boots','ingot']:assert any(m['slot']==slot for m in meshes)
        model_raw=compact(model).encode();packed.append(base64.b64encode(zlib.compress(model_raw,9)).decode())
        dest=ROOT/'models'/s['id'];dest.mkdir(parents=True,exist_ok=True)
        for kind in ['armor','ingot']:
            chosen=[m for m in meshes if (m['slot']=='ingot' if kind=='ingot' else m['slot'] not in ['ingot','body'])]
            save_glb(chosen,model['materials'],dest/(kind+'.glb'))
            # OBJ/MTL supplied in the package as a universally inspectable fallback.
            save_obj(chosen,model['materials'],dest/(kind+'.obj'))
        (dest/'source-meshes.json.gz').write_bytes(gzip.compress(model_raw,mtime=0))
        stats=dict(vertices=sum(x['vertices'] for x in checks_one),triangles=sum(x['triangles'] for x in checks_one),meshes=len(meshes))
        s['stats']=stats; s['materials']={k:v for k,v in model['materials'].items() if k not in ['skin','hair']}
        checks.append(dict(id=s['id'],name=s['name'],status='passed',stats=stats,mesh_sha256=hashlib.sha256(model_raw).hexdigest(),checks=['finite_positions','finite_normals','valid_triangle_indices','nondegenerate_triangles','all_five_required_parts','portable_GLB_written'],meshes=checks_one))
        print(s['id'],stats,'packed',len(packed[-1]),flush=True)
    (ROOT/'src'/'catalog.json').write_text(json.dumps(DATA,ensure_ascii=False,indent=2),encoding='utf-8')
    (ROOT/'src'/'packed-models.json').write_text(compact(packed),encoding='utf-8')
    (ROOT/'tests'/'geometry-report.json').write_text(json.dumps(dict(status='passed',set_count=15,realm_count=5,checks=checks),ensure_ascii=False,indent=2),encoding='utf-8')
    assemble()

def assemble():
    template=(ROOT/'src'/'viewer.html').read_text(encoding='utf-8')
    catalog=json.loads((ROOT/'src'/'catalog.json').read_text());packed=json.loads((ROOT/'src'/'packed-models.json').read_text())
    thumbs={}
    for s in catalog:
        p=ROOT/'previews'/(s['id']+'.webp')
        if p.exists():thumbs[s['id']]='data:image/webp;base64,'+base64.b64encode(p.read_bytes()).decode()
    engine=(ROOT/'src'/'software.js').read_text(encoding='utf-8')+'\n'+(ROOT/'src'/'viewer.js').read_text(encoding='utf-8')
    html=template.replace('/*__DATA__*/','const CATALOG='+compact(catalog)+';\nconst PACKED='+compact(packed)+';\nconst THUMBS='+compact(thumbs)+';').replace('/*__ENGINE__*/',engine)
    (ROOT/'index.html').write_text(html,encoding='utf-8')
    print('HTML bytes',(ROOT/'index.html').stat().st_size)

if __name__=='__main__':
    if '--assemble-only' in sys.argv:assemble()
    else:main()
