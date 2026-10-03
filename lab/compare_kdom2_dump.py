"""Native dump multiline column bytes vs raw Java physical patches; no event acceptance."""
import json
import re
import sys
from pathlib import Path

dump=Path(sys.argv[1]).read_text(encoding='utf-8')
rows=[s.split('|') for s in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
actual={tuple(map(int,r[:3])):r for r in rows}
expected={}
counts={'FORWARD':0,'INVERSE':0,'XR':0,'cross_block_headers':0}
undo_blocks=set()
long_xids=set()
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba: continue
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+)\.(\d+)\b',change)
        if not h: continue
        if h[3]=='11' and h[4] in ('2','5') and int(h[2],16)==0x03000095:
            side='FORWARD'
        elif h[3]=='5' and h[4]=='1' and re.search(r'objd:\s*73043\b',change):
            side='INVERSE'
            if 'KDO Op code: URP' in change:
                undo_blocks.add(h[2]);long_xids.add(re.search(r'xid:\s*(0x[0-9a-f.]+)',change)[1])
                previous=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',change)
                if int(previous[1],16):
                    if int(previous[2])!=0: raise RuntimeError('unexpected native cross-block rci')
                    counts['cross_block_headers']+=1
        else: continue
        kind=re.search(r'KDO Op code: (IRP|URP|DRP)',change)[1]
        slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',change)[1])
        dba=int(re.search(r'bdba: 0x([0-9a-f]+)',change)[1],16)
        xtype=2 if re.search(r'xtype: XR',change) else 1
        if side=='FORWARD' and xtype==2: counts['XR']+=1
        values={}
        lines=change.splitlines()
        for i,line in enumerate(lines):
            col=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]',line)
            if not col: continue
            payload=[]
            suffix=line[col.end():].strip()
            if suffix: payload.extend(suffix.split())
            for following in lines[i+1:]:
                if not re.fullmatch(r'\s*(?:[0-9a-fA-F]{2}\s*)+',following): break
                payload.extend(following.split())
            if len(payload)!=int(col[2]): raise RuntimeError('native column length mismatch')
            values[int(col[1])]='VALUE:'+''.join(payload).lower()
        for col in re.finditer(r'col\s+(\d+):\s*\*NULL\*',change): values[int(col[1])]='NULL:'
        key=(int(rba[1],16),int(rba[2],16),int(h[1]))
        row=list(map(str,[*key,side,{'IRP':'INSERT','URP':'UPDATE','DRP':'DELETE'}[kind],dba,slot,xtype,','.join(values.get(i,'ABSENT:') for i in range(2))]))
        if key in expected: raise RuntimeError('native duplicate position')
        expected[key]=row;counts[side]+=1
errors=[{'position':key,'reason':'PATCH_DIFF','expected':expected.get(key),'actual':actual.get(key)} for key in actual.keys()|expected.keys() if actual.get(key)!=expected.get(key)]
if len(actual)!=len(rows) or counts['FORWARD']!=181 or counts['INVERSE']!=121 or counts['XR']!=60 or counts['cross_block_headers']==0:
    errors.append({'reason':'COVERAGE_MISMATCH'})
print(json.dumps({'counts':counts,'undo_blocks':sorted(undo_blocks),'long_xids':sorted(long_xids),'mismatches':len(errors),'errors':errors,
                  'scope':'physical columns only; no complete cross-block chain or surviving transaction effects acceptance'},indent=2))
sys.exit(bool(errors))
