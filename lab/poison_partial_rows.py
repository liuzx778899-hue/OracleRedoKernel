"""Actual seq32 partial rollback and new packed profile counterexamples, resealed without source actions."""
import hashlib,json,struct,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;raw=(r/'lab/out/oracle19-partial-rows-20261005/archive-1-32.dbf').read_bytes();assert hashlib.sha256(raw).hexdigest()=='357ee00bc9877b1eb89f9e6d7c2d5f80d1fd1300c78261fb199435422c712dc8'
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
update=vectors(1014,108);marker=vectors(1011,64)[1];end=vectors(1014,396)[0];delete=vectors(1018,148)[4]
assert update[0][0]==(5,1) and update[1][0]==(11,5) and marker[0]==(5,6) and end[0]==(5,4) and delete[0]==(5,1)
assert bytes(raw[i] for i in update[1][2][3])==bytes.fromhex('0c7061727469616c2d6e6f7465') and bytes(raw[i] for i in delete[2][6])==b'partial-note'
cases={'unknown_short_control':[(update[1][2][1][26],32)],'wrong_short_map':[(update[1][2][2][0],1)],'short_prefix_length':[(update[0][2][5][0],13)],'wrong_cancel_slot':[(marker[2][0][18],5)],'missing_cancel_marker':[(marker[1][1],7)],'wrong_commit_tail':[(end[2][1][6],27)],'late_delete_before':[(delete[2][6][0],120)]};result=[]
for name,changes in cases.items():
 bad=bytearray(raw)
 for at,value in changes:bad[at]=value
 for block in {at//512 for at,_ in changes}:
  start=block*512;struct.pack_into('<H',bad,start+14,0);checksum=0
  for word in struct.unpack('<256H',bad[start:start+512]):checksum^=word
  struct.pack_into('<H',bad,start+14,checksum)
 p=out/(name+'.dbf');p.write_bytes(bad);result.append({'case':name,'changes':changes,'bytes':len(bad),'sha256':hashlib.sha256(bad).hexdigest()})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':result},indent=2)+'\n')
