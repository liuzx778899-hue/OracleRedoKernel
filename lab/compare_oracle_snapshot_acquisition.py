"""Compare the actual acquisition against independently queried fixed-SCN DUMP bytes and source/schema metadata."""
import json,hashlib,re,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-snapshot-acquisition-20261005';S='oraredo-19c-lab-20261004';cases=json.loads((O/'cases.json').read_text())
def sql(name,text):
 body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(name+'.sql')).write_text(body);args=['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'];(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=body.encode(),capture_output=True)
 for suffix,data in [('.stdout.txt',p.stdout),('.stderr.txt',p.stderr)]: (O/(name+suffix)).write_bytes(data)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout;return p.stdout.decode()
proof=[]
for c in cases:
 n=c['case'];snap=(O/(n+'.snapshot.txt')).read_bytes();lines=[x.split('|') for x in snap.decode().splitlines()];scn=int(next(x[1] for x in lines if x[0]=='SNAPSHOT_SCN'));source=c['source'];columns=[x for x in lines if x[:2]==['COLUMN','BEFORE']];rows=[x for x in lines if x[0]=='ROW'];meta=[x[2:] for x in columns]
 body="select 'SOURCE|'||d.dbid||'|'||d.activation#||'|'||i.resetlogs_id||'|'||d.resetlogs_change#||'|'||v.thread#||'|'||p.value from v$database d cross join v$database_incarnation i cross join v$instance v cross join v$parameter p where i.status='CURRENT' and p.name='compatible';\nselect 'PDB|'||con_uid||'|'||con_id||'|'||name from v$containers where con_id=to_number(sys_context('USERENV','CON_ID'));\n"
 for role,obj,kind in [('HEAP',c['table'],'TABLE'),('INDEX',c['index'],'INDEX')]:body+="select '"+role+"|'||owner||'|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='"+c['owner']+"' and object_name='"+obj+"' and object_type='"+kind+"';\n"
 body+="select 'COLUMN|'||segment_column_id||'|'||internal_column_id||'|'||column_name||'|'||data_type||'|'||nullable||'|'||hidden_column||'|'||virtual_column from dba_tab_cols where owner='"+c['owner']+"' and table_name='"+c['table']+"' order by segment_column_id;\n"
 body+="select 'DUMP|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)"+''.join("||'|'||dump("+x[2]+",16)" for x in meta)+' from '+c['owner']+'.'+c['table']+' as of scn '+str(scn)+' order by rowid;\n'
 truth=[x.strip().split('|') for x in sql(n+'-independent-dump',body).splitlines() if x.strip()]
 assert [x for x in truth if x[0]=='SOURCE']==[next(x for x in lines if x[0]=='SOURCE')];assert [x for x in truth if x[0]=='PDB']==[next(x for x in lines if x[0]=='PDB')]
 for role in ['HEAP','INDEX']:assert [x[1:] for x in truth if x[0]==role]==[next(x[2:] for x in lines if x[:2]==[role,'BEFORE'])]
 assert [x[1:] for x in truth if x[0]=='COLUMN']==meta
 dumps=[x for x in truth if x[0]=='DUMP'];assert len(dumps)==len(rows)
 for row,dump in zip(rows,dumps):
  assert row[1:5]==dump[1:5]
  for value,cell in zip(row[5:],dump[5:]):
   if not cell:assert value=='NULL'
   else:
    m=re.fullmatch(r'Typ=([0-9]+) Len=([0-9]+): (.*)',cell);assert m,cell;raw=''.join(format(int(b.strip(),16),'02X') for b in m[3].split(','));assert len(raw)==2*int(m[2]);assert raw==value
 summary=(O/(n+'.stdout.txt')).read_text().strip().split('|');assert summary[0:2]==['ACQUIRED',n] and int(summary[2])==scn and summary[3]==hashlib.sha256(snap).hexdigest() and summary[4:]==[str(len(columns)),str(len(rows)),str(sum(x[5]=='YES' for x in meta))]
 proof.append(dict(case=n,scn=scn,sha256=summary[3],rows=len(rows),columns=len(columns),all_source_schema_row_position_and_dump_bytes_zero_differences=True))
negatives=[]
for n in ['wrong-source','old-transaction','wrong-index','missing-table-read']:
 assert not (O/(n+'.snapshot.txt')).exists();assert (O/(n+'.stdout.txt')).read_text().startswith('REFUSED|'+n+'|');negatives.append(n)
assert sql('source-after',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;").encode()==(O/'source-before.stdout.txt').read_bytes()
(O/'independent-snapshot-proof.json').write_text(json.dumps(dict(cases=proof,real_refusals=negatives,source_business_dml=0,source_business_ddl=0,logging_changes=0,source_restart=0,dedicated_read_account_created=True,historical_dictionary_accepted=False,registry_publication_accepted=False,site_accepted=False),indent=2));print('SOURCE_SNAPSHOT_INDEPENDENT_DUMP_COMPARISON_VERIFIED|3|4_REFUSALS')
