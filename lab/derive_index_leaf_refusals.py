"""Offline raw counterexamples at native-located B INSERT, never source database actions."""
import hashlib,json,struct,sys
from pathlib import Path

source=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=False)
raw=source.read_bytes();expected='497d09a556faed6b96307f9a439413e08109d0920b75bbeacb909cccbd582d63'
assert hashlib.sha256(raw).hexdigest()==expected
block,offset=225419,328;length=struct.unpack_from('<I',raw,block*512+offset)[0]
positions=[];b=block;o=offset
while len(positions)<length:
    n=min(length-len(positions),512-o);positions.extend(range(b*512+o,b*512+o+n));b+=1;o=16
record=bytes(raw[p] for p in positions);p=68 if record[4]&4 else 24;vectors=[]
while p<len(record):
    table=struct.unpack_from('<H',record,p+32)[0];data=p+32+((table+3)&~3)
    lengths=[struct.unpack_from('<H',record,j)[0] for j in range(p+34,p+32+table,2)]
    vectors.append((p,data,lengths));p=data+sum((n+3)&~3 for n in lengths)
forward=vectors[2];undo=vectors[5]
assert record[forward[0]:forward[0]+2]==bytes([10,2]) and struct.unpack_from('<I',record,forward[0]+8)[0]==0x0300013b
assert record[undo[0]:undo[0]+2]==bytes([5,1]) and undo[2][0]==20
assert struct.unpack_from('<HHI',record,undo[1]+8)==(4,2,587) and struct.unpack_from('<H',record,undo[1]+16)[0]==201
cases=[('foreign-maintenance',[(forward[0]+1,31)]),
       ('foreign-cleanout-opcode',[(forward[0],4),(forward[0]+1,1)]),
       ('foreign-undo-uba-sequence',[(undo[1]+16,202)])]
entries=[]
for name,edits in cases:
    changed=bytearray(raw);physical=[]
    for logical,value in edits:changed[positions[logical]]=value;physical.append(positions[logical])
    for b in {p//512 for p in physical}:
        base=b*512;struct.pack_into('<H',changed,base+14,0);checksum=0
        for j in range(base,base+512,2):checksum^=struct.unpack_from('<H',changed,j)[0]
        struct.pack_into('<H',changed,base+14,checksum)
    path=out/(name+'.dbf');path.write_bytes(changed)
    entries.append({'name':name,'path':path.name,'bytes':len(changed),'sha256':hashlib.sha256(changed).hexdigest(),'logical_edits':edits,'physical_offsets':physical})
(out/'derivation.json').write_text(json.dumps({'source':str(source),'source_sha256':expected,'native_record':[19,block,offset],'native_vectors':[3,6],'cases':entries,'scope':'derived input rejection only; no real Oracle maintenance or changed UBA execution'},indent=2))
print(json.dumps({'derived_cases':len(entries),'source_sha256':expected}))
