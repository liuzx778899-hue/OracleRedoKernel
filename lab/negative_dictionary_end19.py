"""Checksum-valid DERIVED corrupt end tail/extension/identity. No source writes."""
import hashlib,json,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-end-profiles-20261006';OLD=R/'lab/out/dictionary-anchor-real-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
source=OLD/'erased-59.oracap';original=source.read_bytes();frames=[];blocks=[];at=0
while at<len(original):
    m,n=struct.unpack_from('<II',original,at+8);assert n==512
    frames.append(json.loads(original[at+16:at+16+m]));blocks.append(bytearray(original[at+16+m:at+16+m+n]));at+=16+m+n+32
virtual=b''.join(bytes(b[16:]) for b in blocks[2:])
start=(179-2)*496+16-16
length=struct.unpack_from('<I',virtual,start)[0];body=virtual[start:start+length];pos=68 if body[4]&4 else 24
for vector in range(1,4):
    fieldBytes=struct.unpack_from('<H',body,pos+32)[0];data=pos+32+((fieldBytes+3)//4*4)
    lengths=struct.unpack_from('<'+str(fieldBytes//2-1)+'H',body,pos+34)
    if vector==3:break
    pos=data+sum((n+3)//4*4 for n in lengths)
assert body[pos:pos+2]==bytes([5,4]) and lengths==(20,16,4)
offsets=[data]
for length in lengths[:-1]:offsets.append(offsets[-1]+((length+3)//4*4))
(O/'negative-end-execution-pin-r2.json').write_text(json.dumps({'script_sha256':sha(Path(__file__)),'kernel_sha256':sha(O/'kernel.jar'),'source_sha256':sha(source),'record':{'sequence':59,'block':179,'offset':16,'vector':3},'DERIVED_only':True},indent=2))
results=[]
for name,relative in [('target-wrong-tail',offsets[1]),('target-wrong-slot',offsets[0]),('target-wrong-record-container',16)]:
    changed=[bytearray(b) for b in blocks];p=start+relative;bi=2+p//496;bo=16+p%496;old=changed[bi][bo];changed[bi][bo]^=1
    encoded=bytearray()
    for frame,payload in zip(frames,changed):
        struct.pack_into('<H',payload,14,0);checksum=0
        for word in struct.unpack('<256H',payload):checksum^=word
        struct.pack_into('<H',payload,14,checksum);frame=dict(frame);frame['sha256']=hashlib.sha256(payload).hexdigest();m=json.dumps(frame,sort_keys=True,separators=(',',':')).encode()
        encoded+=b'ORACAP01'+struct.pack('<II',len(m),512)+m+payload+hashlib.sha256(m+payload).digest()
    assert all(all(a==b for j,(a,b) in enumerate(zip(x,y)) if j not in {14,15} and (i,j)!=(bi,bo)) for i,(x,y) in enumerate(zip(blocks,changed)))
    path=O/(name+'-59.oracap');path.write_bytes(encoded)
    (O/(name+'.mutation.json')).write_text(json.dumps({'DERIVED':True,'original_sha256':sha(source),'result_sha256':sha(path),'block':bi,'byte_offset':bo,'old':old,'new':changed[bi][bo]},indent=2))
    args=json.loads((O/'erased-no-anchor-control.command.json').read_text());i=args.index('CapturedBatchProbe')+4;assert args[i]==str(source);args[i]=str(path);args[i+1]=sha(path)
    for mode,command in [('structure',[args[0],'-cp',args[args.index('-cp')+1],'CapturedRecordsProbe',str(path),sha(path),'records']),('semantic',args)]:
        label=name+'-'+mode;assert not (O/(label+'.command.json')).exists();(O/(label+'.command.json')).write_text(json.dumps(command));p=subprocess.run(command,capture_output=True,timeout=120)
        (O/(label+'.stdout.txt')).write_bytes(p.stdout);(O/(label+'.stderr.txt')).write_bytes(p.stderr);(O/(label+'.rc.txt')).write_text(str(p.returncode))
        if mode=='structure':assert p.returncode==0
        else:
            assert p.returncode==1 and not p.stdout,(name,p.returncode,p.stderr)
            results.append({'case':name,'structural_passed':True,'rc':p.returncode,'stdout_bytes':0,'reason':p.stderr.decode().splitlines()[0]})
foreign=(O/'wrong-tail-semantic.stdout.txt').read_bytes();healthy=(O/'erased-no-anchor-control.stdout.txt').read_bytes()
select=lambda raw:[l for l in raw.splitlines() if l.startswith((b'EVENT|',b'ROW|'))]
assert select(foreign)==select(healthy) and (O/'wrong-tail-semantic.rc.txt').read_text()=='0'
(O/'negative-end-proof.json').write_text(json.dumps({'results':results,'foreign_control':'r1 mutated a foreign end tail; rc0, same two EVENT/ROW outputs, not a target-tail negative','source_actions':0,'target_actions':0},indent=2));print(json.dumps(results))
