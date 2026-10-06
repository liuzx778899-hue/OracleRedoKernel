"""Stream selected native dump records for the six discovery refusals; never produce events."""
import json,re
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006'
assert not (H/'native-discovery-refusals.json').exists()
(H/'executed-native-discovery-driver.py').write_bytes(Path(__file__).read_bytes())
selected={tuple(map(int,line.split('|')[1].split(':')[:3])):line for line in (H/'discovery-research.stdout.txt').read_text().splitlines() if line.startswith('DISCOVERY|') and '|REFUSED:' in line}
assert len(selected)==6;found={};current=None;buffer=[]
def save():
    if current in selected:
        path=H/('native-discovery-'+('-'.join(map(str,current)))+'.txt');path.write_text(''.join(buffer))
        found[current]=path.name
with (H/'dump.trc').open(errors='replace') as stream:
    for line in stream:
        match=re.match(r'REDO RECORD - Thread:1 RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
        if match:
            save();current=tuple(int(x,16) for x in match.groups());buffer=[]
        if current in selected:buffer.append(line)
save();assert set(found)==set(selected)
out=dict(refusals=6,records=[dict(position=list(key),diagnostic=selected[key],native=found[key]) for key in selected],events=0,diagnostic_only=True)
(H/'native-discovery-refusals.json').write_text(json.dumps(out,indent=2));print(json.dumps(dict(records=len(found),events=0)))
