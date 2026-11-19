"""Remodel the four existing swords in their original palettes and motifs."""
from .geometry import Model, PALETTES, mirrored

# These correspond to the dark stone blade, red fire blade, cloud-green blade,
# and blue-gray ancient sword of the existing items.
OLD_PALETTES = {
    'xuanyue': ('#333237','#24252b','#918879','#595250','#655240','#312d2f',
                '#a68b62','#44312b','#422d27'),
    'chilian': ('#8d251d','#551c1b','#d87837','#683021','#933427','#492922',
                '#c55e29','#5e302a','#331d1c'),
    'qingxiao': ('#b4caca','#7ca69e','#e1e8de','#4e736b','#669587','#325248',
                 '#a3bfb9','#3d6562','#355750'),
    'guiyan': ('#6c9190','#4a696b','#b1c8bf','#47605e','#6d8779','#36494a',
                '#b8a474','#424e44','#394d47'),
}
for name, colors in OLD_PALETTES.items():
    PALETTES[name] = dict(zip(('face','shade','edge','wall','socket','socket_dark','rim','wood','wrap'),
                            zip(colors,('blade','blade','edge','edge','plates','plates','edge','wood','wrap'))))


def blade(model, sections, flat=.52):
    """Continuous bevels adapted to each original silhouette."""
    rows = []
    for y, width, depth in sections:
        rows.append([
            (depth,y,0),(.91*depth,y,flat*width),(.16*depth,y,width),(-.16*depth,y,width),
            (-.91*depth,y,flat*width),(-depth,y,0),(-.91*depth,y,-flat*width),
            (-.16*depth,y,-width),(.16*depth,y,-width),(.91*depth,y,-flat*width),
        ])
    model.loft('刃身',rows,['face','edge','wall','edge','shade','face','edge','wall','edge','shade'],
               longitudinal=False,caps='wall')


def handle(model, guard_y, width):
    model.shaft('剑首',[(0,.48),(.19,.73),(.57,.77),(.8,.58)],'socket',second='rim')
    model.shaft('握柄',[(.55,.54),(1.00,width),(guard_y-.65,width),(guard_y,.60)],'wrap')
    model.shaft('柄箍',[(guard_y-.69,.59),(guard_y-.57,.69),(guard_y-.29,.69),
                      (guard_y-.16,.57)],'socket_dark',second='rim')


def xuanyue():
    model = Model('xuanyue_zhenshan_sword','xuanyue','玄岳镇山剑','',3.4,(4,10))
    handle(model,6.10,.73)
    blade(model,[(6.1,1.55,.55),(7.08,2.45,.66),(8.2,2.78,.73),(23.99,2.67,.64),
                 (26.55,2.5,.60),(27.95,1.9,.46),(28.05,1.56,.39)],flat=.74)
    model.plate('重剑横格',[(-3.35,6.08),(-3.08,5.64),(-1.08,5.35),(1.08,5.35),
              (3.08,5.64),(3.35,6.08),(2.88,6.8),(.8,6.65),(-.8,6.65),(-2.88,6.8)],
              .77,'socket','rim')
    for side in (-1,1):
        model.plate('剑根石阶', [(-1.13,6.2),(1.13,6.2),(.88,7.9),(.58,8.48),(-.58,8.48),(-.88,7.9)],
                    .143,'socket_dark','rim',x=side*.65)
    return model


def chilian():
    model = Model('chilian_lihuo_sword','chilian','赤炼离火剑','',3.1,(4,10))
    handle(model,5.7,.62)
    blade(model,[(5.6,.86,.44),(6.8,1.31,.53),(7.51,1.67,.60),(8.5,1.26,.57),(12.3,1.23,.54),
                 (13.1,1.57,.49),(14.2,1.19,.47),(18.2,1.12,.43),(19.1,1.37,.40),
                 (20.2,1.04,.35),(23.7,.64,.27),(25.65,.027,.033)],flat=.53)
    for sign in (-1,1):
        model.plate('焰瓣护手',mirrored([(.30,5.28),(1.04,5.18),(2.41,5.71),(2.92,6.69),
                    (2.18,6.55),(1.77,7.36),(1.17,6.64),(.68,6.02)],sign),.46,'socket','rim')
    model.plate('剑格中心',[(-.65,5.15),(.65,5.15),(.85,5.84),(.4,6.61),(-.4,6.61),(-.85,5.84)],
                .66,'shade','rim')
    return model


def qingxiao():
    model = Model('qingxiao_liuyun_sword','qingxiao','青霄流云剑','',3.15,(4,10))
    handle(model,5.68,.59)
    blade(model,[(5.62,.62,.41),(6.86,.79,.47),(7.96,1.28,.48),(9.3,1.18,.43),
                 (17.3,.97,.38),(21.6,.82,.33),(24.16,.51,.23),(25.75,.039,.030)],flat=.63)
    for sign in (-1,1):
        model.plate('流云两端',mirrored([(.53,5.38),(1.02,5.06),(1.9,4.82),(2.81,5.27),
                    (2.7,5.75),(2.19,5.96),(1.54,5.60),(.98,5.89),(.55,6.17)],sign),.50,'socket','rim')
    model.ring('云纹扣',5.56,.87,.44,.165,'rim','socket',n=8,x=.51)
    model.ring('云纹扣',5.56,.87,.44,.165,'rim','socket',n=8,x=-.51)
    model.plate('内扣',[(-.62,5.1),(.62,5.1),(.62,6.07),(-.62,6.07)],.58,'socket_dark','socket')
    return model


def guiyan():
    model = Model('guiyan_sword','guiyan','归燕剑','',3.18,(4,10))
    handle(model,5.84,.59)
    blade(model,[(5.7,.76,.43),(6.78,.98,.44),(8.2,1.34,.50),(11.8,1.24,.48),
                 (18.65,1.05,.42),(23.8,.75,.36),(26.5,.42,.25),(28.1,.028,.028)],flat=.58)
    for sign in (-1,1):
        model.plate('燕尾护手',mirrored([(.48,5.27),(1.58,4.92),(2.83,4.48),(2.62,5.17),
                    (2.92,5.72),(1.81,5.62),(.86,6.16),(.48,6.03)],sign),.45,'socket','rim')
    model.plate('藏榫',[(-.72,5.27),(.72,5.27),(.80,5.98),(.45,6.47),(-.45,6.47),(-.80,5.98)],
                .605,'socket_dark','rim')
    return model


def existing_models():
    return {model.key:model for model in (xuanyue(),chilian(),qingxiao(),guiyan())}
