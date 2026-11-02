"""室外整数站位的保守无跳跃连接；一格升高必须由上一级的底台阶承接。"""
from province_capitals.blueprint_plan.atomic_blocks import parse

def can_step(model,a,b):
    dx,dy,dz=(b[i]-a[i] for i in range(3))
    if abs(dx)+abs(dz)!=1 or abs(dy)>1:return False
    if not dy:return True
    high=b if dy>0 else a
    direction={(1,0):'east',(-1,0):'west',(0,1):'south',(0,-1):'north'}[(dx,dz) if dy>0 else (-dx,-dz)]
    name,props=parse(model.get((high[0],high[1]-1,high[2])))
    return name.endswith('_stairs') and props.get('half','bottom')=='bottom' and props.get('facing')==direction

def stair_face(centers,index):
    x,y,z=centers[index]
    lower=next((p for p in centers[max(0,index-1):index+2] if p[1]<y),None)
    if lower is None:return None
    dx,dz=x-lower[0],z-lower[2]
    return 'east' if dx>0 else 'west' if dx<0 else 'south' if dz>0 else 'north'
