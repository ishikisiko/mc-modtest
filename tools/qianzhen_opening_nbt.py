"""Bounded typed NBT and dimension provenance for the isolated roof comparison."""
from __future__ import annotations
from dataclasses import dataclass
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import zipfile
import zlib

@dataclass(frozen=True)
class Tag:
    kind: int
    value: object

class Reader:
    def __init__(self, raw):
        if len(raw) > 64 * 1024 * 1024:
            raise ValueError('NBT exceeds bounded size')
        self.stream = io.BytesIO(raw)
    def take(self, n):
        value = self.stream.read(n)
        if len(value) != n:
            raise ValueError('truncated NBT')
        return value
    def number(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]
    def utf(self):
        return self.take(self.number('>H')).decode('utf8')
    def count(self):
        n = self.number('>i')
        if not 0 <= n <= 1_048_576:
            raise ValueError('NBT array/list length out of bounds')
        return n
    def payload(self, kind, depth=0):
        if depth > 64:
            raise ValueError('NBT nesting exceeds bound')
        formats = {1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}
        if kind in formats:
            value = self.number(formats[kind])
        elif kind == 7:
            value = self.take(self.count())
        elif kind == 8:
            value = self.utf()
        elif kind == 9:
            element, n = self.number('>B'), self.count()
            if element == 0 and n:
                raise ValueError('nonempty END list')
            value = [self.payload(element, depth + 1) for _ in range(n)]
        elif kind == 10:
            value = {}
            while True:
                tag = self.number('>B')
                if tag == 0: break
                key = self.utf()
                if key in value: raise ValueError('duplicate NBT key: ' + key)
                value[key] = self.payload(tag, depth + 1)
        elif kind in (11,12):
            value = [self.number('>i' if kind == 11 else '>q') for _ in range(self.count())]
        else:
            raise ValueError('unknown NBT tag type')
        return Tag(kind, value)
    def root(self):
        if self.number('>B') != 10: raise ValueError('NBT root must be compound')
        self.utf()
        result = self.payload(10)
        if self.stream.read(1): raise ValueError('trailing NBT bytes')
        return result

def plain(tag):
    if tag.kind == 10: return {k:plain(v) for k,v in tag.value.items()}
    if tag.kind == 9: return [plain(v) for v in tag.value]
    return tag.value

def decompress(raw, gzip=False):
    decoder = zlib.decompressobj(31 if gzip else 15)
    value = decoder.decompress(raw, 64 * 1024 * 1024 + 1)
    if len(value) > 64 * 1024 * 1024 or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
        raise ValueError('compressed NBT truncated/trailing/oversized')
    return value

def field(tag, name, kind):
    if tag.kind != 10 or name not in tag.value or tag.value[name].kind != kind:
        raise ValueError('NBT missing/wrong tag type: ' + name)
    return tag.value[name].value

def read_level(path):
    return Reader(decompress(path.read_bytes(), gzip=True)).root()

