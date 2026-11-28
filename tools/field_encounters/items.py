"""Closed pixel silhouettes and icons for the field encounter materials and temporary terrain."""
import argparse
import json
from pathlib import Path

from tools.gen_qingfeng_sword_model import encode_png

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/yuanxian'
ART = {
    'tide_crab_calcium': (('...22......','..233......','.2321..22..','.231..233..',
                          '231..231...','2321232....','233332.....','.2332......','..11.......'),
                         ('594d43','ab8065','e5c49a')),
    'tide_salt_sac': (('....121....','....232....','...12221...','..1222321..',
                      '..2333321..','..2332321..','...23321...','....111....'),
                     ('474651','7f7e94','d5cfdb')),
    'hook_jaw_shell': (('....111....','...12221...','..2333321..','..2322321..',
                        '.23323321..','.2322221...','..23221....','...111.....'),
                       ('48352d','856044','bd905e')),
    'packed_sand_stinger': (('......21...','.....2321..','....23321..','...23321...',
                             '..23321....','..2321.....','...2321....','....121....','.....1.....'),
                            ('634538','aa7448','e3b581')),
    'tide_dragon_scale': (('.....11....','....1231...','...123331..','..123321...',
                           '.123321....','.23321.....','..221......','...1.......'),
                          ('235257','43817c','9cc5a8')),
    'tidal_gorge_seal': (('...11111...','..1222221..','..1233321..','..1232321..',
                          '..1233321..','..1222221..','...21312...','..1111111..'),
                         ('354953','5d8291','c3be99')),
    'stele_ape_marrow': (('.....2.....','....232....','...23321...','..233321...',
                          '..232321...','...2321....','....21.....','.....1.....'),
                         ('5f493b','a77b55','dfb989')),
    'old_pass_seal': (('...........','...113111..','..1233321..','..1222321..',
                       '..1233321..','..1222221..','...23121...','..1111111..'),
                      ('594334','8b6a4f','b89667')),
}
EGGS = ('tide_mud_crab', 'hook_jaw_scorpion', 'tide_dragon', 'stele_ape')


def files():
    for item, (rows, palette) in ART.items():
        colors = {'0': (0,0,0,0), '.': (0,0,0,0)}
        colors.update({str(i): (*bytes.fromhex(code),255) for i,code in enumerate(palette,1)})
        pixels = [[colors['.']] * 16 for _ in range(16)]
        for y, row in enumerate(rows, (16 - len(rows)) // 2):
            if len(row) != 11:
                raise ValueError(f'{item} 的图标行宽无效')
            for x, key in enumerate(row,2):
                pixels[y][x] = colors[key]
        yield ASSETS / f'textures/item/{item}.png', encode_png(pixels)
        model={'parent':'minecraft:item/generated','textures':{'layer0':f'yuanxian:item/{item}'}}
        yield ASSETS / f'models/item/{item}.json', (json.dumps(model,indent=2)+'\n').encode()
    for item in EGGS:
        yield ASSETS / f'models/item/{item}_spawn_egg.json', b'{\n  "parent": "minecraft:item/template_spawn_egg"\n}\n'


    for block in ('encounter_tide_sheet', 'encounter_stele'):
        state = {'variants': {'': {'model': f'yuanxian:block/{block}'}}}
        yield ASSETS / f'blockstates/{block}.json', formatted(state)
    stele = {'parent':'minecraft:block/cube_column',
             'textures':{'end':'minecraft:block/sandstone_top','side':'minecraft:block/cut_sandstone'}}
    yield ASSETS / 'models/block/encounter_stele.json', formatted(stele)
    sheet = {'render_type':'minecraft:translucent',
             'textures':{'surface':'yuanxian:block/encounter_tide_sheet','particle':'yuanxian:block/encounter_tide_sheet'},
             'elements':[{'from':[0,15,0], 'to':[16,15.5,16],
                          'faces':{face:{'texture':'#surface'} for face in ('up','down')}}]}
    yield ASSETS / 'models/block/encounter_tide_sheet.json', formatted(sheet)
    colors = [(64,119,115,173),(67,123,119,176),(73,129,123,178),(91,150,138,184)]
    rows = [[colors[(x * 3 + z * 11 + ((x + z) % 11)**2) % 4] for x in range(16)] for z in range(16)]
    yield ASSETS / 'textures/block/encounter_tide_sheet.png', encode_png(rows)


def formatted(value):
    return (json.dumps(value,indent=2)+'\n').encode()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    options=parser.parse_args()
    for path,data in files():
        if options.check:
            if not path.is_file() or path.read_bytes()!=data:
                raise SystemExit(f'需要重新生成 {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)


if __name__=='__main__':
    main()
