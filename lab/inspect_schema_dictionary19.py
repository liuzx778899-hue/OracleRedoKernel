"""Read-only independent SYS dictionary identity/anchor feasibility; never an event input."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/schema-coverage-gap-20261005';S='oraredo-19c-lab-20261004'
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
assert not (O/'dictionary-inspection-pin.json').exists();(O/'executed-dictionary-inspection.py').write_bytes(Path(__file__).read_bytes());(O/'dictionary-inspection-pin.json').write_text(json.dumps(dict(source_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),read_only=True,event_input=False)))
def sql(name,body):
 body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\nalter session set container=ORKPDB;\n'+body+'\nexit success\n';(O/(name+'.sql')).write_text(body);args=['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'];(O/(name+'.command.json')).write_text(json.dumps(args))
 p=subprocess.run(args,input=body.encode(),capture_output=True,timeout=90)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));return p
p=sql('dictionary-roles',"select 'ROLE|'||object_name||'|'||object_type||'|'||object_id||'|'||data_object_id from dba_objects where owner='SYS' and object_name in ('OBJ$','TAB$','COL$','C_OBJ#') order by object_name;\nselect 'DICT_COL|'||table_name||'|'||column_name||'|'||internal_column_id||'|'||segment_column_id||'|'||data_type from dba_tab_cols where owner='SYS' and table_name in ('OBJ$','TAB$','COL$') order by table_name,internal_column_id;\nselect 'SOURCE|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;");assert p.returncode==0
results=[]
for phase,clause in [('baseline',' as of scn 2501884'),('current','')]:
 body=''
 for table in ['OBJ$','TAB$','COL$']:
  body+="select 'ANCHOR|"+phase+'|'+table+"|'||obj#||'|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)"+("||'|'||intcol#||'|'||segcol#||'|'||name" if table=='COL$' else '')+' from SYS.'+table+clause+' where obj# in (74695,74696) order by obj#,rowid;\n'
 p=sql('dictionary-anchors-'+phase,body);results.append(dict(phase=phase,rc=p.returncode,rows=sum(l.startswith(b'ANCHOR|') for l in p.stdout.splitlines()),not_trusted_acquisition=True))
(O/'dictionary-inspection-proof.json').write_text(json.dumps(dict(results=results,source_actions=0,event_input=False,current_metadata_not_historical_authority=True),indent=2));print(json.dumps(results))
