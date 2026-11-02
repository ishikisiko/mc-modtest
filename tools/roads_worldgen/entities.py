"""严格读取实体分区；Minecraft 创建但未写入记录的零字节文件按空分区记录。"""
import hashlib
from pathlib import Path

from yunshan.natural_terrain import read_region


def saved_items(world):
    regions, items = [], []
    for path in sorted((Path(world) / 'entities').glob('*.mca')):
        if path.stat().st_size == 0:
            regions.append(dict(name=path.name, sha256=hashlib.sha256(b'').hexdigest(),
                                chunks=0, empty_region_placeholder=True))
            continue
        chunks, digest = read_region(path)
        regions.append(dict(name=path.name, sha256=digest, chunks=len(chunks),
                            empty_region_placeholder=False))
        for chunk in chunks.values():
            items.extend(e for e in chunk.get('Entities', []) if e.get('id') == 'minecraft:item')
    return regions, items
