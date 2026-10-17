"""Translate the authored city once into a disposable flat-world preview site.

The source SCHEM, tile NBT and artwork coordinates stay unchanged. Only the
manifest's absolute placement and evidence coordinates move together.
"""
from __future__ import annotations

import copy
import math


def _delta(value):
    if not isinstance(value, (list, tuple)) or len(value) != 3 or any(
            isinstance(v, bool) or not isinstance(v, int) for v in value):
        raise ValueError('preview_translation must contain exactly three integers')
    if any(abs(v) > 30_000_000 for v in value):
        raise ValueError('preview_translation exceeds the Minecraft coordinate bounds')
    return list(value)


def translate_point(pos, delta):
    delta = _delta(delta)
    if not isinstance(pos, (list, tuple)) or len(pos) != 3 or any(
            isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in pos):
        raise ValueError('World position must contain exactly three finite numbers')
    return [pos[i]+delta[i] for i in range(3)]


def translate_manifest(manifest):
    """Return a translated deep copy; reject a manifest that already moved."""
    if 'applied_preview_translation' in manifest or 'original_world_coordinates' in manifest:
        raise ValueError('Preview translation already applied; use the original source manifest')
    if 'preview_translation' not in manifest:
        raise ValueError('Source manifest must explicitly declare preview_translation')
    delta = _delta(manifest['preview_translation'])
    result = copy.deepcopy(manifest)
    result['original_world_coordinates'] = {
        key: copy.deepcopy(manifest[key]) for key in ('world_offset', 'world_min', 'world_max')}
    result['applied_preview_translation'] = delta

    def points(record, keys):
        for key in keys:
            if key in record:
                record[key] = translate_point(record[key], delta)

    points(result, ('world_offset', 'world_min', 'world_max'))
    if result['world_min'][1] < -64 or result['world_max'][1] > 319:
        raise ValueError('Translated city leaves the overworld build height -64..319')
    for tile in result.get('tiles', []):
        points(tile, ('world_min', 'world_max'))
        points(tile['probe'], ('pos',))
    for building in result.get('buildings', []):
        points(building, ('world_min', 'world_max', 'proposed_world_min', 'approach', 'door'))
        if 'threshold_world_y' in building:
            building['threshold_world_y'] += delta[1]
    for key, route in result.get('routes', {}).items():
        result['routes'][key] = [translate_point(point, delta) for point in route]
    for trial in result.get('path_trials', []):
        points(trial, ('start', 'end'))
        if 'min_y' in trial:
            trial['min_y'] += delta[1]
    points(result.get('recommended_spawn', {}), ('pos',))
    for camera in result.get('fixture_cameras', []):
        points(camera, ('pos', 'target'))
    for sign in result.get('signs', []):
        points(sign, ('pos',))
    for house in result.get('retained_houses', []):
        points(house, ('door',))
    return result
