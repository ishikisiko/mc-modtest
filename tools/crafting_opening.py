"""开局三词条固定贡献的独立参考实现。"""
from __future__ import annotations
import hashlib
import re
import struct
import uuid
from pathlib import Path
from tools import crafting_quality

ROOT=Path(__file__).resolve().parents[1]
RESOURCE=Path('src/main/resources/data/myvillage/crafting/opening.json')


def parse(data):
    def keys(obj,expected):
        if not isinstance(obj,dict) or set(obj)!=set(expected):raise ValueError('开局资质字段不符')
    def integer(value,minimum,maximum):
        if type(value)is not int or not minimum<=value<=maximum:raise ValueError('开局资质整数不合法')
    keys(data,('version','hash_domain','base','weighted_deltas'))
    integer(data['version'],1,1);integer(data['base'],0,crafting_quality.MAX_SCORE)
    if not isinstance(data['hash_domain'],str) or not re.fullmatch(r'[a-z0-9:_-]{1,128}',data['hash_domain']):raise ValueError('哈希域无效')
    if not isinstance(data['weighted_deltas'],list) or not data['weighted_deltas']:raise ValueError('缺少权重')
    seen=set();total=0
    for row in data['weighted_deltas']:
        keys(row,('value','weight'));integer(row['value'],-2_147_483_648,crafting_quality.MAX_SCORE);integer(row['weight'],1,crafting_quality.MAX_SCORE)
        if row['value'] in seen:raise ValueError('重复加值')
        seen.add(row['value']);total+=row['weight']
    if total>crafting_quality.MAX_SCORE or data['base']+3*min(seen)<0 or data['base']+3*max(seen)>crafting_quality.MAX_SCORE:raise ValueError('初始范围溢出')
    return data


def load(root=ROOT):return parse(crafting_quality.load_json((Path(root)/RESOURCE).read_bytes()))


def delta(rules,player,profession,fate):
    if profession not in ('alchemy','forging'):raise ValueError('开局规则仅用于炼丹炼器')
    if not isinstance(fate,str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+',fate):raise ValueError('词条 ID 无效')
    fields=[rules['hash_domain'].encode('utf-8'),uuid.UUID(str(player)).bytes,profession.encode('utf-8'),fate.encode('utf-8')]
    payload=b''.join(struct.pack('>I',len(field))+field for field in fields)
    bound=sum(row['weight'] for row in rules['weighted_deltas']);limit=2**32-2**32%bound;counter=0
    while True:
        raw=int.from_bytes(hashlib.sha256(payload+struct.pack('>I',counter)).digest()[:4],'big')
        if raw<limit:break
        counter+=1
    bucket=raw%bound
    for row in rules['weighted_deltas']:
        if bucket<row['weight']:return row['value']
        bucket-=row['weight']
    raise AssertionError('无效权重桶')


def score(rules,player,profession,selected,override=None):
    if len(selected)!=3 or len(set(selected))!=3:raise ValueError('须确认三个不同词条')
    if profession not in ('alchemy','forging') or any(not isinstance(fate,str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+',fate) for fate in selected):raise ValueError('职业或词条 ID 无效')
    if override is not None:
        if type(override)is not int or not 0<=override<=crafting_quality.MAX_SCORE:raise ValueError('固定初始资质无效')
        return override
    return rules['base']+sum(delta(rules,player,profession,fate) for fate in sorted(selected))
