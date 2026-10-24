"""Real binary NBT/Anvil regressions for tightly bound light-only normalization."""
import copy
import gzip
import json
import os
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import zlib
from tools.qianzhen_opening_nbt import Tag, Reader, bind_dimension, lighting_only_added
from tools.qianzhen_opening_repair import block_changes, require_only_patch_changes


def utf(s):
    b=s.encode();return struct.pack('>H',len(b))+b

def payload(t):
    k,v=t.kind,t.value
    if k in (1,2,3,4):return struct.pack({1:'>b',2:'>h',3:'>i',4:'>q'}[k],v)
    if k==7:return struct.pack('>i',len(v))+v
    if k==8:return utf(v)
    if k==9:return bytes([v[0].kind if v else 10])+struct.pack('>i',len(v))+b''.join(payload(i) for i in v)
    if k==10:return b''.join(bytes([i.kind])+utf(n)+payload(i) for n,i in v.items())+b'\0'
    if k in (11,12):return struct.pack('>i',len(v))+b''.join(struct.pack('>i' if k==11 else '>q',x) for x in v)
    raise AssertionError(k)

def nbt(t):return b'\x0a'+utf('')+payload(t)

def light(y,**extra):return Tag(10,{'Y':Tag(1,y),'SkyLight':Tag(7,bytes(2048)),**extra})

def air(y,name='minecraft:air'):
    return Tag(10,{'Y':Tag(1,y),'block_states':Tag(10,{'palette':Tag(9,[Tag(10,{'Name':Tag(8,name)})])})})

def region(path,chunk,tail=b''):
    data=zlib.compress(nbt(chunk)+tail);body=struct.pack('>i',len(data)+1)+b'\x02'+data
    count=(len(body)+4095)//4096;header=bytearray(8192);header[:4]=((2<<8)|count).to_bytes(4,'big')
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(header+body+bytes(count*4096-len(body)))

class TypedSectionTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.base=Path(self.tmp.name);self.root=self.base/'repo';self.root.mkdir()
        self.before,self.after=self.base/'before',self.base/'after'
        self.jar=self.root/'runtime.jar';self.cache=self.base/'cache'
        self.addCleanup(patch.stopall);patch.dict(os.environ,{'GRADLE_USER_HOME':str(self.cache)}).start()
        (self.root/'gradle.properties').write_text('minecraft_version=1.21.1\n')
        from tools.qianzhen_opening_run import PAL
        for p in (self.jar,self.root/PAL):
            with zipfile.ZipFile(p,'w') as z:z.writestr('marker','known fixture runtime input')
        self.configure(-64,384)

    def configure(self,minimum,height):
        self.minimum,self.height=minimum,height
        mc=self.cache/'caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar';mc.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(mc,'w') as z:
            z.writestr('version.json',json.dumps({'id':'1.21.1'}))
            z.writestr('data/minecraft/dimension_type/overworld.json',json.dumps({'min_y':minimum,'height':height}))
        cp=self.root/'build/moddev/acceptanceServerLegacyClasspath.txt';cp.parent.mkdir(parents=True,exist_ok=True);cp.write_text(str(mc)+'\n')
        self.original=Tag(10,{'xPos':Tag(3,0),'zPos':Tag(3,0),'yPos':Tag(3,minimum//16),
                              'Status':Tag(8,'minecraft:full'),'sections':Tag(9,[air(minimum//16)])})
        self.current=copy.deepcopy(self.original)
        for world in (self.before,self.after):
            world.mkdir(exist_ok=True)
            level=Tag(10,{'Data':Tag(10,{'WorldGenSettings':Tag(10,{'dimensions':Tag(10,{
                'minecraft:overworld':Tag(10,{'type':Tag(8,'minecraft:overworld')})})})})})
            (world/'level.dat').write_bytes(gzip.compress(nbt(level)))
        self.write()

    def write(self):
        region(self.before/'region/r.0.0.mca',self.original)
        region(self.after/'region/r.0.0.mca',self.current)
    def compare(self):return block_changes(self.before,self.after,root=self.root,jar=self.jar)

    def test_real_dimension_sources_bind_distinct_lower_and_upper_light_boundaries(self):
        for minimum,height in ((-64,384),(0,256),(-32,96)):
            with self.subTest(minimum=minimum,height=height):
                self.configure(minimum,height)
                self.current.value['sections'].value.extend([light(minimum//16-1),light((minimum+height)//16)])
                self.write();self.assertEqual([],self.compare())
                binding=bind_dimension(self.before,self.after,self.root,self.jar)
                self.assertEqual(minimum,binding['min_y']);self.assertEqual(height,binding['height'])
                self.assertTrue(binding['resource_sha256']);self.assertTrue(binding['saved_datapacks_scanned_no_dimension_override'])

    def test_removed_old_air_or_nonair_section_always_refuses(self):
        for state in ('minecraft:air','minecraft:stone'):
            self.original.value['sections']=Tag(9,[air(-4,state)]);self.current.value['sections']=Tag(9,[]);self.write()
            with self.subTest(state=state),self.assertRaisesRegex(ValueError,'section消失'):self.compare()

    def test_new_build_section_nonboundary_and_unknown_payload_refuse(self):
        bad=[light(-3),light(-6),light(21),light(-5,biomes=Tag(10,{})),
             light(-5,block_states=air(-5,'minecraft:stone').value['block_states']),light(-5,unknown=Tag(3,1)),
             Tag(10,{'Y':Tag(1,-5)}),light(-5,SkyLight=Tag(7,bytes(2047))),light(-5,SkyLight=Tag(7,bytes(2049))),
             light(-5,SkyLight=Tag(11,[0]*2048)),light(-5,Y=Tag(3,-5))]
        for section in bad:
            self.current=copy.deepcopy(self.original);self.current.value['sections'].value.append(section);self.write()
            with self.subTest(keys=section.value.keys()),self.assertRaises(ValueError):self.compare()

    def test_shared_nonair_change_still_requires_exact_authored_target_set(self):
        self.current.value['sections']=Tag(9,[air(-4,'minecraft:stone')]);self.write()
        changes=self.compare();self.assertEqual(4096,len(changes))
        with self.assertRaises(AssertionError):require_only_patch_changes(changes,[])

    def test_duplicate_section_wrong_ypos_coordinates_status_and_missing_chunk_refuse(self):
        for kind in ('duplicate','yPos','xPos','Status','missing'):
            self.current=copy.deepcopy(self.original)
            if kind=='duplicate':self.current.value['sections'].value.append(air(-4))
            elif kind in ('yPos','xPos'):self.current.value[kind]=Tag(3,17)
            elif kind=='Status':self.current.value[kind]=Tag(8,'minecraft:light')
            self.write()
            if kind=='missing':(self.after/'region/r.0.0.mca').write_bytes(bytes(8192))
            with self.subTest(kind=kind),self.assertRaises(ValueError):self.compare()

    def test_nbt_duplicate_truncated_trailing_and_compressed_truncation_refuse(self):
        duplicate=b'\x0a\0\0'+b'\x03'+utf('x')+struct.pack('>i',1)+b'\x03'+utf('x')+struct.pack('>i',2)+b'\0'
        for raw in (duplicate,nbt(self.original)[:-1],nbt(self.original)+b'garbage'):
            with self.subTest(length=len(raw)),self.assertRaises(ValueError):Reader(raw).root()
        region(self.after/'region/r.0.0.mca',self.current,tail=b'garbage')
        with self.assertRaises(ValueError):self.compare()
        self.write();p=self.after/'level.dat';p.write_bytes(p.read_bytes()[:-1])
        with self.assertRaises(ValueError):self.compare()

    def test_datapack_and_runtime_dimension_overrides_fail_closed(self):
        pack=self.after/'datapacks/injected/data/minecraft/dimension_type';pack.mkdir(parents=True)
        (pack/'overworld.json').write_text('{}')
        with self.assertRaisesRegex(ValueError,'overrides'):self.compare()
        (pack/'overworld.json').unlink()
        with zipfile.ZipFile(self.jar,'a') as z:z.writestr('data/minecraft/dimension_type/overworld.json','{}')
        with self.assertRaisesRegex(ValueError,'ambiguous'):self.compare()

    def test_shared_section_nbt_types_are_checked_even_if_unchanged(self):
        invalid = [Tag(10,{'palette':Tag(9,[Tag(8,'minecraft:air')])}),
                   Tag(10,{'palette':Tag(9,[Tag(10,{'Name':Tag(3,1)})])}),
                   Tag(10,{'palette':Tag(9,[Tag(10,{'Name':Tag(8,'minecraft:air')})]),'data':Tag(11,[0])})]
        for state in invalid:
            self.original.value['sections'].value[0].value['block_states'] = state
            self.current=copy.deepcopy(self.original);self.write()
            with self.subTest(state=state),self.assertRaises(ValueError):self.compare()

    def test_bare_callers_bounds_cannot_replace_actual_dimension_binding(self):
        with self.assertRaisesRegex(ValueError,'来源绑定'):block_changes(self.before,self.after)
