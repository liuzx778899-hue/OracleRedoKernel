"""Compare typed rows with complete native image bytes and independently decoded scalar/rowid."""
import collections,json,re,struct,sys
from fractions import Fraction
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/index-leaf-rows-candidate-20261006';H=R/'lab/out/v3-product-history-20261006'
if len(sys.argv)>1:
    assert sys.argv[1:]==['r2'];N=R/'lab/out/index-leaf-rows-r2-20261006'
assert not (N/'native-leaf-proof.json').exists()
(N/'executed-native-leaf-driver.py').write_bytes(Path(__file__).read_bytes())
def memory(section):
    m=re.search(r'Dump of memory from 0x([0-9A-F]+) to 0x([0-9A-F]+)',section);start,end=map(lambda x:int(x,16),m.groups());found={}
    for line in section.splitlines():
        address=re.match(r'^([0-9A-F]+) ',line)
        if not address:continue
        base=int(address[1],16);prefix=address.end();hexpart=line.split('[',1)[0]
        for word in re.finditer(r'\b(?:[0-9A-F]{8}|[0-9A-F]{4}|[0-9A-F]{2})\b',hexpart[prefix:]):
            at=base+(word.start()//9)*4
            for i,b in enumerate(bytes.fromhex(word[0])[::-1]):
                if start<=at+i<end:found[at+i]=b
    assert len(found)==end-start
    return bytes(found[i] for i in range(start,end))
native=(H/'native-discovery-70-126593-240.txt').read_text().split('(8032):',1)[1].split('CHANGE #3',1)[0]
body=bytes.fromhex(' '.join(line.strip() for line in native.splitlines()));assert len(body)==8032
count=struct.unpack_from('<H',body,8)[0];directory=struct.unpack_from('<'+'H'*count,body,36)
raw={'BEFORE':[body[a:b] for a,b in zip(directory,(*directory[1:],len(body)))]}
for kind,name in [('NEW','native-maintenance-70-126610-328.txt'),('OLD','native-maintenance-70-126620-204.txt')]:
    text=(H/name).read_text();index_section=text.split('dumping row index\n',1)[1].split('dumping rows\n',1)[0]
    index=memory(index_section);data=memory(text.split('dumping rows\n',1)[1]);offsets=struct.unpack('<'+'H'*(len(index)//2),index)
    positions=[o-offsets[0]-2*i for i,o in enumerate(offsets)];assert positions[-1]==len(data)
    raw[kind]=[data[a:b] for a,b in zip(positions,positions[1:])]
def number(value):
    assert value[0]>=128
    result=sum((Fraction(b-1)*Fraction(100)**(value[0]-193-i) for i,b in enumerate(value[1:])),Fraction(0))
    assert result.denominator==1 and result>0;return int(result)
def check(line):
    p=line.split('|');kind,slot=p[1],int(p[2]);b=raw[kind][slot]
    assert p[9]==b.hex() and int(p[3])==b[0] and int(p[4])==b[1]
    a=8;parent=b[a+1:a+1+b[a]];a+=1+b[a];column=b[a+1:a+1+b[a]];assert a+1+b[a]==len(b)
    assert [int(x) for x in p[5:9]]==[number(parent),number(column),int.from_bytes(b[2:6],'big'),int.from_bytes(b[6:8],'big')]
lines=[s for s in (N/'real-leaves.stdout.txt').read_text().splitlines() if s.startswith('ROW|')]
if len(sys.argv)>1:
    assert 'IMAGE|OLD|4215085|null|4215087|226' in (N/'real-leaves.stdout.txt').read_text()
assert len(lines)==sum(map(len,raw.values()))==891
for line in lines:check(line)
removed=collections.Counter(raw['BEFORE'])-collections.Counter(raw['NEW']+raw['OLD']);added=collections.Counter(raw['NEW']+raw['OLD'])-collections.Counter(raw['BEFORE'])
assert not removed and sum(added.values())==1 and raw['OLD'][7]==next(iter(added)) and raw['OLD'][7][:2]==b'\1\1'
negatives=[]
for name,at,value in [('wrong_raw',9,'00'),('wrong_number',5,'1'),('wrong_rowid',7,'1'),('wrong_flags',3,'1')]:
    p=lines[0].split('|');p[at]=value
    try:check('|'.join(p))
    except AssertionError:negatives.append(name)
    else:raise AssertionError(name+' not detected')
out=dict(diagnostic_only=True,events=0,rows_compared=891,counts={k:len(v) for k,v in raw.items()},native_byte_and_scalar_differences=0,removed_rows=0,added_deleted_rows=1,restored_slot=7,raw_deleted_row=raw['OLD'][7].hex(),negative_detected=negatives,complete_split=False)
(N/'native-leaf-proof.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
