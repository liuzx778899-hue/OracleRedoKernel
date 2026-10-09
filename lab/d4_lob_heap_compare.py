"""Independent native heap original-owner coverage and SQL inline INSERT value comparison."""
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2'
assert len(sys.argv)==3 and sys.argv[1]=='--run' and re.fullmatch(r'inventory-r[1-9][0-9]*',sys.argv[2])
N=F/sys.argv[2];raw=[];inline=[];nulls=[]
assert hashlib.sha256((F/'97.trc').read_bytes()).hexdigest()=='807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472'
for line in (N/'inventory.stdout').read_text().splitlines():
 f=line.split('|')
 if f[0]=='HEAPPAIR':raw.append(dict(position=list(map(int,f[1:4])),object=int(f[5]),xid=f[6],uba=list(map(int,f[7:10]))))
 if f[0]=='LOB' and int(f[2]) in (50365966,50366011):
  assert f[11]!='-';inline.append(dict(dba=int(f[2]),col=int(f[4]),length=int(f[8]),value=bytes.fromhex(f[11])))
 if f[0]=='LOCATOR' and int(f[6]) in (50365966,50366011) and f[10]=='NULL':nulls.append((int(f[6]),int(f[9])))
native=[];section=[];selected=False;position=None
def finish():
 if not selected:return
 text='\n'.join(section)
 obj=re.search(r'flg: 0x([0-9a-f]+).*opc: 11\.1 \[objn: (79334|79340) objd: (\d+) tsn: 5\]',text)
 if not obj or int(obj[1],16)&0x10:return
 xid=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',text)
 seq=re.search(r'ktudb redo:.*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',text)
 assert xid and seq and obj[2]==obj[3]
 native.append(dict(position=position,object=int(obj[2]),xid='.'.join(str(int(x,16)) for x in xid.groups()),uba=[dba,int(seq[1],16),int(seq[2],16)]))
for line in (F/'97.trc').read_text().splitlines()+['END']:
 if 'REDO RECORD -' in line or 'CHANGE #' in line or line=='END':finish();selected=False;section=[]
 m=re.search(r'REDO RECORD -.*RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
 if m:position=[int(x,16) for x in m.groups()]
 m=re.search(r'CHANGE #\d+ CON_ID:3 .* DBA:0x([0-9a-f]+).*OP:5\.1 ',line)
 if m:selected=True;dba=int(m[1],16)
 if selected:section.append(line)
key=lambda x:(x['position'],x['uba'])
assert raw and len(raw)==len(native),(len(raw),len(native))
assert sorted(raw,key=key)==sorted(native,key=key)
poison=[dict(x) for x in raw];poison[0]['xid']='10.19.4251';assert sorted(poison,key=key)!=sorted(native,key=key)
assert len(inline)==8 and len(nulls)==4,(len(inline),len(nulls))
for table,heap in [('T_D4_LOBB_20261009',50365966),('T_D4_LOBS_20261009',50366011)]:
 for stage in ['insert','empty','null']:
  truth=json.loads((F/(table+'-'+stage+'-truth.json')).read_text());row=next(x for x in truth if x['id']==2)
  for col,tag in [(1,'BHEX'),(2,'CHEX')]:
   if stage=='null':
    assert (heap,col) in nulls and row[tag] is None;continue
   item=next(x for x in inline if x['dba']==heap and x['col']==col and (x['length']==0)==(stage=='empty'))
   value=item['value'] if col==1 else item['value'].decode('utf-16-be','strict').encode('utf-8')
   assert len(item['value'])==item['length'] and value.hex()==row[tag]['hex']
   assert hashlib.sha256(value).hexdigest()==row[tag]['sha256']
proof=dict(originalPairs=len(raw),fullNativeOwnerMismatch=0,inlineInsertValues=4,fullSqlValueMismatch=0,
           emptyValues=4,nullValues=4,poisonDetected=1,events=0,transactionGraphAccepted=False)
p=N/'heap-comparison.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
