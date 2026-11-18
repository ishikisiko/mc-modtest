"""Closed stonework, hollow hearth and the working face of the forging anvil."""
from tools.equipment import mesh
from tools.forging.mesh_profiles import Surface, average, quads, solid


def slab(s,name,rows,hint):
    return solid(s,name,'y',[(y,x,z,a,b,c) for y,x,z,a,b,c in rows],hint)


def block(s,name,bounds,hint):
    low,high=bounds
    xy=[(low[0],low[1]),(high[0],low[1]),(high[0],high[1]),(low[0],high[1])]
    depths=(low[2],high[2])
    a,b=[[(x,y,z) for x,y in xy] for z in depths]
    centre=average(a+b)
    return [s.face(name,p,hint,centre) for p in [a,b]+[[a[i],a[(i+1)%4],b[(i+1)%4],b[i]] for i in range(4)]]


def hollow_hearth(s):
    outside=[(.57,1.38),(15.43,1.38),(15.43,8.52),(14.67,10.22),(13.53,11.38),(2.47,11.38),(1.33,10.22),(.57,8.52)]
    mouth=[(4.07,2.92),(11.93,2.92),(11.93,6.58),(10.72,8.05),(9.67,8.68),(6.33,8.68),(5.28,8.05),(4.07,6.58)]
    front=[(x,y,1.06) for x,y in outside]
    tail=[(x,y,14.87) for x,y in outside]
    ahead=[(x,y,1.06) for x,y in mouth]
    inside=[(8+(x-8)*.88,2.77+(y-2.77)*.88,12.17) for x,y in mouth]
    result=[]
    for i in range(8):
        j=(i+1)%8
        centre=average(front+tail)
        result.append(s.face('连体炉壁',[front[i],front[j],tail[j],tail[i]],'左炉柱',centre))
        result.append(s.face('炉口边',[front[i],front[j],ahead[j],ahead[i]],'炉口横梁',centre))
        result.append(s.face('炉膛内壁',[ahead[i],ahead[j],inside[j],inside[i]],'炉膛内壁',average(ahead+inside),remit=True))
    result.append(s.face('炉膛后壁',inside,'炉膛内壁',average(ahead+inside),remit=True))
    result.append(s.face('炉背',tail,'左炉柱',average(front+tail)))
    return result


def throat_rim(s):
    outer=[(3.68,2.52),(12.32,2.52),(12.32,6.68),(11.1,8.34),(9.91,9.07),(6.09,9.07),(4.9,8.34),(3.68,6.68)]
    inner=[(4.15,2.94),(11.85,2.94),(11.85,6.55),(10.67,8.02),(9.62,8.64),(6.38,8.64),(5.33,8.02),(4.15,6.55)]
    result=[]
    for i in range(8):
        j=(i+1)%8
        a,b=outer[i],outer[j]
        c,d=inner[j],inner[i]
        face=[(*a,.81),(*b,.81),(*c,.69),(*d,.69)]
        result.append(s.face('炉口护铁',face,'钳柄甲',(8,6,8)))
        result.append(s.face('护铁内折',[(*d,.69),(*c,.69),(*c,1.11),(*d,1.11)],'钳首'))
    return result


def top_plate(s):
    outer=[
      (4.28,4.82),(12.91,4.16),(14.83,5.28),(14.83,11.65),
      (13.74,12.83),(5.87,12.83),(4.26,11.67),(4.05,6.08),
    ]
    # Outer and inner ring subdivisions start on the same ray from the hole.
    core=(13.55,8.78)
    def matching_hole(point):
        x,z=point
        dx,dz=x-core[0],z-core[1]
        t=.65/max(abs(dx),abs(dz))
        return (core[0]+dx*t,core[1]+dz*t)
    hole=[matching_hole(p) for p in outer]
    foot=[(8+(x-8)*.95, 8+(z-8)*.93) for x,z in outer]
    rings=[[ (x,y,z) for x,z in path ] for path,y in ((foot,14.68),(outer,15.56),(outer,16.0))]
    bore=[[(x,y,z) for x,z in hole] for y in (14.68,16)]
    centre=(8,15.3,8)
    result=[]
    for i in range(8):
        j=(i+1)%8
        for n in (0,1):
            a,b=rings[n],rings[n+1]
            result.append(s.face('砧身外沿',[a[i],a[j],b[j],b[i]],'砧面亮边' if n else '砧面',centre))
        a,b=rings[-1],bore[-1]
        result.append(s.face('平砧工作面',[a[i],a[j],b[j],b[i]],'砧面',(8,14.9,8)))
        a,b=rings[0],bore[0]
        result.append(s.face('砧脚承面',[a[i],a[j],b[j],b[i]],'砧面',centre))
        a,b=bore
        result.append(s.face('通方孔',[a[i],a[j],b[j],b[i]],'钳首',(core[0],15.33,core[1]),remit=True))
    return result


