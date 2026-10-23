"""Strict, offline helpers for the physical Qianzhen chapter collector."""
from __future__ import annotations

from collections import deque
import json
import math
import re


STATUS_PREFIX = 'QIANZHEN_STORY_STATUS '
DIALOGUE = re.compile(
    r'QIANZHEN_DIALOGUE option=(?P<option>[a-zA-Z0-9_.:-]{1,64}) '
    r'x=(?P<x>-?\d+) y=(?P<y>-?\d+) w=(?P<w>\d+) h=(?P<h>\d+) '
    r'nonce=(?P<nonce>-?\d+)(?:\s|$)')
VIEW = re.compile(r'QIANZHEN_STORY_VIEW revision=(?P<revision>\d+) '
                  r'available=(?P<available>true|false) accepted=(?P<accepted>true|false) '
                  r'completed=(?P<completed>true|false) chapter=(?P<chapter>[a-z0-9_]+)(?:\s|$)')


def client_views(log_tail):
    """Applied client snapshots from caller-sliced fresh log text only."""
    rows = []
    for match in VIEW.finditer(log_tail):
        row = match.groupdict()
        row['revision'] = int(row['revision'])
        for key in ('available', 'accepted', 'completed'):
            row[key] = row[key] == 'true'
        rows.append(row)
    return rows


def status_object(reply):
    """Never mistake an unrelated command reply for successful progression."""
    rows = [json.loads(line[len(STATUS_PREFIX):]) for line in reply.splitlines()
            if line.startswith(STATUS_PREFIX)]
    if len(rows) != 1 or not isinstance(rows[0], dict):
        raise ValueError('Expected exactly one QIANZHEN_STORY_STATUS JSON object: ' + reply)
    return rows[0]


def dialogue_buttons(log_tail, size, option=None):
    """Only caller-sliced fresh log text; runtime logs physical rectangle origins."""
    rows = []
    for line in log_tail.splitlines():
        match = DIALOGUE.search(line)
        if not match:
            continue
        row = match.groupdict()
        for key in ('x', 'y', 'w', 'h', 'nonce'):
            row[key] = int(row[key])
        if row['w'] <= 0 or row['h'] <= 0 or not (
                0 <= row['x'] and row['x']+row['w'] <= size[0]
                and 0 <= row['y'] and row['y']+row['h'] <= size[1]):
            raise ValueError('Dialogue physical coordinates outside actual window: ' + line)
        rows.append(row)
    if not rows:
        return []
    # Each screen render can repeat a nonce; take the newest session/render only.
    newest = rows[-1]['nonce']
    rows = [row for row in rows if row['nonce'] == newest]
    unique = {row['option']: row for row in rows}
    return [row for name, row in unique.items() if option is None or name == option]


def finite_vector(value, length=3):
    if not isinstance(value, (list, tuple)) or len(value) != length or any(
            isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in value):
        raise ValueError('Expected finite coordinate vector: ' + repr(value))
    return list(value)


def cardinal_path(graph, start, target, blocked=()):
    """Blocked NPC feet stay excluded; unreachable routes fail without fallback TP."""
    start, target = tuple(start), tuple(target)
    blocked = set(map(tuple, blocked))
    if start not in graph or target not in graph or target in blocked:
        raise ValueError(f'Route endpoint absent/blocked: {start} -> {target}')
    parents, pending = {start: None}, deque([start])
    while pending:
        node = pending.popleft()
        if node == target:
            path = []
            while node is not None:
                path.append(node)
                node = parents[node]
            return path[::-1]
        for other in graph[node]:
            if sum(abs(node[i]-other[i]) for i in (0, 2)) != 1:
                raise ValueError('Non-cardinal graph edge')
            if other not in parents and other not in blocked:
                parents[other] = node
                pending.append(other)
    raise ValueError(f'No continuous mapped route: {start} -> {target}')


def require_subset(actual, expected, label='state'):
    """Recursive assertions are authored expectations, not fuzzy stage guesses."""
    if isinstance(expected, dict):
        if not isinstance(actual, dict):
            raise AssertionError(f'{label}: expected object, got {actual!r}')
        for key, value in expected.items():
            if key not in actual:
                raise AssertionError(f'{label}: missing {key}')
            require_subset(actual[key], value, label+'.'+key)
    elif actual != expected:
        raise AssertionError(f'{label}: {actual!r} != {expected!r}')


def reward_counts(inventory, rewards):
    """Only reward item IDs from actual chapter data; no fixed denomination."""
    return {row['item']: sum(s.get('count', s.get('Count', 0)) for s in inventory
                            if s.get('id') == row['item']) for row in rewards}


def compile_route(chapter, ids):
    """Check the actual handoff action order against actual authored transitions."""
    actions = {row['id']: row for row in chapter['actions']}
    stages = {row['id']: row for row in chapter['stages']}
    if len(actions) != len(chapter['actions']) or len(stages) != len(chapter['stages']):
        raise ValueError('Duplicate authored action/stage')
    if not isinstance(ids, list) or not ids or any(not isinstance(key, str) for key in ids):
        raise ValueError('Handoff route must be a nonempty ordered action ID list')
    current, evidence, route = 'new', set(), []
    for key in ids:
        if key not in actions:
            raise ValueError('Handoff refers to missing action '+key)
        action = actions[key]
        if current not in action['stages'] or not set(action['requires']) <= evidence:
            raise ValueError(f'Handoff action {key} is not eligible in {current}: {sorted(evidence)}')
        if action['kind'] not in ('talk', 'investigate', 'claim'):
            raise ValueError('Unsupported physical action kind '+action['kind'])
        before = current
        if action['evidence']:
            evidence.add(action['evidence'])
        current = action['next'] or current
        seen = set()
        while stages[current]['auto_next'] and set(stages[current]['auto_requires']) <= evidence:
            if current in seen:
                raise ValueError('Authored auto-transition cycle')
            seen.add(current)
            current = stages[current]['auto_next']
        route.append({'action': action, 'before': before, 'after': current,
                      'evidence': sorted(evidence)})
    if current != 'complete':
        raise ValueError('Handoff route does not complete the authored chapter: '+current)
    return route
