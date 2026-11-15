"""核对源仙资源命名空间与客户端模型引用，支持源目录或最终JAR。"""
from pathlib import Path
import argparse,base64,gzip,json,re,sys,tempfile,zipfile
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.runtime_resources.read import DirectoryResources
OLD='myvillage';NEW='yuanxian'
LEGACY=[b'myvillage:',b'assets/myvillage/',b'data/myvillage/',b'myvillage_packed',b'subtitles.myvillage.',b'plugin_myvillage.']

def inspect(root):
 reader=DirectoryResources(root);names={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() and not p.name.endswith('.class')};names.update(reader.entries)
 errors=[];counts={'resources':len(names),'json':0,'nbt':0,'models':0,'encoded_payloads':0};seen=set()
 def fail(name,message):
  if (name,message) not in seen:errors.append({'resource':name,'error':message});seen.add((name,message))
 def old_bytes(name,raw):
  if any(token in raw for token in LEGACY):fail(name,'仍含旧资源ID或资源路径')
 def nested(name,obj,depth=0):
  if depth>32:raise ValueError('JSON递归深度超过32')
  if isinstance(obj,dict):
   for key,value in obj.items():nested(name,value,depth+1)
  elif isinstance(obj,list):
   for value in obj:nested(name,value,depth+1)
  elif isinstance(obj,str):
   if not obj.startswith(('com.example.myvillage','com/example/myvillage')) and re.search(r'(^|[.:/_-])myvillage([.:/_-]|$)',obj):fail(name,'仍含旧资源ID或资源路径')
   if len(obj)<=40:return
   encoded=obj[5:] if obj.startswith('gzip:') else obj
   try:raw=base64.b64decode(encoded,validate=True)
   except (ValueError,TypeError):return
   if not raw.startswith(b'\x1f\x8b'):return
   raw=gzip.decompress(raw);counts['encoded_payloads']+=1;old_bytes(name,raw)
   if raw[:1] in (b'{',b'['):nested(name,json.loads(raw),depth+1)
 def require_asset(name,ref,kind):
  if not isinstance(ref,str) or not ref.startswith(NEW+':'):return
  path=ref.split(':',1)[1]
  if kind=='texture':target='assets/'+NEW+'/textures/'+path+('' if path.endswith('.png') else '.png')
  elif kind=='model':target='assets/'+NEW+'/'+path if path.endswith(('.obj','.json')) else 'assets/'+NEW+'/models/'+path+'.json'
  else:target='assets/'+NEW+'/'+path
  if target not in names:fail(name,'缺少'+kind+'引用：'+ref+' → '+target)
 def asset_model_refs(name,obj):
  if isinstance(obj,dict):
   for key,value in obj.items():
    if key=='parent':require_asset(name,value,'model')
    elif key=='textures' and isinstance(value,dict):
     for ref in value.values():require_asset(name,ref,'texture')
    elif key in ('model','surface_mesh','resource_ref'):require_asset(name,value,'model' if key=='model' else 'asset')
    asset_model_refs(name,value)
  elif isinstance(obj,list):
   for value in obj:asset_model_refs(name,value)
 for name in sorted(names):
  if OLD in name:fail(name,'资源路径保留旧命名空间')
  physical=root/name
  raw=physical.read_bytes() if physical.is_file() else reader.read(name)
  if name.endswith(('.png','.ttf','.ogg','.class')):continue
  if name.endswith(('.nbt','.gz')):
   try:raw=gzip.decompress(raw)
   except OSError as e:fail(name,'压缩数据无效：'+str(e));continue
  old_bytes(name,raw)
  if name.endswith('.nbt'):counts['nbt']+=1
  if name.endswith('.json') or name.endswith('.json.gz'):
   try:obj=json.loads(raw)
   except (ValueError,UnicodeDecodeError) as e:fail(name,'JSON无效：'+str(e));continue
   counts['json']+=1;nested(name,obj)
   if name=='META-INF/enumextensions.json' and isinstance(obj,dict):
    for entry in obj.get('entries',[]):
     if not isinstance(entry.get('name'),str) or not entry['name'].startswith(NEW.upper()+'_'):
      fail(name,'枚举扩展名称必须使用当前mod ID前缀：'+str(entry.get('name')))
   if '/lang/' in name and isinstance(obj,dict):
    if any('.'+OLD+'.' in key or key.endswith('.'+OLD) or 'plugin_'+OLD+'.' in key for key in obj):fail(name,'翻译键保留旧命名空间')
   if name=='assets/'+NEW+'/sounds.json' and isinstance(obj,dict):
    locales=['zh_cn']+(['en_us'] if 'assets/'+NEW+'/lang/en_us.json' in names else [])
    for locale in locales:
     language_name='assets/'+NEW+'/lang/'+locale+'.json'
     language=json.loads(reader.read(language_name)) if language_name in names else {}
     for event,entry in obj.items():
      if isinstance(entry,dict) and isinstance(entry.get('subtitle'),str) and entry['subtitle'] not in language:fail(name,'缺少字幕翻译：'+locale+'/'+event+'/'+entry['subtitle'])
   if name.startswith('assets/'+NEW+'/') and any(part in name for part in ['/models/','/blockstates/']):counts['models']+=1;asset_model_refs(name,obj)
 return {'status':'passed' if not errors else 'failed','counts':counts,'errors':errors}

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT/'src/main/resources');parser.add_argument('--jar',type=Path);parser.add_argument('--out',type=Path);args=parser.parse_args()
 if args.jar:
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder)
   with zipfile.ZipFile(args.jar) as jar:
    for name in jar.namelist():
     if not name.endswith(('/','.class')):jar.extract(name,root)
   report=inspect(root)
 else:report=inspect(args.root)
 if args.out:args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'status':report['status'],'counts':report['counts'],'errors':len(report['errors']),'examples':report['errors'][:12]},ensure_ascii=False));raise SystemExit(0 if report['status']=='passed' else 1)
if __name__=='__main__':main()
