"""Derive equal end-record SCNs from frozen seq33/34, using native XID windows only as an independent locator."""
import hashlib,json,re,struct,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;source=r/'lab/out/oracle19-concurrent-rows-20261005';m=json.loads((source/'manifest.json').read_text())
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=False)
saved=sys.argv;sys.argv=['native',m['a_xid'],'--native-only',str(source/'dump.trc')];ns={}
prefix=(r/'lab/compare_compensation_associations.py').read_text().split("if sys.argv[2]=='--native-only':")[0];exec(compile(prefix,'native-tie-window-locator','exec'),ns);sys.argv=saved
scns={}
for record in re.split(r'(?=REDO RECORD - Thread:)',(source/'dump.trc').read_text()):
 if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
 at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());scns[at]=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
a=ns['windows'][tuple(map(int,m['a_xid'].split('.')))]['last'];b=ns['windows'][tuple(map(int,m['b_xid'].split('.')))]['last'];assert a[0]==b[0]==34 and b<a and scns[b]<scns[a]
raw=(source/'archive-1-34.dbf').read_bytes();assert hashlib.sha256(raw).hexdigest()==m['files'][1]['sha256']
results=[]
for name,at,value in [('a_end_to_b',a,scns[b]),('b_end_to_a',b,scns[a])]:
 start=at[1]*512+at[2];assert struct.unpack_from('<I',raw,start+8)[0]|(struct.unpack_from('<H',raw,start+6)[0]<<32)==scns[at]
 bad=bytearray(raw);struct.pack_into('<H',bad,start+6,value>>32);struct.pack_into('<I',bad,start+8,value&0xffffffff)
 block=at[1]*512;struct.pack_into('<H',bad,block+14,0);checksum=0
 for word in struct.unpack('<256H',bad[block:block+512]):checksum^=word
 struct.pack_into('<H',bad,block+14,checksum);path=out/(name+'.dbf');path.write_bytes(bad)
 results.append({'case':name,'end_record':at,'old_record_scn':scns[at],'new_record_scn':value,'sha256':hashlib.sha256(bad).hexdigest(),'bytes':len(bad)})
(out/'summary.json').write_text(json.dumps({'source_sha256':hashlib.sha256(raw).hexdigest(),'native_a_end':a,'native_b_end':b,'cases':results},indent=2)+'\n')
