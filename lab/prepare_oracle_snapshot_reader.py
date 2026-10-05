"""Prepare only a dedicated lab read account; production acquisition never grants its own privileges."""
import json,secrets,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-snapshot-acquisition-20261005';S='oraredo-19c-lab-20261004';assert O.is_dir();assert not (O/'source.secret').exists();assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
def sql(name,text,private=False):
 body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';suffix='.secret' if private else '';(O/(name+suffix+'.sql')).write_text(body);p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True)
 (O/(name+suffix+'.stdout.txt')).write_bytes(p.stdout);(O/(name+suffix+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout;return p.stdout
user='ORKACQ_20261005';pwd='P'+secrets.token_hex(12)+'a1';assert b'COUNT|0' in sql('reader-preflight',"select 'COUNT|'||count(*) from dba_users where username='"+user+"';")
cases=[]
for n in ['SNON','SNUL','SMIX']:
 e=json.loads((R/'lab/out/oracle-typed-evidence-20261005'/(n+'-typed.stdout.txt')).read_text())['evidence'];cases.append(dict(case=n,owner=e['heap']['owner'],table=e['heap']['name'],index=e['index']['name'],source=e['source']))
(O/'cases.json').write_text(json.dumps(cases,indent=2));body='create user '+user+' identified by "'+pwd+'";\ngrant create session to '+user+';\n'
for obj in ['v_$database','v_$database_incarnation','v_$instance','v_$parameter','v_$containers','dba_objects','dba_tab_cols','dba_indexes','nls_database_parameters']:body+='grant select on sys.'+obj+' to '+user+';\n'
body+='grant execute on sys.dbms_flashback to '+user+';\n'
for c in cases:body+='grant select,flashback on '+c['owner']+'.'+c['table']+' to '+user+';\n'
sql('create-reader',body,True);(O/'source.secret').write_text(user+'\n'+pwd+'\njdbc:oracle:thin:@//localhost:1521/ORKPDB\n');sql('source-before',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;");print('DEDICATED_READ_ACCOUNT_READY|NO_SOURCE_BUSINESS_DML')
