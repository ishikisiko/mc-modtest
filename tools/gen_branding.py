"""从唯一名称输入同步展示名称、命令常量和共享语言词条。"""
from pathlib import Path
import argparse,copy,json,re,sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.language_assets import language_text,load_language

def outputs(root=ROOT,rename_namespace_from=None):
    data=json.loads((root/'tools/branding.json').read_text())
    if set(data)!={'display_name','namespace','command_root','command_short','description'}:raise ValueError('名称输入字段错误')
    if not all(isinstance(v,str) and v and '\n' not in v for v in data.values()):raise ValueError('名称须为非空单行文字')
    for key in ['namespace','command_root','command_short']:
        if not re.fullmatch('[a-z][a-z0-9_]{1,31}',data[key]):raise ValueError('命令入口须为小写拼音或英文')
    if data['command_root']==data['command_short']:raise ValueError('完整入口与快捷入口不能相同')
    out={};quote=lambda v:json.dumps(v,ensure_ascii=False)
    java='package com.example.myvillage;\n\n/** 由 tools/gen_branding.py 生成；编辑 tools/branding.json。 */\npublic final class ModBrand {\n'
    for key,field in [('display_name','DISPLAY_NAME'),('namespace','NAMESPACE'),('command_root','COMMAND_ROOT'),('command_short','COMMAND_SHORT')]:java+=f'    public static final String {field} = {quote(data[key])};\n'
    java+='    private ModBrand() {}\n}\n';out['src/main/java/com/example/myvillage/ModBrand.java']=java
    path='src/main/resources/META-INF/neoforge.mods.toml';text=(root/path).read_text()
    previous=re.search(r'^modId = "([a-z0-9_]+)"',text,re.M).group(1)
    text=re.sub(r'^modId = "'+re.escape(previous)+'"',lambda m:'modId = '+quote(data['namespace']),text,count=1,flags=re.M)
    text=text.replace('[[dependencies.'+previous+']]','[[dependencies.'+data['namespace']+']]')
    text,count=re.subn(r'^displayName = .*$',lambda m:'displayName = '+quote(data['display_name']),text,count=1,flags=re.M)
    if count!=1:raise ValueError('缺少模组显示名称')
    text,count=re.subn(r"description = '''.*?'''",lambda m:"description = '''\n"+data['description']+"\n'''",text,count=1,flags=re.S)
    if count!=1:raise ValueError('缺少模组简介')
    out[path]=text
    for locale in ['zh_cn','en_us']:
        path=f'src/main/resources/assets/{data["namespace"]}/lang/{locale}.json';original=load_language((root/path).read_text())
        prefixes={k.split('.')[1] for k in original if k.startswith('itemGroup.') and k.endswith('.main')}
        if rename_namespace_from:
            if not re.fullmatch('[a-z][a-z0-9_]{1,31}',rename_namespace_from) or rename_namespace_from==data['namespace']:raise ValueError('旧命名空间无效')
            prefixes.add(rename_namespace_from)
            # Jade 用下划线拼接 namespace；旧键与已生成的新键同值时移除旧键。
            for key in list(original):
                old='plugin_'+rename_namespace_from+'.';new='plugin_'+data['namespace']+'.'
                if old in key:
                    target=key.replace(old,new)
                    if target in original:
                        if original[key]!=original[target]:raise ValueError('语言命名空间变更存在冲突：'+key)
                        del original[key]
        rekeyed={}
        for key,value in original.items():
            for prefix in prefixes:
                key=key.replace('.'+prefix+'.','.'+data['namespace']+'.')
                key=key.replace('plugin_'+prefix+'.','plugin_'+data['namespace']+'.')
                if key.endswith('.'+prefix):key=key[:-len(prefix)]+data['namespace']
                value=value.replace(prefix+':',data['namespace']+':')
            if key in rekeyed:raise ValueError('命名空间重排产生重复语言键：'+key)
            rekeyed[key]=value
        original=rekeyed;updated=copy.deepcopy(original)
        for key in ['itemGroup.yuanxian.main','key.categories.yuanxian']:updated[key]=data['display_name']
        out[path]=language_text(original,updated)
    path='src/main/resources/pack.mcmeta';pack=json.loads((root/path).read_text());pack['pack']['description']=data['display_name']+'模组资源';out[path]=json.dumps(pack,ensure_ascii=False,indent=2)+'\n'
    path='gradle.properties';text=(root/path).read_text();out[path]=re.sub(r'^mod_id=.*$',lambda m:'mod_id='+data['namespace'],text,flags=re.M)
    path='settings.gradle';text=(root/path).read_text();out[path]=re.sub(r'^rootProject.name = .*$',lambda m:'rootProject.name = '+quote(data['namespace']),text,flags=re.M)
    return out

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--rename-namespace-from');args=parser.parse_args()
    bad=[]
    for name,text in outputs(rename_namespace_from=args.rename_namespace_from).items():
        path=ROOT/name
        if args.check:
            if not path.exists() or path.read_text()!=text:bad.append(name)
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
    if bad:raise SystemExit('名称资源未同步：'+', '.join(bad))
    print('名称资源检查通过' if args.check else '名称资源已生成')
if __name__=='__main__':main()
