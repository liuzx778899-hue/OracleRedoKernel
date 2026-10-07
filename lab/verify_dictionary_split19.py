"""Full autonomous split gate: archive/capture equivalence and checksum-valid raw counterexamples."""
import hashlib,json,struct,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/dictionary-split-candidate-20261006';H=R/'lab/out/v3-product-history-20261006'
assert not (N/'split-proof.json').exists();phase=sys.argv[1] if len(sys.argv)>1 else 'first';assert phase in ['first','r2','r3'];retry=phase!='first'
(N/('executed-split-verifier-'+phase+'.py')).write_bytes(Path(__file__).read_bytes())
j='D:/work/Java/jdk-17.0.7/bin/';cp=str(N/'kernel.jar')+';'+str(N/'classes');receipt='323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,rc=0):
    assert not (N/(name+'.command.json')).exists();args=list(map(str,args));(N/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=900)
    for ext,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+ext)).write_bytes(value)
    assert p.returncode==rc,(name,p.returncode,p.stderr[-500:]);print(name,rc,flush=True);return p
base=[j+'java.exe','-Xmx1536m','-cp',cp,'ArchivedDictionarySplitProbe',H/'actual-product-v3.snapshot',receipt]
positive=(N/'first-split.stdout.txt').read_bytes()
if not retry:
    assert run('split-repeat',base+[H/'archive-70.dbf',H/'archive-71.dbf']).stdout==positive
    run('compile-captured-split',[j+'javac.exe','-cp',cp,'-d',N/'classes',R/'lab/CapturedDictionarySplitProbe.java'])
else:assert (N/'split-repeat.stdout.txt').read_bytes()==positive and (N/'compile-captured-split.rc.txt').read_text()=='0'
capture=base[:4]+['CapturedDictionarySplitProbe',H/'actual-product-v3.snapshot',receipt]
for seq in [70,71]:
    path=H/f'capture/sequence-{seq}.oracap';capture.extend([path,sha(path)])
if phase=='r3':assert (N/'captured-split-r2.stdout.txt').read_bytes()==positive and (N/'captured-split-r2.rc.txt').read_text()=='0'
else:assert run('captured-split-r2' if retry else 'captured-split',capture).stdout==positive
original=(H/'archive-70.dbf').read_bytes();payload=b''.join(original[i+16:i+512] for i in range(0,len(original),512))
fields={tuple(s.split('|')[1:4]):bytes.fromhex(s.split('|')[4]) for s in (H/'maintenance-research.stdout.txt').read_text().splitlines() if s.startswith('FIELD|')}
def locate(at,v,f):
    seq,block,offset=map(int,at.split(':'));start=block*496+offset-16
    raw=fields[(at,str(v),str(f))];p=payload.find(raw,start,start+10000);assert p>=0 and payload.find(raw,p+1,start+10000)<0
    return p
def opcode(at,value):
    seq,block,offset=map(int,at.split(':'));start=block*496+offset-16;size=struct.unpack_from('<I',payload,start)[0];p=payload.find(value,start,start+size)
    assert p>=0 and payload.find(value,p+1,start+size)<0;return p
cases=[('wrong-link',locate('70:126619:224',2,2)+4),('wrong-child',locate('70:126619:460',2,2)+4),('wrong-separator',locate('70:126619:460',2,3)+4),
    ('wrong-current-R-XID',locate('70:126610:328',3,1)+20),('wrong-before-rowid',locate('70:126593:240',2,5)+926+7),
    ('wrong-generation',locate('70:126593:240',2,2)+4),('wrong-directory',locate('70:126620:204',2,3)+2),
    ('missing-before-forward',opcode('70:126593:240',b'\x0a\x09')),('missing-end-rebuild',opcode('70:126620:204',b'\x0a\x08'))]
proof=[]
for name,at in cases:
    pos=at//496*512+16+at%496;raw=bytearray(original);raw[pos]^=1;block=pos//512*512;raw[block+14:block+16]=b'\0\0';checksum=0
    for word in struct.unpack('<256H',raw[block:block+512]):checksum^=word
    struct.pack_into('<H',raw,block+14,checksum);path=N/('DERIVED-split-'+name+'.dbf');path.write_bytes(raw)
    run(name+'-scan',[j+'java.exe','-cp',N/'kernel.jar','com.streamhouse.oracle.RedoCli','scan',path])
    p=run(name+'-split',base+[path,H/'archive-71.dbf'],1);assert not p.stdout
    proof.append(dict(name=name,structure_rc=0,split_rc=1,events=0,reason=p.stderr.decode().splitlines()[0],raw_offset=pos,old=original[pos],new=raw[pos],sha256=sha(path)))
(N/'split-proof.json').write_text(json.dumps(dict(autonomous_splits=1,archive_capture_equal=True,repeat_equal=True,negative=proof,source_actions=0,target_actions=0,events=0),indent=2));print('SPLIT_GATE_COMPLETE|negatives='+str(len(proof)),flush=True)
