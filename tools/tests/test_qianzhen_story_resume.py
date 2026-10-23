"""Offline rejection tests for the pinned chapter continuation transport."""
import copy
import hashlib
import io
import json
from pathlib import Path
import stat
import tarfile
import tempfile
import unittest
import urllib.error
import warnings
import zipfile
from unittest.mock import patch

from tools.qianzhen import story_resume as resume


class ResumeSourceTests(unittest.TestCase):
    def test_no_git_modal_archive_uses_pinned_source_not_generated_outputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, request = root/'source.tar', root/'request.json'
            with tarfile.open(archive, 'w') as out:
                member = tarfile.TarInfo('src/main/java/Production.java')
                member.size = 3
                out.addfile(member, io.BytesIO(b'abc'))
            request.write_text(json.dumps({'inputs': {'source.tar': resume.sha256(archive)}}))
            self.assertEqual(resume.archive_sources(archive, request), ['src/main/java/Production.java'])
            with archive.open('ab') as stream:
                stream.write(b'changed transport')
            with self.assertRaisesRegex(ValueError, 'SHA256'):
                resume.archive_sources(archive, request)

    def test_manifest_requires_pinned_run_commit_https_and_real_digest(self):
        valid = {'schema': 1, 'baseline_commit': resume.BASE_COMMIT,
                 'baseline_run': resume.BASE_RUN, 'url': 'https://example.test/prefix.zip',
                 'sha256': 'f'*64}
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'manifest.json'
            path.write_text(json.dumps(valid))
            self.assertEqual(resume.load_manifest(path), valid)
            for field, value in [('url', 'http://example.test/a.zip'),
                                 ('url', 'https://user:password@example.test/a.zip'),
                                 ('baseline_commit', 'a'*40), ('baseline_run', 'another-run'),
                                 ('schema', 2), ('sha256', 'xyz')]:
                with self.subTest(field=field, value=value):
                    path.write_text(json.dumps({**valid, field: value}))
                    with self.assertRaises((ValueError, AssertionError)):
                        resume.load_manifest(path)

    def test_source_hash_allows_only_the_two_default_key_changes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            tracked = sorted(resume.MANDATORY | {'src/main/resources/scene.nbt',
                'blueprints/qianzhen/apothecary.json', 'tools/qianzhen/build.py'})
            for name in tracked:
                path = root/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'baseline payload')
            client = root/resume.CLIENT
            client.write_bytes(b'public TRACKER = GLFW.GLFW_KEY_P; // same runtime')
            lang = root/resume.LANG
            lang.write_text(json.dumps({'key.myvillage.story_tracker': '切换任务追踪（默认 P）',
                                        'other': '原文'}, ensure_ascii=False))
            hashes = {p: resume.normalized_source_hash(root/p, p) for p in tracked}
            client.write_bytes(client.read_bytes().replace(b'GLFW_KEY_P', b'GLFW_KEY_Y'))
            lang.write_text('{"other":"原文","key.myvillage.story_tracker":"切换任务追踪（默认 Y）"}')
            resume.validate_sources(root, hashes, tracked)
            for name in ('blueprints/qianzhen/story_scene.json',
                         'tools/qianzhen/story_assets.py',
                         'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json',
                         'src/main/java/com/example/myvillage/story/StoryRuntime.java',
                         'src/main/resources/scene.nbt'):
                path = root/name
                original = path.read_bytes()
                path.write_bytes(original+b'new gameplay')
                with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'drift'):
                    resume.validate_sources(root, hashes, tracked)
                path.write_bytes(original)
            lang.write_text(lang.read_text().replace('原文', '修改正文'))
            with self.assertRaisesRegex(ValueError, 'drift'):
                resume.validate_sources(root, hashes, tracked)

    def test_missing_or_shortened_production_manifest_is_rejected(self):
        for sources in ({}, {'random': 'a'*64}, {p: 'a'*64 for p in resume.MANDATORY}):
            with self.subTest(size=len(sources)), self.assertRaises(ValueError):
                resume.validate_sources(Path('/unused'), sources,
                                        [*resume.MANDATORY, 'blueprints/qianzhen/omitted_scene.json'])

    def test_download_hash_is_checked_before_unpacking_and_curl_is_https_only(self):
        payload = b'fixed archive bytes'
        manifest = {'url': 'https://example.test/fixture.zip',
                    'sha256': hashlib.sha256(payload).hexdigest()}
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder)/'fixture.zip'
            opener = type('Opener', (), {'open': lambda *_args, **_kw: io.BytesIO(payload)})()
            with patch.object(resume.urllib.request, 'build_opener', return_value=opener):
                resume.download(manifest, target)
                with self.assertRaisesRegex(ValueError, 'SHA256'):
                    resume.download({**manifest, 'sha256': '0'*64}, target)
            failure = urllib.error.HTTPError(manifest['url'], 403, 'Forbidden', {}, None)
            with patch.object(resume.urllib.request, 'build_opener') as build:
                build.return_value.open.side_effect = failure
                with patch.object(resume.subprocess, 'run') as run:
                    run.side_effect = lambda *_args, **_kw: target.write_bytes(payload)
                    resume.download(manifest, target)
                    command = run.call_args.args[0]
                    self.assertEqual(command[0], 'curl')
                    self.assertEqual(command[command.index('--proto')+1], '=https')
                    self.assertEqual(command[command.index('--proto-redir')+1], '=https')
                    self.assertIn('--max-filesize', command)
            with self.assertRaisesRegex(ValueError, 'Non-HTTPS'):
                resume.HTTPSRedirect().redirect_request(None, None, 302, '', {}, 'http://example.test/x')


