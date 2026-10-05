"""Safe owned-lab sparse OBJ$ pair vectors, for decoder regression only."""
import json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-history-research-20261006';lines=[]
for r in json.loads((O/'obj-row-shapes.json').read_text()):
    lines.append('RECORD|'+'|'.join(map(str,[*r['position'],r['scn'],r['xid']])))
    for v in [r['undo'],*r['forward']]:lines.append('|'.join(['VECTOR',v['opcode'],str(v['dba']),*v['fields']]))
p=R/'src/test/resources/dictionary-object-row-profiles.txt';assert not p.exists();p.write_text('\n'.join(lines)+'\n');(O/'executed-build-obj-fixture.py').write_bytes(Path(__file__).read_bytes())
