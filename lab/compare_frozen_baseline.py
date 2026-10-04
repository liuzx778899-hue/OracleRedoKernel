"""Independent projection of the actual acquisition output and raw archived source header; no Java metadata as expectation."""
import hashlib,json,re,struct,sys
from pathlib import Path
snapshot,archive,actual=map(Path,sys.argv[1:4]);digest=hashlib.sha256(snapshot.read_bytes()).hexdigest();frames=[line.split('|') for line in snapshot.read_text().splitlines() if line.strip()];by={}
for f in frames:by.setdefault(f[0],[]).append(f)
source=by['SOURCE'][0];pdb=by['PDB'][0];scn=by['SNAPSHOT_SCN'][0][1];header=archive.read_bytes()[512:1024]
word=lambda offset:struct.unpack_from('<I',header,offset)[0]
half=lambda offset:struct.unpack_from('<H',header,offset)[0]
expected_source=[str(word(24)),str(word(52)),str(word(160)),str(word(164)+((half(168)&0x7fff)<<32)),str(half(176))]
errors=[]
if source[1:6]!=expected_source:errors.append({'kind':'actual_query_vs_raw_source','query':source[1:6],'raw':expected_source})
if len(sys.argv)>4:
 trace=Path(sys.argv[4]).read_text();reset=re.search(r'resetlogs count: 0x([0-9a-f]+) scn: 0x([0-9a-f]+)',trace)
 native=[re.search(r'Db ID=(\d+)',trace)[1],re.search(r'Activation ID=(\d+)',trace)[1],str(int(reset[1],16)),str(int(reset[2],16)),re.search(r'\n thread: (\d+)',trace)[1]]
 if native!=expected_source:errors.append({'kind':'native_dump_vs_raw_source','native':native,'raw':expected_source})
heap=next(f for f in by['HEAP'] if f[1]=='BEFORE');index=next(f for f in by['INDEX'] if f[1]=='BEFORE')
expected=[['SNAPSHOT',scn,digest,source[1],pdb[1],pdb[2]],['HEAP',heap[4],heap[5]],['INDEX',index[4],index[5]]]
for c in by['COLUMN']:
 if c[1]=='BEFORE':expected.append(['COLUMN',c[2],c[3],c[4],c[5],str(c[6]=='Y').lower(),str(c[7]=='YES').lower()])
for row in sorted(by.get('ROW',[]),key=lambda f:(int(f[2])<<22)|int(f[3])):
 values=','.join('NULL:' if c=='NULL' else 'VALUE:'+c.lower() for c in row[5:]);expected.append(['ROW',row[1],str((int(row[2])<<22)|int(row[3])),row[4],values])
rows=[line.split('|') for line in actual.read_text(encoding='utf-8-sig').splitlines()]
for i in range(max(len(expected),len(rows))):
 e=expected[i] if i<len(expected) else None;a=rows[i] if i<len(rows) else None
 if e!=a:errors.append({'row':i,'expected':e,'actual':a})
print(json.dumps({'actual_snapshot_scn':int(scn),'actual_query_rows':len(by.get('ROW',[])),'physical_columns':len(expected)-3-len(by.get('ROW',[])),'raw_source_fields_compared':5,'native_source_fields_compared':5 if len(sys.argv)>4 else 0,'mismatches':len(errors),'errors':errors,'trust_boundary':'original acquisition digest supplied by control process; not a self-signed receipt or historical schema proof','cdc_events':0},indent=2));sys.exit(bool(errors))
