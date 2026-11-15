"""仅在显式重命名导出时转换共享标签输入；运行时不加载此工具。"""
import re

def tag_values(values, previous_namespace=None):
    if previous_namespace is None:
        return list(values)
    if not re.fullmatch(r'[a-z0-9_.-]+',previous_namespace) or previous_namespace=='yuanxian':
        raise ValueError('原命名空间无效')
    prefix=previous_namespace+':'
    def convert(value):
        if not isinstance(value,str):
            raise ValueError('命名空间导出要求标签成员为字符串')
        marker='#' if value.startswith('#') else ''
        identity=value[len(marker):]
        return marker+'yuanxian:'+identity[len(prefix):] if identity.startswith(prefix) else value
    return [convert(value) for value in values]
