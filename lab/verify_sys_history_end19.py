"""Independently compare existing candidate histories with native control windows; do not rerun them."""
import hashlib,json,re,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';O=R/'lab/out/sys-control-candidate-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (O/'native-end-proof.json').exists();(O/'executed-native-end-driver.py').write_bytes(Path(__file__).read_bytes())
records={}
for n in re.split(r'(?=REDO RECORD - Thread:)',(H/'dump.trc').read_text()):
    pos=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',n)
    if pos:records[tuple(int(x,16) for x in pos.groups())]=n
cases=[]
for seq,xid in [(66,'10.18.2272'),(66,'3.30.705'),(67,'3.26.705')]:
    lines=(O/f'history-{seq}-{xid}.stdout.txt').read_text().splitlines()
    end=next(l.split('|') for l in lines if l.startswith('END|'));endpos=tuple(map(int,end[1:4]));n=records[endpos]
    assert int(re.search(r'SCN: 0x([0-9a-f]+)',n)[1],16)==int(end[4])
    c=next(c for c in re.split(r'(?=CHANGE #\d+)',n)[1:] if re.search(r'OP:5\.4\b',c))
    segment=int(re.search(r'DBA:0x([0-9a-f]+)',c)[1],16)
    m=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c)
    assert tuple(int(x,16) for x in m.groups())==tuple(map(int,xid.split('.')[1:]))
    tail=re.search(r'ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
    assert tail;u=tuple(int(x,16) for x in tail.groups())
    assert end[7]==f'Uba[dba={u[0]}, sequence={u[1]}, record={u[2]}]'
    actual=[l.split('|') for l in lines if l.startswith('CONTROL|')]
    firstpos=tuple(map(int,actual[0][1:4]));expected=[]
    for pos,n in sorted(records.items()):
        if pos<firstpos or pos>=endpos:continue
        for c in re.split(r'(?=CHANGE #\d+)',n)[1:]:
            h=re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',c)
            m=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) flg: 0x([0-9a-f]+)',c)
            if h and m and int(h[1],16)==segment and int(m[1],16)==int(xid.split('.')[1]):
                assert int(m[2],16) in [0,int(xid.split('.')[2])]
                expected.append([*pos,'5.2',int(m[3],16)])
    assert [[*map(int,a[1:4]),a[4],int(a[5])] for a in actual]==expected
    cases.append(dict(sequence=seq,xid=xid,end_position=endpos,end_scn=int(end[4]),tail=u,control_count=len(expected),native_differences=0))
refusals=[]
for name in ['actual-v2-batch','erased-v2-batch']:
    args=json.loads((H/(name+'.command.json')).read_text());args[3]=str(O/'kernel.jar')+';'+str(H/'parser-classes')
    stem=O/('new-kernel-'+name);stem.with_suffix('.command.json').write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=180)
    stem.with_suffix('.stdout.txt').write_bytes(p.stdout);stem.with_suffix('.stderr.txt').write_bytes(p.stderr);stem.with_suffix('.rc.txt').write_text(str(p.returncode))
    assert p.returncode==1 and not p.stdout
    assert p.stderr.decode().splitlines()[0]==(H/(name+'.stderr.txt')).read_text().splitlines()[0]
    refusals.append(dict(input=name,rc=1,events=0,reason=p.stderr.decode().splitlines()[0]))
(O/'native-end-proof.json').write_text(json.dumps(dict(cases=cases,history_refusals=refusals,driver_sha256=sha(Path(__file__)),source_actions=0),indent=2))
print(json.dumps(dict(native_control_windows=len(cases),historical_refusals=len(refusals),source_actions=0)))
