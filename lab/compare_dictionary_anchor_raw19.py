"""Raw SYS positions versus actual acquired anchors and independent native dump; no source actions."""
import hashlib,json,re,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-anchor-real-20261005';OLD=O
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
b=json.loads((O/'baseline-reference.json').read_text());lines=Path(b['path']).read_text().splitlines();roles={};anchors={}
for line in lines:
 p=line.split('|')
 if p[0]=='DICT_ROLE' and p[1]=='BEFORE':roles[int(p[4])]=(p[3],int(p[5]))
 if p[0]=='DICT_ANCHOR':anchors[(p[1],int(p[3]),(int(p[4])<<22)|int(p[5]),int(p[6]))]=int(p[2])
(O/'dictionary-comparison-pin-r2.json').write_text(json.dumps(dict(script_sha256=sha(Path(__file__)),acquisition_sha256=b['sha256'],native_dump_sha256=sha(O/'dump.trc'),event_input=False)))
rows=[];unknown=[];unanchored=[]
for seq in [59,60]:
 path=OLD/'capture'/('sequence-'+str(seq)+'.oracap');args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(O/'kernel.jar')+';'+str(O/'parser-classes'),'CapturedRecordsProbe',str(path),sha(path),'vectors'];name='dictionary-raw-vectors-r2-'+str(seq);(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=120)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0
 recordscns={}
 for line in (O/('actual-structure-'+str(seq)+'.stdout.txt')).read_text().splitlines():
  columns=line.split('|');recordscns[(int(columns[0]),int(columns[1]))]=int(columns[4])
 for line in p.stdout.decode().splitlines():
  f=line.split('|');pos=(int(f[0]),int(f[1]));scn=recordscns[pos]
  if scn<=b['scn'] or f[3]!='5.1' or f[9]!='3':continue
  fields=[bytes.fromhex(h) for h in f[10].split(',')]
  if len(fields)<4 or len(fields[0])!=20 or len(fields[1])<24:continue
  logical,data,tsn=struct.unpack_from('<III',fields[1]);role=roles.get(logical)
  if role is None:continue
  assert data==role[1] and tsn==0
  kdo=fields[3]
  if len(kdo)<12 or kdo[10] not in [2,3,5]:unknown.append(dict(sequence=seq,position=pos,object=logical,profile=kdo.hex()));continue
  slotat={2:42,3:16,5:20}[kdo[10]]
  if len(kdo)<slotat+2:unknown.append(dict(sequence=seq,position=pos,object=logical,profile=kdo.hex()));continue
  dba=struct.unpack_from('<I',kdo)[0];slot=struct.unpack_from('<H',kdo,slotat)[0];target=anchors.get((role[0],data,dba,slot))
  xid=struct.unpack_from('<HHI',fields[0],8);item=dict(sequence=seq,block=pos[0],offset=pos[1],vector=int(f[2]),scn=scn,dictionary=role[0],logical=logical,data=data,dba=dba,slot=slot,target_object=target,xid='.'.join(map(str,xid)),undo_kdo_opcode=kdo[10])
  (rows if target is not None else unanchored).append(item)
# Native dump is a second, independent ruler for every raw-derived selected row.
native={}
for record in re.split(r'(?=REDO RECORD - Thread:)',(OLD/'dump.trc').read_text()):
 m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
 if not m:continue
 key=tuple(int(x,16) for x in m.groups());native[key]=record
errors=[]
for row in rows+unanchored:
 record=native[(row['sequence'],row['block'],row['offset'])];changes=re.split(r'(?=CHANGE #\d+)',record)[1:];change=next(c for c in changes if c.startswith('CHANGE #'+str(row['vector'])+' '))
 if '[objn: '+str(row['logical'])+' objd: '+str(row['data'])+' tsn: 0]' not in change:errors.append('role')
 if not re.search(r'bdba:\s+0x'+format(row['dba'],'08x')+r'\b',change) or not re.search(r'slot:\s*'+str(row['slot'])+r'\b',change):errors.append(dict(reason='row_position',row=row,native_change=change))
(O/'dictionary-native-comparison-current.json').write_text(json.dumps(dict(rows=rows,unanchored=unanchored,errors=errors),indent=2))
assert rows and not errors,errors
assert {r['dictionary'] for r in rows} >= {'OBJ$','TAB$'}
assert any(r['sequence']==59 for r in rows) and any(r['sequence']==60 for r in rows)
owned={r['xid'] for r in rows};linked=[r for r in unanchored if r['xid'] in owned]
assert any(r['dictionary']=='COL$' for r in linked)
(O/'dictionary-association-proof.json').write_text(json.dumps(dict(raw_selected_rows=rows,unanchored_rows_same_XID=linked,native_mismatches=errors,unsupported_inventory_profiles=unknown,scope='physical row-position and raw XID association only; not full SYS transaction decoding',anchors_from_actual_V2_acquisition=True,independent_same_SCN_SQL_differences=0,raw_dependency_signal_survives_marker_mutation=True,historical_dictionary_accepted=False),indent=2));print('RAW_DICTIONARY_TARGET_ANCHORS_NATIVE_MATCH|'+str(len(rows))+'|LINKED_NEW_ROWS|'+str(len(linked)))
