"""Offline complete mixed-field projection against independent SQL truth; SQL is never Java input."""
from pathlib import Path
import hashlib,json,os,re,subprocess,sys
R=Path(__file__).resolve().parent.parent
BASE=R/'lab/out/common-fields-20261010'
S=BASE/'source';F=S/'resume-r2';C=BASE/(sys.argv[2] if len(sys.argv)==3 else 'candidate-r2')
assert len(sys.argv) in [2,3] and re.fullmatch(r'verify-r[1-9][0-9]*',sys.argv[1])
assert re.fullmatch(r'candidate-r[1-9][0-9]*',C.name) and int(C.name.split('-r')[1])>=2
assert (C/'development-candidate.json').is_file() and (C/'kernel.jar').is_file() and (C/'adapter.jar').is_file(), 'candidate build must finish before verification'
N=BASE/sys.argv[1];N.mkdir(exist_ok=False)
archives=json.loads((F/'archives.json').read_text());snapshots=json.loads((S/'snapshots.json').read_text())
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
for a in archives:assert sha(a['path'])==a['sha256']
cp=os.pathsep.join(map(str,[C/'kernel.jar',C/'adapter.jar',S/'libs/*',S/'classes']))
proof=[]
for storage in ['B','S']:
    table='T_D4_MIX_'+storage+'_20261010';snapshot=snapshots[table];assert sha(snapshot['path'])==snapshot['sha256']
    command=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'LobProductPreparationProbe',snapshot['path'],snapshot['sha256']]+[a['path'] for a in archives]
    p=subprocess.run(command,capture_output=True,timeout=300)
    for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:(N/(storage+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(storage,p.returncode)
    data=json.loads(p.stdout);assert len(data['events'])==1
    event=data['events'][0];assert event['operation']=='UPDATE'
    before,after=event['before'],event['after']
    assert list(after)==['ID','TXT','BIN','DT','TS','B','C','J','X']
    assert after['ID']==1 and after['TXT']=='mixed-text' and after['BIN']==dict(hex='ff0080')
    assert after['DT']['iso']=='2025-03-01T00:00:01' and after['TS']['iso']=='2025-03-01T00:00:01.654321000'
    assert before['TXT']=='seed' and before['B']['hex']=='0080ff'
    for col in ['B','C','J','X']:
        path=S/(table+'-truth-'+col+'.stdout') if storage=='B' else F/('secure-truth-'+col+'.stdout')
        lines=path.read_text().splitlines()
        parts=sorted((int(l.split('|')[1]),l.split('|')[2]) for l in lines if l.startswith('HEX|'))
        assert [i for i,_ in parts]==list(range(len(parts)))
        raw=bytes.fromhex(''.join(part for _,part in parts))
        assert after[col]['type']==('BLOB' if col=='B' else 'CLOB') and after[col]['hex']==raw.hex(),(storage,col)
        length=int(next(l.split('|')[1] for l in lines if l.startswith('LENGTH|')))
        assert (len(raw) if col=='B' else len(raw.decode('utf-8')))==length
    assert json.loads(bytes.fromhex(after['J']['hex']))==dict(value='j'*5000)
    import xml.etree.ElementTree as ET
    assert ET.fromstring(bytes.fromhex(after['X']['hex'])).text=='x'*5000
    proof.append(dict(storage=storage,events=1,columns=9,fullAfterImageDifferences=0,rawInputs=[a['sha256'] for a in archives],snapshotSha256=snapshot['sha256'],projectionSha256=sha(N/(storage+'.stdout'))))
(N/'proof.json').write_text(json.dumps(dict(kernelSha256=sha(C/'kernel.jar'),adapterSha256=sha(C/'adapter.jar'),scenarios=proof,sqlUsedAsEventInput=False,targetIntegrated=False),indent=2))
print('MIXED_FULL_PROJECTION_PASS',flush=True)