def new_station(before,hot):
    s=Surface(before)
    f=slab(s,'削角整石底座',
        [(0,8,8,7.66,7.40,.68),(.36,8,8,7.94,7.86,.79),
         (1.17,8,8,7.72,7.66,.83),(1.58,8,8,7.45,7.36,.76)],'足座')
    f+=hollow_hearth(s)+throat_rim(s)
    f+=block(s,'炉床',((4.15,2.64,1.26),(11.85,3.00,11.63)),'炉床')
    for x,z,w,h,d in [(5.1,3.68,1.04,.47,1.78),(6.8,4.75,.89,.67,1.15),
         (9.0,3.7,.94,.48,1.28),(10.7,6.08,.82,.63,1.58),(4.94,7.45,.66,.44,.93),
         (7.25,7.6,.98,.85,1.17),(8.39,8.36,.83,.62,1.62),(10.28,9.79,.85,.50,1.04),
         (5.55,10.11,.78,.43,1.28)]:
        f+=slab(s,'棱角焦炭',
            [(2.99,x,z,w,d*.59,.22),(3+h*.56,x+.07,z+.04,w*.95,d*.62,.32),
             (3+h,x+.12,z+.13,w*.51,d*.42,.18)],'炭块')
    for x in (6.38,7.49,8.60,9.69):
        f+=block(s,'灰槽通风缝',((x-.31,1.92,.93),(x+.31,2.17,1.13)),'炉膛内壁')
    f+=slab(s,'带压边的台围',
        [(10.34,8,8,7.42,7.31,.87),(10.64,8,8,7.66,7.52,.98),
         (11.76,8,8,7.57,7.45,.91),(12.04,8,8,7.19,7.06,.77)],'深木台沿')
    for x in (1.53,14.47):
        f+=slab(s,'沉头台箍',
            [(10.68,x,8,.30,7.49,.15),(10.87,x,8,.42,7.55,.18),
             (11.43,x,8,.42,7.55,.18),(11.59,x,8,.31,7.46,.16)],'铜箍')
    f+=slab(s,'砧脚和收腰',
       [(11.9,8.5,8.3,4.83,4.07,.61),(12.31,8.5,8.3,4.92,4.13,.61),
        (12.66,8.4,8.3,4.09,3.47,.56),(13.12,8.2,8.3,2.81,2.64,.45),
        (13.94,8.15,8.3,2.77,2.46,.49),(14.71,8.39,8.3,4.23,3.66,.68),
        (15.18,8.42,8.3,4.44,3.57,.71)],['砧座','砧座','砧腰','砧腰','砧腰','砧面'])
    f+=top_plate(s)
    f+=solid(s,'连体砧角','x',[
        (.26,14.61,8.32,.19,.43,.14),(1.18,14.64,8.32,.33,1.04,.26),
        (2.61,14.68,8.31,.55,2.02,.31),(3.82,14.79,8.28,.73,2.76,.39),
        (5.03,14.92,8.22,.89,3.30,.48)],['砧面亮边','砧角','砧角','砧面'])
    f+=block(s,'嵌入器身的挂钳座',((12.9,7.95,.65),(15.01,8.53,1.14)),'工具横架')
    for i,(x,y,z) in enumerate([(13.39,3.50,.55),(14.18,3.67,.50)]):
        for delta in (-.21,.21):
            f+=slab(s,'锻钳柄',
                [(y,x+delta,z,.135,.129,.067),(y+.9,x+delta,z,.157,.131,.073),
                 (7.58+i*.19,x+delta*.55,z,.148,.13,.061),(7.84+.17*i,x+delta*.55,z,.124,.11,.063)],
                '钳柄甲')
        f+=block(s,'钳轴',((x-.27,7.61+i*.17,z-.19),(x+.29,8.10+i*.17,z+.19)),'钳轴')
        f+=slab(s,'收拢的钳口',[(8.02+i*.17,x,z,.20,.17,.08),(8.53+i*.17,x-.03,z,.14,.146,.06)],'钳首')
    bounds=mesh.bounds(f)
    assert all(0<=bounds[0][i]<=bounds[1][i]<=16 for i in range(3)),bounds
    assert any(all(v[1]==16 for v in face['points']) for face in f if face['part']=='平砧工作面')
    assert max(p[1] for face in f for p in face['points'])==16.
    assert any(face['texture'].endswith('/workshop_fire') for face in f)==hot
    return list(quads(f))
