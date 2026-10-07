"""Legal-checksum complete-history refusals, including raw-slot provenance and unknown intervening writes."""
import hashlib,json,struct,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006';N=R/'lab/out/split-history-candidate-20261007';A=N/sys.argv[1]
V=N/'negative-r2';V.mkdir();(V/'executed-negative-driver.py').write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,rc=0):
    args=list(map(str,args));assert not (V/(name+'.command.json')).exists();(V/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=900)
    for ext,b in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(V/(name+'.'+ext)).write_bytes(b)
    assert p.returncode==rc,(name,p.returncode,p.stderr[-600:])
    if rc:assert not p.stdout,(name,'partial rows')
    print(name,rc,flush=True);return p
j='D:/work/Java/jdk-17.0.7/bin/';cp=str(A/'kernel.jar')+';'+str(A/'classes');base=[j+'java.exe','-Xmx1536m','-cp',cp];receipt='323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'
parity=base+['com.streamhouse.oracle.HistoricalRowsParityProbe',H/'actual-product-v3.snapshot',receipt,'archives','50342539']
original=(H/'archive-70.dbf').read_bytes();payload=b''.join(original[i+16:i+512] for i in range(0,len(original),512))
records=json.loads((N/'r1/raw-records.json').read_text());r=next(r for r in records if r['position']==[70,126628,476])
def start(pos):return pos[1]*496+pos[2]-16
def locate(value,pos):
    s=start(pos);limit=s+struct.unpack_from('<I',payload,s)[0];raw=bytes.fromhex(value);p=payload.find(raw,s,limit)
    assert p>=0 and payload.find(raw,p+1,limit)<0,(pos,value[:24]);return p
def vectors(pos):
    s=start(pos);length=struct.unpack_from('<I',payload,s)[0];at=s+(68 if payload[s+4]&4 else 24);out=[]
    while at<s+length:
        out.append(at);table=struct.unpack_from('<H',payload,at+32)[0];data=at+32+(table+3)//4*4
        for p in range(at+34,at+32+table,2):data+=(struct.unpack_from('<H',payload,p)[0]+3)//4*4
        at=data
    assert at==s+length;return out
u=r['vectors'][0]['fields'];f=r['vectors'][1]['fields'];leaf=locate(f[1],r['position']);ktb=locate(f[0],r['position']);key=locate(u[4],r['position']);header=locate(u[0],r['position'])
oldrow=locate('0101004089a4003304c308345602c105',[70,126620,204])
obj=next(x for x in records if x['position']==[70,126629,200]);kdo=locate(obj['vectors'][1]['fields'][1],obj['position'])
cases=[('wrong-restore-slot',[(leaf+2,payload[leaf+2]^1)]),('wrong-restore-size',[(leaf+4,payload[leaf+4]^1)]),('wrong-restore-key',[(key+7,payload[key+7]^1)]),('wrong-restore-current-XID',[(ktb+12,payload[ktb+12]^1)]),('wrong-restore-UBA',[(ktb+16,payload[ktb+16]^1)]),('wrong-restore-full-undo-XID',[(header+8,payload[header+8]^1)]),('missing-restore',[(vectors(r['position'])[1]+1,0)]),('wrong-pending-slot-rowid',[(oldrow+7,payload[oldrow+7]^1)]),('restore-before-split-SCN',list(zip(range(start(r['position'])+8,start(r['position'])+12),struct.pack('<I',2567426)))),('unknown-OBJ-ckix',[(kdo+18,145)])]
structure={}
for seq in [70,71]:
    prior=N/'negative';assert (prior/('structure-'+str(seq)+'.rc.txt')).read_text()=='0'
    lines=(prior/('structure-'+str(seq)+'.stdout.txt')).read_text().splitlines()
    structure[seq]=[dict(position=[seq,int(q[0]),int(q[1])],length=int(q[2]),scn=int(q[4]),uid=int(q[6]),ops=q[7].split(',')) for q in (l.split('|') for l in lines)]
