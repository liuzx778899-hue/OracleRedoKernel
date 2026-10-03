"""Independent native rci/ktucf original-chain audit; not surviving event semantics."""
import json
import re
import sys
from pathlib import Path

actual=Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines()
graphs={}
active={}
expected=[]
heap=index=referenced_tails=0
for dump_path in sys.argv[2:]:
    dump=Path(dump_path).read_text(encoding='utf-8')
    for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
        if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]: continue
        scn=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
        changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
        for undo in changes:
            if not re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*OP:5\.1\b',undo) or not re.search(r'objd:\s*73024\b',undo) or 'Begin trans' not in undo: continue
            xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',undo)
            x=tuple(int(xid[i],16) for i in (1,2,3))
            assignments=[]
            for change in changes:
                h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',change)
                s=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',change)
                if h and s and tuple(int(s[i],16) for i in (1,2))==x[1:]: assignments.append(int(h[1],16))
            if len(assignments)!=1 or x in graphs: raise RuntimeError('native chain start ambiguity')
            active[(assignments[0],x[1],x[2])]=x; graphs[x]={}
        for change in changes:
            h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',change)
            xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
            if not h or not xid: continue
            x=tuple(int(xid[i],16) for i in (1,2,3))
            if x not in graphs: continue
            rec=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
            link=re.search(r'ktub[lu] redo: slt:\s*\d+ wrp:\s*\d+ flg: 0x[0-9a-f]+ prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc:\s*(\d+)\.(\d+) \[objn:\s*\d+ objd:\s*(\d+)',change)
            if not rec or not link or int(link[1],16)!=0: raise RuntimeError('native link missing or cross-block unproven')
            uba=(int(h[1],16),int(rec[1],16),int(rec[2],16))
            predecessor=None if 'Begin trans' in change else (uba[0],uba[1],int(link[2]))
            if uba in graphs[x]: raise RuntimeError('native duplicate UBA')
            graphs[x][uba]=(predecessor,int(link[3]),int(link[4]),int(link[5]))
            heap+=int(link[3])==11; index+=int(link[3])==10
        for change in changes:
            h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.4\b',change)
            end=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) srt: 0 sta: 9 flg: 0x([0-9a-f]+)',change)
            if not h or not end: continue
            key=(int(h[1],16),int(end[1],16),int(end[2],16))
            if key not in active: continue
            x=active.pop(key); graph=graphs.pop(x)
            referenced={n[0] for n in graph.values() if n[0] is not None}
            if not referenced.issubset(graph): raise RuntimeError('native chain hole')
            tails=set(graph)-referenced
            if len(tails)!=1: raise RuntimeError('native tail ambiguity')
            tail=next(iter(tails))
            native=re.search(r'ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
            if native and tail!=tuple(int(native[i],16) for i in (1,2,3)): raise RuntimeError('native tail reference mismatch')
            path=[]; seen=set(); at=tail
            while at is not None:
                if at in seen: raise RuntimeError('native chain cycle')
                seen.add(at); n=graph[at]; path.append(':'.join(map(str,[*at,*n[1:]]))); at=n[0]
            if len(seen)!=len(graph): raise RuntimeError('native disconnected chain')
            expected.append('|'.join(map(str,[*x,scn,str(int(end[3],16)==4).lower(),str(native is not None).lower(),len(path),','.join(path)])))
            referenced_tails+=native is not None
errors=[]
if actual!=expected or len(expected)!=11 or active or graphs or heap!=18 or index!=14 or referenced_tails!=9:
    errors.append({'reason':'CHAIN_DIFF_OR_COVERAGE','expected':expected,'actual':actual,'active':len(active),'graphs':len(graphs)})
print(json.dumps({'chains_compared':len(expected),'heap_nodes':heap,'index_nodes':index,'native_commit_tails':referenced_tails,
                  'mismatches':len(errors),'errors':errors,'scope':'original same-block chains including compensated nodes; no index effects, cross-block or recovery acceptance'},indent=2))
sys.exit(bool(errors))
