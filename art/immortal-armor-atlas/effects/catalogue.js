'use strict';
// Authored model-pixel coordinates. Effects belong to the preview, not OBJ
// geometry, gameplay attributes or combat hitboxes. Every design has one owner.
(function(root){
const flow=(role,path,count,size,color,period,opacity=.55,shape='spark')=>({role,path,count,size,color,period,opacity,shape});
const glow=(targets,color,period,max=.25)=>({targets,color,period,min:.015,max});
const sway=(targets,pivot,axis,angle,period,lift=[0,0,0])=>({targets,pivot,axis,angle,period,lift});
const data={
 qi_moqing:{level:1,title:'墨行微息',description:'衣缘仅有少量青白微光，缓慢上行；保留炼气的朴素体量。',
  flows:[flow('chest',[[-4.5,14,2.8],[-4.7,18,2.8],[-4.1,22,2.8]],3,.18,'#b6d4cc',7,.28)],motions:[],glows:[],arcs:[]},
 qi_tingyu:{level:1,title:'檐下听雨',description:'袖外几滴方雨缓落，亮度在落下时消退，雨不覆盖方脸。',
  flows:[flow('chest',[[-8.8,22,2.9],[-9.2,18,3],[-9.6,14,3]],3,.21,'#b5d8d5',5,.34),flow('chest',[[8.3,22,-2.9],[8.7,18,-2.9],[9,14,-2.9]],2,.18,'#b5d8d5',6,.28)],motions:[],glows:[],arcs:[]},
 qi_chixia:{level:1,title:'赤霞余温',description:'少量赤金余烬沿后披上升，冠心缓缓明暗；没有持续喷火。',
  flows:[flow('chest',[[-4.2,9,-3.8],[-5.4,15,-4],[-4.5,22,-3.8]],4,.22,'#dfad79',6,.38)],motions:[],glows:[glow(['crown_heart'],'#d89662',6,.13)],arcs:[]},
 zhuji_baiqing_lingyun:{level:2,title:'凌云绕袖',description:'浅青云粒从衣侧升向肩背，两边速度错开；灵气仍贴近衣料。',
  flows:[flow('chest',[[-8.5,15,2.8],[-9.7,20,1],[-8.3,24,-2.5]],4,.25,'#b6dfe1',7,.42),flow('chest',[[8.5,16,-2.8],[9.5,20,-2],[7.8,24,1]],3,.2,'#d3e5d5',8,.35)],motions:[],glows:[glow(['cloud_brow_jade'],'#9ed7df',6,.16)],arcs:[]},
 zhuji_yuheng_yizhuang:{level:2,title:'玉衡凝印',description:'玉印温和明灭，细金印光沿背纹上下相接，保持礼仪款的稳定感。',
  flows:[flow('chest',[[0,15,-3.2],[0,19,-3.2],[0,22,-3.2]],4,.22,'#d5c38e',7,.4,'seal')],motions:[],glows:[glow(['yuheng_brow_jade'],'#c6dbc4',7,.18),glow(['orn_back'],'#b8a97e',8,.11)],arcs:[]},
 zhuji_wuyao_yeque:{level:2,title:'夜阙潜辉',description:'幽紫微粒沿披衣侧边游走，明暗缓慢起伏，保持暗色轮廓。',
  flows:[flow('chest',[[-5,10,-3.4],[-6,17,-3.5],[-4.4,24,-3.5]],4,.23,'#afa6ca',8,.34),flow('chest',[[4.4,24,2.8],[5.4,19,3.0],[4.9,14,3]],2,.19,'#a4bccc',9,.27)],motions:[],glows:[glow(['night_brow_jade'],'#b3a8d2',8,.15)],arcs:[]},
 jindan_jinwu:{level:3,title:'金乌扬羽',description:'金色羽火从肩后向上散开，胸前日心先亮、羽火后起，留出衣甲本色。',
  flows:[flow('chest',[[-5,21,-3.6],[-8,25,-3.8],[-9.4,29,-3.4]],4,.3,'#e5b879',5,.52,'leaf'),flow('chest',[[5,21,-3.6],[8,25,-3.8],[9.4,29,-3.4]],4,.3,'#e5c38b',5.8,.46,'leaf')],motions:[],glows:[glow(['sun_seal'],'#dba863',5,.22)],arcs:[]},
 jindan_taiyin:{level:3,title:'太阴霜露',description:'月白霜露沿宽袖和背领缓慢回落，冠上的缺月温和发亮，动势比金乌更静。',
  flows:[flow('chest',[[-9.2,24,2.8],[-10,20,3],[-9.5,15,3.2]],4,.26,'#c4dbe9',8,.45,'star'),flow('chest',[[8.8,25,-3.4],[9.5,19,-3.4],[8,14,-3.3]],4,.22,'#c4dbe9',9,.4,'star')],motions:[],glows:[glow(['square_crescent_crown'],'#abc9df',9,.18)],arcs:[]},
 jindan_zixiao:{level:3,title:'雷府蓄敕',description:'右臂导雷槽先蓄亮，再释放短促折雷；披衣只作微幅呼吸，雷光不过脸、不笼罩全身。',
  flows:[flow('chest',[[6,15.9,3.62],[5.8,17.5,3.66],[7,18.2,3.66],[6.7,20.4,3.62]],3,.24,'#b9d2f4',4.8,.55)],
  motions:[sway(['zx_cape_long','zx_cape_fold'],[-2,23.5,-3.85],'x',1.1,7)],
  glows:[glow(['zx_gauntlet_ridge'],'#9cbde8',4.8,.32),glow(['zx_clasp_core'],'#b2c3e1',4.8,.18)],
  arcs:[{role:'chest',points:[[5.4,20.1,3.7],[4.8,19.4,4],[5.7,18.7,4.1],[5.4,17.6,4],[6.1,16.7,3.7]],color:'#b9d4fa',period:4.8,duration:.65,phase:.28,opacity:.78},
        {role:'chest',points:[[7,20.1,3.8],[8.2,19.4,4],[7.6,18.3,4],[8.6,17.2,3.9],[7.2,16.2,3.7]],color:'#8aabe4',period:4.8,duration:.6,phase:.36,opacity:.62}]},
 yuanying_xinghe:{level:4,title:'星图巡天',description:'大小星点沿披衣上的星图流转，胸前仅有少量星辉；宽披衣低幅摆动，主星与辅星错时明灭。',
  flows:[flow('chest',[[-4.2,19,-4.25],[-1,21.5,-4.25],[2.7,18,-4.25],[0,14.5,-4.25],[-3,10,-4.25],[2,7,-4.25]],7,.32,'#d3e4dc',11,.66,'star'),flow('chest',[[-2.2,21,2.9],[1.2,18.5,2.9],[2.5,15.5,2.9]],3,.18,'#aec9eb',9,.42,'star')],
  motions:[sway(['star_continuous_cape','star_cape_fold_-1','star_cape_fold_1'],[0,23.4,-3.9],'x',.9,10)],glows:[glow(['star_crown_seed'],'#bbd4e6',9,.23)],arcs:[]},
 yuanying_jiuse:{level:4,title:'九色分光',description:'琉瀑晶格中青、紫、浅金三路光错时流动，冠上晶髓微浮；透光底色保持稳定，不整身轮换彩虹。',
  flows:[flow('chest',[[-3,22,-5.65],[0,17,-5.65],[-2,11,-5.65],[0,6.5,-5.65]],4,.27,'#9ed8da',8,.55,'seal'),flow('chest',[[3.2,22,-5.65],[1,18,-5.65],[3,12,-5.65],[0,8,-5.65]],4,.24,'#c6b2de',9,.5,'seal'),flow('chest',[[-2,22,3.1],[0,19,3.2],[2,16,3.1]],3,.2,'#e1d9b8',10,.4)],
  motions:[sway(['glass_crown_inclusion_0','glass_crown_inclusion_1','glass_crown_inclusion_2'],[0,37,-2.8],'y',3,9,[0,.35,0])],glows:[glow(['glass_cut_waterfall'],'#9ec5d1',10,.09)],arcs:[]},
 yuanying_qingdi:{level:4,title:'青帝生息',description:'青露沿背干分向两支，在枝梢化为叶光；肩叶轻摆，胸前叶脉缓缓发亮。',
  flows:[flow('chest',[[0,14,-5.3],[0,22,-5.3],[-4,24,-5.3],[-7.7,28,-5.3],[-10,30.2,-5.3]],5,.32,'#b6dca2',9,.6,'leaf'),flow('chest',[[0,14,-5.3],[0,22,-5.3],[4,24,-5.3],[7.7,28,-5.3],[10,30.2,-5.3]],5,.32,'#b6dca2',10,.55,'leaf')],
  motions:[sway(['wood_shoulder_leaf_-1'],[-4.6,24,0],'z',2.2,8),sway(['wood_shoulder_leaf_1'],[4.6,24,0],'z',-2.2,9)],glows:[glow(['orn_leaf'],'#b7d6a0',9,.13)],arcs:[]},
 huashen_taixu:{level:5,title:'无相游虚',description:'缺口紫髓缓慢浮沉，宽云衣轻摆；银青虚光沿玉璧外缘向缺口汇合，始终保留真实空处。',
  flows:[flow('chest',[[-7.8,14,-7.3],[-8,21,-7.3],[-3,25.4,-7.3],[2.6,25.4,-7.3]],5,.23,'#c9ded4',12,.42,'seal'),flow('chest',[[1.8,10,-7.3],[7.8,14,-7.3],[7.8,20,-7.3],[6.4,23,-7.3]],5,.25,'#b9b5da',13,.43,'star')],
  motions:[sway(['void_gap_gem'],[6.3,24,-6.5],'y',7,10,[0,.48,0]),sway(['void_cloud_fall','void_cloud_turn','void_rear_seal','void_rear_border'],[-1.4,22.2,-7.5],'x',1.2,12)],
  glows:[glow(['void_gap_gem'],'#b8b0d2',10,.25),glow(['void_jade_etch_-7.55_20.5','void_jade_etch_7.5_17','void_jade_etch_1.8_9.5'],'#bbd6c7',12,.15)],arcs:[]},
 huashen_xuanji:{level:5,title:'玄极垂宪',description:'悬圭有极轻的起伏，金色敕印沿两侧法器下行；胸前主印周期显现，整体动势端正而缓慢。',
  flows:[flow('chest',[[-9.2,24,-4.6],[-9.2,21,-4.6],[-9.2,17,-4.6]],5,.32,'#d7c28f',11,.6,'seal'),flow('chest',[[9.2,24,-4.6],[9.2,21,-4.6],[9.2,17,-4.6]],5,.32,'#d7c28f',12,.56,'seal'),flow('chest',[[0,22,3.7],[0,19,3.7],[0,16,3.7]],3,.2,'#e0cda4',10,.45,'seal')],
  motions:[sway(['suspended_tablet_-1','tablet_gold_axis_-1'],[-9,24.7,-4.1],'x',1,12,[0,.25,0]),sway(['suspended_tablet_1','tablet_gold_axis_1'],[9,24.7,-4.1],'x',-1,13,[0,.25,0])],glows:[glow(['orn_chest_seal'],'#cbbb8c',11,.22)],arcs:[]},
 huashen_dari:{level:5,title:'归墟金莲',description:'背莲两侧升起金焰，冠心与胸中日核错时蓄亮；外侧短光芒缓缓伸展，避免持续强闪和满屏火焰。',
  flows:[flow('chest',[[-5,18,-5.1],[-8,23,-5.3],[-11,28,-5.0],[-10,33,-4.8]],6,.34,'#e6bf80',7,.67,'leaf'),flow('chest',[[5,18,-5.1],[8,23,-5.3],[11,28,-5],[10,33,-4.8]],6,.34,'#efd297',7.8,.62,'leaf'),flow('head',[[-2,34,-.5],[0,38,-1],[2.5,40,-1.2]],3,.25,'#e6cfa6',9,.45,'star')],
  motions:[],glows:[glow(['sun_crown_core'],'#e3bd76',8,.32),glow(['sun_chest_core'],'#ebc789',9,.35)],
  arcs:[{role:'chest',points:[[-9.5,25,-5.4],[-11.5,27,-5.4],[-11.5,29,-5.4]],color:'#dab372',period:8,duration:3.2,phase:.1,opacity:.32},
        {role:'chest',points:[[9.5,25,-5.4],[11.5,27,-5.4],[11.5,29,-5.4]],color:'#dab372',period:8,duration:3.2,phase:.1,opacity:.32}]}
};
// Short, model-bound tails distinguish the high-realm flow directions. Low
// realms remain isolated motes; no common full-body aura is added.
data.jindan_zixiao.flows[0].trail=.10;
data.yuanying_xinghe.flows[0].trail=.065;
data.yuanying_xinghe.flows[0].path=data.yuanying_xinghe.flows[0].path.map(([x,y])=>[x,y,-4.6]);
data.yuanying_xinghe.flows[0].size=.40;
data.yuanying_jiuse.flows[0].trail=.075;data.yuanying_jiuse.flows[1].trail=.075;
data.yuanying_qingdi.flows.forEach(f=>{f.trail=.05;f.size=.4;});
data.huashen_taixu.flows.forEach(f=>{f.trail=.055;f.size=.31;});
data.huashen_xuanji.flows.forEach(f=>f.size=.42);
data.huashen_dari.flows.slice(0,2).forEach(f=>{f.trail=.085;f.size=.46;});
root.ArmorEffectCatalogue=data;if(typeof module!=='undefined'&&module.exports)module.exports=data;
})(globalThis);