# The actual end rebuild and restore are adjacent: no original intervening record exists.
# Insert a valid clone of the observed 13.6 physical vector, redirect its page, then repack remaining records.
assert not any([70,126620,204]<x['position']<[70,126628,476] for x in structure[70])
va=vectors([70,126610,188]);s=start([70,126610,188]);end=s+struct.unpack_from('<I',payload,s)[0]
clone=bytearray(payload[va[1]:end]);assert clone[:2]==bytes([13,6]);struct.pack_into('<I',clone,8,0x0040512d)
body=bytearray(payload[start(r['position']):start(r['position'])+24]);struct.pack_into('<I',body,0,24+len(clone));struct.pack_into('<I',body,8,2567427);body+=clone
repacked=bytearray(payload);at=start(r['position']);repacked[at:]=bytes(len(repacked)-at)
for data in [bytes(body),*[payload[start(x['position']):start(x['position'])+x['length']] for x in structure[70] if x['position']>=r['position']]]:
    if 496-at%496<24:at+=496-at%496
    assert at+len(data)<=len(repacked);repacked[at:at+len(data)]=data;at+=len(data)
cases.append(('unknown-intervening-page-write',[(p,b) for p,b in enumerate(repacked) if b!=payload[p]]))
derived=[];proof=[]
for name,changes in cases:
    raw=bytearray(original);mutations=[];blocks=set()
    for pos,value in changes:
        p=pos//496*512+16+pos%496
        if raw[p]==value:continue
        mutations.append(dict(raw_offset=p,old=raw[p],new=value));raw[p]=value;blocks.add(p//512)
    assert mutations
    for bi in blocks:
        b=bi*512;raw[b+14:b+16]=b'\0\0';checksum=0
        for word in struct.unpack('<256H',raw[b:b+512]):checksum^=word
        struct.pack_into('<H',raw,b+14,checksum)
    path=V/('DERIVED-'+name+'.dbf');path.write_bytes(raw);run(name+'-scan',[j+'java.exe','-cp',A/'kernel.jar','com.streamhouse.oracle.RedoCli','scan',path])
    p=run(name+'-complete-history',parity+[path,H/'archive-71.dbf'],1)
    proof.append(dict(name=name,structure_rc=0,complete_history_rc=1,events=0,sha256=sha(path),mutations=mutations,reason=p.stderr.decode().splitlines()[0]));derived.append((name,path))
# Earlier split negatives now exercise the complete logical history and business entry, not its local parser.
prior=R/'lab/out/dictionary-split-candidate-20261006';m=json.loads((prior/'r3-split-proof.json').read_text())
for case in m['negative']:
    name=case['name'];path=prior/('DERIVED-split-'+name+'.dbf');assert sha(path)==case['sha256']
    run('prior-'+name+'-scan',[j+'java.exe','-cp',A/'kernel.jar','com.streamhouse.oracle.RedoCli','scan',path])
    p=run('prior-'+name+'-complete-history',parity+[path,H/'archive-71.dbf'],1)
    proof.append(dict(name='prior-'+name,structure_rc=0,complete_history_rc=1,events=0,sha256=sha(path),reason=p.stderr.decode().splitlines()[0]))
# Capture framing/hashes are independently rebuilt for three critical provenance failures.
source=(H/'capture/sequence-70.oracap').read_bytes();frames=[];at=0
while at<len(source):
    m,n=struct.unpack_from('<II',source,at+8);assert n==512
    frames.append((json.loads(source[at+16:at+16+m]),source[at+16+m:at+16+m+n]));at+=16+m+n+32
cap71=H/'capture/sequence-71.oracap'
for name,path in derived:
    if name not in ['wrong-pending-slot-rowid','unknown-intervening-page-write','missing-restore']:continue
    raw=path.read_bytes();encoded=bytearray()
    for meta,old in frames:
        meta=dict(meta);bi=meta['block'];b=raw[bi*512:(bi+1)*512] if bi>=2 else old
        meta['sha256']=hashlib.sha256(b).hexdigest();m=json.dumps(meta,sort_keys=True,separators=(',',':')).encode();encoded+=b'ORACAP01'+struct.pack('<II',len(m),512)+m+b+hashlib.sha256(m+b).digest()
    cap=V/('DERIVED-'+name+'.oracap');cap.write_bytes(encoded)
    run(name+'-capture-structure',base+['CapturedRecordsProbe',cap,sha(cap),'records'])
    p=run(name+'-complete-capture-business',base+['CapturedHistoricalBatchProbe',H/'actual-product-v3.snapshot',receipt,'50342539',cap,sha(cap),cap71,sha(cap71)],1)
    proof.append(dict(name='capture-'+name,structure_rc=0,complete_history_rc=1,events=0,sha256=sha(cap),reason=p.stderr.decode().splitlines()[0]))
(V/'negative-proof.json').write_text(json.dumps(dict(kernel_sha256=sha(A/'kernel.jar'),cases=proof,source_actions=0,target_actions=0),indent=2))
print('COMPLETE_HISTORY_NEGATIVES|'+str(len(proof)),flush=True)
