#!/usr/bin/env python3
"""独立检查五境十五套正式资源；不生成资源，不启动游戏。"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import struct
import subprocess
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
SLOTS = {'head': 'helmet', 'chest': 'chestplate', 'legs': 'leggings', 'feet': 'boots'}
BONES = {'head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'}
SLOT_BONES = {'head': {'head'}, 'chest': {'body', 'right_arm', 'left_arm'},
              'legs': {'body', 'right_leg', 'left_leg'}, 'feet': {'right_leg', 'left_leg'}}
BASELINE = 'fe1a7e69'
APPROVED_ART = 'c376d8e4'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def finite(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def vector(value, length, label):
    require(isinstance(value, list) and len(value) == length and all(finite(n) for n in value), label + '：需要有限数值向量')


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def validate_png(image, material):
    require(image[:8] == b'\x89PNG\r\n\x1a\n', 'PNG头错误')
    cursor, chunks, header = 8, [], None
    while cursor < len(image):
        size = struct.unpack('>I', image[cursor:cursor + 4])[0]
        kind, data = image[cursor + 4:cursor + 8], image[cursor + 8:cursor + 8 + size]
        crc = struct.unpack('>I', image[cursor + 8 + size:cursor + 12 + size])[0]
        require(zlib.crc32(kind + data) & 0xffffffff == crc, 'PNG CRC错误')
        if kind == b'IHDR':
            header = struct.unpack('>IIBBBBB', data)
        elif kind == b'IDAT':
            chunks.append(data)
        cursor += 12 + size
    rows, palette = material['pixelArt']['rows'], material['pixelArt']['palette']
    require(header == (len(rows[0]), len(rows), 8, 6, 0, 0, 0), '像素材质尺寸/格式改变')
    width, height = header[:2]
    raw = zlib.decompress(b''.join(chunks));stride = width * 4
    require(len(raw) == height * (stride + 1), 'PNG行数据长度错误')
    previous = bytearray(stride)
    for y in range(height):
        offset = y * (stride + 1);mode = raw[offset];line = bytearray(raw[offset + 1:offset + 1 + stride])
        require(mode in range(5), 'PNG过滤类型错误')
        for i in range(stride):
            left, up, upper_left = line[i - 4] if i >= 4 else 0, previous[i], previous[i - 4] if i >= 4 else 0
            if mode == 1: prediction = left
            elif mode == 2: prediction = up
            elif mode == 3: prediction = (left + up) // 2
            elif mode == 4:
                p = left + up - upper_left;distances = [abs(p - n) for n in (left, up, upper_left)]
                prediction = (left, up, upper_left)[distances.index(min(distances))]
            else: prediction = 0
            line[i] = (line[i] + prediction) % 256
        expected = b''.join(bytes.fromhex(palette[int(c)][1:]) + b'\xff' for c in rows[y])
        require(line == expected, '像素材质颜色/行序改变或重复乘alpha')
        previous = line


def resource(root, identity, kind='assets'):
    require(isinstance(identity, str) and re.fullmatch(r'myvillage:[a-z0-9_/.-]+', identity) is not None
            and not identity.split(':')[1].startswith('/') and '..' not in identity.split(':')[1].split('/'), '非法资源引用：' + str(identity))
    namespace, name = identity.split(':')
    return root / 'src/main/resources' / kind / namespace / name


def source_designs(root):
    result = {}
    for level in range(1, 6):
        rows = read_json(root / f'art/immortal-armor-atlas/levels/level{level}.json')
        require(len(rows) == 3, f'第{level}境必须三套')
        for row in rows:
            require(row['level'] == level and row['id'] not in result, '作者身份重复或境界不一致')
            result[row['id']] = row
    require(len(result) == 15, '作者数据必须十五套')
    return result


def authored_effects(root):
    script = "process.stdout.write(JSON.stringify(require(process.argv[1])))"
    return json.loads(subprocess.check_output(['node', '-e', script, str(root / 'art/immortal-armor-atlas/effects/catalogue.js')], text=True))


def geometry_summary(vertices, indices):
    points = [vertices[i][:3] for i in indices]
    bounds = [[min(p[k] for p in points), max(p[k] for p in points)] for k in range(3)]
    area, moment = 0.0, [0.0] * 3
    for i in range(0, len(points), 3):
        a, b, c = points[i:i + 3]
        ab, ac = [b[k] - a[k] for k in range(3)], [c[k] - a[k] for k in range(3)]
        cross = [ab[1]*ac[2]-ab[2]*ac[1], ab[2]*ac[0]-ab[0]*ac[2], ab[0]*ac[1]-ab[1]*ac[0]]
        tri_area = math.sqrt(sum(v*v for v in cross)) / 2
        require(tri_area > 1e-12, '退化三角面')
        area += tri_area
        for k in range(3):
            moment[k] += tri_area * (a[k] + b[k] + c[k]) / 3
    return {'bounds': bounds, 'area': area, 'centroid': [v / area for v in moment]}


def validate_mesh(data, design, effects=None):
    label = design['id']
    require(data.get('version') == 1 and data.get('set_id') == label, label + '：网格身份/版本错误')
    materials = data.get('materials', {})
    require(set(materials) == set(design['materials']), label + '：材质集合与作者不一致')
    for name, material in materials.items():
        require(finite(material.get('opacity')) and 0 < material['opacity'] <= 1, label + '：非法透明度')
        require(material['opacity'] == design['materials'][name].get('opacity', 1), label + '：作者透明度丢失')
    components = {c['id']: c for c in design['components']}
    require(set(data.get('profiles', {})) == {'standard', 'slim'}, label + '：缺少宽/细臂身形')
    by_profile = {}
    summaries = {}
    for profile, model in data['profiles'].items():
        origins = model.get('bone_origins', {})
        require(set(origins) == BONES, label + '：骨骼原点不完整')
        for origin in origins.values():
            vector(origin, 3, label + '骨骼原点')
        parts = model.get('parts', [])
        ids = [p.get('id') for p in parts]
        require(len(ids) == len(set(ids)) and set(ids) == set(components), label + '：组件丢失/重复或混入预览人台')
        summaries[profile], by_profile[profile] = {}, {p['id']: p for p in parts}
        for part in parts:
            name = label + '/' + profile + '/' + part['id']
            slot = part.get('slot')
            require(slot in SLOTS and part.get('bone') in SLOT_BONES[slot], name + '：槽位/骨骼错误')
            role = components[part['id']]['role']
            require(slot == ('feet' if role == 'boots' else role), name + '：原组件槽位改变')
            require(part.get('material') == components[part['id']]['material'], name + '：原材质改变')
            author_material = design['materials'][part['material']]
            expected_side = 'front' if author_material.get('opacity', 1) < 1 and author_material.get('pixelArt', {}).get('mapping') == 'fit' else 'double'
            require(part.get('side') == expected_side, name + '：透明背面/双面类型与作者不一致')
            vertices, indices = part.get('vertices', []), part.get('indices', [])
            require(vertices and indices and len(indices) % 3 == 0, name + '：缺少完整三角面')
            for v in vertices:
                vector(v, 8, name)
                require(all(0 <= n <= 1 for n in v[3:5]), name + '：UV越界')
                require(abs(sum(n*n for n in v[5:8]) - 1) < 1e-4, name + '：法线未单位化')
            require(all(type(i) is int and 0 <= i < len(vertices) for i in indices), name + '：索引越界')
            summaries[profile][part['id']] = geometry_summary(vertices, indices)
        require(set(p['slot'] for p in parts) == set(SLOTS), label + '：四槽不完整')
    a, b = by_profile['standard'], by_profile['slim']
    for key in a:
        require(all(a[key][field] == b[key][field] for field in ('bone', 'slot', 'material')), label + '：身形组件归属不一致')
        if not a[key]['bone'].endswith('_arm'):
            require(a[key]['vertices'] == b[key]['vertices'] and a[key]['indices'] == b[key]['indices'], label + '：细臂改变了非手臂组件')
    fx = data.get('effects', {})
    if effects is not None:
        require(fx == effects, label + '：作者效果配置丢失或被改写')
    bindings = data.get('effect_bindings', {})
    for field in ('flows', 'arcs'):
        require(len(bindings.get(field, [])) == len(fx.get(field, [])), label + '：效果绑定数量不一致')
        for effect, bone in zip(fx.get(field, []), bindings[field]):
            slot = 'feet' if effect.get('role') == 'boots' else effect.get('role')
            require(bone in SLOT_BONES.get(slot, set()), label + '：效果骨骼非法')
    for field in ('motions', 'glows'):
        for effect in fx.get(field, []):
            targets = effect.get('targets', [])
            require(targets and all(t in a for t in targets), label + '：未知效果组件')
            if field == 'motions':
                require(len({a[t]['bone'] for t in targets}) == 1, label + '：动态组跨骨骼')
    return summaries


REFERENCE_JS = r"""
const fs=require('fs'),path=require('path'),root=process.argv[1];
globalThis.THREE=require(path.join(root,'tools/web/three.min.js'));
const T=globalThis.THREE,engine=require(path.join(root,'art/immortal-armor-atlas/core/engine.js'));
const output={};
for(let level=1;level<=5;level++)for(const spec of JSON.parse(fs.readFileSync(path.join(root,'art/immortal-armor-atlas/levels/level'+level+'.json')))){
 const group=engine.buildDesign(spec,{avatar:false});group.updateMatrixWorld(true);const rows={};
 group.traverse(node=>{if(!node.isMesh)return;const id=node.userData.componentId,g=node.geometry,p=g.attributes.position,uv=g.attributes.uv,n=g.attributes.normal,ix=g.index,nm=new T.Matrix3().getNormalMatrix(node.matrixWorld);
  const vertices=[];for(let i=0;i<p.count;i++){const v=new T.Vector3().fromBufferAttribute(p,i).applyMatrix4(node.matrixWorld),normal=new T.Vector3().fromBufferAttribute(n,i).applyMatrix3(nm).normalize();vertices.push([16*v.x,24-16*v.y,-16*v.z,uv.getX(i),uv.getY(i),normal.x,-normal.y,-normal.z]);}
  const indices=ix?Array.from(ix.array):Array.from({length:p.count},(_,i)=>i);rows[id]={vertices,indices};
 });output[spec.id]=rows;engine.dispose(group);
}
process.stdout.write(JSON.stringify(output));
"""


def validate_uv_source(points, indices, source, tile):
    """逐个导出三角面的重心回查原三角面；允许为周期UV切面，禁止换图或翻转。"""
    def sub(a, b): return [a[k] - b[k] for k in range(3)]
    def dot(a, b): return sum(x * y for x, y in zip(a, b))
    source_triangles = [[source['vertices'][i] for i in source['indices'][offset:offset + 3]]
                        for offset in range(0, len(source['indices']), 3)]
    for offset in range(0, len(indices), 3):
        tri = [points[i] for i in indices[offset:offset + 3]]
        center = [sum(v[k] for v in tri) / 3 for k in range(5)]
        matched = False
        for a, b, c in source_triangles:
            ab, ac, ap = sub(b, a), sub(c, a), sub(center, a)
            aa, cc, bc = dot(ab, ab), dot(ac, ac), dot(ab, ac)
            determinant = aa * cc - bc * bc
            if determinant <= 1e-16: continue
            u = (cc * dot(ap, ab) - bc * dot(ap, ac)) / determinant
            v = (aa * dot(ap, ac) - bc * dot(ap, ab)) / determinant
            if min(u, v, 1 - u - v) < -1e-5: continue
            if sum((a[k] + u * ab[k] + v * ac[k] - center[k]) ** 2 for k in range(3)) > 1e-8: continue
            expected = [a[k] + u * (b[k] - a[k]) + v * (c[k] - a[k]) for k in (3, 4)]
            expected[1] = 1 - expected[1]
            differences = [abs(center[k + 3] - expected[k]) for k in range(2)]
            if all(abs(delta - round(delta)) < 2e-5 if tile else delta < 2e-5 for delta in differences):
                if len(a) == 8 and len(tri[0]) == 8:
                    authored = [a[k] + u * (b[k] - a[k]) + v * (c[k] - a[k]) for k in range(5, 8)]
                    generated = [sum(row[k] for row in tri) / 3 for k in range(5, 8)]
                    scale = math.sqrt(dot(authored, authored))
                    if not all(abs(x / scale - y) < 1e-4 for x, y in zip(authored, generated)): continue
                matched = True;break
        require(matched, '原三角面UV不符或跨周期三角面未正确切分')


def validate_provenance(root, designs, meshes):
    reference = json.loads(subprocess.check_output(['node', '-e', REFERENCE_JS, str(root)], text=True))
    for identity in designs:
        model = meshes[identity]['profiles']['standard']
        parts = {p['id']: p for p in model['parts']}
        require(set(reference[identity]) == set(parts), identity + '：原引擎组件集合不符')
        for key, original in reference[identity].items():
            part = parts[key]
            origin = model['bone_origins'][part['bone']]
            points = [[v[k] + origin[k] for k in range(3)] for v in part['vertices']]
            expected = geometry_summary(original['vertices'], original['indices'])
            actual = geometry_summary(points, part['indices'])
            for field in ('bounds', 'centroid', 'area'):
                def flatten(value):
                    if isinstance(value, list):
                        return [n for row in value for n in flatten(row)]
                    return [value]
                require(all(math.isclose(a, b, rel_tol=2e-5, abs_tol=2e-4) for a, b in zip(flatten(expected[field]), flatten(actual[field]))),
                        identity + '/' + key + '：原模型几何不符 ' + field)
            uv_points = [point + part['vertices'][i][3:] for i, point in enumerate(points)]
            tile = designs[identity]['materials'][part['material']]['pixelArt'].get('mapping', 'tile') != 'fit'
            validate_uv_source(uv_points, part['indices'], original, tile)


def validate_bindings(root, identity, mesh):
    rig = read_json(root / 'equipment/immortal/rig.json')
    authored = rig['sets'][identity]
    require(mesh['effect_bindings'] == authored['effect_bindings'], identity + '：效果绑定不来自rig')
    base_origins = read_json(root / 'equipment/poses.json')['bones']
    for profile, model in mesh['profiles'].items():
        expected = dict(base_origins)
        expected.update(rig['profiles'][profile].get('bone_origin_overrides', {}))
        require(model['bone_origins'] == expected, identity + '：骨骼原点不来自配置')
        require({p['id']: p['bone'] for p in model['parts']} == authored['components'], identity + '：组件骨骼不来自rig')
    normal = {p['id']: p for p in mesh['profiles']['standard']['parts']}
    slim = {p['id']: p for p in mesh['profiles']['slim']['parts']}
    fit = rig['profiles']['slim']['arm_fit']
    for key, source in normal.items():
        if not source['bone'].endswith('_arm'): continue
        target = slim[key];bone = source['bone']
        require(source['indices'] == target['indices'] and len(source['vertices']) == len(target['vertices']), identity + '：细臂拓扑改变')
        origin_a = mesh['profiles']['standard']['bone_origins'][bone]
        origin_b = mesh['profiles']['slim']['bone_origins'][bone]
        anchor = fit['inner_edge'] * (1 if bone == 'left_arm' else -1)
        for a, b in zip(source['vertices'], target['vertices']):
            expected = [anchor + (a[0] + origin_a[0] - anchor) * fit['width_scale'] - origin_b[0],
                        a[1] + origin_a[1] + fit['y_offset'] - origin_b[1], a[2] + origin_a[2] - origin_b[2]]
            require(all(math.isclose(x, y, abs_tol=1e-6) for x, y in zip(expected, b[:3])) and a[3:5] == b[3:5], identity + '：细臂未保持内侧边缘/UV')


def validate_approved_art(root):
    names = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', APPROVED_ART, '--',
                                     'art/immortal-armor-atlas/authors', 'art/immortal-armor-atlas/levels',
                                     'art/immortal-armor-atlas/effects/catalogue.js', 'art/immortal-armor-atlas/core/engine.js'], cwd=root, text=True).splitlines()
    require(names, '已确认美术基线为空')
    for name in names:
        expected = subprocess.check_output(['git', 'show', APPROVED_ART + ':' + name], cwd=root)
        require((root / name).read_bytes() == expected, '已确认美术发生变化：' + name)
    return len(names)


def legacy_paths(root):
    # 原玄铁甲、器坯、共用装备图集和三件武器资源；不把新lang键加入逐字节锁。
    names = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASELINE, '--', 'equipment', 'src/main/resources'], cwd=root, text=True).splitlines()
    return [name for name in names if ('xuantie' in name or '/equipment/' in name or any(w in name for w in ('qingfeng_sword', 'lingxiao_spear'))) and name.endswith(('.json', '.png', '.obj', '.mtl'))]


def validate_legacy(root):
    names = legacy_paths(root)
    require(names, '旧资源基线为空')
    for name in names:
        expected = subprocess.check_output(['git', 'show', BASELINE + ':' + name], cwd=root)
        require((root / name).is_file() and (root / name).read_bytes() == expected, '旧玄铁/武器资源变化：' + name)
    return len(names)


def validate_item(root, identity, components):
    assets = root / 'src/main/resources/assets/myvillage'
    model = read_json(assets / ('models/item/' + identity + '.json'))
    if model.get('loader') == 'neoforge:separate_transforms': model = model['base']
    require(model.get('loader') == 'neoforge:obj', identity + '：物品不是原三维OBJ')
    lines = resource(root, model['model']).read_text().splitlines()
    positions, uvs, normals, faces, objects, used, libraries = [], [], [], [], set(), set(), []
    for line in lines:
        words = line.split()
        if not words: continue
        tag, values = words[0], words[1:]
        if tag in ('v', 'vt', 'vn'):
            row = [float(n) for n in values]
            vector(row, 2 if tag == 'vt' else 3, identity + '/' + tag)
            {'v': positions, 'vt': uvs, 'vn': normals}[tag].append(row)
        elif tag == 'f': faces.append(values)
        elif tag == 'o': objects.add(values[0])
        elif tag == 'usemtl': used.add(values[0])
        elif tag == 'mtllib': libraries.append(values[0])
    require(objects == {c['id'] for c in components}, identity + '：物品缺原组件/混入人台')
    require(faces and len(libraries) == 1, identity + '：OBJ缺面/材质库')
    for face in faces:
        require(len(face) == 3, identity + '：OBJ不是三角面')
        for corner in face:
            ix = corner.split('/')
            require(len(ix) == 3 and all(n.isdigit() for n in ix), identity + '：OBJ缺UV/法线索引')
            require(all(1 <= int(n) <= len(rows) for n, rows in zip(ix, (positions, uvs, normals))), identity + '：OBJ索引越界')
    require(all(all(0 <= n <= 1 for n in uv) for uv in uvs), identity + '：OBJ UV越界')
    require(all(abs(sum(n*n for n in normal) - 1) < 1e-4 for normal in normals), identity + '：OBJ法线未单位化')
    declared, maps = set(), []
    for line in resource(root, libraries[0]).read_text().splitlines():
        words = line.split()
        if not words: continue
        if words[0] == 'newmtl': declared.add(words[1])
        elif words[0] == 'map_Kd': maps.append(words[1])
    require(used <= declared and len(maps) == len(declared), identity + '：MTL材质/纹理缺失')
    for texture in maps:
        namespace, name = texture.split(':')
        require(resource(root, namespace + ':textures/' + name + '.png').is_file(), identity + '：MTL纹理缺失')


def validate(root=ROOT, provenance=True, legacy=True):
    designs = source_designs(root)
    manifest_path = root / 'src/main/resources/assets/myvillage/immortal_equipment/provenance.json'
    manifest = read_json(manifest_path)
    require(manifest.get('set_count') == 15 and manifest.get('item_count') == 75 and manifest.get('inputs'), '导出来源清单缺身份')
    for name, expected in manifest['inputs'].items():
        require(not Path(name).is_absolute() and '..' not in Path(name).parts, '导出来源清单路径越界')
        require(hashlib.sha256((root / name).read_bytes()).hexdigest() == expected, '导出来源漂移：' + name)
    catalogue = read_json(root / 'src/main/resources/data/myvillage/immortal_equipment/catalogue.json')
    require(catalogue.get('version') == 1, '目录版本错误')
    require(catalogue.get('tiers') == read_json(root / 'equipment/immortal/balance.json')['tiers'], '数值不是balance输入原值')
    sets = catalogue.get('sets', [])
    require(len(sets) == 15 and {s['id'] for s in sets} == set(designs), '目录十五套身份错误')
    ids, meshes = [], {}
    effects = authored_effects(root)
    lang = read_json(root / 'src/main/resources/assets/myvillage/lang/zh_cn.json')
    for row in sets:
        identity, spec = row['id'], designs[row['id']]
        require(row['level'] == spec['level'] and row['name'] == spec['name'], identity + '：套装名/境界改变')
        armor = row.get('armor', [])
        require(len(armor) == 4 and {a['slot'] for a in armor} == set(SLOTS), identity + '：四件目录不完整')
        for item in [*armor, row['ingot']]:
            expected = identity + '_' + (SLOTS[item['slot']] if 'slot' in item else 'ingot')
            require(item['id'] == 'myvillage:' + expected, '物品ID不符：' + item['id'])
            ids.append(item['id'])
            require(item.get('name') and lang.get('item.myvillage.' + expected) == item['name'], expected + '：中文名未接入')
            require(re.search('[\u4e00-\u9fff]', item['name']), expected + '：名称不是中文')
            components = spec['ingot']['components'] if 'slot' not in item else [c for c in spec['components'] if c['role'] == ('boots' if item['slot'] == 'feet' else item['slot'])]
            validate_item(root, expected, components)
        model = read_json(resource(root, row['model']))
        validate_mesh(model, spec, effects[identity]);meshes[identity] = model
        validate_bindings(root, identity, model)
        require(resource(root, row['texture']).is_file(), identity + '：兼容贴图缺失')
        for key, mat in model['materials'].items():
            image = resource(root, mat['texture']).read_bytes()
            validate_png(image, spec['materials'][key])
            metadata = read_json(Path(str(resource(root, mat['texture'])) + '.mcmeta'))
            require(metadata.get('texture', {}).get('blur') is False, identity + '：纹理没有Nearest采样')
    require(len(ids) == 75 and len(set(ids)) == 75, '必须75个唯一ID')
    if provenance:
        validate_provenance(root, designs, meshes)
    old = validate_legacy(root) if legacy else 0
    approved = validate_approved_art(root)
    old_lang = json.loads(subprocess.check_output(['git', 'show', BASELINE + ':src/main/resources/assets/myvillage/lang/zh_cn.json'], cwd=root, text=True))
    require(all(lang.get(k) == v for k, v in old_lang.items()), '原中文翻译被删除或改写')
    return {'status': 'passed', 'sets': 15, 'armor': 60, 'ingots': 15, 'profiles': 30,
            'legacy_resources': old, 'approved_art_files': approved, 'geometry_provenance': 'checked' if provenance else 'not_verified',
            'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
            'export_provenance_sha256': hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
            'runtime': 'not_verified', 'owner_appearance': 'not_verified'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    try:
        result = validate(args.root.resolve())
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        result = {'status': 'failed', 'error_type': type(error).__name__, 'error': str(error), 'runtime': 'not_verified'}
    output = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True);args.report.write_text(output, encoding='utf-8')
    print(output, end='')
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    sys.exit(main())
