"""从正式施工格展开真实府邸构件，城内细部沿用既有方块预览器。"""
import copy
from .manor_relayout import ROOT,read,manor_catalog,rotate_position
from province_capitals.regional.v2.preview import SceneBuilder

def properties(state):
    return dict(v.split('=',1) for v in state[:-1].split('[',1)[1].split(',')) if '[' in state else {}

def preview_scene(planner,data):
    builder=SceneBuilder(planner.model);catalog=manor_catalog();banks={'myvillage:'+b['id']:b for b in catalog['banks']}
    materials={'myvillage:'+m['id']:m for m in catalog['materials']};scene_materials=planner.manor_scene['materials'];extra={}
    def color(role):
        key='manor_'+role;value=scene_materials.get(role,'#725838' if role.startswith('plaque_') else '#655f63')
        extra[key]={'color':value};return key
    native=[];pillar_models={}
    for key in sorted(planner.model.construction.by_tile):
        raw=planner.model.construction.tile(*key);standard={}
        for p,record in raw.items():
            state,tag,layer=record;base=state.split('[',1)[0];values=properties(state);x,y,z=p;x+=key[0]*16;z+=key[1]*16
            boxes=[];turn=0
            if base in banks:
                profile=banks[base]['profiles'][int(values['shape'])];boxes=profile['boxes'];turn=('north','east','south','west').index(values['facing'])
            elif base in materials:
                boxes=[[0,0,0,16,16,16,materials[base]['role']]]
            elif base=='myvillage:demon_pact_pillar':
                part=int(values['part']);turn=('north','east','south','west').index(values['facing'])
                if part not in pillar_models:
                    f=ROOT/f'src/main/resources/assets/myvillage/models/block/demon_pact_pillar/part_{part}.json'
                    pillar_models[part]=read(str(f.relative_to(ROOT)))['elements']
                boxes=[e['from']+e['to']+['stone_dark'] for e in pillar_models[part]]
            else:standard[p]=record;continue
            for b in boxes:
                a,b_y,c,e,f,g,role=b[:7];corners=[rotate_position(u,v,16,16,turn) for u,v in ((a,c),(a,g),(e,c),(e,g))];xs=[p[0] for p in corners];zs=[p[1] for p in corners]
                lo=[x+min(xs)/16,y+b_y/16,z+min(zs)/16];hi=[x+max(xs)/16,y+f/16,z+max(zs)/16]
                native.append({'p':[(a+b)/2 for a,b in zip(lo,hi)],'s':[b-a for a,b in zip(lo,hi)],'m':color(role),'layer':'buildings','tag':'lord_manor','q':0})
        builder.add_tile(*key,standard)
    mats={k:{'color':v} for k,v in builder.palette.items()};mats.update(extra)
    return {'boxes':builder.boxes+native,'meshes':builder.meshes,'materials':mats,'terrain':planner.model.terrain}
