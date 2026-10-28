"""Independently read all exported GLB containers and geometry."""
from pathlib import Path
import json,struct
import numpy as np
import trimesh
ROOT=Path(__file__).resolve().parents[1]
rows=[]
for p in sorted((ROOT/'models').glob('*/*.glb')):
 b=p.read_bytes();magic,version,size=struct.unpack_from('<III',b)
 assert (magic,version,size)==(0x46546c67,2,len(b))
 n,typ=struct.unpack_from('<II',b,12);assert typ==0x4e4f534a
 g=json.loads(b[20:20+n]);slots={x['extras']['slot'] for x in g['nodes']}
 assert 'body' not in slots
 if p.stem=='armor':assert {'head','chest','legs','boots'}<=slots and 'ingot' not in slots
 else:assert slots=={'ingot'}
 scene=trimesh.load(p,force='scene',process=False)
 assert scene.geometry
 tris=0
 for geom in scene.geometry.values():
  assert np.isfinite(geom.vertices).all() and len(geom.faces)>0
  tris+=len(geom.faces)
 rows.append(dict(file=str(p.relative_to(ROOT)),status='passed',nodes=len(g['nodes']),triangles=tris,slots=sorted(slots)))
assert len(rows)==30
report=dict(status='passed',GLB_files=30,checks=rows)
(ROOT/'tests'/'portable-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print('30 GLB files re-read successfully by independent trimesh loader; 4 armor slot groups and ingot separation checked')
