"""Swap two complete forward vectors in frozen seq25 and reseal block checksums; no source database action."""
from pathlib import Path
import struct,hashlib,json,sys
r=Path(__file__).resolve().parent.parent;s=Path(sys.argv[1]);s.mkdir(parents=True,exist_ok=True);raw=(r/'lab/out/oracle19-baseline-20261004/archive-1-25.dbf').read_bytes();assert hashlib.sha256(raw).hexdigest()=='6d593f143521236722386196a5f0908459419d305c1364d968637599d0b109fe'
mapping=[];b=11164;o=16;length=struct.unpack_from('<I',raw,b*512+o)[0]
while len(mapping)<length:
 take=min(length-len(mapping),512-o);mapping.extend(range(b*512+o,b*512+o+take));b+=1;o=16
record=bytes(raw[i] for i in mapping);pos=68;vectors=[]
while pos<len(record):
 start=pos;table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4
 for f in range(pos+34,pos+32+table,2):data+=((struct.unpack_from('<H',record,f)[0]+3)//4)*4
 vectors.append(record[start:data]);pos=data
assert tuple(vectors[0][:2])==(11,5) and tuple(vectors[3][:2])==(11,5)
vectors[0],vectors[3]=vectors[3],vectors[0];replacement=record[:68]+b''.join(vectors);assert len(replacement)==len(record)
bad=bytearray(raw)
for at,value in zip(mapping,replacement):bad[at]=value
for block in {p//512 for p in mapping}:
 start=block*512;struct.pack_into('<H',bad,start+14,0);checksum=0
 for word in struct.unpack('<256H',bad[start:start+512]):checksum^=word
 struct.pack_into('<H',bad,start+14,checksum)
dst=s/'wrong-row-order.dbf';assert not dst.exists();dst.write_bytes(bad);(s/'wrong-row-order.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'derived_sha256':hashlib.sha256(bad).hexdigest(),'swapped_forward_vectors':[1,4]}))
