"""Resealed seq25 extent/pair counterexamples; frozen copies only, no Oracle actions."""
import hashlib,json,struct,sys
from pathlib import Path
raw=Path(sys.argv[1]).read_bytes();out=Path(sys.argv[2]);out.mkdir()
if hashlib.sha256(raw).hexdigest()!='6d593f143521236722386196a5f0908459419d305c1364d968637599d0b109fe':raise RuntimeError('FROZEN_TAIL_SOURCE_MISMATCH')
block=11164;offset=16;length=struct.unpack_from('<I',raw,block*512+offset)[0];mapping=[]
while len(mapping)<length:
    count=min(length-len(mapping),512-offset);mapping.extend(range(block*512+offset,block*512+offset+count));block+=1;offset=16
record=bytes(raw[i] for i in mapping);pos=68 if record[4]&4 else 24;vectors=[]
while pos<len(record):
    table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
    for f in range(pos+34,pos+32+table,2):
        size=struct.unpack_from('<H',record,f)[0];fields.append(mapping[data:data+size]);data+=((size+3)//4)*4
    vectors.append({'op':tuple(record[pos:pos+2]),'header':mapping[pos:pos+32],'fields':fields});pos=data
tail=vectors[2];undo=vectors[6];ordinary=vectors[3]
assert tail['op']==(11,5) and undo['op']==(5,1) and len(tail['fields'][1])==28
cases={
    'wrong_tail_map':[(tail['fields'][2][0],1)],
    'wrong_forward_slot':[(tail['fields'][1][20],raw[tail['fields'][1][20]]^1)],
    'missing_undo_node':[(undo['header'][1],0)],
    'wrong_inverse_slot':[(undo['fields'][3][20],raw[undo['fields'][3][20]]^1)],
    'wrong_size_delta':[(tail['fields'][1][24],250)],
    'wrong_inverse_map':[(undo['fields'][4][0],1)],
    'duplicate_forward_uba':[(tail['fields'][0][14],38)],
    'ncol_over_schema':[(ordinary['fields'][1][22],4)],
}
results=[]
for name,changes in cases.items():
    bad=bytearray(raw)
    for at,value in changes:bad[at]=value
    for block in {at//512 for at,_ in changes}:
        start=block*512;struct.pack_into('<H',bad,start+14,0);check=0
        for word in struct.unpack('<256H',bad[start:start+512]):check^=word
        struct.pack_into('<H',bad,start+14,check)
    (out/(name+'.dbf')).write_bytes(bad);results.append({'name':name,'changes':changes,'sha256':hashlib.sha256(bad).hexdigest()})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':results},indent=2),encoding='utf-8');print('derived',len(results))
