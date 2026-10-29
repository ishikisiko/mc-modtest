import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from .audit_legacy_jar import compare,city_diagnostics,digest,ROOT


class LegacyJarAuditTest(unittest.TestCase):
    def test_geometry_bytes_cannot_be_relabelled_as_provenance(self):
        with tempfile.TemporaryDirectory() as d:
            old,new=Path(d)/'old.jar',Path(d)/'new.jar'
            name='data/myvillage/structure/province_capitals/v2/capital_lingyue/tile_0_0.nbt'
            for p,value in [(old,b'original'),(new,b'changed')]:
                with zipfile.ZipFile(p,'w') as z:z.writestr(name,value)
            result=compare(old,new)
            self.assertFalse(result['passed']);self.assertEqual('byte_change',result['failures'][0]['kind'])
            self.assertTrue(compare(old,old)['passed'])

    def test_only_hashes_of_actual_source_files_can_change(self):
        path='tools/buildgen/groups.py'
        old={'display_name':'旧城','source':{'hashes':{path:'old'}}}
        new={'display_name':'旧城','source':{'hashes':{path:digest((ROOT/path).read_bytes())}}}
        self.assertTrue(city_diagnostics(old,new)[0])
        new['display_name']='换城'
        self.assertFalse(city_diagnostics(old,new)[0])

    def test_missing_old_resources_are_failures(self):
        with tempfile.TemporaryDirectory() as d:
            old,new=Path(d)/'old.jar',Path(d)/'new.jar'
            with zipfile.ZipFile(old,'w') as z:z.writestr('data/myvillage/worldplan/frozen_v9.json','{}')
            with zipfile.ZipFile(new,'w'):pass
            self.assertEqual('missing',compare(old,new)['failures'][0]['kind'])

    def test_v2_index_uses_zhongzhou_v1_row_and_checks_both_jar_sizes(self):
        with tempfile.TemporaryDirectory() as d:
            old,new=Path(d)/'old.jar',Path(d)/'new.jar'
            source='tools/buildgen/groups.py'
            manifest_path='data/myvillage/capitals/v1/capital_zhongzhou/city.json'
            terrain_path='data/myvillage/capitals/v1/capital_zhongzhou/terrain.bin.gz'
            tile_path='data/myvillage/structure/province_capitals/v1/capital_zhongzhou/tile_0_0.nbt'
            index_path='data/myvillage/capitals/v2/index.json'
            manifest={'city_id':'capital_zhongzhou',
                      'terrain':{'resource':'myvillage:capitals/v1/capital_zhongzhou/terrain.bin.gz'},
                      'tiles':[{'template':'myvillage:province_capitals/v1/capital_zhongzhou/tile_0_0'}],
                      'source':{'hashes':{source:'old'}}}
            def write_jar(path,source_hash,extra_bytes=0,resource_version='v1'):
                manifest['source']['hashes'][source]=source_hash
                raw=json.dumps(manifest).encode();terrain=b'terrain';tile=b'tile'
                row={'city_id':'capital_zhongzhou','resource':'myvillage:capitals/v1/capital_zhongzhou/city.json',
                     'resource_version':resource_version,'raw_city_json_sha256':digest(raw),
                     'bytes':len(raw)+len(terrain)+len(tile)+extra_bytes}
                index={'cities':[row],'total_bytes':row['bytes']}
                with zipfile.ZipFile(path,'w') as z:
                    for name,data in ((manifest_path,raw),(terrain_path,terrain),(tile_path,tile),
                                      (index_path,json.dumps(index).encode())):z.writestr(name,data)
            write_jar(old,'old')
            write_jar(new,digest((ROOT/source).read_bytes()))
            result=compare(old,new)
            self.assertTrue(result['passed'],result)
            self.assertEqual({manifest_path,index_path},{x['path'] for x in result['diagnostic_changes']})
            write_jar(new,digest((ROOT/source).read_bytes()),extra_bytes=1)
            self.assertFalse(compare(old,new)['passed'])
            write_jar(new,digest((ROOT/source).read_bytes()),resource_version='v2')
            self.assertFalse(compare(old,new)['passed'])
            write_jar(old,'old',extra_bytes=1)
            write_jar(new,digest((ROOT/source).read_bytes()))
            self.assertFalse(compare(old,new)['passed'])


if __name__=='__main__':unittest.main()
