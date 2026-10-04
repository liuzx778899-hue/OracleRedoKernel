"""Derive resealed seq34 cleanout and late before-image counterexamples; no source database actions."""
import hashlib,json,struct,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;raw=(r/'lab/out/oracle19-concurrent-rows-20261005/archive-1-34.dbf').read_bytes();assert hashlib.sha256(raw).hexdigest()=='a9a34a12d666aa62a5ed9d92e6be62b9b4018677ecad8fcf19f46dc968c5fc75'
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
clean=vectors(14,24)[0];delete=vectors(13,16)[2]
assert clean[0]==(4,1) and len(clean[2])==1 and bytes(raw[i] for i in clean[2][0]).hex()=='02010d00a2d82300008000000206008096d82300000000000000'
assert delete[0]==(5,1) and bytes(raw[i] for i in delete[2][5])==b'concurrent-b'
field=clean[2][0]
cases={'cleanout_count':[(field[1],2)],'cleanout_itl':[(field[12],3)],'cleanout_flags':[(field[13],7)],'cleanout_scn':[(field[4],163)],'cleanout_future_prior':[(field[16],255)],'cleanout_tail':[(field[24],1)],'late_delete_before':[(delete[2][5][0],120)]};result=[]
for name,changes in cases.items():
 bad=bytearray(raw)
 for at,value in changes:bad[at]=value
 for block in {at//512 for at,_ in changes}:
  start=block*512;struct.pack_into('<H',bad,start+14,0);checksum=0
  for word in struct.unpack('<256H',bad[start:start+512]):checksum^=word
  struct.pack_into('<H',bad,start+14,checksum)
 p=out/(name+'.dbf');p.write_bytes(bad);result.append({'case':name,'changes':changes,'bytes':len(bad),'sha256':hashlib.sha256(bad).hexdigest()})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':result},indent=2)+'\n')
