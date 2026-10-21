"""Authored architecture for the approved four lotus courts / four gates and lamps."""
def box(a,b,texture,rotation=None):
    result={'from':a,'to':b,'texture':texture}
    if rotation:result['rotation']=rotation
    return result


def parts():
    def part(tier,name,boxes,collision,material,count=4):
        return dict(tier=tier,name=name,boxes=boxes,collision_boxes=collision,
                    recipe={'pattern':['MMM',' S '], 'key':{'M':material,'S':'minecraft:stone_bricks'},'count':count})
    slab=[box([0,0,0],[16,3,16],'dark'),box([1,3,1],[15,5,15],'copper'),box([2,5,2],[14,6,14],'stone')]
    full=[box([0,0,0],[16,3,16],'dark'),box([1,3,1],[15,14,15],'stone'),box([0,14,0],[16,16,16],'copper')]
    return {
      'jade_gathering_step':part('medium','青玉阵阶',slab,[box([0,0,0],[16,6,16],'stone')],'minecraft:quartz'),
      'jade_gathering_wing':part('medium','青玉阵翼',slab+[
          box([3,6,3],[13,9,13],'dark'),box([4,9,4],[12,12,12],'jade'),box([5,12,5],[11,14,11],'copper')],
          [box([0,0,0],[16,6,16],'stone'),box([3,6,3],[13,14,13],'stone')],'minecraft:prismarine_crystals'),
      'golden_gathering_base':part('high','紫金阵座',slab,[box([0,0,0],[16,6,16],'stone')],'minecraft:gold_ingot'),
      'golden_gathering_pillar':part('high','紫金阵柱',[
          box([5,0,5],[11,2,11],'copper'),box([6,2,6],[10,5,10],'copper'),
          box([6,11,6],[10,14,10],'copper'),box([5,14,5],[11,16,11],'copper'),
          box([6,5,6],[10,11,10],'dark'),box([7,6,5.8],[9,10,6],'jade')],
          [box([5,0,5],[11,16,11],'stone')],'minecraft:gold_ingot'),
      'golden_gathering_beam':part('high','紫金阵梁',[
          box([0,0,3],[16,1,13],'dark'),box([0,1,2],[16,3,14],'copper'),box([0,3,3],[16,4,13],'stone'),
          box([1,4,4],[15,5,12],'copper')],[box([0,0,2],[16,5,14],'stone')],'minecraft:gold_ingot'),
      'purple_gathering_plinth':part('high','镇阵灯台基',slab,[box([0,0,0],[16,6,16],'stone')],'minecraft:obsidian'),
      'purple_gathering_lamp':part('high','镇阵灵灯',[
          box([-4,-10,-4],[4,0,4],'dark'),box([-5,-8,-5],[5,-6,5],'copper'),box([-7,0,-7],[7,2,7],'copper'),
          box([-4,2,-4],[4,3,4],'dark'),box([-3,3,-3],[3,7,3],'jade'),box([-2,7,-2],[2,9,2],'copper')],
          [box([-5,-10,-5],[5,0,5],'stone'),box([-7,0,-7],[7,9,7],'stone')],'minecraft:amethyst_shard')
    }


def node(tier):
    base=[box([0,0,0],[16,3,16],'dark'),box([1,3,1],[15,5,15],'copper')]
    if tier=='medium':
        base += [box([2,5,6],[14,8,22],'stone'),box([3,8,9],[13,10,21],'jade')]
        # Four overlapping tilted jade petals around the 3x2 station's centre.
        for x,z,axis,angle in [(4,15,'z',22.5),(12,15,'z',-22.5),(8,11,'x',-22.5),(8,19,'x',22.5)]:
            base.append(box([x-2,10,z-2],[x+2,20,z+2],'jade',{'origin':[x,10,z],'axis':axis,'angle':angle}))
        base.append(box([6,10,13],[10,19,17],'jade',{'origin':[8,16,15],'axis':'y','angle':45}))
    else:
        base += [box([3,5,3],[13,7,13],'stone'),box([7,27,7],[9,32,9],'copper'),
                 box([4,16,4],[12,24,12],'jade',{'origin':[8,20,8],'axis':'z','angle':45})]
    return base,[box([0,0,0],[16,5,16],'stone')]


def construction(tier,radius):
    if tier=='low':return []
    cells={}
    def add(x,y,z,bid,facing='south'):
        key=(x,y,z)
        assert key not in cells,key
        cells[key]={'offset':list(key),'block':bid,'facing':facing}
    for turn in range(4):
        facing=['south','west','north','east'][turn]
        def put(x,y,z,bid):
            for _ in range(turn):x,z=-z,x
            add(x,y,z,bid,facing)
        for x in (-1,0,1):
            for inward in (0,1):
                z=-radius+inward
                if tier=='medium':
                    bid=('medium_grade_gathering_node' if x==0 else 'jade_gathering_wing') if inward==0 else 'jade_gathering_step'
                    put(x,1,z,bid)
                    put(x,2,z,'minecraft:air')
                else:
                    bid='high_grade_gathering_node' if x==0 and inward==0 else 'golden_gathering_pillar' if x else 'golden_gathering_base'
                    put(x,1,z,bid)
                    put(x,2,z,'golden_gathering_pillar' if x else 'minecraft:air')
                    put(x,3,z,'golden_gathering_beam')
    if tier=='high':
        for sx in (-1,1):
            for sz in (-1,1):
                for dx in (0,1):
                    for dz in (0,1):
                        x,z=sx*(radius-dx),sz*(radius-dz)
                        add(x,1,z,'purple_gathering_plinth')
                        add(x,2,z,'purple_gathering_lamp' if dx==1 and dz==1 else 'minecraft:air',
                            {(-1,-1):'south',(1,-1):'west',(1,1):'north',(-1,1):'east'}[(sx,sz)])
    for row in cells.values():
        if ':' not in row['block']:row['block']='myvillage:'+row['block']
    return list(cells.values())
