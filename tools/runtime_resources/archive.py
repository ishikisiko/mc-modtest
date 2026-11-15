"""规范化最终 JAR：按需 ZIP64、DEFLATE 9，保留每个解压后的条目。"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile
import zlib
from .pack import CONFIG


def compact(path):
    level = json.loads(CONFIG.read_text())['compression_level']
    temporary = path.with_suffix('.compact.tmp')
    before = {}
    with zipfile.ZipFile(path) as source, zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED,
                                                       compresslevel=level, allowZip64=True) as target:
        for info in source.infolist():
            name = info.filename
            if name in before:
                raise ValueError('JAR 存在重复路径：' + name)
            if name.startswith('META-INF/') and name.endswith(('.SF', '.RSA', '.DSA', '.EC')):
                raise ValueError('不能在签名后重写 JAR')
            blob = source.read(info)
            before[name] = hashlib.sha256(blob).digest()
            item = copy.copy(info)
            item.extra = b''
            method = info.compress_type
            if name.startswith('yuanxian_packed/') and name.endswith('.gz'):
                compressor = zlib.compressobj(level, zlib.DEFLATED, -15)
                compressed = compressor.compress(blob) + compressor.flush()
                method = zipfile.ZIP_DEFLATED if len(compressed) < len(blob) else zipfile.ZIP_STORED
            target.writestr(item, blob, compress_type=method, compresslevel=level)
    with zipfile.ZipFile(temporary) as check:
        assert check.testzip() is None
        assert len(check.infolist()) == len(before)
        assert all(hashlib.sha256(check.read(name)).digest() == sha for name, sha in before.items())
    old_bytes = path.stat().st_size
    temporary.replace(path)
    print(f'JAR 归档规范化：{old_bytes} → {path.stat().st_size} 字节，{len(before)} 个条目逐字节保持一致')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('jar', type=Path)
    compact(parser.parse_args().jar)
