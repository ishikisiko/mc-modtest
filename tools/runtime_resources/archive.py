"""规范化最终 JAR：按需 ZIP64、DEFLATE 9，保留每个解压后的条目。"""
import argparse
import copy
import hashlib
from pathlib import Path
import zipfile


def compact(path):
    temporary = path.with_suffix('.compact.tmp')
    before = {}
    with zipfile.ZipFile(path) as source, zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED,
                                                       compresslevel=9, allowZip64=True) as target:
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
            method = zipfile.ZIP_STORED if name.startswith('myvillage_packed/') and name.endswith('.gz') else info.compress_type
            target.writestr(item, blob, compress_type=method, compresslevel=9)
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
