"""Independent native metadata/inverse-byte matching, not generic cross-block graph acceptance."""
import json,re,sys
from pathlib import Path
records=re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[1]).read_text(encoding='utf-8'))
actual=[s.split('|') for s in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
object_id=int(sys.argv[3]);x=tuple(map(int,sys.argv[4].split('.')))
originals={};forward={};segment=None
def value(change):
    lines=change.splitlines()
    for i,line in enumerate(lines):
        m=re.search(r'col\s+1:\s*\[\s*(\d+)\]',line)
        if not m:continue
        data=line[m.end():].split()
        for following in lines[i+1:]:
            if not re.fullmatch(r'\s*(?:[0-9a-fA-F]{2}\s*)+',following):break
            data.extend(following.split())
        if len(data)!=int(m[1]) or len(data)!=900:raise RuntimeError('native restored column length mismatch')
        return ''.join(data).lower()
    raise RuntimeError('native changed column missing')
def position(change):return (int(re.search(r'bdba: 0x([0-9a-f]+)',change)[1],16),int(re.search(r'tabn:\s*0 slot:\s*(\d+)',change)[1]))
def metadata(change):
    m=re.search(r'ktub[lu] redo: slt:\s*(\d+) wrp:\s*(\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc: 11\.1 \[objn:\s*(\d+) objd:\s*(\d+) tsn:\s*(\d+)',change)
    if not m:raise RuntimeError('native metadata missing')
    return (int(m[1]),int(m[2]),int(m[4],16),int(m[5]),int(m[6]),int(m[7]),int(m[8]))
for record in records:
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    for change in changes:
        h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',change)
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        if not h or not xid or tuple(int(xid[i],16) for i in (1,2,3))!=x:continue
        b=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
        uba=tuple([int(h[1],16),int(b[1],16),int(b[2],16)])
        originals[uba]=(metadata(change),position(change),value(change),'Begin trans' in change)
        if 'Begin trans' in change:
            starts=[int(m[1],16) for c in changes if (m:=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',c)) and re.search(r'ktudh redo: slt: 0x'+format(x[1],'04x')+r' sqn: 0x'+format(x[2],'08x'),c)]
            if len(starts)!=1 or segment is not None:raise RuntimeError('native active segment ambiguity')
            segment=starts[0]
for record in records:
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        if not re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*OP:11\.5\b',change) or not re.search(r'xtype: XA',change):continue
        u=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        if u:
            uba=tuple(int(u[i],16) for i in (1,2,3))
            if uba in originals:forward[uba]=(position(change),value(change))
expected=[];cancelled=set();cross=0
for record in records:
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba:continue
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    for change in changes:
        h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.(6|11)\b',change)
        if not h or not re.search(r'objd:\s*'+str(object_id)+r'\b',change):continue
        meta=metadata(change);rows=[c for c in changes if re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*OP:11\.5\b',c) and re.search(r'xtype: XR',c)]
        if len(rows)!=1:raise RuntimeError('native XR count')
        candidates=[u for u,(m,pos,before,begin) in originals.items() if not begin and m==meta and pos==position(rows[0]) and before==value(rows[0]) and (int(h[1],16)==segment if h[2]=='11' else int(h[1],16)==u[0])]
        if len(candidates)!=1:raise RuntimeError('native compensation candidate count')
        uba=candidates[0]
        if uba in cancelled:raise RuntimeError('native duplicate cancellation')
        cancelled.add(uba);cross+=h[2]=='11'
        expected.append((int(rba[1],16),int(rba[2],16),':'.join(map(str,uba))))
actual_cancel=[(int(r[1]),int(r[2]),r[3]) for r in actual if r[0]=='CANCEL']
errors=[]
if actual_cancel!=expected:errors.append({'reason':'CANCELLATION_ASSOCIATION_DIFF'})
retained={':'.join(map(str,u)):forward[u] for u in originals if u not in cancelled}
seen=set()
for row in actual:
    if row[0]!='RETAIN':continue
    seen.add(row[1]);expected_patch=retained.get(row[1]);actual_patch=((int(row[3]),int(row[4])),row[5].split(',')[1].split(':')[1])
    if actual_patch!=expected_patch:errors.append({'reason':'RETAINED_PHYSICAL_PATCH_DIFF','uba':row[1]})
if len(originals)!=120 or len(forward)!=120 or len(cancelled)!=60 or len(retained)!=60 or seen!=set(retained) or len(actual)!=120:errors.append({'reason':'COVERAGE_DIFF'})
print(json.dumps({'original_heap_pairs':len(originals),'cancelled_heap_patches':len(cancelled),'cross_block_compensations':cross,
                  'retained_heap_patches':len(retained),'mismatches':len(errors),'errors':errors,
                  'scope':'bounded metadata and exact restored/retained column bytes; full cross-block predecessor graph, index and recovery unaccepted'},indent=2))
sys.exit(bool(errors))
