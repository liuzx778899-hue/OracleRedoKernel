"""Reseal real seq29 empty cleanout counterexamples without modifying any source database."""
import hashlib,json,struct,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;raw=(r/'lab/out/oracle19-snapshot-boundary-20261004/archive-1-29.dbf').read_bytes()
assert hashlib.sha256(raw).hexdigest()=='9fe4e3eb83a3f0d8c7b9fe7506691a7473887d3fa184fb735cf5d6cf41c56934'
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=False);start=2*512+16;record=raw[start:start+120];pos=68
assert record[pos:pos+2]==bytes([4,1]);table=struct.unpack_from('<H',record,pos+32)[0];field=start+pos+32+((table+3)//4)*4
assert struct.unpack_from('<H',record,pos+34)[0]==16 and raw[field:field+16]==bytes.fromhex('01000d00d5c923000080000000000000')
cases={'nonempty_entries':(1,1),'unknown_flags':(2,15),'wrong_scn':(4,214),'nonzero_spare':(10,1)};result=[]
for name,(offset,value) in cases.items():
 bad=bytearray(raw);bad[field+offset]=value;block=2*512;struct.pack_into('<H',bad,block+14,0);checksum=0
 for word in struct.unpack('<256H',bad[block:block+512]):checksum^=word
 struct.pack_into('<H',bad,block+14,checksum);p=out/(name+'.dbf');p.write_bytes(bad)
 result.append({'case':name,'bytes':len(bad),'sha256':hashlib.sha256(bad).hexdigest(),'changed_offset':field+offset})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':result},indent=2)+'\n')
