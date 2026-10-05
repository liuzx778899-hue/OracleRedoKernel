"""Checksum-valid business undo/commit poisons must publish zero complete historical events."""
import hashlib,json,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-row-schema-candidate-20261006';O=R/'lab/out/v3-historical-raw-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (N/'business-negative-proof.json').exists()
(N/'executed-business-negative-driver.py').write_bytes(Path(__file__).read_bytes())
original=(O/'archive-69.dbf').read_bytes()
payload=b''.join(original[i*512+16:(i+1)*512] for i in range(2,len(original)//512))
start=(12-2)*496;limit=start+652
def find(hexvalue):
    value=bytes.fromhex(hexvalue);at=payload.find(value,start,limit)
    assert at>=start and payload.find(value,at+1,limit)<0
    return at
before=find('686973746f72792d76332d6164646564')
tail=find('c7004002ff0007000000f01b000b0000')
# The actual serialized undo header is selected by its independently verified full XID/UBA.
records=json.loads((O/'new-raw-records.json').read_text())
record=next(r for r in records if r['position']==[69,12,16])
v=next(v for v in record['vectors'] if v['opcode']=='5.1');undo=find(v['fields'][0])
capture=(O/'capture/sequence-69.oracap').read_bytes();frames=[];at=0
while at<len(capture):
    m,n=struct.unpack_from('<II',capture,at+8);assert n==512
    frames.append((json.loads(capture[at+16:at+16+m]),capture[at+16+m:at+16+m+n]));at+=16+m+n+32
args=json.loads((N/'actual-historical-batch-r1.command.json').read_text());proof=[]
for name,pos in [('wrong-business-before',before+15),('wrong-business-XID',undo+12),('wrong-commit-tail',tail),('unknown-commit-opaque',tail+13)]:
    pos=(2+pos//496)*512+16+pos%496
    raw=bytearray(original);raw[pos]^=1;block=pos//512;raw[block*512+14:block*512+16]=b'\0\0';checksum=0
    for word in struct.unpack('<256H',raw[block*512:(block+1)*512]):checksum^=word
    struct.pack_into('<H',raw,block*512+14,checksum)
    encoded=bytearray()
    for meta,old in frames:
        meta=dict(meta);bi=meta['block'];payload=bytes(raw[bi*512:(bi+1)*512]) if bi>=2 else old
        meta['sha256']=hashlib.sha256(payload).hexdigest();m=json.dumps(meta,sort_keys=True,separators=(',',':')).encode()
        encoded+=b'ORACAP01'+struct.pack('<II',len(m),512)+m+payload+hashlib.sha256(m+payload).digest()
    path=N/('DERIVED-business-'+name+'.oracap');path.write_bytes(encoded)
    command=args[:10]+[str(path),sha(path)];stem='business-'+name
    (N/(stem+'.command.json')).write_text(json.dumps(command));p=subprocess.run(command,capture_output=True,timeout=240)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(stem+'.'+suffix)).write_bytes(data)
    assert p.returncode==1 and not p.stdout,(name,p.stdout,p.stderr)
    proof.append(dict(name=name,rc=1,events=0,raw_offset=pos,old=original[pos],new=raw[pos],input_sha256=sha(path),reason=p.stderr.decode().splitlines()[0]))
(N/'business-negative-proof.json').write_text(json.dumps(dict(cases=proof,kernel_sha256=sha(N/'kernel.jar'),source_actions=0,target_actions=0),indent=2));print(json.dumps(proof))
