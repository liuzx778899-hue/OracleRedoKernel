"""Independent native PDB-window coverage, without claiming each marker belongs to the target."""
import json,re,sys
from pathlib import Path

x=tuple(map(int,sys.argv[3].split('.')));seq=int(sys.argv[4]);active=False;segment=None;begins=ends=0;expected={}
for record in re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[1]).read_text(encoding='utf-8')):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    target_begins=[]
    for c in changes:
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
        if re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*OP:5\.1\b',c) and xid and tuple(int(xid[i],16) for i in (1,2,3))==x and 'Begin trans' in c:target_begins.append(c)
    if target_begins:
        starts=[int(h[1],16) for c in changes if (h:=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',c)) and re.search(r'ktudh redo: slt: 0x'+format(x[1],'04x')+r' sqn: 0x'+format(x[2],'08x'),c)]
        if active or len(target_begins)!=1 or len(starts)!=1:raise RuntimeError('native begin ambiguity')
        segment=starts[0];begins+=1;active=True
    if active:
        for c in changes:
            h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.(6|11)\b',c)
            if not h:continue
            m=re.search(r'ktub[lu] redo: slt:\s*(\d+) wrp:\s*(\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc: \d+\.\d+ \[objn:\s*(\d+) objd:\s*(\d+)',c)
            if not m:raise RuntimeError('native marker metadata missing')
            key=(seq,int(rba[1],16),int(rba[2],16),int(h[1]))
            if key in expected:raise RuntimeError('duplicate native position')
            expected[key]=[int(h[2],16),int(m[6]),int(m[7]),int(m[1]),int(m[2]),int(m[3],16),int(m[4],16),int(m[5])]
    for c in changes:
        h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.4\b',c)
        e=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c)
        if h and e and int(h[1],16)==segment and (int(e[1],16),int(e[2],16))==x[1:]:
            if not active:raise RuntimeError('native duplicate end')
            active=False;ends+=1
if active or begins!=1 or ends!=1:raise RuntimeError('native window incomplete')
actual={}
for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines():
    p=line.split('|')
    if p[0]!='MARKER' or len(p)!=13:raise RuntimeError('unexpected marker output')
    key=tuple(map(int,p[1:5]))
    if key in actual:raise RuntimeError('duplicate parser position')
    actual[key]=list(map(int,p[5:]))
errors=[{'position':k,'expected':expected.get(k),'actual':actual.get(k)} for k in sorted(expected.keys()|actual.keys()) if expected.get(k)!=actual.get(k)]
print(json.dumps({'native_window_markers':len(expected),'parser_window_markers':len(actual),'mismatches':len(errors),'errors':errors,'scope':'all same-PDB 5.6/5.11 in verified target record window; ownership and other opcodes not certified'},indent=2))
sys.exit(bool(errors))
