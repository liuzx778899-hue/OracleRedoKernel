"""Reseal finite seq24 counterexamples. Writes copies only; never executes source DML."""
import hashlib
import json
import struct
import sys
from pathlib import Path

source=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir()
raw=source.read_bytes()
if hashlib.sha256(raw).hexdigest()!='def82f0b1009a60142150e90726bfa8482d55c0a1a8a617603e4e3cf58395b09':raise RuntimeError('FROZEN_POISON_SOURCE_MISMATCH')
def vectors(block,offset):
    length=struct.unpack_from('<I',raw,block*512+offset)[0];mapping=[]
    while len(mapping)<length:
        count=min(length-len(mapping),512-offset);mapping.extend(range(block*512+offset,block*512+offset+count));block+=1;offset=16
    record=bytes(raw[i] for i in mapping);pos=68 if record[4]&4 else 24;result=[]
    while pos<len(record):
        table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
        for f in range(pos+34,pos+32+table,2):
            size=struct.unpack_from('<H',record,f)[0];fields.append(mapping[data:data+size]);data+=((size+3)//4)*4
        result.append({'opcode':tuple(record[pos:pos+2]),'header':mapping[pos:pos+32],'fields':fields});pos=data
    return result
originals=vectors(181,328);b_cancel=vectors(182,456);a_cancel=vectors(185,16);a_heap=vectors(185,240)
assert originals[3]['opcode']==(5,1) and originals[4]['opcode']==(5,1)
assert b_cancel[1]['opcode']==(5,6) and a_cancel[1]['opcode']==(5,6)
assert a_heap[0]['opcode'] in ((11,2),(11,3),(11,5))
def xor(at):return [(at,raw[at]^1)]
cases={
    'omit_B_originals':[(originals[i]['header'][1],0) for i in (3,4)],
    'omit_B_begin_node':[(originals[3]['header'][1],0)],
    'omit_foreign_marker':[(b_cancel[1]['header'][1],0)],
    'wrong_begin_owner':xor(a_heap[1]['header'][8]),
    'wrong_A_heap_slot':xor(a_heap[0]['fields'][1][42 if a_heap[0]['opcode'][1]==2 else 16 if a_heap[0]['opcode'][1]==3 else 20]),
}
bm=b_cancel[1]['fields'][0];am=a_cancel[1]['fields'][0]
assert len(bm)==len(am)
cases['repeat_closed_B_marker']=[(a,raw[b]) for a,b in zip(am,bm)]
summary=[]
for name,changes in cases.items():
    bad=bytearray(raw)
    for at,value in changes:bad[at]=value
    for block in sorted({at//512 for at,_ in changes}):
        start=block*512;struct.pack_into('<H',bad,start+14,0);check=0
        for word in struct.unpack('<256H',bad[start:start+512]):check^=word
        struct.pack_into('<H',bad,start+14,check)
    path=out/(name+'.dbf');path.write_bytes(bad)
    summary.append({'name':name,'sha256':hashlib.sha256(bad).hexdigest(),'changes':changes})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'cases':summary},indent=2),encoding='utf-8')
print(json.dumps({'derived_cases':len(summary),'scope':'finite source-bound byte counterexamples, not source operations'}))
