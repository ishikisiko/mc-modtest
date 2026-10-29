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


if __name__=='__main__':unittest.main()