class ResumeArchiveTests(unittest.TestCase):
    def test_world_prefix_is_stripped_and_existing_world_is_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, destination = root/'world.zip', root/'world'
            with zipfile.ZipFile(archive, 'w') as out:
                out.writestr('Qianzhen Story/level.dat', b'level')
                out.writestr('Qianzhen Story/playerdata/id.dat', b'player')
            resume.safe_extract(archive, destination, prefix='Qianzhen Story')
            self.assertEqual((destination/'playerdata/id.dat').read_bytes(), b'player')
            with self.assertRaisesRegex(ValueError, 'nonempty'):
                resume.safe_extract(archive, destination, prefix='Qianzhen Story')
            self.assertEqual((destination/'level.dat').read_bytes(), b'level')
            with self.assertRaisesRegex(RuntimeError, 'disposable'):
                resume.restore_world(root, root/'server', 'world', archive)

    def test_unsafe_duplicates_collisions_and_oversize_fail_before_any_write(self):
        symlink = zipfile.ZipInfo('link')
        symlink.create_system = 3
        symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
        samples = [([('../escape', b'a')], {}), ([('/absolute', b'a')], {}),
                   ([('a/../../b', b'a')], {}), ([('a\\b', b'a')], {}),
                   ([('C:/world', b'a')], {}), ([(symlink, b'/tmp')], {}),
                   ([('same', b'a'), ('same', b'b')], {}),
                   ([('a', b'file'), ('a/child', b'child')], {}),
                   ([('large', b'123456')], {'limit': 5}),
                   ([('Other World/level.dat', b'a')], {'prefix': 'Qianzhen Story'})]
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for index, (members, kwargs) in enumerate(samples):
                with self.subTest(index=index):
                    archive, dest = root/f'{index}.zip', root/f'dest-{index}'
                    with warnings.catch_warnings():
                        warnings.simplefilter('ignore', UserWarning)
                        with zipfile.ZipFile(archive, 'w') as out:
                            for name, data in members:
                                out.writestr(name, data)
                    with self.assertRaises(ValueError):
                        resume.safe_extract(archive, dest, **kwargs)
                    self.assertFalse(dest.exists())
            outside = root/'outside'
            outside.mkdir()
            (root/'linked').symlink_to(outside, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, 'Symlink'):
                resume.safe_extract(root/'0.zip', root/'linked/world')


def prefix_fixture():
    """A tiny independent chapter-shaped trace; no actual world or game needed."""
    route, actions, readbacks = [], [], []
    for n in range(1, 14):
        action = {'id': 'action_'+str(n), 'effect': 'regulate' if n == 13 else ''}
        stage = 'report' if n == 13 else 'stage_'+str(n)
        route.append({'action': action, 'after': stage, 'evidence': ['sample']})
        for kind in ('chapter_action_begin', 'chapter_action_complete'):
            actions.append({'action': kind, 'id': action['id'], 'stage': stage, 'player': 'CaptureDev'})
        readbacks.append({'label': action['id']+'-after', 'player': 'CaptureDev',
                          'state': {'revision': n, 'stage': stage, 'evidence': ['sample']}})
    state = {'stage': 'report', 'revision': 13, 'evidence': ['sample'],
             'accepted': True, 'completed': False, 'reward': 'pending', 'receipts': [],
             'shared': {'returned': True, 'regulated': True},
             'npc': {name: {'personId': i} for i, name in enumerate(('a','b','c','d'))}}
    readbacks.append({'label': 'ui-before', 'player': 'CaptureDev', 'state': state})
    prior = {'source_commit': resume.BASE_COMMIT, 'status': 'fail', 'termination': 'confirmed',
             'completed': sorted(resume.REUSED), 'failures': ['Y predecessor P menu conflict'],
             'checks': [{'pass': True}], 'readbacks': readbacks,
             'commands': [{'command': 'tp CaptureDev 0 1 2'}, {'command': 'tp CaptureDev 1 1 3'}]}
    return prior, actions, [{'player': 'CaptureDev', 'position': [1.25, 2, 3.75]}], route


class ResumePrefixTests(unittest.TestCase):
    def test_exact_thirteen_action_prefix_has_verified_checkpoint(self):
        checkpoint = resume.validate_prefix(*prefix_fixture())
        self.assertEqual(checkpoint['prefix_length'], 13)
        self.assertEqual(checkpoint['position'], [1.25, 2, 3.75])

    def test_wrong_order_extra_action_missing_evidence_or_reward_never_resume(self):
        for case in ('wrong_order', 'extra_claim', 'wrong_evidence', 'wrong_revision',
                     'receipt', 'fixture', 'teleport', 'missing_readback', 'failed_check', 'missing_case'):
            prior, actions, movements, route = copy.deepcopy(prefix_fixture())
            if case == 'wrong_order':
                actions[1]['id'] = 'action_2'
            elif case == 'extra_claim':
                actions.append({'action': 'chapter_action_begin', 'id': 'claim'})
            elif case == 'wrong_evidence':
                prior['readbacks'][-1]['state']['evidence'] = []
            elif case == 'wrong_revision':
                prior['readbacks'][-1]['state']['revision'] = 12
            elif case == 'receipt':
                prior['readbacks'][-1]['state']['receipts'] = ['claimed']
            elif case == 'fixture':
                actions.append({'action': 'fixture_start'})
            elif case == 'teleport':
                prior['commands'].append({'command': 'tp CaptureDev 1 2 3'})
            elif case == 'missing_readback':
                prior['readbacks'].pop(3)
            elif case == 'failed_check':
                prior['checks'][0]['pass'] = False
            elif case == 'missing_case':
                prior['completed'].pop()
            with self.subTest(case=case), self.assertRaises((ValueError, AssertionError)):
                resume.validate_prefix(prior, actions, movements, route)


if __name__ == '__main__':
    unittest.main()
