"""Source-bound seq27 packed length, XR profile and cross-role counterexamples; reseal only affected checksums."""
from pathlib import Path
import hashlib,json,struct,sys
r=Path(__file__).resolve().parent.parent;out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=False);raw=(r/'lab/out/oracle19-typed-generation-replay-20261004/archive-1-27.dbf').read_bytes()
assert hashlib.sha256(raw).hexdigest()=='061b8b3d915145eedbff3cf3ec22416495d60ea6f5f60106c7d504e69ea15817'
mapping=[];block=268892;offset=148;length=struct.unpack_from('<I',raw,block*512+offset)[0]
while len(mapping)<length:
 n=min(length-len(mapping),512-offset);mapping.extend(range(block*512+offset,block*512+offset+n));block+=1;offset=16
record=bytes(raw[p] for p in mapping);pos=68 if record[4]&4 else 24;vectors=[]
while pos<len(record):
 table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
 for at in range(pos+34,pos+32+table,2):
  count=struct.unpack_from('<H',record,at)[0];fields.append(mapping[data:data+count]);data+=((count+3)//4)*4
 vectors.append((tuple(record[pos:pos+2]),fields));pos=data
packed=vectors[2][1];undo=vectors[6][1];assert vectors[2][0]==(11,5) and vectors[6][0]==(5,1)
assert bytes(raw[p] for p in packed[3])==bytes.fromhex('02c10c')
cases={'short_length':[(packed[3][0],3)],'unproven_short_xr':[(packed[1][11],0x82)],'cross_role_original':[(undo[1][0],0x72)]};results=[]
for name,changes in cases.items():
 bad=bytearray(raw)
 for at,value in changes:bad[at]=value
 for block in {at//512 for at,_ in changes}:
  start=block*512;struct.pack_into('<H',bad,start+14,0);checksum=0
  for word in struct.unpack('<256H',bad[start:start+512]):checksum^=word
  struct.pack_into('<H',bad,start+14,checksum)
 path=out/(name+'.dbf');path.write_bytes(bad);results.append({'case':name,'changes':changes,'bytes':len(bad),'sha256':hashlib.sha256(bad).hexdigest()})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':results},indent=2)+'\n',encoding='utf-8')
