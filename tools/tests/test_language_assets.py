import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from tools.language_assets import language_difference, language_text, load_language
from tools.validate_language_assets import validate


class LanguageAssetsTest(unittest.TestCase):
    def test_delete_and_reinsert_owned_keys_preserves_unrelated_new_fates_and_original_bytes(self):
        original = {'block.manor': '城主府', 'block.courtyard': '水院', 'fate.affinity': '亲金'}
        generated = {k: v for k, v in original.items() if not k.startswith('block.manor')}
        generated['block.manor'] = '城主府'
        self.assertEqual(language_text(original, original), language_text(original, generated))

    def test_two_owners_can_regenerate_in_either_order_without_byte_drift(self):
        original = {'manor.a': '甲', 'courtyard.a': '乙', 'fate.affinity': '亲木'}
        def run(doc, owner):
            generated = {k: v for k, v in doc.items() if not k.startswith(owner)}
            generated.update((k, v) for k, v in doc.items() if k.startswith(owner))
            return load_language(language_text(doc, generated))
        first = run(run(original, 'manor.'), 'courtyard.')
        second = run(run(original, 'courtyard.'), 'manor.')
        self.assertEqual(language_text(original, original), language_text(first, first))
        self.assertEqual(language_text(first, first), language_text(second, second))

    def test_actual_owned_value_changes_and_stale_removal_are_retained(self):
        original = {'owned.live': '旧值', 'foreign': '保留', 'owned.stale': '过期'}
        generated = {'foreign': '保留', 'owned.live': '新值', 'owned.new': '新增'}
        actual = load_language(language_text(original, generated))
        self.assertEqual(['owned.live', 'foreign', 'owned.new'], list(actual))
        self.assertEqual('新值', actual['owned.live'])
        self.assertNotIn('owned.stale', actual)

    def test_duplicate_keys_and_non_string_values_fail_instead_of_being_overwritten(self):
        for text in ['{"a":"甲","a":"乙"}', '{"a":3}', '{"a":{"nested":"甲"}}', '[]']:
            with self.assertRaises(ValueError):
                load_language(text)

    def test_difference_distinguishes_order_from_content_loss_and_value_change(self):
        self.assertEqual('key_order', language_difference('{"a":"甲","b":"乙"}', '{"b":"乙","a":"甲"}')['kind'])
        diff = language_difference('{"a":"甲","b":"乙"}', '{"b":"变更","c":"丙"}')
        self.assertEqual({'kind': 'content', 'added': ['c'], 'removed': ['a'], 'changed': ['b']}, diff)
        self.assertEqual('format', language_difference('{"a":"甲"}', '{ "a": "甲" }')['kind'])

    def test_jar_check_keeps_exact_assertion_for_order_only_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            names = ['assets/myvillage/lang/zh_cn.json', 'assets/myvillage/lang/en_us.json']
            text = language_text({'a': '甲', 'b': '乙'}, {'a': '甲', 'b': '乙'})
            for name in names:
                p = root/'src/main/resources'/name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            jar = root/'test.jar'
            with zipfile.ZipFile(jar, 'w') as z:
                for name in names:
                    z.writestr(name, language_text({'b': '乙', 'a': '甲'}, {'b': '乙', 'a': '甲'}))
            errors = validate(root, jar)
            self.assertEqual(2, len(errors))
            self.assertTrue(all('key_order' in e for e in errors))
            with zipfile.ZipFile(jar, 'w') as z:
                for name in names:
                    z.writestr(name, text)
            self.assertEqual([], validate(root, jar))

    def test_generation_snapshot_rejects_both_order_drift_and_real_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            texts = {}
            for locale in ['zh_cn', 'en_us']:
                name = f'assets/myvillage/lang/{locale}.json'
                text = language_text({'a': '甲', 'b': '乙'}, {'a': '甲', 'b': '乙'})
                path = root/'src/main/resources'/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
                texts[name] = text
            baseline = root/'snapshot.json'
            baseline.write_text(json.dumps({'version': 1, 'files': texts}))
            self.assertEqual([], validate(root, baseline=baseline))
            path = root/'src/main/resources/assets/myvillage/lang/zh_cn.json'
            path.write_text(language_text({'b': '乙', 'a': '甲'}, {'b': '乙', 'a': '甲'}))
            self.assertTrue(any('key_order' in e for e in validate(root, baseline=baseline)))
            path.write_text(language_text({'a': '甲', 'b': '乙'}, {'a': '变更', 'b': '乙'}))
            self.assertTrue(any('content' in e for e in validate(root, baseline=baseline)))


if __name__ == '__main__':
    unittest.main()
