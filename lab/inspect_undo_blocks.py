"""Advisory native redo vs later block-header snapshot. Never a runtime/event decoder."""
import json,re,sys
from pathlib import Path

redo=Path(sys.argv[1]).read_text(encoding='utf-8')
blocks=Path(sys.argv[2]).read_text(encoding='utf-8')
xid='0x0001.001.0000025c'
snapshots={}
for section in re.split(r'(?=(?:  )?buffer tsn:)',blocks)[1:]:
    dba=re.search(r'rdba: 0x([0-9a-f]+)',section)
    header=re.search(r'UNDO BLK:\s*xid: (0x[0-9a-f.]+)\s+seq: 0x([0-9a-f]+)\s+cnt: 0x([0-9a-f]+)\s+irb: 0x([0-9a-f]+)',section)
    if not dba or not header: continue
    if header[1]!=xid:
        raise RuntimeError('selected snapshot full XID changed: DBA=0x'+dba[1]+' expected='+xid+' actual='+header[1])
    value=tuple(int(header[i],16) for i in (2,3,4))
    key=int(dba[1],16)
    if key in snapshots and snapshots[key]!=value: raise RuntimeError('cache/disk snapshot header differs')
    snapshots[key]=value
nodes={}
for record in re.split(r'(?=REDO RECORD - Thread:)',redo):
    if 'CON_UID: 677737149' not in record.split('\n')[0]: continue
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',change)
        if not h or not re.search(r'xid:\s*'+re.escape(xid)+r'\b',change): continue
        seq=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
        link=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',change)
        if not seq or not link: raise RuntimeError('missing native node fields')
        uba=(int(h[1],16),int(seq[1],16),int(seq[2],16))
        if uba in nodes: raise RuntimeError('duplicate native node')
        previous=int(link[1],16);rci=int(link[2])
        if 'Begin trans' in change: predecessor=None
        elif previous:
            if rci!=0 or previous not in snapshots: raise RuntimeError('cross-block header unavailable')
            s,cnt,irb=snapshots[previous]
            predecessor=(previous,s,cnt)
        else: predecessor=(uba[0],uba[1],rci)
        nodes[uba]=predecessor
references={p for p in nodes.values() if p is not None}
if not references.issubset(nodes): raise RuntimeError('snapshot-derived original chain has a hole')
tails=set(nodes)-references
if len(tails)!=1: raise RuntimeError('snapshot-derived tail ambiguous')
at=next(iter(tails));chain=[]
while at is not None:
    if at in chain: raise RuntimeError('snapshot-derived cycle')
    chain.append(at);at=nodes[at]
if len(chain)!=120 or len(chain)!=len(nodes) or len(snapshots)!=16: raise RuntimeError('snapshot coverage mismatch')
print(json.dumps({'full_xid':xid,'native_original_nodes':len(nodes),'later_block_snapshots':len(snapshots),
                 'cnt_irb_differences':[{'dba':hex(d),'sequence':s,'cnt':c,'irb':i} for d,(s,c,i) in snapshots.items() if c!=i],
                 'snapshot_derived_original_chain_tail_to_begin':['%08x.%04x.%02x'%a for a in chain],
                 'scope':'native-only exploratory proof with later datafile block snapshots; raw-redo-only cross-block acceptance remains unproven; cdc_events=0'},indent=2))
