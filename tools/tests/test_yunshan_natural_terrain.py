"""Small genuine Anvil/NBT fixtures exercise terrain sampling, never a game."""
import copy
import base64
import gzip
import math
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

from tools.yunshan.natural_terrain import (decode_section,evaluate_site,expanded_window,rank_sites,
    read_region,read_settings,rotate_column,sample_chunk,sample_window,section_state,unpack_padded)


def packed(values,bits):
    count=64//bits
    result=[0]*math.ceil(len(values)/count)
    for i,value in enumerate(values):result[i//count]|=value<<((i%count)*bits)
    return [v-(1<<64) if v>>63 else v for v in result]


def utf(value):
    value=value.encode();return struct.pack('>H',len(value))+value


def payload(value):
    if isinstance(value,dict):
        return 10,b''.join(bytes([kind])+utf(key)+data for key,v in value.items() for kind,data in [payload(v)])+b'\0'
    if isinstance(value,str):return 8,utf(value)
    if isinstance(value,int):return 3,struct.pack('>i',value)
    if isinstance(value,tuple) and value[0]=='longs':
        return 12,struct.pack('>i',len(value[1]))+b''.join(struct.pack('>q',v) for v in value[1])
    if isinstance(value,list):
        items=[payload(v) for v in value]
        kind=items[0][0] if items else 10
        return 9,bytes([kind])+struct.pack('>i',len(items))+b''.join(data for _,data in items)
    raise AssertionError(value)


def nbt_bytes(value):
    kind,data=payload(value);return bytes([kind])+utf('')+data


def terrain_chunk(cx=0,cz=0):
    palette=[{'Name':'minecraft:'+name} for name in ('air','grass_block','oak_log','oak_leaves','water','chest')]
    cells=[1 if y<=3 else 0 for y in range(16) for z in range(16) for x in range(16)]
    for y in range(4,12):cells[(y*16)*16]=2
    cells[(12*16)*16]=3
    for y in range(4,8):cells[(y*16)*16+1]=4
    cells[(4*16)*16+2]=5
    heights=[4+64]*256;heights[0]=13+64;heights[1]=8+64;heights[2]=5+64
    return {'xPos':cx,'zPos':cz,'Status':'minecraft:full',
        'sections':[{'Y':0,'block_states':{'palette':palette,'data':packed(cells,4)}}],
        'Heightmaps':{'WORLD_SURFACE':packed(heights,9)}}


def saved_chunk(chunk):
    chunk=copy.deepcopy(chunk)
    for section in chunk['sections']:
        if 'data' in section['block_states']:section['block_states']['data']=('longs',section['block_states']['data'])
    for key,data in chunk.get('Heightmaps',{}).items():chunk['Heightmaps'][key]=('longs',data)
    return chunk


class NaturalTerrainTest(unittest.TestCase):
    def test_actual_snapshot_terminal_chunk_needs_payload_not_sector_padding(self):
        # Unchanged zlib payload from retained Modal snapshot coarse-west,
        # r.-2.-1.mca index927: its 354-byte chunk length ends exactly at EOF.
        encoded='eJzF1ktOwzAQBuC/NLR59AVsEEdhCwuQACFeEqvKTaet1bxkTxBwL87DRQrBIYWWCwze2R7PN979IeCjc8uKS4ujVGcUGzXjY8umjLk0NLasDNs2vNfr3FZVtQowmCR5vBxTxpo1WdTLFbysCzyEF8ryfTFVTGhu30KEvz2t29zQjAxlsXseotMggIf+ebZQE800vdPp+vV39+em+7t7e0Z6vuBUFRYBfEsx6zxzTYHDEL1mONeRyQboFiohZqpvWz68K+W69jcfVdqgnmCi83S73q/rMdoUFonSmXVnrccVRJgPGeZThqlkGMgwLRlmR4ZpyzCeDLMrw3RkmK4M48swgQwTyjCRDNOTYfoyzECGGcowIxlmT4bZl2EO6tz2Jz4GiBqWdbz8ORq4yMfXJnch0epsHtQBD/+02ohOFasHMtaFTWDo8mbvRJknN9ilsss6ikazpNTTrS/gC+bo890='
        compressed=base64.b64decode(encoded)
        body=struct.pack('>I',len(compressed)+1)+bytes([2])+compressed
        self.assertEqual(len(body),358)
        header=bytearray(8192);header[927*4:927*4+4]=((2<<8)|1).to_bytes(4,'big')
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary)/'r.-2.-1.mca'
            path.write_bytes(header+body)
            chunks,_=read_region(path)
            self.assertEqual((chunks[31,28]['xPos'],chunks[31,28]['zPos']),(-33,-4))
            self.assertEqual(chunks[31,28]['DataVersion'],3955)
            path.write_bytes(header+body[:-1])
            with self.assertRaisesRegex(ValueError,'truncated Anvil chunk payload'):read_region(path)
            oversized=bytearray(header);oversized[927*4:927*4+4]=((2<<8)|2).to_bytes(4,'big')
            path.write_bytes(oversized+body)
            with self.assertRaisesRegex(ValueError,'invalid Anvil sector allocation'):read_region(path)
            overlapping=bytearray(header);overlapping[0:4]=((2<<8)|1).to_bytes(4,'big')
            path.write_bytes(overlapping+body)
            with self.assertRaisesRegex(ValueError,'overlapping Anvil sector allocation'):read_region(path)

    def test_padding_signed_longs_and_palette_boundaries(self):
        values=[i%17 for i in range(4096)]
        data=packed(values,5)
        self.assertEqual([unpack_padded(data,i,5) for i in range(4096)],values)
        self.assertEqual(unpack_padded([-1],15,4),15)
        decoded=decode_section({'block_states':{'palette':[{'Name':f'minecraft:test_{i}'} for i in range(17)],'data':data}})
        self.assertEqual(section_state(decoded,12,0,0),'minecraft:test_12')
        with self.assertRaisesRegex(ValueError,'truncated'):
            decode_section({'block_states':{'palette':[{'Name':'minecraft:air'},{'Name':'minecraft:stone'}]}})
        decoded=decode_section({'block_states':{'palette':[{'Name':'minecraft:air'},{'Name':'minecraft:stone'}],'data':[-1]*256}})
        with self.assertRaisesRegex(ValueError,'out of bounds'):section_state(decoded,0,0,0)

    def test_canopy_water_and_artificial_surface_are_distinct(self):
        columns,heightmap=sample_chunk(terrain_chunk())
        self.assertTrue(heightmap)
        self.assertEqual((columns[0]['ground_y'],columns[0]['surface_y'],columns[0]['water_y']),(4,13,None))
        self.assertEqual((columns[1]['ground_y'],columns[1]['surface_y'],columns[1]['water_y']),(4,8,8))
        self.assertTrue(columns[2]['artificial'])
        self.assertEqual(columns[2]['ground_state'],'minecraft:grass_block')
        without=terrain_chunk();without.pop('Heightmaps')
        fallback,heightmap=sample_chunk(without)
        self.assertFalse(heightmap)
        self.assertEqual(fallback,columns)

    def test_negative_section_y_and_incomplete_chunk_fail(self):
        chunk={'Status':'minecraft:full','sections':[{'Y':-1,'block_states':{'palette':[{'Name':'minecraft:stone'}]}}]}
        columns,_=sample_chunk(chunk)
        self.assertTrue(all(c['ground_y']==0 for c in columns))
        chunk['Status']='minecraft:features'
        with self.assertRaisesRegex(ValueError,'FULL'):sample_chunk(chunk)

    def write_world(self,root,chunk,generator='minecraft:noise'):
        (root/'region').mkdir(exist_ok=True)
        level={'Data':{'WorldGenSettings':{'seed':20261002,'dimensions':{'minecraft:overworld':{'generator':{'type':generator}}}}}}
        (root/'level.dat').write_bytes(gzip.compress(nbt_bytes(level),mtime=0))
        cx,cz=chunk['xPos'],chunk['zPos']
        compressed=zlib.compress(nbt_bytes(saved_chunk(chunk)))
        body=struct.pack('>I',len(compressed)+1)+bytes([2])+compressed
        sectors=math.ceil(len(body)/4096)
        header=bytearray(8192);index=(cz%32)*32+cx%32
        header[index*4:index*4+4]=((2<<8)|sectors).to_bytes(4,'big')
        (root/'region'/f'r.{cx//32}.{cz//32}.mca').write_bytes(header+body+b'\0'*(sectors*4096-len(body)))

    def test_negative_world_coordinates_real_anvil_and_missing_chunk(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);self.write_world(root,terrain_chunk(-1,-1))
            survey=sample_window(root,[-16,-16,-12,-15],20261002)
            self.assertEqual(survey['ground_y'],[4]*4)
            self.assertEqual(survey['surface_y'],[13,8,5,4])
            self.assertEqual(survey['water_y'],[None,8,None,None])
            self.assertEqual(survey['width'],4)
            self.assertIn('r.-1.-1.mca',survey['provenance']['region_sha256'])
            self.assertEqual(survey['provenance']['heightmap_chunks'],1)
            with self.assertRaisesRegex(ValueError,'missing survey chunk'):
                sample_window(root,[-32,-16,-12,-15],20261002)
            with self.assertRaisesRegex(ValueError,'seed differs'):
                sample_window(root,[-16,-16,-12,-15],1)
            self.write_world(root,terrain_chunk(-1,-1),'minecraft:flat')
            with self.assertRaisesRegex(ValueError,'flat'):
                sample_window(root,[-16,-16,-12,-15],20261002)

    def settings(self):
        settings=read_settings();settings.update(sample_stride=1,origin_stride=2,minimum_site_separation=20)
        return settings

    def survey(self,x,height=63,artificial=False):
        return {'window':[x,0,x+8,8],'width':8,'depth':8,'ground_y':[height]*64,'surface_y':[height]*64,
                'water_y':[None]*64,'artificial':[artificial]*64}

    def test_distinct_candidates_gates_and_weighted_peripheral_art(self):
        core={'size':[4,4,4],'ground_y':[68]*16,'water_surface':68,'column_weights':[1]*16}
        surveys=[self.survey(0),self.survey(40,80),self.survey(80,65,True)]
        result=rank_sites(surveys,core,self.settings())
        self.assertEqual(len(result),3)
        self.assertEqual({c['survey_index'] for c in result},{0,1,2})
        self.assertEqual(result[0]['survey_index'],0)
        self.assertTrue(result[0]['eligible'])
        self.assertIn('artificial_fraction',result[-1]['limitations'])
        self.assertEqual(result[0]['dy'],-5)
        core['ground_y']=[68]*8+[115]*8
        core['column_weights']=[1]*8+[.05]*8
        result=rank_sites(surveys,core,self.settings())
        self.assertEqual(result[0]['survey_index'],0)
        self.assertLess(result[0]['metrics']['mean_earthwork'],3)

    def test_rotations_and_expanded_margin(self):
        for q in range(4):
            points={rotate_column(x,z,210,265,q) for z in range(265) for x in range(210)}
            self.assertEqual(len(points),210*265)
            expected=(265,210) if q%2 else (210,265)
            self.assertEqual((max(x for x,z in points)+1,max(z for x,z in points)+1),expected)
            candidate={'origin':[-31,57],'rotation_quarters':q,'size':list(expected)}
            window=expanded_window(candidate)
            self.assertGreaterEqual(candidate['origin'][0]-window[0],96)
            self.assertGreaterEqual(window[2]-candidate['origin'][0]-expected[0],96)
            self.assertGreaterEqual(candidate['origin'][1]-window[1],96)
            self.assertGreaterEqual(window[3]-candidate['origin'][1]-expected[1],96)

    def test_real_slope_direction_is_part_of_rotation_ranking(self):
        settings=self.settings();settings['origin_stride']=8
        core={'size':[4,4,4],'ground_y':[68+x for z in range(4) for x in range(4)],'water_surface':68}
        surveys=[self.survey(x) for x in (0,40,80)]
        for survey in surveys:survey['ground_y']=[63+x for z in range(8) for x in range(8)]
        candidates=rank_sites(surveys,core,settings)
        self.assertTrue(all(c['rotation_quarters']==0 for c in candidates))
        self.assertTrue(all(c['metrics']['slope_mismatch']==0 for c in candidates))

    def test_single_site_uses_same_rank_score_for_exact_footprint_and_rotation(self):
        settings=self.settings()
        core={'size':[3,4,4],'ground_y':[68+x for z in range(4) for x in range(3)],'water_surface':68}
        survey=self.survey(-10)
        before=copy.deepcopy(survey)
        candidate=evaluate_site(survey,[-9,1],1,core,settings)
        self.assertEqual(candidate['origin'],[-9,1])
        self.assertEqual(candidate['rotation_quarters'],1)
        self.assertEqual(candidate['size'],[4,3])
        cropped={**survey,'window':[-9,1,-5,4],'width':4,'depth':3,
                 'ground_y':[63]*12,'surface_y':[63]*12,'water_y':[None]*12,'artificial':[False]*12}
        expected=rank_sites([cropped],core,{**settings,'candidate_count':1},rotations=[1])[0]
        self.assertEqual(candidate,expected)
        self.assertEqual(survey,before)
        self.assertEqual(settings['candidate_count'],3)
        survey['ground_y'][1*8+1]=80
        changed=evaluate_site(survey,[-9,1],1,core,settings)
        self.assertNotEqual(changed['score'],candidate['score'])

    def test_single_site_refuses_missing_extent_or_missing_column(self):
        settings=self.settings()
        core={'size':[3,4,4],'ground_y':[68]*12,'water_surface':68}
        for origin,q in (([-11,0],0),([-4,6],1),([-9,-1],0),([-9,0],4)):
            with self.subTest(origin=origin,q=q),self.assertRaises(ValueError):
                evaluate_site(self.survey(-10),origin,q,core,settings)
        survey=self.survey(-10);survey['ground_y'][0]=None
        with self.assertRaisesRegex(ValueError,'missing or noninteger'):
            evaluate_site(survey,[-10,0],0,core,settings)


if __name__=='__main__':unittest.main()
