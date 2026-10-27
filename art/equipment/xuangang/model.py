"""玄罡完整模型草案。唯一 authored 形状/材质来源；不进入装备注册目录。"""
MATERIALS = {
 'xg_steel': {'pattern':'metal','base':'#465760','light':'#849397','dark':'#29353d','noise_scale':.10,'rivets':False},
 'xg_edge': {'pattern':'metal','base':'#839399','light':'#bac4c1','dark':'#53666f','noise_scale':.04,'rivets':False},
 'xg_dark': {'pattern':'metal','base':'#303d46','light':'#647580','dark':'#1d2931','noise_scale':.08,'rivets':False},
 'xg_cloth': {'pattern':'cloth','base':'#263b46','light':'#465a65','dark':'#1d2e38','noise_scale':.12},
 'xg_linen': {'pattern':'cloth','base':'#b2b4aa','light':'#c7c8b9','dark':'#8d9693','noise_scale':.12},
 'xg_leather': {'pattern':'leather','base':'#34332f','light':'#5f6057','dark':'#222621','noise_scale':.08,'stitches':False},
 'xg_wood': {'pattern':'leather','base':'#645044','light':'#92785e','dark':'#392f2b','noise_scale':.1,'stitches':False},
}

def box(name,bone,xyz,size,material='xg_steel',**kw):
 return dict(name=name,bone=bone,**{'from':xyz,'size':size,'material':material},**kw)

# Joined solid shells, not ornamental stacks or segmented plates.
HEAD=[
 box('连体盔冠','head',[-3.65,-8.85,-4.55],[7.3,.45,9.1]),
 box('左冠倒角','head',[-4.65,-8.4,-4.55],[1.15,.42,9.1],rotation=[0,0,-22.5],pivot=[-4.65,-8.4,0]),
 box('右冠倒角','head',[3.55,-8.83,-4.55],[1.15,.42,9.1],rotation=[0,0,22.5],pivot=[3.55,-8.83,0]),
 box('额面','head',[-4.55,-8.35,-4.6],[9.1,1.1,.38]),
 box('后颅壳','head',[-4.55,-8.4,4.12],[9.1,5.3,.43]),
 box('左盔壳','head',[-4.55,-8.0,-3.65],[.43,4.4,7.8]),
 box('右盔壳','head',[4.12,-8.0,-3.65],[.43,4.4,7.8]),
 box('左护颊','head',[-4.55,-7.3,-4.55],[.72,2.8,.40]),
 box('右护颊','head',[3.83,-7.3,-4.55],[.72,2.8,.40]),
 box('枕下收口','head',[-4.1,-3.1,4.14],[8.2,1.2,.38], 'xg_dark'),
]
CHEST=[
 box('整幅内衣','body',[-4.3,-.25,-2.3],[8.6,12.55,4.6],'xg_cloth'),
 box('左襟','body',[-3.05,.0,-3.30],[.65,9.7,.16],'xg_linen',rotation=[0,0,-29],pivot=[-2.8,0,-3.30]),
 box('右襟','body',[2.5,.0,-3.28],[.60,5.6,.16],'xg_linen',rotation=[0,0,29],pivot=[2.75,0,-3.28]),
 box('一体胸腹护面','body',[-4.1,2.5,-3.23],[8.2,7.55,.62],rotation=[6,0,0],pivot=[0,2.5,-3.23]),
 box('连体背护面','body',[-4.28,1.5,2.25],[8.56,8.55,.62]),
 box('左侧连续壳','body',[-4.4,2.5,-2.7],[.43,7.55,5.0],'xg_dark'),
 box('右侧连续壳','body',[3.97,2.5,-2.7],[.43,7.55,5.0],'xg_dark'),
 box('窄腰封','body',[-4.42,10.05,-2.55],[8.84,1.8,5.1],'xg_leather'),
 box('腰扣座','body',[-.65,10.3,-2.72],[1.3,1.1,.24],'xg_dark'),
 box('素钢腰扣','body',[-.42,10.5,-2.99],[.84,.66,.2],'xg_edge'),
]
for bone,x in [('right_arm',-3.35),('left_arm',-1.35)]:
 CHEST.extend([
  box(bone+'整袖',bone,[x,-2.25,-2.3],[4.7,11.0,4.6],'xg_cloth',fit='arm'),
  box(bone+'低肩连壳',bone,[x-.1,-2.45,-2.4],[4.9,1.5,4.8],'xg_steel',fit='arm'),
  box(bone+'整面护臂',bone,[x-.08,4.6,-2.43],[4.86,4.4,4.86],'xg_dark',fit='arm'),
 ])
LEGS=[box('腰下衣身','body',[-4.25,11.3,-2.25],[8.5,1.2,4.5],'xg_cloth')]
FEET=[]
for bone in ['right_leg','left_leg']:
 LEGS.extend([
  box(bone+'整裤',bone,[-2.25,.0,-2.25],[4.5,11.5,4.5],'xg_cloth'),
  box(bone+'连续护面',bone,[-2.28,.3,-2.58],[4.56,6.3,.40],'xg_steel'),
 ])
 FEET.extend([
  box(bone+'整靴筒',bone,[-2.34,7.2,-2.34],[4.68,5.0,4.68],'xg_dark'),
  box(bone+'连体靴头',bone,[-2.34,10.25,-3.15],[4.68,1.95,.85],'xg_steel'),
  box(bone+'薄靴底',bone,[-2.42,12.15,-3.2],[4.84,.32,5.64],'xg_leather'),
 ])
ARMOR={'version':1,'kind':'armor_set','enabled':False,'id':'myvillage:xuangang_armor','name':'玄罡甲 · 完整模型草案',
 'material':'minecraft:iron','durability_multiplier':15,'texture_size':512,'texel_density':4,
 'items':[dict(id='myvillage:xuangang_'+id,name=name,slot=slot,boxes=boxes) for id,name,slot,boxes in [
 ('helmet','玄罡盔','head',HEAD),('chestplate','玄罡胸甲','chest',CHEST),('leggings','玄罡护腿','legs',LEGS),('boots','玄罡靴','feet',FEET)]]}
# Continuous polygonal steel heads. Units are Minecraft model pixels, Y up.
TOOLS={
 'pickaxe':{'name':'玄罡镐','polygon':[[-9,17.5],[-7.7,20.6],[-4,22.4],[0,23],[4,22.4],[7.7,20.6],[9,17.5],[6.2,19.4],[3,20.7],[0,21],[-3,20.7],[-6.2,19.4]],'thickness':1.8,'motif':True},
 'axe':{'name':'玄罡斧','polygon':[[-2.7,18],[-2.7,21.5],[0,22.0],[4.7,23.2],[7.4,23.0],[8.2,20],[7.9,16.2],[5.7,14.8],[2.3,16.4],[.8,18.2]],'thickness':1.55,'motif':True},
 'hoe':{'name':'玄罡锄','socket':[0,20,0],'bridge_end':[0,19,5.2],'blade_top':[0,19,5.2],'blade_bottom':[0,14.5,6.0],'blade_width':6.7,'thickness':.65,'motif':True},
}
# No new tier bonuses, recipes or registration are included in this design study.
