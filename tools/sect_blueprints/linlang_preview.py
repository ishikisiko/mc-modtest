"""从正式 NBT 切片提取六座琳琅阁，生成可旋转的同尺度对照。"""
from __future__ import annotations
import argparse,base64,gzip,json,struct,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from sect_blueprints.generate import SectModel,SectScene,digest
from province_capitals.regional.v2.preview import COLORS
from buildgen.nbtread import read_gzipped_nbt,state_string

def build(out,resources):
    out=Path(out);out.mkdir(parents=True,exist_ok=True);resources=Path(resources)
    boxes=[];palette={};buildings=[];anchors=[];sources=[]
    views=[{'id':'all','name':'六套琳琅阁对照','eye':[178,87,-65],'target':[86,5,50]}]
    catalog=json.loads((resources/'data/myvillage/sect_blueprints/catalog.json').read_text())
    for i,row in enumerate(catalog['layouts']):
        key=row['id'].split(':')[1];path=resources/f'data/myvillage/sect_blueprints/{key}/sect.json';data=json.loads(path.read_text())
        if digest(path)!=row['sha256']:raise ValueError('正式宗门清单摘要不一致')
        spec=json.loads((ROOT/f'blueprints/sects/{key}.json').read_text());model=SectModel(spec)
        b=next(v for v in data['buildings'] if v['id']=='linlang');ox,oy,oz=b['origin'];w,h,d=b['size']
        x,z=15+(i%3)*58,12+(i//3)*52;offset=[x-ox,-oy,z-oz]
        scene=object.__new__(SectScene);scene.model=model;scene.boxes=[];scene.town_materials={}
        scene.palette={**COLORS,**spec['palette']};palette.update({key+'_'+k:v for k,v in scene.palette.items()})
        for tile in data['tiles']:
            if not(tile['x']<ox+w and tile['x']+16>ox and tile['z']<oz+d and tile['z']+16>oz):continue
            path=resources/('data/myvillage/structure/'+tile['template'].split(':')[1]+'.nbt')
            if digest(path)!=tile['sha256']:raise ValueError('正式宗门切片摘要不一致')
            _,nbt=read_gzipped_nbt(path);states=[state_string(s) for s in nbt['palette']];cells={}
            for block in nbt['blocks']:
                lx,y,lz=block['pos'];wx,wz=tile['x']+lx,tile['z']+lz;state=states[block['state']]
                if not(ox<=wx<ox+w and oy<=y<oy+h and oz<=wz<oz+d) or state.startswith('myvillage:linlang_counter'):continue
                layer='roof' if '_stairs[' in state and y>=oy+10 or '_slab[' in state and y>=oy+10 else 'building'
                cells[lx,y,lz]=(state,key,layer)
            scene.add_tile(tile['x']//16,tile['z']//16,cells)
        for box in scene.boxes:boxes.append([[box['p'][j]+offset[j] for j in range(3)],box['s'],key+'_'+box['m'],box['layer'],key])
        counter=data['linlang']['position'];counter_model=json.loads((ROOT/'src/main/resources/assets/myvillage/models/block/linlang_counter.json').read_text())
        for element in counter_model['elements']:
            low,high=element['from'],element['to'];p=[counter[j]+(low[j]+high[j])/32+offset[j] for j in range(3)]
            boxes.append([p,[(high[j]-low[j])/16 for j in range(3)],key+'_wood','facility',key])
        boxes.append([[x+w/2,-.3,z+d/2],[w+4,.6,d+4],key+'_stone','ground',key])
        building={**b,'id':key,'name':spec['name']+'·琳琅阁','origin':[x,0,z],'entry':[b['entry'][j]+offset[j] for j in range(3)]};buildings.append(building)
        anchors.append({'id':key,'name':building['name'],'position':[x+w//2,2,z]})
        views.append({'id':key,'name':building['name'],'eye':[x+34,24,z-27],'target':[x+w/2,7,z+d/2]})
        sources.append({'layout':key,'manifest_sha256':row['sha256'],'building':b,'counter':data['linlang']})
    width,depth=180,110
    payload={'name':'六套宗门的琳琅阁','status':'implemented','dimensions':[width,64,depth],'palette':palette,'boxes':boxes,'buildings':buildings,'views':views,'routes':[],'anchors':anchors,'showLabels':True,
             'terrain':base64.b64encode(struct.pack('<'+'h'*(width*depth),*([0]*(width*depth)))).decode(),'surfaces':['ground']*(width*depth),'influence':base64.b64encode(bytes(width*depth)).decode(),
             'checks':{'anchors':6},'design':['模型直接提取自本次正式宗门 NBT，各座建筑保持同一方块尺度。',
             '云阙采用双层重檐，松崖采用低矮木构；听泉两套分别使用纵向双层四坡屋顶与单层园居。',
             '照月两套分别采用收分岛阁与环廊水榭，材料沿用各宗门原有配置。',
             '预览用于检查形体与层次；颜色按方块类别示意，原生贴图、界面操作和玩家观感分别验收。']}
    packed={'data':payload,'three':(ROOT/'tools/web/three.min.js').read_text(),'orbit':(ROOT/'tools/web/OrbitControls.js').read_text(),'viewer':(ROOT/'tools/sect_blueprints/viewer.js').read_text()}
    raw=base64.b64encode(gzip.compress(json.dumps(packed,ensure_ascii=False,separators=(',',':')).encode(),mtime=0)).decode()
    html=(ROOT/'tools/sect_blueprints/template.html').read_text().replace('__PAYLOAD__',raw).replace('__NAME__',payload['name'])
    (out/'index.html').write_text(html);(out/'sources.json').write_text(json.dumps(sources,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'buildings':len(buildings),'boxes':len(boxes),'html':str(out/'index.html')},ensure_ascii=False))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--resources',type=Path,default=ROOT/'build/generated/sect_blueprints/resources');a=p.parse_args();build(a.out,a.resources)
