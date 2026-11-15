import json,re,tempfile,unittest
from pathlib import Path
from tools.gen_branding import outputs
ROOT=Path(__file__).resolve().parents[2]

class BrandingTest(unittest.TestCase):
    def test_owned_outputs_match_current_author_input(self):
        for name,text in outputs().items():self.assertEqual(text,(ROOT/name).read_text(),name)

    def test_branding_keeps_namespace_and_existing_language_keys(self):
        generated=outputs();meta=generated['src/main/resources/META-INF/neoforge.mods.toml']
        self.assertIn('modId = "yuanxian"',meta);self.assertIn('displayName = "源仙"',meta)
        for locale in ['zh_cn','en_us']:
            name=f'src/main/resources/assets/yuanxian/lang/{locale}.json';before=json.loads((ROOT/name).read_text());after=json.loads(generated[name])
            for key in ['itemGroup.yuanxian.main','key.categories.yuanxian']:self.assertEqual('源仙',after[key])
            for key,value in before.items():
                if key not in ['itemGroup.yuanxian.main','key.categories.yuanxian']:self.assertEqual(value,after[key],key)
            self.assertEqual(list(before),list(after))

    def test_every_runtime_root_uses_shared_factory(self):
        pattern=r'(?:net\.minecraft\.commands\.)?Commands\.literal\((?:"yuanxian"|MOD_ID|MyVillageMod\.MOD_ID)\)'
        for path in (ROOT/'src/main/java').rglob('*.java'):
            self.assertIsNone(re.search(pattern,path.read_text()),str(path))

    def test_invalid_or_duplicate_command_names_are_rejected(self):
        for root,short in [('bad root','yx'),('yuanxian','yuanxian')]:
            with tempfile.TemporaryDirectory() as folder:
                base=Path(folder);(base/'tools').mkdir();(base/'tools/branding.json').write_text(json.dumps(dict(display_name='源仙',namespace='yuanxian',description='说明',command_root=root,command_short=short)))
                with self.assertRaises(ValueError):outputs(base)
if __name__=='__main__':unittest.main()
