"""Mutate one native-located active slot control, retaining raw record layout and block checksum."""
import hashlib,json,re,struct,sys
from pathlib import Path

archive,dump,xid,out=Path(sys.argv[1]),Path(sys.argv[2]),tuple(map(int,sys.argv[3].split('.'))),Path(sys.argv[4])
out.mkdir(parents=True,exist_ok=True);source=archive.read_bytes();active=False;segment=None;selected=None
for record in re.split(r'(?=REDO RECORD - Thread:)',dump.read_text(encoding='utf-8')):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    for c in changes:
        x=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
        if re.search(r'CON_ID:3 [^\n]*OP:5\.1\b',c) and x and tuple(int(x[i],16) for i in (1,2,3))==xid and 'Begin trans' in c:
            starts=[int(h[1],16) for t in changes if (h:=re.search(r'CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',t)) and re.search(r'ktudh redo: slt: 0x'+format(xid[1],'04x')+r' sqn: 0x'+format(xid[2],'08x'),t)]
            if len(starts)!=1:raise RuntimeError('native begin ambiguity')
            segment=starts[0];active=True
    if not active:continue
    for c in changes:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',c)
        t=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) flg: 0x([0-9a-f]+)',c)
        if h and t and int(h[2],16)==segment and int(t[1],16)==xid[1] and int(t[2],16)==0:
            rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
            selected=(int(rba[1],16),int(rba[2],16),int(h[1]),int(t[3],16));break
    if selected:break
if not selected:raise RuntimeError('no native target zero-sequence control')
block,offset,index,flags=selected;length=struct.unpack_from('<I',source,block*512+offset)[0]
positions=[];b=block;o=offset
while len(positions)<length:
    n=min(length-len(positions),512-o);positions.extend(range(b*512+o,b*512+o+n));b+=1;o=16
raw=bytes(source[p] for p in positions);p=68 if raw[4]&4 else 24
for i in range(1,index+1):
    table=struct.unpack_from('<H',raw,p+32)[0];data=p+32+((table+3)&~3)
    lengths=[struct.unpack_from('<H',raw,j)[0] for j in range(p+34,p+32+table,2)]
    if i==index:break
    p=data+sum((n+3)&~3 for n in lengths)
assert raw[p:p+2]==bytes([5,2]) and struct.unpack_from('<I',raw,p+8)[0]==segment
assert lengths[0]==32 and struct.unpack_from('<H',raw,data)[0]==xid[1]
assert struct.unpack_from('<I',raw,data+4)[0]==0 and struct.unpack_from('<H',raw,data+16)[0]==flags
results=[]
for name,sequence in [('low-wrap-collision',xid[2]+65536),('different-sequence',xid[2]+1),('maximum-sequence',0xffffffff)]:
    changed=bytearray(source);field=struct.pack('<I',sequence)
    for i,v in enumerate(field):changed[positions[data+4+i]]=v
    blocks={positions[data+4+i]//512 for i in range(4)}
    for n in blocks:
        base=n*512;struct.pack_into('<H',changed,base+14,0);checksum=0
        for j in range(base,base+512,2):checksum^=struct.unpack_from('<H',changed,j)[0]
        struct.pack_into('<H',changed,base+14,checksum)
    path=out/(name+'.dbf');path.write_bytes(changed)
    results.append({'name':name,'path':str(path),'sequence_before':0,'sequence_after':sequence,'sha256':hashlib.sha256(changed).hexdigest()})
(out/'derivation.json').write_text(json.dumps({'source':str(archive),'source_sha256':hashlib.sha256(source).hexdigest(),'target_xid':xid,'native_position':selected,'segment':segment,'raw_field_positions':positions[data+4:data+8],'mutations':results,'scope':'derived raw counterexamples; not executed Oracle slot reuse'},indent=2),encoding='utf-8')
print(json.dumps({'native_position':selected,'derived_cases':len(results)}))
