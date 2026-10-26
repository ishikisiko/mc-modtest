import hashlib

from tools.npc_garden_evidence import INITIAL, RESTART, merge, report_for_missing, set_property, validate_phase, verify_source


def phase(which, source):
    selected = list(INITIAL if which == 'initial' else RESTART)
    return {'phase': which, 'source': source, 'selected': selected, 'completed': selected,
            'unrun': [], 'checks': [{'name': 'native', 'pass': True}], 'pass': True}


def test_phase_contract_and_combined_provenance():
    initial = phase('initial', 'sha256:abc')
    restart = phase('restart', 'sha256:abc')
    assert not validate_phase(initial, 'initial', 'sha256:abc')
    combined = merge(initial, restart, 'sha256:abc', 'new-world')
    assert combined['pass']
    assert combined['completed'] == combined['selected']
    assert combined['unrun'] == []


def test_failure_keeps_unrun_and_failed_check():
    initial = phase('initial', 'sha256:abc')
    initial['completed'] = list(INITIAL[:2])
    initial['unrun'] = list(INITIAL[2:])
    initial['checks'].append({'name': 'real_plant', 'pass': False})
    initial['pass'] = False
    restart = report_for_missing('restart', 'sha256:abc', 'initial failed')
    combined = merge(initial, restart, 'sha256:abc', 'new-world')
    assert not combined['pass']
    assert combined['completed'] == list(INITIAL[:2])
    assert combined['unrun'] == list(INITIAL[2:] + RESTART)
    assert combined['checks'][1]['pass'] is False


def test_source_and_case_order_mismatch_fail_closed():
    restart = phase('restart', 'wrong-source')
    restart['completed'].reverse()
    errors = validate_phase(restart, 'restart', 'fixed-source')
    assert any('source' in error for error in errors)
    assert any('completed+unrun' in error for error in errors)


def test_server_properties_replace_only_target_key():
    updated = set_property('# snapshot\nlevel-name=old\nother=keep\n', 'level-name', 'new')
    assert updated.count('level-name=') == 1
    assert 'level-name=new' in updated
    assert 'other=keep' in updated


def test_archive_source_digest_is_checked(tmp_path):
    archive = tmp_path / 'source.tar.gz'
    archive.write_bytes(b'fixed-source')
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    verify_source(tmp_path, f'sha256:{digest}', archive)
    try:
        verify_source(tmp_path, 'sha256:' + '0' * 64, archive)
    except ValueError:
        pass
    else:
        raise AssertionError('incorrect archive identity accepted')
