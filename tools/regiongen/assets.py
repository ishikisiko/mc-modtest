"""Adapt existing owned generators without changing their geometry."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import json_to_nbt as nbt
from yunshan import baseline_houses
from yunshan.build_functional import rotated_pos, rotated_state
from yunshan.build_inn import canonical, check_walks, check_roof


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                    separators=(',', ':')).encode()).hexdigest()


def load_assets(recipe):
    result = {}
    for key, spec in recipe['assets'].items():
        signs = []
        if spec['kind'] == 'baseline':
            structure, attachments, meta = getattr(baseline_houses, spec['family'])(spec['scale'])
            cells = dict(structure)
            cells.update(attachments)
            minimum = [min(p[i] for p in cells) for i in range(3)]
            maximum = [max(p[i] for p in cells) for i in range(3)]
            size = [maximum[i] - minimum[i] + 1 for i in range(3)]
            # Normalize then rotate the existing south facade to a north facade.
            cells = {rotated_pos(tuple(p[i]-minimum[i] for i in range(3)), size, 2):
                     canonical(rotated_state(s, 2)) for p, s in cells.items()}
            entry = list(rotated_pos((meta['door_x']-minimum[0], meta['base']-minimum[1],
                                      maximum[2]-minimum[2]), size, 2))
            entry[2] = 0
            source = 'tools/yunshan/baseline_houses.py'
            targets = {'entry': entry}
        elif spec['kind'] == 'authored':
            source = spec['source']
            data = json.loads((ROOT / source).read_text())
            size, _, cells, _ = nbt.expand_structure(data)
            cells = {p: canonical(s) for p, s in cells.items()}
            entry = data['walk_targets']['entry']
            targets = data['walk_targets']
            signs = data.get('signs', [])
            check_walks(cells, targets, size)
            for roof in data.get('roof_checks', []):
                check_roof(cells, roof, size)
        else:
            raise ValueError('Unknown asset adapter: ' + spec['kind'])
        public = dict(id=key, size=size, entry=entry, source=source,
                      source_sha256=hashlib.sha256((ROOT / source).read_bytes()).hexdigest(),
                      geometry_sha256=digest([[*p, s] for p, s in sorted(cells.items())]))
        result[key] = dict(public=public, blocks=cells, signs=signs, targets=targets)
    return result