def read_region(path):
    raw = path.read_bytes()
    if len(raw) < 8192 or len(raw) > 128*1024*1024: raise ValueError('region size outside bounds')
    match = re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca', path.name)
    if not match: raise ValueError('invalid region filename')
    rx, rz = map(int, match.groups())
    chunks, occupied = {}, set()
    for index in range(1024):
        location = int.from_bytes(raw[index*4:index*4+4], 'big')
        offset, sectors = location >> 8, location & 255
        if not offset and not sectors: continue
        end = (offset + sectors) * 4096
        if offset < 2 or not sectors or end > ((len(raw)+4095)//4096)*4096:
            raise ValueError('invalid region sector allocation')
        allocation = set(range(offset, offset + sectors))
        if allocation & occupied: raise ValueError('overlapping region sectors')
        occupied |= allocation
        start = offset * 4096
        if start + 5 > len(raw): raise ValueError('truncated region chunk')
        length = int.from_bytes(raw[start:start+4], 'big')
        if length < 1 or length + 4 > sectors * 4096 or start + 4 + length > len(raw):
            raise ValueError('invalid region chunk length')
        mode = raw[start+4]
        compressed = raw[start+5:start+4+length]
        if mode in (1,2): payload = decompress(compressed, gzip=mode == 1)
        elif mode == 3: payload = compressed
        else: raise ValueError('unsupported/external region chunk compression')
        chunk = Reader(payload).root()
        coord = (rx*32+index%32, rz*32+index//32)
        if (field(chunk,'xPos',3),field(chunk,'zPos',3)) != coord:
            raise ValueError('chunk coordinates differ from actual region slot')
        field(chunk,'yPos',3)
        if field(chunk,'Status',8) not in {'minecraft:'+v for v in ('empty','structure_starts','structure_references','biomes','noise','surface','carvers','features','initialize_light','light','spawn','full')}:
            raise ValueError('unknown chunk Status')
        sections = field(chunk,'sections',9)
        ys = [field(s,'Y',1) for s in sections]
        for section in sections:
            for key in ('SkyLight','BlockLight'):
                if key in section.value and len(field(section,key,7)) != 2048:
                    raise ValueError('section light byte array must have 2048 bytes')
            for container, entry_kind in (('block_states',10),('biomes',8)):
                if container not in section.value: continue
                states = Tag(10,field(section,container,10))
                palette = field(states,'palette',9)
                if not palette or any(entry.kind != entry_kind for entry in palette):
                    raise ValueError('section palette tag type/length invalid')
                if 'data' in states.value: field(states,'data',12)
                if container == 'block_states':
                    for entry in palette:
                        field(entry,'Name',8)
                        if 'Properties' in entry.value:
                            properties = field(entry,'Properties',10)
                            if any(value.kind != 8 for value in properties.values()):
                                raise ValueError('block-state Properties tag type invalid')
        if len(ys) != len(set(ys)): raise ValueError('duplicate section Y')
        chunks[coord] = chunk
    return chunks

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def bind_dimension(before_world, after_world, root, runtime_jar):
    """Resolve actual saved type against hashed runtime resource jars; reject overrides."""
    levels = []
    for world in (before_world, after_world):
        data = Tag(10,field(read_level(world/'level.dat'),'Data',10))
        settings = Tag(10,field(data,'WorldGenSettings',10))
        dimensions = Tag(10,field(settings,'dimensions',10))
        overworld = Tag(10,field(dimensions,'minecraft:overworld',10))
        levels.append(field(overworld,'type',8))
    if levels[0] != levels[1] or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+', levels[0]):
        raise ValueError('actual dimension identity changed/unknown')
    namespace, name = levels[0].split(':')
    if '..' in name.split('/'): raise ValueError('unsafe dimension id')
    member = f'data/{namespace}/dimension_type/{name}.json'
    props = (root/'gradle.properties').read_text()
    version = re.search(r'^minecraft_version=([^\s]+)$', props, re.M)
    if not version: raise ValueError('no fixed Minecraft version')
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home()/'.gradle')))
    minecraft = cache/'caches/neoformruntime/artifacts'/f'minecraft_{version[1]}_client.jar'
    if not minecraft.is_file(): raise ValueError('actual fixed Minecraft resource jar missing')
    with zipfile.ZipFile(minecraft) as reference:
        if json.loads(reference.read('version.json'))['id'] != version[1]:
            raise ValueError('Minecraft reference version differs')
        reference_dimension = reference.read(member) if member in reference.namelist() else None
    classpath = root/'build/moddev/acceptanceServerLegacyClasspath.txt'
    if not classpath.is_file(): raise ValueError('actual server runtime classpath missing')
    entries = [Path(line) for line in classpath.read_text().splitlines() if line.strip()]
    if not entries or any(not p.exists() for p in entries):
        raise ValueError('actual runtime classpath incomplete')
    jars = {runtime_jar.resolve()}
    jars.update(p.resolve() for p in entries if p.is_file() and p.suffix == '.jar')
    from tools.qianzhen_opening_run import PAL
    jars.add((root/PAL).resolve())
    guideme = re.search(r'^guideme_version=([^\s]+)$', props, re.M)
    if guideme:
        dependencies = list((cache/'caches/modules-2/files-2.1/org.appliedenergistics/guideme'/guideme[1]).glob('*/guideme-'+guideme[1]+'.jar'))
        if len(dependencies) != 1: raise ValueError('declared GuideME runtime dependency missing/ambiguous')
        jars.add(dependencies[0].resolve())
    jars.update(p.resolve() for p in (root/'run-acceptance/mods').glob('*.jar'))
    sources, inspected = [], []
    def inspect_zip(raw, identity, depth=0):
        if depth > 4 or len(raw) > 128*1024*1024:
            raise ValueError('nested runtime jar exceeds bound')
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            names = z.namelist()
            if len(names) != len(set(names)): raise ValueError('duplicate resource jar member')
            matches = [n for n in names if n == member or n.endswith('/'+member)]
            if matches:
                if matches != [member]: raise ValueError('nested/overlay dimension resource ambiguous')
                sources.append((identity,z.read(member)))
            for name in names:
                if name.endswith('pack.mcmeta') and any(k in json.loads(z.read(name)) for k in ('filter','overlays')):
                    raise ValueError('runtime pack filtering/overlays need explicit resolution')
                if name.endswith('.jar'):
                    value = z.read(name)
                    inspected.append({'path':identity+'!/'+name,'sha256':hashlib.sha256(value).hexdigest()})
                    inspect_zip(value,identity+'!/'+name,depth+1)
    for path in sorted(jars):
        inspect_zip(path.read_bytes(),str(path))
        inspected.append({'path':str(path),'sha256':sha(path)})
    for folder in [p for p in entries if p.is_dir()]:
        if (folder/member).exists():
            raise ValueError('loose runtime dimension resource needs explicit resolution')
    for world in (before_world, after_world):
        packroot = world/'datapacks'
        for p in sorted(packroot.rglob('*')):
            if p.is_symlink(): raise ValueError('datapack symlink invalidates no-override proof')
            if p.is_file() and (str(p.relative_to(packroot)).endswith('/'+member) or str(p.relative_to(packroot)) == member):
                raise ValueError('saved datapack overrides dimension type')
            if p.is_file() and p.suffix == '.zip':
                with zipfile.ZipFile(p) as z:
                    if any(n == member or n.endswith('/'+member) for n in z.namelist()):
                        raise ValueError('saved zipped datapack overrides dimension type')
                    for n in z.namelist():
                        if n.endswith('pack.mcmeta') and any(k in json.loads(z.read(n)) for k in ('filter','overlays')):
                            raise ValueError('datapack filtering/overlays need explicit resolution')
            elif p.is_file() and p.name == 'pack.mcmeta' and any(k in json.loads(p.read_text()) for k in ('filter','overlays')):
                raise ValueError('datapack filtering/overlays need explicit resolution')
    if len(sources) != 1: raise ValueError('dimension source absent/overridden/ambiguous')
    source, raw = sources[0]
    if reference_dimension is not None and reference_dimension != raw:
        raise ValueError('actual runtime dimension differs from fixed Minecraft resource')
    dimension = json.loads(raw)
    minimum, height = dimension['min_y'], dimension['height']
    if type(minimum) is not int or type(height) is not int or minimum % 16 or height <= 0 or height % 16:
        raise ValueError('dimension height bounds invalid')
    return {'dimension':levels[0], 'min_section':minimum//16,'max_section':(minimum+height)//16,
            'source':source,'source_sha256':next(row['sha256'] for row in inspected if row['path']==source),'resource':member,
            'minecraft_reference':{'path':str(minecraft),'sha256':sha(minecraft)},
            'actual_server_classpath':{'path':str(classpath),'sha256':sha(classpath)},
            'resource_sha256':hashlib.sha256(raw).hexdigest(),'min_y':minimum,'height':height,
            'inspected_runtime_jars':inspected,'saved_datapacks_scanned_no_dimension_override':True}

def lighting_only_added(section, binding):
    y = field(section,'Y',1)
    if y not in (binding['min_section']-1,binding['max_section']):
        raise ValueError('new section is not actual outside light boundary')
    keys = set(section.value)
    if not keys <= {'Y','SkyLight','BlockLight'} or not keys & {'SkyLight','BlockLight'}:
        raise ValueError('new section contains nonlighting/unknown data')
    for key in keys-{'Y'}:
        if len(field(section,key,7)) != 2048:
            raise ValueError('lighting byte array must have 2048 bytes')
