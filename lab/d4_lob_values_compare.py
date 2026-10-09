"""Reconstruct fixture LOB bytes from raw only; SQL/native remain independent comparators, never event inputs."""
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2'
typed=sys.argv[1:]==['--typed-locators'];assert typed or not sys.argv[1:];N=F/('inventory-r6' if typed else 'inventory-r5')
pages=[];images=[];fills=[];locators=[];typedLocators=[];pending=None
for line in (N/'inventory.stdout').read_text().splitlines():
 f=line.split('|')
 if f[0]=='RECORD':scn=int(f[4])
 if f[0]=='PAGE':
  pending=dict(scn=scn,position=list(map(int,f[1:5])),object=int(f[5]),lid=f[6],version=int(f[7]),page=int(f[8]),payload=bytes.fromhex(f[11]));pages.append(pending)
 if f[0]=='VECTOR' and pending:
  assert f[2]=='19.1';pending['dba']=int(f[3]);pending=None
 if f[0]=='IMAGE':images.append(dict(scn=scn,position=list(map(int,f[1:5])),object=int(f[5]),lid=f[6],dba=int(f[7]),pageScn=int(f[8]),xid=f[9],payload=bytes.fromhex(f[10])))
 if f[0]=='FILL':fills.append(dict(scn=scn,position=list(map(int,f[1:5])),dba=int(f[5]),lid=f[6],offset=int(f[8]),payload=bytes.fromhex(f[11])))
 if f[0]=='LOCATOR':locators.append(dict(scn=int(f[5]),dba=int(f[6]),slot=int(f[7]),col=int(f[9]),state=f[10],bytes=bytes.fromhex(f[11])))
 if f[0]=='LOB':typedLocators.append(dict(scn=int(f[1]),dba=int(f[2]),slot=int(f[3]),col=int(f[4]),lid=f[7],length=int(f[8]),pages=list(map(int,f[10].split(','))) if f[10] else [],inline=None if f[11]=='-' else bytes.fromhex(f[11])))
# Independently parse full native BIMG fields and bytes; no native value enters the reconstruction.
native=[];section=[];selected=False;position=None
def finish():
 if not selected:return
 text='\n'.join(section)
 get=lambda pattern:re.search(pattern,text)[1]
 payload=[]
 for line in re.split(r'KDLI data load \[[^\n]+\]\n',text)[1].splitlines():
  if not re.fullmatch(r'\s*(?:[0-9a-f]{2}\s+)+[0-9a-f]{2}\s*',line):break
  payload.extend(line.split())
 xid=re.search(r'\n  xid\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',text)
 native.append(dict(position=position+[change],object=int(get(r'\n  objd\s+(\d+)')),lid=get(r'\n  lid\s+([0-9a-f]{20})'),dba=int(get(r'\n  dba\s+0x([0-9a-f]+)'),16),pageScn=int(get(r'\n  scn\s+0x([0-9a-f]+)'),16),xid='.'.join(str(int(x,16)) for x in xid.groups()),payload=bytes.fromhex(''.join(payload))))
for line in (F/'97.trc').read_text().splitlines()+['END']:
 if 'REDO RECORD -' in line or 'CHANGE #' in line or line=='END':finish();selected=False;section=[]
 m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
 if m:position=[int(v,16) for v in m.groups()]
 m=re.search(r'CHANGE #(\d+).*CON_ID:3 .*OP:26\.6 ',line)
 if m:selected=True;change=int(m[1])
 if selected:section.append(line)
assert len(images)==len(native)==4
assert [{k:v for k,v in image.items() if k!='scn'} for image in images]==native
comparisons=[]
for table,heap in [('T_D4_LOBB_20261009',50365967),('T_D4_LOBS_20261009',50366015)]:
 for stage in ['large','partial','trim','rollback']:
  cutoff=int(re.search(r'SCN\|(\d+)',(F/(table+'-'+stage+'-scn.stdout')).read_text())[1])
  truth=json.loads((F/(table+'-'+stage+'-truth.json')).read_text());assert len(truth)==1
  for col,tag in [(1,'BHEX'),(2,'CHEX')]:
   locator=[x for x in locators if x['dba']==heap and x['slot']==0 and x['col']==col and x['scn']<=cutoff][-1]
   assert locator['state']=='VALUE';b=locator['bytes'];lid=b[10:20].hex()
   if typed:
    decoded=[x for x in typedLocators if x['dba']==heap and x['slot']==0 and x['col']==col and x['scn']<=cutoff][-1]
    assert decoded['lid']==lid;length=decoded['length'];pointers=decoded['pages'];assert decoded['inline'] is None
   if heap==50365967:
    assert b[22]==5 and len(b) in (40,44)
    length=int.from_bytes(b[26:28],'big')*8132+int.from_bytes(b[28:30],'big')
    rawPointers=[int.from_bytes(b[i:i+4],'big') for i in range(36,len(b),4)]
    if typed:assert pointers==rawPointers and length==decoded['length']
    else:pointers=rawPointers
    parts=[]
    for pageNo,dba in enumerate(pointers):
     page=[x for x in pages if x['dba']==dba and x['lid']==lid and x['scn']<=cutoff][-1]
     assert page['page']==pageNo;parts.append(page['payload'])
    value=b''.join(parts)[:length];assert len(value)==length
   else:
    assert len(b)==38 and b[22:27]==bytes.fromhex('4090000c21')
    length=int.from_bytes(b[27:30],'big');first=int.from_bytes(b[33:37],'big');count=b[37]
    if typed:assert pointers==list(range(first,first+count)) and length==decoded['length']
    parts=[]
    for dba in range(first,first+count):
     image=[x for x in images if x['dba']==dba and x['lid']==lid and x['scn']<=cutoff][-1]
     page=bytearray(image['payload'])
     for fill in fills:
      if fill['dba']!=dba or fill['lid']!=lid or fill['scn']>cutoff or fill['position']<image['position']:continue
      end=fill['offset']+len(fill['payload']);assert fill['offset']<=len(page),'unproven gap'
      if end>len(page):page.extend(bytes(end-len(page)))
      page[fill['offset']:end]=fill['payload']
     parts.append(bytes(page))
    value=b''.join(parts)[:length];assert len(value)==length
   # Fixture CLOB byte stream is big-endian UTF-16; strict decoding, never replacement.
   logical=value if col==1 else value.decode('utf-16-be').encode('utf-8')
   expected=bytes.fromhex(truth[0][tag]['hex']);assert logical==expected,(table,stage,col,len(logical),len(expected))
   comparisons.append(dict(table=table,stage=stage,column=col,rawBytes=len(value),logicalBytes=len(logical),sha256=hashlib.sha256(logical).hexdigest(),mismatch=0))
proof=dict(pageImages=4,imagePayloadBytes=sum(len(x['payload']) for x in images),fullValues=comparisons,mismatches=0,events=0,transactionAuthority=False,baselineApproved=False,role='DIAGNOSTIC_RECONSTRUCTION_ONLY')
(N/'full-values-comparison.json').write_text(json.dumps(proof,indent=2));print(json.dumps({k:v for k,v in proof.items() if k!='fullValues'}))
