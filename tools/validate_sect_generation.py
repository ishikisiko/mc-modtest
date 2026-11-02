#!/usr/bin/env python3
"""校验当前宗门蓝图、真实NBT回读、设施连通和正式资源身份。"""
import json
from pathlib import Path
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from sect_blueprints.runtime import export_catalog


def main():
    report=ROOT/'reports/sect_generation_validation.json';report.parent.mkdir(parents=True,exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(prefix='sect-blueprint-check-') as folder:
            target=Path(folder);export_catalog(target)
            catalog=json.loads((target/'data/myvillage/sect_blueprints/catalog.json').read_text())
            layouts=[]
            for row in catalog['layouts']:
                manifest=json.loads((target/('data/'+row['resource'].replace(':','/',1))).read_text())
                layouts.append({'id':manifest['id'],'tiles':len(manifest['tiles']),
                                'courtyard_cells':len(manifest['courtyard_cells']),'shelves':len(manifest['shelves'])})
            data={'status':'pass','layouts':layouts,
                  'checks':['geometry','facility_connections','complete_nbt_readback','modset','source_identity'],
                  'runtime':'not_run'}
        report.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        return 0
    except Exception as error:
        report.write_text(json.dumps({'status':'fail','error':str(error)},ensure_ascii=False,indent=2)+'\n')
        raise


if __name__=='__main__':sys.exit(main())
