"""Whole-input semantic refusals after resealing raw block and journal checksums."""
import hashlib, json, struct, subprocess
from pathlib import Path

R=Path(__file__).resolve().parent.parent
O=R/'lab/out/product-active-20261007';N=R/'lab/out/product-active-marker2-20261007'
b=json.loads((O/'baseline-reference.json').read_text())

def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def derive(name,needle,relative,value):
    dest=N/(name+'.oracap');assert not dest.exists();changes=0
    with (O/'capture/sequence-75.oracap').open('rb') as source,dest.open('xb') as target:
        while header:=source.read(16):
            assert header[:8]==b'ORACAP01';m,size=struct.unpack_from('<II',header,8)
            encoded=source.read(m);raw=bytearray(source.read(size));digest=source.read(32)
            meta=json.loads(encoded)
            if meta['block']==95318:
                at=raw.find(needle);assert at>=0 and raw.find(needle,at+1)<0
                raw[at+relative]=value;changes+=1
                struct.pack_into('<H',raw,14,0);checksum=0
                for word in struct.unpack('<256H',raw):checksum^=word
                struct.pack_into('<H',raw,14,checksum)
                assert not __import__('functools').reduce(int.__xor__,struct.unpack('<256H',raw))
                meta['sha256']=hashlib.sha256(raw).hexdigest()
                encoded=json.dumps(meta,sort_keys=True,separators=(',',':')).encode()
                header=b'ORACAP01'+struct.pack('<II',len(encoded),size);digest=hashlib.sha256(encoded+raw).digest()
            target.write(header+encoded+raw+digest)
    assert changes==1
    return dest

proofs=[]
for name,needle,relative,value,reason in [
    ('marker4',bytes.fromhex('842a0003822a0003fa120581010000002c0112000000030100000200'),26,4,'UNSUPPORTED_KDOM2_UPDATE_LAYOUT'),
    ('short-length',b'\x18product-active-stage-one',0,23,'UNSUPPORTED_KDOM2_VALUE_LENGTH')]:
    path=derive(name,needle,relative,value)
    second=O/'capture/sequence-76.oracap'
    args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(N/'classes')+';'+str(N/'kernel.jar'),
          'CapturedHistoricalBatchProbe',b['path'],b['sha256'],'50342539',str(path),sha(path),str(second),sha(second)]
    (N/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:
        (N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==1 and not p.stdout and reason.encode() in p.stderr
    proofs.append(dict(name=name,rc=1,stdout_bytes=0,events=0,reason=reason,input_sha256=sha(path),all_checksums_resealed=True))
(N/'actual-negative-proof.json').write_text(json.dumps(proofs,indent=2))
print(json.dumps(proofs))
