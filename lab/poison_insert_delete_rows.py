"""Actual seq30 INSERT/DELETE row evidence counterexamples; no source actions."""
import hashlib,json,struct,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;raw=(r/'lab/out/oracle19-insert-delete-rows-20261004/archive-1-30.dbf').read_bytes();assert hashlib.sha256(raw).hexdigest()=='986241002eeea784ca8ce7540226c0471512ce5bf828256a161ad3a5c6d03907'
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=False)
def vectors(block):
 mapping=[];offset=148;length=struct.unpack_from('<I',raw,block*512+offset)[0]
 while len(mapping)<length:
  n=min(length-len(mapping),512-offset);mapping.extend(range(block*512+offset,block*512+offset+n));block+=1;offset=16
 record=bytes(raw[i] for i in mapping);pos=68 if record[4]&4 else 24;result=[]
 while pos<len(record):
  table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
  for at in range(pos+34,pos+32+table,2):
   count=struct.unpack_from('<H',record,at)[0];fields.append(mapping[data:data+count]);data+=((count+3)//4)*4
  result.append((tuple(record[pos:pos+2]),fields));pos=data
 return result
ins=vectors(398);delete=vectors(403);assert ins[0][0]==(11,2) and delete[4][0]==(5,1)
forward=ins[0][1];inverse=delete[4][1];assert bytes(raw[i] for i in forward[2])==bytes.fromhex('c10d') and bytes(raw[i] for i in inverse[4])==bytes.fromhex('c10d')
cases={'insert_after_key':[(forward[2][1],14)],'delete_before_key':[(inverse[4][1],14)],'delete_inverse_slot':[(inverse[3][42],2)],'insert_column_count':[(forward[1][18],2)]};result=[]
for name,changes in cases.items():
 bad=bytearray(raw)
 for at,value in changes:bad[at]=value
 for block in {at//512 for at,_ in changes}:
  start=block*512;struct.pack_into('<H',bad,start+14,0);checksum=0
  for word in struct.unpack('<256H',bad[start:start+512]):checksum^=word
  struct.pack_into('<H',bad,start+14,checksum)
 p=out/(name+'.dbf');p.write_bytes(bad);result.append({'case':name,'changes':changes,'bytes':len(bad),'sha256':hashlib.sha256(bad).hexdigest()})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':result},indent=2)+'\n')
