"""Checksum-valid raw counterexamples for the new scoped OBJ$ nine-column pair."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-history-research-20261006';H=R/'lab/out/v2-historical-product-20261006';K=O/'new-kernel.jar'
assert not (O/'object-raw-negative-proof.json').exists();(O/'executed-object-raw-negative-driver-r2.py').write_bytes(Path(__file__).read_bytes())
r=json.loads((O/'obj-row-shapes.json').read_text())[-1];f=r['forward'][0];u=r['undo'];original=(H/'archive-67.dbf').read_bytes()
payload=b''.join(original[i+16:i+512] for i in range(0,len(original),512))
def locate(raw,start=0,end=None,unique=True):
    b=bytes.fromhex(raw);at=payload.find(b,start,len(payload) if end is None else end);assert at>=0
    if unique:assert payload.find(b,at+1,len(payload) if end is None else end)<0
    return at
physical=lambda at:at//496*512+16+at%496
ktbflat=locate(f['fields'][0]);kdoflat=locate(f['fields'][1]);metaflat=locate(u['fields'][1]);mapflat=locate(u['fields'][4],metaflat,ktbflat)
ktb=physical(ktbflat);kdo=physical(kdoflat);meta=physical(metaflat);columnmap=physical(mapflat)
cases=[('wrong-bitmap',kdo+26,0x10),('wrong-current-full-XID',ktb+12,original[ktb+12]^1),('wrong-UBA',ktb+16,original[ktb+16]^1),('wrong-logical',meta,19),('wrong-generation',meta+4,19),('wrong-table',kdo+19,1),('wrong-inverse-column-map',columnmap,2)]
def run(name,args):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    return p
proof=[]
for name,at,value in cases:
    raw=bytearray(original);assert raw[at]!=value;raw[at]=value;block=at//512*512;raw[block+14:block+16]=b'\0\0';checksum=0
    for i in range(block,block+512,2):checksum^=int.from_bytes(raw[i:i+2],'little')
    raw[block+14:block+16]=checksum.to_bytes(2,'little');path=O/('DERIVED-object-'+name+'.dbf');path.write_bytes(raw)
    scan=run('object-'+name+'-scan',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',K,'com.streamhouse.oracle.RedoCli','scan',path]);assert scan.returncode==0,scan.stderr.decode()
    p=run('object-'+name+'-pair',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',str(K)+';'+str(O/'row-classes-r2'),'SysDictionaryRowPairsProbe',H/'snapshots/100644812901937152.snapshot','e16e6275651894614dc9a79e42381338ddbf0771aa68c971df66f35abd5c0e27',path,'3.26.705']);assert p.returncode==1 and not p.stdout
    proof.append(dict(name=name,raw_offset=at,changed_offsets=[i for i,(a,b) in enumerate(zip(original,raw)) if a!=b],structure_rc=0,pair_rc=1,events=0,reason=p.stderr.decode().splitlines()[0]))
(O/'object-raw-negative-proof.json').write_text(json.dumps(dict(cases=proof,kernel_sha256=hashlib.sha256(K.read_bytes()).hexdigest(),business_ddl_dml=0,target_actions=0),indent=2));print(json.dumps(dict(raw_semantic_refusals=len(proof),events=0)))
