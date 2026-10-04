"""Resealed actual seq36 full-rollback counterexamples; never execute source DML."""
import hashlib,json,struct,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;raw=(r/'lab/out/oracle19-rollback-only-20261005/archive-1-36.dbf').read_bytes();assert hashlib.sha256(raw).hexdigest()=='d7c65405e5944a20c87a55b2266be2e41ff2810c760bd0335dbe718bb3c1c86e'
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=False)
def vectors(block,offset):
 mapping=[];length=struct.unpack_from('<I',raw,block*512+offset)[0]
 while len(mapping)<length:
  n=min(length-len(mapping),512-offset);mapping.extend(range(block*512+offset,block*512+offset+n));block+=1;offset=16
 record=bytes(raw[i] for i in mapping);pos=68 if record[4]&4 else 24;result=[]
 while pos<len(record):
  table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
  for at in range(pos+34,pos+32+table,2):
   count=struct.unpack_from('<H',record,at)[0];fields.append(mapping[data:data+count]);data+=((count+3)//4)*4
  result.append((tuple(record[pos:pos+2]),mapping[pos:pos+32],fields));pos=data
 return result
regular=vectors(467,348)[1];begin=vectors(468,68)[1];restored=vectors(468,68)[0];end=vectors(468,300)[0]
assert regular[0]==(5,6) and begin[0]==(5,11) and restored[0]==(11,5) and end[0]==(5,4)
values=[field for field in restored[2] if bytes(raw[i] for i in field)==b'concurrent-closed'];assert len(values)==1
cases={'missing_regular_marker':[(regular[1][1],7)],'wrong_begin_cancel_slot':[(begin[2][0][18],22)],'wrong_restored_value':[(values[0][0],120)],'missing_end':[(end[1][1],7)],'unknown_rollback_flags':[(end[2][0][16],5)]};result=[]
for name,changes in cases.items():
 bad=bytearray(raw)
 for at,value in changes:bad[at]=value
 for block in {at//512 for at,_ in changes}:
  start=block*512;struct.pack_into('<H',bad,start+14,0);checksum=0
  for word in struct.unpack('<256H',bad[start:start+512]):checksum^=word
  struct.pack_into('<H',bad,start+14,checksum)
 path=out/(name+'.dbf');path.write_bytes(bad);result.append({'case':name,'changes':changes,'bytes':len(bad),'sha256':hashlib.sha256(bad).hexdigest()})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':result},indent=2)+'\n')
