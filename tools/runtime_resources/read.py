"""开发工具读取当前构建的逻辑资源，兼容拥有者的未打包输出。"""
from collections import OrderedDict
import gzip
import json
from pathlib import Path
from zipfile import Path as ArchivePath

from .pack import decode_records, digest, restore_model, valid_path


class DirectoryResources:
    def __init__(self, root):
        self.root = root if isinstance(root, ArchivePath) else Path(root)
        path = self.root / 'yuanxian_packed/manifest.json'
        self.manifest = json.loads(path.read_bytes()) if path.exists() else None
        self.entries = {v[0]: v[1:] for v in self.manifest['entries']} if self.manifest else {}
        self.cache = OrderedDict()
        self.cached_bytes = 0

    def group(self, number):
        if number in self.cache:
            self.cache.move_to_end(number)
            return self.cache[number][1]
        name, codec, size, sha, count = self.manifest['chunks'][number]
        value = (self.root / name).read_bytes()
        assert digest(value) == sha, name
        raw = gzip.decompress(value)
        assert len(raw) == size <= self.manifest['limits']['max_group_bytes']
        decoded = decode_records(raw, self.manifest['limits']['max_file_bytes']) if codec == 'bytes' else json.loads(raw)
        assert len(decoded) == count
        self.cache[number] = (size, decoded)
        self.cached_bytes += size
        while self.cached_bytes > self.manifest['limits']['cache_bytes'] or len(self.cache) > self.manifest['limits']['cache_groups']:
            _, (removed, _) = self.cache.popitem(last=False)
            self.cached_bytes -= removed
        return decoded

    def read(self, name):
        if not valid_path(name):
            raise ValueError('非法逻辑资源路径：' + str(name))
        path = self.root / name
        if path.exists():
            return path.read_bytes()
        if name not in self.entries:
            raise FileNotFoundError(name)
        group, index, sha, size = self.entries[name]
        value = self.group(group)[index]
        if self.manifest['chunks'][group][1] == 'models':
            template, parameters = value
            tg, ti = self.manifest['templates'][template]
            value = restore_model(self.group(tg)[ti], parameters)
        assert len(value) == size and digest(value) == sha, name
        return value
