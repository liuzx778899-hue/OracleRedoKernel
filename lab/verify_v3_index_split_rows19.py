"""Diagnostic row conservation and native-byte comparison; not production lineage."""
import collections,hashlib,json,re,struct
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006'
assert not (H/'index-split-rows-research.json').exists()
(H/'executed-index-split-rows-driver.py').write_bytes(Path(__file__).read_bytes())
fields={tuple(s.split('|')[1:4]):bytes.fromhex(s.split('|')[4]) for s in (H/'maintenance-research.stdout.txt').read_text().splitlines() if s.startswith('FIELD|')}
def field(at,v,f):return fields[(at,str(v),str(f))]
def key(row):
    assert row[:2] in [b'\0\0',b'\1\1'] and len(row)>=12
    at=8
    for _ in range(2):
        size=row[at];assert 0<size<250 and at+1+size<=len(row);at+=1+size
    assert at==len(row)
    return row[8:]
def image(data):
    assert len(data)==8032
    n=struct.unpack_from('<H',data,8)[0];start=36+2*n
    offsets=struct.unpack_from('<'+'H'*n,data,36)
    assert offsets[0]==start and tuple(sorted(set(offsets)))==offsets
    rows=[data[o:e] for o,e in zip(offsets,(*offsets[1:],len(data)))]
    for row in rows:key(row);assert row[:2]==b'\0\0'
    return rows
def leaf(directory,data):
    assert len(directory)%2==0
    offsets=struct.unpack('<'+'H'*(len(directory)//2),directory);n=len(offsets)-1
    assert n>0 and all(b>a+2 for a,b in zip(offsets,offsets[1:]))
    positions=[o-offsets[0]-2*i for i,o in enumerate(offsets)]
    assert positions[-1]==len(data)
    rows=[data[a:b] for a,b in zip(positions,positions[1:])]
    for row in rows:key(row)
    return rows
def native_rows(path):
    text=path.read_text();section=text.split('dumping rows\n',1)[1]
    m=re.search(r'Dump of memory from 0x([0-9A-F]+) to 0x([0-9A-F]+)',section);start,end=map(lambda x:int(x,16),m.groups());found={}
    for line in section.splitlines():
        address=re.match(r'^([0-9A-F]+) ',line)
        if not address:continue
        base=int(address[1],16);prefix=address.end();hexpart=line.split('[',1)[0]
        for word in re.finditer(r'\b(?:[0-9A-F]{8}|[0-9A-F]{4}|[0-9A-F]{2})\b',hexpart[prefix:]):
            at=base+(word.start()//9)*4
            raw=bytes.fromhex(word[0])[::-1]
            for i,b in enumerate(raw):
                if start<=at+i<end:found[at+i]=b
    assert len(found)==end-start
    return bytes(found[i] for i in range(start,end))
before_bytes=field('70:126593:240',2,5);before=image(before_bytes)
native= (H/'native-discovery-70-126593-240.txt').read_text().split('(8032):',1)[1].split('CHANGE #3',1)[0]
assert bytes.fromhex(' '.join(line.strip() for line in native.splitlines()))==before_bytes
new_data=field('70:126610:328',3,4);old_data=field('70:126620:204',2,4)
assert native_rows(H/'native-maintenance-70-126610-328.txt')==new_data
assert native_rows(H/'native-maintenance-70-126620-204.txt')==old_data
new=leaf(field('70:126610:328',3,3),new_data);old=leaf(field('70:126620:204',2,3),old_data)
def conservation(before,new,old):
    removed=collections.Counter(before)-collections.Counter(new+old)
    added=collections.Counter(new+old)-collections.Counter(before)
    assert not removed and sum(added.values())==1
    row=next(iter(added));assert row[:2]==b'\1\1' and old[7]==row
    assert key(row)==field('70:126628:476',1,5)
    descriptor=field('70:126628:476',2,2)
    assert descriptor==bytes.fromhex('020007001200') and len(row)+2==18
    return row
extra=conservation(before,new,old)
negatives=[]
for name,values in [('lost_row',(before,new[1:],old)),('duplicate_row',(before,new+[new[0]],old)),('slot_swap',(before,new,old[:7]+[old[8],old[7]]+old[9:])),('wrong_key',(before,new,old[:7]+[extra[:-1]+b'\x06']+old[8:]))]:
    try:conservation(*values)
    except AssertionError:negatives.append(name)
    else:raise AssertionError('counterexample not detected: '+name)
out=dict(diagnostic_only=True,events=0,before_rows=len(before),new_leaf_rows=len(new),old_leaf_rows=len(old),removed_rows=0,added_deleted_rows=1,restored_slot=7,raw_key=key(extra).hex(),raw_rowid=extra[2:8].hex(),native_byte_differences=0,negative_detected=negatives,scope='row conservation only; transaction, links and branch coverage not yet verified')
(H/'index-split-rows-research.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
