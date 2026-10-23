"""截图来源接线测试：伪造渲染边界，不启动浏览器或 HTTP 服务。"""
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import MagicMock, patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from qianzhen import build, capture


class CaptureProvenanceTest(unittest.TestCase):
    def test_partial_captures_keep_verified_prior_evidence_and_package_it(self):
        with tempfile.TemporaryDirectory(prefix='qianzhen-capture-fixture-') as temp:
            out = Path(temp)
            for site in ('relay_inn', 'apothecary'):
                (out / site).mkdir()
                (out / site / 'index.html').write_text('offline fixture')
            (out / 'index.html').write_text('offline fixture')
            (out / 'batch-report.json').write_text(json.dumps({
                'buildings': [{'id': 'relay_inn'}, {'id': 'apothecary'}]}))
            build.write_provenance(out, {'schema': 1, 'source_inputs': build.source_identity(build.SOURCE),
                                        'build_files': build.output_files(out), 'capture_files': {}})
            playwright = types.ModuleType('playwright.sync_api')
            playwright.sync_playwright = MagicMock()
            browser = playwright.sync_playwright.return_value.__enter__.return_value.chromium.launch.return_value
            page = browser.new_page.return_value

            def evaluate(script, *args):
                if script == 'window.__previewInfo':
                    return {'views': ['full']}
                if 'document.images' in script:
                    return []
                return False

            def screenshot(*, path, **kwargs):
                Path(path).write_bytes(b'fake screenshot; not visual evidence')

            page.evaluate.side_effect = evaluate
            page.screenshot.side_effect = screenshot
            page.locator.return_value.screenshot.side_effect = screenshot
            modules = {'playwright': types.ModuleType('playwright'), 'playwright.sync_api': playwright}
            with patch.dict(sys.modules, modules), patch.object(capture, 'ThreadingHTTPServer'), \
                    patch.object(capture.threading, 'Thread'):
                for site in ('relay_inn', 'apothecary'):
                    with patch.object(sys, 'argv', ['capture.py', '--out', str(out), '--only', site]):
                        capture.main()
                record = build.validate_output(out)
                self.assertEqual(set(record['capture_files']), {
                    'relay_inn/full.png', 'apothecary/full.png', 'overview.png', 'browser-check.json'})
                report = json.loads((out / 'browser-check.json').read_text())
                self.assertEqual(report['completed'], ['relay_inn', 'apothecary'])
                build.package(out)
                with zipfile.ZipFile(out / 'qianzhen_blueprints.zip') as archive:
                    self.assertIn('relay_inn/full.png', archive.namelist())
                    self.assertIn('apothecary/full.png', archive.namelist())
                # A failed/interrupted capture cannot be adopted by the next run.
                (out / 'relay_inn/full.png').write_bytes(b'changed')
                before = build.output_files(out)
                browser.reset_mock()
                with patch.object(sys, 'argv', ['capture.py', '--out', str(out)]):
                    with self.assertRaisesRegex(ValueError, 'files changed or untracked'):
                        capture.main()
                browser.new_page.assert_not_called()
                self.assertEqual(build.output_files(out), before)


if __name__ == '__main__':
    unittest.main()
