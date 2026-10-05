"""Inject only into the owned acquisition control schema, retain all failed results and orphan files."""
import json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-acquisition-controller-20261005';U='ORKACCTL_20261005';S='oraredo-19c-lab-20261004';runtime=R/'lab/oracle_acquisition_controller_runtime.py';identifier=(O/'acquisition-id.txt').read_text();assert identifier.isdigit()
def sql(name,text):
 body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';assert not (O/(name+'.sql')).exists();(O/(name+'.sql')).write_text(body)
 p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True)
 (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout
def http(kind,name,success=True):
 p=subprocess.run([sys.executable,str(runtime),kind,name],capture_output=True)
 (O/(name+'.driver.stdout.txt')).write_bytes(p.stdout);(O/(name+'.driver.stderr.txt')).write_bytes(p.stderr);(O/(name+'.driver.rc.txt')).write_text(str(p.returncode));assert (p.returncode==0)==success
 return json.loads((O/(name+'.stdout.txt')).read_text())
for name,field,changed,restored in [('authority','AUTHORITY',"'client'","'owned-acquisition-controller-20261005'"),('scn','SNAPSHOT_SCN','SNAPSHOT_SCN+1','SNAPSHOT_SCN-1'),('schema','METADATA_JSON',"'{}'",None)]:
 original=json.loads((O/'acquire.stdout.txt').read_text())[1]['data']['metadataJson']
 restored=restored or "to_clob('"+original.replace("'","''")+"')"
 sql(name+'-inject','update '+U+'.SH_ORACLE_ACQUISITION set '+field+'='+changed+' where ID='+identifier+';\ncommit;')
 try:assert http('verify',name+'-refused')[1]['code']!=0
 finally:sql(name+'-restore','update '+U+'.SH_ORACLE_ACQUISITION set '+field+'='+restored+' where ID='+identifier+';\ncommit;')
 assert http('verify',name+'-healthy')[1]['code']==0
before=set((O/'snapshots').glob('*.snapshot'))
sql('registration-failure-trigger',"create or replace trigger "+U+".ORK_ACQ_INSERT_FAIL before insert on "+U+".SH_ORACLE_ACQUISITION begin raise_application_error(-20001,'OWNED_REGISTRATION_FAILURE'); end;\n/")
try:assert http('acquire','registration-failed',False)[1]['code']!=0
finally:sql('registration-failure-trigger-drop','drop trigger '+U+'.ORK_ACQ_INSERT_FAIL;')
orphans=set((O/'snapshots').glob('*.snapshot'))-before;assert len(orphans)==1;assert (O/'acquisition-id.txt').read_text()==identifier
(O/'registration-failure-proof.json').write_text(json.dumps(dict(acquisition_id=identifier,orphan_snapshot=[p.name for p in orphans],new_registration_rejected=True,existing_acquisition_unchanged=True),indent=2))
print('OWNED_ACQUISITION_AUTHORITY_SCN_SCHEMA_AND_REGISTRATION_FAILURE_VERIFIED')
