"""One entry point for equipment authoring, generation, checks and fitting previews."""
from __future__ import annotations

import argparse
import copy
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from .catalogue import ROOT, load


def interpreter():
    if all(importlib.util.find_spec(m) for m in ("numpy","PIL")):
        return
    target = os.environ.get("MC_PREVIEW_PYTHON", str(ROOT / ".venv-preview/bin/python"))
    if os.environ.get("MC_EQUIPMENT_REEXEC") or not Path(target).is_file():
        raise ValueError("需要 numpy/Pillow；使用 .venv-preview 或设置 MC_PREVIEW_PYTHON")
    os.execve(target,[target,"-m","tools.equipment",*sys.argv[1:]],dict(os.environ,MC_EQUIPMENT_REEXEC="1"))


def find(specs, value):
    value = value if ":" in value else "myvillage:" + value
    for spec in specs:
        if spec["id"] == value:
            return spec
    raise ValueError(f"未找到装备 {value}")


def scaffold(root, specs, source, item_id, name):
    if not re.fullmatch(r"[a-z][a-z0-9_]*",item_id):
        raise ValueError("新 id 只允许小写字母、数字和下划线")
    template = find(specs, source)
    if template["kind"] != "armor_set":
        raise ValueError("new-armor 必须从防具模板开始")
    destination = root / "equipment" / item_id
    if destination.exists() or any(s["id"] == "myvillage:"+item_id for s in specs):
        raise ValueError("目标已存在，不覆盖")
    spec = copy.deepcopy(template)
    spec.update(id="myvillage:"+item_id,name=name,enabled=False)
    suffixes={"head":("helmet","盔"),"chest":("chestplate","胸甲"),"legs":("leggings","护腿"),"feet":("boots","靴")}
    for item in spec["items"]:
        suffix,zh=suffixes[item["slot"]]
        item.update(id=f"myvillage:{item_id}_{suffix}",name=name+zh)
    destination.mkdir()
    (destination/"equipment.json").write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n')
    return destination


def main(argv=None):
    parser=argparse.ArgumentParser(description="装备制作：同源导出、检查与试衣间")
    parser.add_argument("--root",type=Path,default=ROOT)
    commands=parser.add_subparsers(dest="command",required=True)
    commands.add_parser("list",help="列出 authored 装备")
    commands.add_parser("build",help="生成启用装备的游戏资源")
    commands.add_parser("check",help="检查目录、引用与生成资源漂移")
    preview=commands.add_parser("preview",help="生成防具试衣间或既有武器预览")
    preview.add_argument("id");preview.add_argument("--out",type=Path,required=True)
    preview.add_argument("--vanilla-jar",type=Path)
    preview.add_argument("--weapon",help="试衣间配持的武器目录 id；默认第一件武器引用")
    new=commands.add_parser("new-armor",help="从模板创建未启用的防具草稿")
    new.add_argument("--from",dest="source",required=True);new.add_argument("--id",required=True);new.add_argument("--name",required=True)
    args=parser.parse_args(argv)
    try:
        specs,materials=load(args.root)
        if args.command=="list":
            for spec in specs: print(f"{spec['id']} · {spec['name']} · {spec['kind']} · {'启用' if spec.get('enabled',True) else '草稿'}")
            return 0
        if args.command=="new-armor":
            print(scaffold(args.root,specs,args.source,args.id,args.name));print("草稿 enabled=false；调整外观并预览后再启用。")
            return 0
        interpreter()
        if args.command in ("build","check"):
            from .export import write_or_check
            changed=write_or_check(args.root,check=args.command=="check")
            if args.command=="check":
                from .validate import validate_resources
                errors=validate_resources(args.root)
                for error in errors:print(error)
                for path in changed:print("生成资源不同："+path)
                if errors or changed:return 1
                print("装备目录、资源引用与生成结果一致")
            else:
                print(f"已更新 {len(changed)} 个资源")
            return 0
        spec=find(specs,args.id)
        if spec["kind"]=="weapon_reference":
            cmd=[sys.executable,"-m","tools.combat_preview","model",spec["model"],"--geometry",
                 str(args.root/"src/main/resources"/spec["geometry"]),"--root",str(args.root/"src/main/resources"),"--out",str(args.out)]
            if args.vanilla_jar:cmd += ["--vanilla-jar",str(args.vanilla_jar)]
            return subprocess.call(cmd,cwd=args.root)
        from .export import compile_armor
        from .preview import render_sheet
        from .web import write_preview
        model,pixels=compile_armor(spec,materials)
        args.out.mkdir(parents=True,exist_ok=True)
        render_sheet(model,pixels,args.out/"sheet.png",root=args.root,title=spec['name'])
        weapon=find(specs,args.weapon) if args.weapon else next((s for s in specs if s['kind']=='weapon_reference'),None)
        if weapon is not None and weapon['kind']!='weapon_reference':
            raise ValueError("--weapon 必须指定 weapon_reference")
        write_preview(spec,model,pixels,args.out/"index.html",root=args.root,weapon_spec=weapon)
        print(args.out/"index.html")
        return 0
    except (ValueError,KeyError,OSError) as error:
        print(f"装备工具错误：{error}",file=sys.stderr)
        return 1


if __name__=="__main__":
    raise SystemExit(main())
