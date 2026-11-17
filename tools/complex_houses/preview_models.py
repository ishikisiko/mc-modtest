"""本轮单格灰瓦以碰撞体轮廓生成简化表面；完整曲线屋顶继续使用原模型。"""
from functools import lru_cache
import json,math
from tools.player_homes.preview import HomeModels,ROOT
from tools.player_estates.preview import parse,rotate


def boundary_rectangles(occupied,size=16):
    """布尔体素外表面按平面合并；结果不含内部共面面片。"""
    result=[]
    for axis in range(3):
        u,v=[i for i in range(3)if i!=axis]
        for sign in(-1,1):
            for plane in range(size+1):
                mask=set()
                for a in range(size):
                    for b in range(size):
                        p=[0,0,0];p[axis]=plane-(1 if sign==1 else 0);p[u]=a;p[v]=b
                        q=p.copy();q[axis]+=sign
                        if tuple(p)in occupied and tuple(q)not in occupied:mask.add((a,b))
                while mask:
                    a,b=min(mask);end=a+1
                    while (end,b)in mask:end+=1
                    top=b+1
                    while all((x,top)in mask for x in range(a,end)):top+=1
                    mask.difference_update((x,z)for x in range(a,end)for z in range(b,top))
                    lo=[0,0,0];hi=[0,0,0];lo[axis]=hi[axis]=plane;lo[u]=a;hi[u]=end;lo[v]=b;hi[v]=top
                    result.append((axis,sign,lo,hi))
    return result


class ComplexModels(HomeModels):
    @lru_cache(None)
    def faces(self,block):
        name,props=parse(block)
        if name!='yuanxian:courtyard_roof'or int(props.get('variant','0'))>=8:return super().faces(block)
        return self.roof_surface(int(props['variant']),props['facing'])

    @lru_cache(None)
    def roof_surface(self,variant,facing):
        catalog=json.loads((ROOT/'src/main/resources/data/yuanxian/courtyard/assemblies.json').read_text())
        family=next(f for f in catalog['families']if f['id']=='courtyard_roof');boxes=family['variants'][variant]['parts'][0]['boxes']
        occupied=set()
        for a,b,c,d,e,f in boxes:
            occupied.update((x,y,z)for x in range(math.floor(a),math.ceil(d))for y in range(math.floor(b),math.ceil(e))for z in range(math.floor(c),math.ceil(f)))
        tile=self.texture('yuanxian:block/courtyard/'+('tile'if variant<4 else'stone'));timber=self.texture('yuanxian:block/courtyard/'+('timber'if variant<4 else'stone'))
        turns=['north','east','south','west'].index(facing);out=[]
        for axis,sign,lo,hi in boundary_rectangles(occupied):
            a,b,c=lo;d,e,f=hi
            key=(axis,sign)
            points={
                (0,-1):[(a,e,c),(a,b,c),(a,b,f),(a,e,f)],(0,1):[(d,e,f),(d,b,f),(d,b,c),(d,e,c)],
                (1,-1):[(a,b,f),(a,b,c),(d,b,c),(d,b,f)],(1,1):[(a,e,c),(a,e,f),(d,e,f),(d,e,c)],
                (2,-1):[(d,e,c),(d,b,c),(a,b,c),(a,e,c)],(2,1):[(a,e,f),(a,b,f),(d,b,f),(d,e,f)]}[key]
            normal=[0,0,0];normal[axis]=sign
            uu,vv=[i for i in range(3)if i!=axis];uv=[(p[uu],p[vv])for p in points]
            if turns:
                points=[rotate(p,'y',-turns*90)for p in points];normal=rotate(normal,'y',-turns*90,(0,0,0))
            # Texture scale remains one 16-pixel tile per block, including sub-block steps.
            out.append((points,normal,uv,timber if axis==1 and sign==-1 else tile,(1,1,1),None,False))
        return out
