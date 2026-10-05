"""Synthetic V3 parser fixture assembled from frozen lab rulers; never an acquisition receipt."""
import json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-baseline-research-20261006'
p=json.loads((O/'full-row-proof.json').read_text());cells=json.loads((O/'canonical-cells.json').read_text())
old=(R/'lab/out/v2-historical-product-20261006/snapshots/100644812901937152.snapshot').read_text()
section=['DICT_HISTORY_BEGIN|1']
for name,layout in p['layouts'].items():
    table=p['tables'][name][0] or '-1';section.append(f'DICT_LAYOUT|{name}|{table}|{len(layout)}')
    for internal,segment,col,type,required in layout:section.append(f'DICT_LAYOUT_COLUMN|{name}|{segment}|{internal}|{col}|{type}|{required}')
for line in (R/'lab/out/dictionary-index-pair-20261006/independent-index-address.stdout.txt').read_text().splitlines():
    if line.startswith('INDEX_SCOPE|'):
        _,logical,data,name,file,block=line.split('|');section.append(f'DICT_INDEX|BEFORE|{name}|{logical}|{data}|{int(file)*4194304+int(block)}')
assert sum(x.startswith('DICT_INDEX|') for x in section)==3
for name,target,data,file,block,slot in p['rows']:
    selected=[c for c in cells if c[:3]==[name,target,slot]]
    assert len(selected)==len(p['layouts'][name])
    section.append('|'.join(['DICT_ROW',name,target,data,file,block,slot,*[c[-1].upper() for c in selected]]))
section.append('DICT_HISTORY_END|6')
text=old.replace('ORK_HEAP_SNAPSHOT_V2','ORK_HEAP_SNAPSHOT_V3').replace('ROW|75185|', '\n'.join(section)+'\nROW|75185|')
after='\n'.join(s.replace('|BEFORE|','|AFTER|') for s in section if s.startswith('DICT_INDEX|'))
text=text.replace('SOURCE_AFTER|',after+'\nSOURCE_AFTER|')
destination=R/'src/test/resources/dictionary-baseline-lab-v3.txt';destination.parent.mkdir(exist_ok=True);assert not destination.exists();destination.write_text(text)
(O/'executed-build-test-fixture.py').write_bytes(Path(__file__).read_bytes())
print('Synthetic V3 unit fixture generated; not trusted old-history provenance.')
