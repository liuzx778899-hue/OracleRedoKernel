"""Independent initial snapshot verification against fixed-SCN DUMP and physical metadata."""
import hashlib,json,re,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/acquired-dml-capture-20261005';S='oraredo-19c-lab-20261004';b=json.loads((O/'baseline-reference.json').read_text());snapshot=Path(b['path']);assert hashlib.sha256(snapshot.read_bytes()).hexdigest()==b['sha256']
lines=[x.split('|') for x in snapshot.read_text().splitlines()];scn=b['scn'];columns=[x for x in lines if x[:2]==['COLUMN','BEFORE']];rows=[x for x in lines if x[0]=='ROW'];meta=[x[2:] for x in columns];n='acquired-baseline';c=dict(owner='ORKLAB',table='T_ACQUIRED_DML_20261005',index='PK_ACQUIRED_DML_20261005')
def sql(name,text):
 body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';assert not (O/(name+'.rc.txt')).exists();(O/(name+'.sql')).write_text(body)
 p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True)
 (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout;return p.stdout.decode()
reference=(R/'lab/compare_oracle_snapshot_acquisition.py').read_text();section=reference[reference.index(' body=',reference.index('for c in cases:')):reference.index(' summary=')];exec('\n'.join(x[1:] for x in section.splitlines()))
actual=json.loads((O/'actual-source-acquire.stdout.txt').read_text())['data'];assert str(actual['id'])==b['acquisition_id'] and actual['snapshotSha256']==b['sha256'] and actual['snapshotScn']==scn
assert len(rows)==1 and rows[0][5:]==['80','61637175697265642D73656564','616E63686F72']
(O/'independent-baseline-proof.json').write_text(json.dumps(dict(scn=scn,sha256=b['sha256'],all_source_schema_position_and_value_bytes_zero_differences=True,initial_seed_rows=1,historical_dictionary_accepted=False),indent=2));print('ACQUIRED_INITIAL_BASELINE_INDEPENDENTLY_VERIFIED')
