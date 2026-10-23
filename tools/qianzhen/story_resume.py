"""Bounded, fail-closed transport for one explicitly pinned story continuation.

No Minecraft imports or commands: offline tests exercise the same archive,
source and prefix checks used before the native collector starts anything.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tarfile
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

from tools.qianzhen.story_evidence_helpers import finite_vector, require_subset

BASE_COMMIT = '2bc57445283bedf3dc620d21c3d9789a20b6f57f'
BASE_RUN = '20261023T155335Z-0cb1b9e3'
LIMIT = 512 * 1024 * 1024
CLIENT = 'src/main/java/com/example/myvillage/client/story/StoryClient.java'
LANG = 'src/main/resources/assets/myvillage/lang/zh_cn.json'
MANDATORY = {
    CLIENT, LANG, 'src/main/java/com/example/myvillage/story/StoryRuntime.java',
    'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json',
    'src/main/resources/data/myvillage/story/qianzhen_actors.json',
    'blueprints/qianzhen/story_scene.json', 'tools/qianzhen/story_assets.py',
}
REUSED = {'production_deploy', 'fresh_entry_world', 'rescue_restart'}
def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def relative_name(name):
    if not isinstance(name, str) or not name or '\\' in name or '\x00' in name:
        raise ValueError('Unsafe relative path: '+repr(name))
    path = PurePosixPath(name)
    if path.is_absolute() or any(p in ('', '.', '..') or ':' in p for p in name.split('/')):
        raise ValueError('Unsafe relative path: '+name)
    return path


def normalized_source_hash(path, relative):
    data = Path(path).read_bytes()
    if relative == CLIENT:
        data = data.replace(b'GLFW.GLFW_KEY_Y', b'GLFW.GLFW_KEY_P')
    elif relative == LANG:
        value = json.loads(data)
        value['key.myvillage.story_tracker'] = value['key.myvillage.story_tracker'].replace('默认 Y', '默认 P')
        data = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
    return hashlib.sha256(data).hexdigest()


def archive_sources(archive, request_path):
    """Modal normal presets have no .git; use their hash-pinned git archive."""
    request = json.loads(Path(request_path).read_text())
    if Path(archive).is_symlink() or sha256(archive) != request['inputs']['source.tar']:
        raise ValueError('Current Modal source archive SHA256 mismatch')
    with tarfile.open(archive) as source:
        names = [member.name for member in source if not member.isdir()]
    for name in names:
        relative_name(name)
    if len(names) != len(set(names)):
        raise ValueError('Duplicate current source archive member')
    return names


def validate_sources(root, sources, tracked=None):
    """Require the whole packaged source/resource + authored scene surface.

    Additional generators/build inputs in the parent manifest are checked too.
    Git enumeration prevents a shortened manifest silently omitting a scene or
    Java/resource file. Collector tooling and tests are intentionally separate.
    """
    root = Path(root)
    if not isinstance(sources, dict) or not MANDATORY <= sources.keys():
        raise ValueError('source_files misses mandatory production coverage')
    if tracked is None:
        if (root/'.git').exists():
            tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
        elif root == Path('/workspace/repo'):
            tracked = archive_sources('/workspace/source.tar', '/workspace/request.json')
        else:
            raise ValueError('No authoritative tracked-source list for this checkout')
    collector = {'tools/qianzhen/story_evidence.py', 'tools/qianzhen/story_evidence_helpers.py',
                 'tools/qianzhen/story_resume.py', 'tools/qianzhen/story_resume_fixture.json'}
    required = {p for p in tracked if p.startswith(('src/main/', 'blueprints/'))
                or p in ('build.gradle', 'settings.gradle', 'gradle.properties')
                or (p.startswith('tools/') and not p.startswith(('tools/tests/', 'tools/modal_runner/'))
                    and p not in collector)}
    if not required <= sources.keys():
        raise ValueError('source_files omits tracked production inputs: '+repr(sorted(required-sources.keys())[:12]))
    for name, digest in sources.items():
        relative_name(name)
        path = root/name
        if name not in tracked or not re.fullmatch('[0-9a-f]{64}', str(digest)):
            raise ValueError('Untracked source or invalid SHA256: '+name)
        if path.is_symlink() or any(p.is_symlink() for p in path.parents if p != root.parent):
            raise ValueError('Symlink source: '+name)
        if not path.is_file() or normalized_source_hash(path, name) != digest:
            raise ValueError('Production source drift: '+name)


def load_manifest(path):
    manifest = json.loads(Path(path).read_text())
    require_subset(manifest, {'schema': 1, 'baseline_commit': BASE_COMMIT, 'baseline_run': BASE_RUN})
    parsed = urllib.parse.urlsplit(manifest['url'])
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
            or parsed.fragment or not re.fullmatch('[0-9a-f]{64}', str(manifest['sha256']))):
        raise ValueError('Resume requires an HTTPS ZIP URL and SHA256')
    return manifest


class HTTPSRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        if urllib.parse.urlsplit(newurl).scheme != 'https':
            raise ValueError('Non-HTTPS resume redirect refused')
        return super().redirect_request(request, fp, code, msg, headers, newurl)


def download(manifest, target):
    deadline = time.monotonic()+240
    try:
        with urllib.request.build_opener(HTTPSRedirect()).open(manifest['url'], timeout=45) as reply:
            with target.open('wb') as out:
                copied = 0
                while chunk := reply.read(1024 * 1024):
                    copied += len(chunk)
                    if copied > LIMIT or time.monotonic() > deadline:
                        raise ValueError('Resume download exceeds size/time limit')
                    out.write(chunk)
    except urllib.error.HTTPError as error:
        if error.code != 403:
            raise
        subprocess.run(['curl', '-fsSL', '--proto', '=https', '--proto-redir', '=https',
                        '--max-time', '240', '--max-filesize', str(LIMIT),
                        '--output', str(target), manifest['url']], check=True, timeout=250)
    if target.stat().st_size > LIMIT or sha256(target) != manifest['sha256']:
        raise ValueError('Resume ZIP SHA256 mismatch or oversized download')


def safe_extract(archive_path, destination, *, prefix=None, limit=LIMIT):
    """Validate the entire central directory before writing any archive member."""
    destination = Path(destination)
    if destination.is_symlink() or (destination.exists() and any(destination.iterdir())):
        raise ValueError('Refuse existing nonempty/symlink extraction destination')
    for parent in destination.parents:
        if parent.is_symlink():
            raise ValueError('Symlink extraction ancestor')
    with zipfile.ZipFile(archive_path) as archive:
        seen, files, total = set(), [], 0
        if len(archive.infolist()) > 50000:
            raise ValueError('Excessive ZIP member count')
        for info in archive.infolist():
            if info.orig_filename != info.filename:
                raise ValueError('ZIP member contains a NUL path')
            name = info.filename[:-1] if info.is_dir() else info.filename
            path = relative_name(name)
            mode = info.external_attr >> 16
            if stat.S_ISLNK(mode) or stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR):
                raise ValueError('Unsafe ZIP member type: '+name)
            if name in seen:
                raise ValueError('Duplicate ZIP member: '+name)
            seen.add(name)
            total += info.file_size
            if total > limit:
                raise ValueError('ZIP extraction exceeds bounded limit')
            if prefix is not None:
                if path.parts[0] != prefix:
                    raise ValueError('Unexpected world ZIP root: '+name)
                path = PurePosixPath(*path.parts[1:])
                if not path.parts:
                    if not info.is_dir():
                        raise ValueError('World root is not a directory')
                    continue
            files.append((info, path))
        file_names = {str(path) for info, path in files if not info.is_dir()}
        for _, path in files:
            if any(str(parent) in file_names for parent in path.parents):
                raise ValueError('ZIP file/directory collision: '+str(path))
        destination.mkdir(parents=True, exist_ok=True)
        for info, path in files:
            target = destination/str(path)
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as src, target.open('xb') as dst:
                    shutil.copyfileobj(src, dst, 1024 * 1024)
    return total


def read_jsonl(path):
    with Path(path).open() as stream:
        return [json.loads(line) for line in stream if line.strip()]


def validate_prefix(prior, actions, movements, route):
    require_subset(prior, {'source_commit': BASE_COMMIT, 'status': 'fail', 'termination': 'confirmed'})
    if set(prior['completed']) != REUSED or not prior['failures']:
        raise ValueError('Unexpected prior completed cases or missing original failure')
    if any(row.get('pass') is not True for row in prior['checks']):
        raise ValueError('Prior checks contain a failed assertion')
    completed = [row for row in actions if row.get('action') == 'chapter_action_complete']
    begun = [row for row in actions if row.get('action') == 'chapter_action_begin']
    prefix = route[:13]
    if len(completed) != 13 or [row.get('id') for row in completed] != [r['action']['id'] for r in prefix]:
        raise ValueError('Prior chapter actions are not the exact primary prefix')
    if [r.get('id') for r in begun] != [r['action']['id'] for r in prefix]:
        raise ValueError('Unexpected partial action after the prior prefix')
    if any(row.get('player') != 'CaptureDev' or row.get('stage') != step['after']
           for row, step in zip(completed, prefix)) or prefix[-1]['action']['effect'] != 'regulate':
        raise ValueError('Prior prefix actor/stage/effect mismatch')
    # Only fixture starts before acceptance are allowed to contain teleports.
    first = next(i for i, row in enumerate(actions) if row.get('action') == 'chapter_action_begin')
    if any(row.get('action', '').startswith('fixture_') for row in actions[first:]):
        raise ValueError('Fixture jump inside the prior continuous chapter')
    for row in prior['commands']:
        command = row['command']
        if 'myvillage story ' in command and not command.startswith('myvillage story status '):
            raise ValueError('Prior story mutation command')
    teleports = [r['command'] for r in prior['commands']
                 if re.search(r'(?:^|\s)(?:tp|teleport)\s', r['command'])]
    if len(teleports) != 2 or any(not command.startswith('tp CaptureDev ') for command in teleports):
        raise ValueError('Unexpected prior teleport outside the two initial fixture placements')
    states = [row['state'] for row in prior['readbacks'] if row['label'] == 'ui-before'
              and row['player'] == 'CaptureDev']
    if not states:
        raise ValueError('Missing prior ui-before state')
    state = states[-1]
    require_subset(state, {'stage': 'report', 'revision': 13, 'accepted': True,
        'completed': False, 'receipts': [], 'reward': 'pending',
        'shared': {'returned': True, 'regulated': True}})
    if set(state['evidence']) != set(prefix[-1]['evidence']):
        raise ValueError('Prior evidence set differs from exact primary prefix')
    for index, step in enumerate(prefix, 1):
        after = [r['state'] for r in prior['readbacks'] if r['label'] == step['action']['id']+'-after'
                 and r['player'] == 'CaptureDev']
        if not after:
            raise ValueError('Missing action readback: '+step['action']['id'])
        require_subset(after[-1], {'stage': step['after'], 'revision': index})
        if set(after[-1]['evidence']) != set(step['evidence']):
            raise ValueError('Prior action evidence mismatch: '+step['action']['id'])
    if not movements or movements[-1].get('player') != 'CaptureDev':
        raise ValueError('Missing final primary movement sample')
    position = finite_vector(movements[-1]['position'])
    people = {role: row['personId'] for role, row in state['npc'].items()}
    if len(people) != 4 or len(set(people.values())) != 4 or any(pid < 0 for pid in people.values()):
        raise ValueError('Prior four story identities are incomplete')
    return {'state': state, 'position': position, 'people': people, 'prefix_length': 13}


def prepare(root, out, manifest_path, route, origin):
    """Download/verify/preserve prior evidence; leave live world restore separate."""
    manifest = load_manifest(manifest_path)
    out = Path(out)
    prior_dest = out/'prior-run'
    if prior_dest.exists():
        raise ValueError('Keep prior evidence; prior-run already exists')
    with tempfile.TemporaryDirectory(prefix='qianzhen-resume-') as folder:
        folder = Path(folder)
        transport = folder/'fixture.zip'
        download(manifest, transport)
        extracted = folder/'extracted'
        expanded = safe_extract(transport, extracted)
        if {p.name for p in extracted.iterdir()} != {'baseline.json', 'evidence'}:
            raise ValueError('Resume ZIP requires exactly baseline.json + evidence/')
        baseline = json.loads((extracted/'baseline.json').read_text())
        require_subset(baseline, {'schema': 1, 'source_commit': BASE_COMMIT, 'source_run': BASE_RUN})
        validate_sources(root, baseline['source_files'])
        prior_dir = extracted/'evidence'
        required = {'evidence.json', 'actions.jsonl', 'movement-readbacks.jsonl',
                    'qianzhen_story_world.zip', 'fresh-entry-world.zip', 'chapter-used.json',
                    'runtime-handoff-used.json'}
        if not prior_dir.is_dir() or not all((prior_dir/p).is_file() for p in required):
            raise ValueError('Prior native evidence files are incomplete')
        prior = json.loads((prior_dir/'evidence.json').read_text())
        if prior['scene']['origin'] != list(origin):
            raise ValueError('Resume origin differs from preserved scene')
        chapter = Path(root)/'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json'
        if sha256(prior_dir/'chapter-used.json') != sha256(chapter):
            raise ValueError('Prior chapter-used differs from current production chapter')
        for name in [*prior['videos'], *(r['path'] for r in prior['screenshots'])]:
            relative_name(name)
            if not (prior_dir/name).is_file():
                raise ValueError('Prior screenshot/video missing: '+name)
        checkpoint = validate_prefix(prior, read_jsonl(prior_dir/'actions.jsonl'),
                                     read_jsonl(prior_dir/'movement-readbacks.jsonl'), route)
        # Validate the inner archive before preserving it or touching server/world.
        safe_extract(prior_dir/'qianzhen_story_world.zip', folder/'world-check',
                     prefix='Qianzhen Story', limit=LIMIT-expanded)
        shutil.copytree(prior_dir, prior_dest)
        shutil.copy2(extracted/'baseline.json', out/'resume-baseline.json')
    checkpoint.update(prior=prior, manifest=manifest, remaining_limit=LIMIT-expanded,
                      baseline_sha256=sha256(out/'resume-baseline.json'))
    return checkpoint


def restore_world(root, server_dir, level_name, archive, limit=LIMIT):
    root, server_dir = Path(root), Path(server_dir)
    if root != Path('/workspace/repo') or not server_dir.resolve().is_relative_to(root):
        raise RuntimeError('World restore requires the disposable /workspace/repo server')
    if not level_name or len(relative_name(level_name).parts) != 1:
        raise ValueError('Unsafe capture level name')
    # Refuse both Minecraft's usual world and the capture-specific destination.
    for destination in {server_dir/'world', server_dir/level_name}:
        if destination.is_symlink() or (destination.exists() and any(destination.iterdir())):
            raise ValueError('Refuse existing nonempty server world: '+str(destination))
    safe_extract(archive, server_dir/level_name, prefix='Qianzhen Story', limit=limit)
    if not (server_dir/level_name/'level.dat').is_file() or not (server_dir/level_name/'playerdata').is_dir():
        raise ValueError('Restored world lacks level.dat or saved primary playerdata')
