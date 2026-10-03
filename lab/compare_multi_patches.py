"""Native dump multiline column bytes vs raw Java physical patches; no event acceptance."""
import json
import re
import sys
from pathlib import Path

object_id=int(sys.argv[4])
inserts=int(sys.argv[5])
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
        if h[3]=='11' and h[4] in ('2','5') and re.search(r'OBJ:'+str(object_id)+r'\b',change):
            side='FORWARD'
        elif h[3]=='5' and h[4]=='1' and re.search(r'objd:\s*'+str(object_id)+r'\b',change):
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
if len(actual)!=len(rows) or counts['FORWARD']!=180+inserts or counts['INVERSE']!=120+inserts or counts['XR']!=60 or counts['cross_block_headers']==0:
    errors.append({'reason':'COVERAGE_MISMATCH'})
truth_match=True
truth=json.loads(Path(sys.argv[3]).read_text(encoding='utf-8'))
source={r['id']:r for r in truth if 'id' in r}
locations={}
for row in rows:
    if row[3]=='FORWARD' and row[4]=='INSERT':
        encoded=row[8].split(',')[0]
        matches=[i for i in source if encoded=='VALUE:c1'+format(i+1,'02x')]
        if len(matches)!=1: raise RuntimeError('bounded fixture NUMBER key does not match SQL IDs')
        locations[(row[5],row[6])]=matches[0]
last_xr={}
for row in rows:
    if row[3]=='FORWARD' and row[7]=='2':last_xr[(row[5],row[6])]=row
matched=[]
for location,row in last_xr.items():
    row_id=locations[location]
    final=bytes.fromhex(row[8].split(',')[1].split(':')[1])
    truth_match=truth_match and source[row_id]['txt_length']==900 and final==source[row_id]['txt'].encode('utf-8')
    matched.append(row_id)
if len(matched)!=2 or set(matched)!=(set([1,2]) if inserts==2 else set([1,20])):truth_match=False
if not truth_match:errors.append({'reason':'FINAL_XR_SQL_VALUE_DIFF'})
print(json.dumps({'counts':counts,'undo_blocks':sorted(undo_blocks),'long_xids':sorted(long_xids),'mismatches':len(errors),'errors':errors,
                  'last_xr_matches_sql_final_values':truth_match,'matched_source_ids':matched,
                  'scope':'physical columns only; no complete cross-block chain or surviving transaction effects acceptance'},indent=2))
sys.exit(bool(errors))
