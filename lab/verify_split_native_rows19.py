"""Independent native-text SYS replay from the already independently checked early V3; no parser event inputs."""
import copy,hashlib,json,re,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006';N=R/'lab/out/split-history-candidate-20261007';V=N/'validation'
assert not (V/'native-full-row-proof.json').exists();attempt=sys.argv[1] if len(sys.argv)>1 else 'r1';frozen=V/('executed-native-row-'+attempt+'.py');assert not frozen.exists();frozen.write_bytes(Path(__file__).read_bytes())
assert json.loads((R/'lab/out/v3-product-runtime-20261006/baseline-truth-proof.json').read_text())['differences']==0
baseline={};sizes={}
for line in (H/'actual-product-v3.snapshot').read_text().splitlines():
    q=line.split('|')
    if q[0]=='DICT_ROW':baseline[(q[1],int(q[2]),int(q[4])*4194304+int(q[5]),int(q[6]))]=[v.upper() for v in q[7:]]
    elif q[0]=='DICT_LAYOUT_COLUMN':sizes[q[1]]=sizes.get(q[1],0)+1
assert sum(map(len,baseline.values()))==230
expected={};nodes={};identities={};commit_scns=[]
for line in (N/'r6/archive-history.stdout.txt').read_text().splitlines():
    q=line.split('|')
    if q[0]=='COMMIT':commit_scns.append(int(q[1]))
    elif q[0]=='IDENTITY':identities[tuple(map(int,q[2:5]))]=int(q[1])
    elif q[0]=='NODE':nodes.setdefault(int(q[1]),set()).add(tuple(map(int,q[2:6])))
    elif q[0]=='ROW':expected.setdefault(int(q[1]),{})[(q[2],int(q[3]),int(q[4]),int(q[5]))]=[v.upper() for v in q[6:]]
originals={scn:[] for scn in commit_scns};native_nodes={scn:set() for scn in commit_scns}
def consume(text):
    m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',text)
    if not m or 'CON_UID: 677737149' not in text.splitlines()[0]:return
    pos=tuple(int(x,16) for x in m.groups());parts=re.split(r'(?=CHANGE #\d+)',text)[1:]
    for c in parts:
        if not re.search(r'^CHANGE #\d+ CON_ID:3 .* OP:5\.1 ',c):continue
        m=re.search(r'ktudb redo:.*\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
        if not m:continue
        xid=tuple(int(x,16) for x in m.groups())
        if xid not in identities:continue
        scn=identities[xid];vector=int(re.match(r'CHANGE #(\d+)',c)[1]);native_nodes[scn].add((*pos,vector))
        originals[scn].append((pos,c,parts))
lines=[]
with (H/'dump.trc').open(errors='replace') as f:
    for line in f:
        if line.startswith('REDO RECORD - Thread:'):
            if lines:consume(''.join(lines))
            lines=[]
        lines.append(line)
if lines:consume(''.join(lines))
assert native_nodes==nodes
def cells(c):
    result={}
    for m in re.finditer(r'col\s+(\d+):\s*(?:\*NULL\*|\[\s*(\d+)\]\s+((?:[0-9a-f]{2}\s*)+))',c):
        at=int(m[1]);assert at not in result
        if m[2] is None:value='NULL'
        else:
            value=bytes.fromhex(m[3]).hex().upper();assert len(value)==int(m[2])*2
        result[at]=value
    return result
def raw_after(label,c):
    m=re.search(label+r': \((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)',c);assert m,(label,c[:100])
    raw=bytes.fromhex(m[2]);assert len(raw)==int(m[1]);return raw
state=copy.deepcopy(baseline);proof=[]
for scn in commit_scns:
    parents={}
    for pos,undo,parts in originals[scn]:
        logical=int(re.search(r'\[objn: (\d+)',undo)[1])
        if logical!=48:continue
        for c in parts:
            if 'OP:10.2 ' not in c.splitlines()[0]:continue
            key=raw_after('insert key',c);data=raw_after('keydata',c);assert len(data)==6
            parent=key[1:1+key[0]].hex().upper();dba=int.from_bytes(data[:4],'big');slot=int.from_bytes(data[4:],'big')
            targets={k[1] for k,v in baseline.items() if v[0]==parent};assert len(targets)==1
            parents[(dba,slot)]=(next(iter(targets)),parent)
    for pos,undo,parts in originals[scn]:
        logical=int(re.search(r'\[objn: (\d+)',undo)[1]);role={18:'OBJ$',4:'TAB$',21:'COL$'}.get(logical)
        if role is None:
            assert logical in [0,48,49,50];continue
        forward=next(c for c in parts if re.search(r'OP:11\.[2345] ',c.splitlines()[0]));op=int(re.search(r'OP:11\.(\d+)',forward.splitlines()[0])[1])
        m=re.search(r'bdba: 0x([0-9a-f]+)',forward);dba=int(m[1],16);slot=int(re.search(r'tabn: \d+ slot: (\d+)',forward)[1])
        prior=cells(undo);after=cells(forward);offset=0 if role=='OBJ$' else 1
        keys=[k for k in state if k[0]==role and k[2:]==(dba,slot)]
        if op==2:
            assert not keys and role=='COL$' and set(after)==set(range(32))
            target,parent=parents[(dba,slot)];values=[parent]+[after[i] for i in range(32)]+['NULL']*4
            assert len(values)==sizes[role];state[(role,target,dba,slot)]=values
        else:
            assert len(keys)==1;key=keys[0];values=state[key]
            if op==4:assert not prior and not after;continue
            for i,value in prior.items():assert values[i+offset]==value,(pos,i,values[i+offset],value)
            if op==3:
                assert role=='COL$' and set(prior)==set(range(32)) and values[33:]==['NULL']*4;del state[key]
            else:
                assert op==5 and set(prior)==set(after)
                for i,value in after.items():values[i+offset]=value
    assert state==expected[scn],scn
    proof.append(dict(commit_scn=scn,native_originals=len(originals[scn]),full_values=sum(map(len,state.values())),differences=0))
negative=[]
for name in ['value','missing-row','rowid','missing-hidden-column']:
    changed=copy.deepcopy(expected[commit_scns[1]]);key=next(iter(changed))
    if name=='value':changed[key][0]='80'
    elif name=='missing-row':del changed[key]
    elif name=='rowid':changed[(key[0],key[1],key[2],key[3]+1)]=changed.pop(key)
    else:changed[key].pop()
    assert changed!=expected[commit_scns[1]];negative.append(name)
(V/'native-full-row-proof.json').write_text(json.dumps(dict(baseline_sha256=hashlib.sha256((H/'actual-product-v3.snapshot').read_bytes()).hexdigest(),native_originals_complete=True,commits=proof,full_values=sum(c['full_values'] for c in proof),comparer_negatives=negative,asof_requery_unavailable=True,production_input_uses_native=False),indent=2))
print(json.dumps(dict(originals=sum(len(x) for x in originals.values()),full_values=sum(c['full_values'] for c in proof),differences=0)),flush=True)
