"""Reseal real empty capture mutations; successful empty output must still audit every marker/control."""
import hashlib,json,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/empty-dml-capture-20261005';O=R/'lab/out/oracle-empty-target-20261005'
original=(F/'capture/sequence-51.oracap').read_bytes();metadata=[];blocks=[];offset=0
while offset<len(original):
    m,n=struct.unpack_from('<II',original,offset+8);assert n==512
    metadata.append(json.loads(original[offset+16:offset+16+m]));blocks.append(original[offset+16+m:offset+16+m+n]);offset+=16+m+n+32
virtual=b''.join(b[16:] for b in blocks[2:]);sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def vectors(block,offset):
    start=(block-2)*496+offset-16;length=struct.unpack_from('<I',virtual,start)[0];r=virtual[start:start+length];pos=68 if r[4]&4 else 24;out=[]
    while pos<len(r):
        table=struct.unpack_from('<H',r,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
        for i in range(pos+34,pos+32+table,2):
            n=struct.unpack_from('<H',r,i)[0];fields.append((start+data,n));data+=((n+3)//4)*4
        out.append((start+pos,tuple(r[pos:pos+2]),fields));pos=data
    return out
ctl=vectors(3849,372);assert ctl[0][1]==(5,2) and ctl[1][1]==(5,1)
marker=vectors(3871,372)[1];assert marker[1]==(5,6)
patches=[('foreign-uba-mismatch',[(ctl[0][2][0][0]+8,b'\x9e')]),('active-same-sequence-slot',[(ctl[0][2][0][0],b'\x00\x00'),(ctl[1][2][0][0]+10,b'\x00\x00')]),('missing-rollback-marker',[(marker[0]+1,b'\x07')])]
results=[]
for name,edits in patches:
    changed=[bytearray(b) for b in blocks]
    for start,data in edits:
        for i,b in enumerate(data):index=start+i;changed[2+index//496][16+index%496]=b
    encoded=bytearray()
    for meta,payload in zip(metadata,changed):
        struct.pack_into('<H',payload,14,0);checksum=0
        for word in struct.unpack('<256H',payload):checksum^=word
        struct.pack_into('<H',payload,14,checksum);meta=dict(meta);meta['sha256']=hashlib.sha256(payload).hexdigest();text=json.dumps(meta,sort_keys=True,separators=(',',':')).encode()
        encoded+=b'ORACAP01'+struct.pack('<II',len(text),512)+text+payload+hashlib.sha256(text+payload).digest()
    path=O/(name+'.oracap');assert not path.exists();path.write_bytes(encoded)
    cmd=json.loads((F/'complete-batch-r2.command.json').read_text());cmd[-2:]=[str(path),sha(path)]
    (O/(name+'.command.json')).write_text(json.dumps(cmd));p=subprocess.run(cmd,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+ext+'.txt')).write_bytes(v)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==1 and not p.stdout
    results.append({'case':name,'rc':p.returncode,'stdout_bytes':len(p.stdout),'stderr':p.stderr.decode(),'byte_edits_in_headerless_redo':[(at,b.hex()) for at,b in edits],'checksums_resealed':True})
(O/'raw-refusals.json').write_text(json.dumps(results,indent=2));print(json.dumps([{'case':r['case'],'rc':r['rc'],'stdout_bytes':r['stdout_bytes']} for r in results]))
