"""Real restart/tamper checks and independent fixed-SCN DUMP comparison; no event production."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-acquisition-controller-20261005';S='oraredo-19c-lab-20261004';runtime=R/'lab/oracle_acquisition_controller_runtime.py';action=sys.argv[1]
def run(kind,name):
 p=subprocess.run([sys.executable,str(runtime),kind,name],capture_output=True)
 (O/(name+'.driver.stdout.txt')).write_bytes(p.stdout);(O/(name+'.driver.stderr.txt')).write_bytes(p.stderr);(O/(name+'.driver.rc.txt')).write_text(str(p.returncode));assert p.returncode==0
 return json.loads((O/(name+'.stdout.txt')).read_text())
identifier=(O/'acquisition-id.txt').read_text();file=O/'snapshots'/(identifier+'.snapshot')
if action=='restart':
 result=run('verify','restart-read');assert result[1]['code']==0 and result[1]['data']==json.loads((O/'initial-read.stdout.txt').read_text())[1]['data']
elif action=='tamper':
 original=file.read_bytes();assert not (O/'retained-pristine.snapshot').exists();(O/'retained-pristine.snapshot').write_bytes(original)
 file.write_bytes(original[:-1]);(O/'retained-truncated.snapshot').write_bytes(file.read_bytes());result=run('verify','truncated-file-read');assert result[1]['code']!=0
 file.write_bytes(original);assert run('verify','restored-file-read')[1]['code']==0
 # A half-written orphan has no ledger authority and cannot recover.
 (O/'snapshots/999.part').write_bytes(b'FORMAT|ORK_HEAP_SNAPSHOT_V1\n');(O/'acquisition-id.txt').write_text('999')
 try:assert run('verify','half-file-read')[1]['code']!=0
 finally:(O/'acquisition-id.txt').write_text(identifier)
elif action=='compare':
 import re
 c=next(c for c in json.loads((R/'lab/out/oracle-snapshot-acquisition-20261005/cases.json').read_text()) if c['case']=='SMIX')
 lines=[x.split('|') for x in file.read_text().splitlines()];scn=int(next(x[1] for x in lines if x[0]=='SNAPSHOT_SCN'));columns=[x for x in lines if x[:2]==['COLUMN','BEFORE']];rows=[x for x in lines if x[0]=='ROW'];meta=[x[2:] for x in columns];n='registered'
 def sql(name,text):
  body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';assert not (O/(name+'.sql')).exists();(O/(name+'.sql')).write_text(body)
  p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True)
  (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout;return p.stdout.decode()
 # Reuse only the independently reviewed DUMP comparator, never a producer or acquisition query.
 reference=(R/'lab/compare_oracle_snapshot_acquisition.py').read_text();section=reference[reference.index(' body=',reference.index('for c in cases:')):reference.index(' summary=')]
 exec('\n'.join(line[1:] for line in section.splitlines()))
 response=json.loads((O/'acquire.stdout.txt').read_text())[1]['data'];assert response['snapshotSha256']==hashlib.sha256(file.read_bytes()).hexdigest() and response['snapshotScn']==scn
 metadata=json.loads(response['metadataJson']);assert int(metadata['scn'])==scn and metadata['rowCount']==len(rows)
 for role in ['heap','index']:
  x=next(x for x in lines if x[:2]==[role.upper(),'BEFORE']);assert metadata[role]==dict(owner=x[2],name=x[3],logicalObject=int(x[4]),dataObject=int(x[5]))
 expectedColumns=[dict(segment=int(x[0]),internal=int(x[1]),name=x[2],type=x[3],nullable=x[4]=='Y',hidden=x[5]=='YES') for x in meta];assert metadata['columns']==expectedColumns and metadata['source']==c['source']
 (O/'independent-acquisition-proof.json').write_text(json.dumps(dict(id=identifier,scn=scn,sha256=response['snapshotSha256'],rows=len(rows),source_schema_position_all_value_bytes_zero_differences=True,historical_dictionary_accepted=False,publication_accepted=False),indent=2))
else:raise ValueError(action)
print('REAL_ACQUISITION_VERIFIED|'+action)
