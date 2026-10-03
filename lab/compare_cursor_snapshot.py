"""Bounded later-page CNT corroboration, not a raw-redo production decoder or time-consistency proof."""
import json,re,sys
from pathlib import Path

redo=Path(sys.argv[1]).read_text(encoding='utf-8')
snapshot=Path(sys.argv[2]).read_text(encoding='utf-8')
rows=[r.split('|') for r in Path(sys.argv[3]).read_text(encoding='utf-8-sig').splitlines() if r.startswith('NODE|')]
actual={tuple(map(int,r[1:4])):r for r in rows}
xid='0x0001.001.0000025c'
pages={}
for section in re.split(r'(?=(?:  )?buffer tsn:)',snapshot)[1:]:
    d=re.search(r'rdba: 0x([0-9a-f]+)',section)
    h=re.search(r'UNDO BLK:\s*xid: (0x[0-9a-f.]+)\s+seq: 0x([0-9a-f]+)\s+cnt: 0x([0-9a-f]+)\s+irb: 0x([0-9a-f]+)',section)
    if not d or not h:continue
    records={int(m[1],16):{'slot':int(m[2],16),'object':int(m[3])} for m in re.finditer(r'\* Rec #0x([0-9a-f]+)\s+slt: 0x([0-9a-f]+)\s+objn: (\d+)',section)}
    value={'xid':h[1],'sequence':int(h[2],16),'cnt':int(h[3],16),'irb':int(h[4],16),'records':records}
    dba=int(d[1],16)
    if dba in pages and pages[dba]!=value:raise RuntimeError('cache/disk page observations differ')
    pages[dba]=value
nodes={};cross=[]
for record in re.split(r'(?=REDO RECORD - Thread:)',redo):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',change)
        if not h or not re.search(r'xid:\s*'+re.escape(xid)+r'\b',change):continue
        b=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
        link=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',change)
        uba=(int(h[2],16),int(b[1],16),int(b[2],16))
        key=(int(rba[1],16),int(rba[2],16),int(h[1]))
        if uba in nodes:raise RuntimeError('duplicate native original UBA')
        nodes[uba]=key
        if int(link[1],16):
            if int(link[2])!=0:raise RuntimeError('unsupported cross rci')
            cross.append((key,int(link[1],16)))
errors=[];compared=[];referenced=set()
for key,dba in cross:
    p=pages.get(dba);referenced.add(dba)
    if p is None or p['xid']!=xid:raise RuntimeError('referenced predecessor page identity unavailable')
    predecessor=(dba,p['sequence'],p['cnt'])
    if predecessor not in nodes or p['records'].get(p['cnt'])!={'slot':1,'object':73043}:raise RuntimeError('page CNT does not name observed target undo')
    expected=':'.join(map(str,predecessor));row=actual.get(key)
    if row is None or row[5]!=expected:errors.append({'position':key,'reason':'SNAPSHOT_CNT_PREDECESSOR_DIFF','expected':expected,'actual':None if row is None else row[5]})
    compared.append({'position':key,'predecessor':expected,'snapshot_irb':p['irb'],'cnt_irb_differ':p['cnt']!=p['irb']})
if len(nodes)!=120 or len(cross)!=15 or len(pages)!=16:raise RuntimeError('bounded fixture coverage differs')
print(json.dumps({'native_original_nodes':len(nodes),'cross_edges_compared':len(cross),'mismatches':len(errors),'errors':errors,'edges':compared,
                  'unreferenced_snapshot_pages':[{'dba':dba,'header_xid':p['xid'],'sequence':p['sequence'],'cnt':p['cnt']} for dba,p in pages.items() if dba not in referenced],
                  'scope':'later predecessor-page sequence/CNT observations corroborate this fixture and reject selector-hole candidate; archive-time consistency and raw-only derivation not certified; cdc_events=0'},indent=2))
sys.exit(bool(errors))
