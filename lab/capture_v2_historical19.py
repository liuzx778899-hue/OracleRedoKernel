"""New V2 acquisition fixture: one-shot ADD/DROP capture; old source actions never replayed."""
import hashlib,json,shutil,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-historical-product-20261006';A=R/'lab/out/oracle-snapshot-acquisition-20261005';S='oraredo-19c-lab-20261004';TABLE='T_V2_HIST_20261006';INDEX='PK_V2_HIST_20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
def run(name,args,body=None,expected=0):
 assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=180)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert expected is None or p.returncode==expected,(name,p.stderr[-1000:]);return p
def sql(name,body):
 body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\n'+body+'\nexit success\n';(O/(name+'.sql')).write_text(body)
 p=run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode());assert b'SP2-' not in p.stdout;return p.stdout.decode()
def metadata(label):
 return "select '"+label+"_OBJECT|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name in ('"+TABLE+"','"+INDEX+"') order by object_name;\nselect '"+label+"_COL|'||column_name||'|'||internal_column_id||'|'||segment_column_id||'|'||data_type||'|'||data_length||'|'||hidden_column||'|'||virtual_column||'|'||nullable from dba_tab_cols where owner='ORKLAB' and table_name='"+TABLE+"' order by internal_column_id;\n"
assert not (O/'capture-execution-started.txt').exists();(O/'capture-execution-started.txt').write_text('SOURCE_ACTIONS_MUST_NOT_BE_REPLAYED');(O/'executed-capture-driver.py').write_bytes(Path(__file__).read_bytes());(O/'capture-driver-sha256.txt').write_text(sha(Path(__file__)))
b=json.loads((O/'baseline-reference.json').read_text());assert sha(Path(b['path']))==b['sha256']
info=sql('members',"select 'MEMBER|'||member from v$logfile order by group#;\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';")
members=[x.strip().split('|')[1] for x in info.splitlines() if x.strip().startswith('MEMBER|')];start=int(next(x.strip().split('|')[1] for x in info.splitlines() if x.strip().startswith('CURRENT|')));assert len(members)==3
for name in ['resume_online_observation.py','follow_closed_members.py']:
 actual=O/('executed-'+name);actual.write_bytes((R/'lab'/name).read_bytes());run('copy-'+name,['docker','cp',actual,S+':/tmp/'+name]);assert sha(actual) in run('sha-'+name,['docker','exec',S,'sha256sum','/tmp/'+name]).stdout.decode()
remote='/tmp/ork-v2-historical-20261006';collector=['docker','exec',S,'python3','/tmp/follow_closed_members.py',remote,start,200,.2]+members;(O/'collector.command.json').write_text(json.dumps(collector))
with (O/'collector.stdout.txt').open('wb') as out,(O/'collector.stderr.txt').open('wb') as err:
 process=subprocess.Popen(list(map(str,collector)),stdout=out,stderr=err);(O/'collector-host-pid.txt').write_text(str(process.pid))
 def wait(key,value):
  deadline=time.time()+55
  while time.time()<deadline:
   if any(json.loads(line).get(key)==value for line in (O/'collector.stdout.txt').read_text().splitlines()):return
   if process.poll() is not None:raise RuntimeError('collector terminal; no source replay')
   time.sleep(.1)
  raise RuntimeError('observation timeout; inspect same handle, no source replay')
 wait('ready',True);archives=[];paths=[]
 for offset,operation in enumerate(['ADD','DROP']):
  seq=start+offset;t='ORKLAB.'+TABLE;old,new=('dictionary-seed','dictionary-added') if operation=='ADD' else ('dictionary-added','dictionary-restored')
  ddl='add EXTRA varchar2(128)' if operation=='ADD' else 'drop column EXTRA'
  body="declare s number;begin select sequence# into s from v$log where status='CURRENT';if s!="+str(seq)+" then raise_application_error(-20003,'sequence changed');end if;end;\n/\nalter session set container=ORKPDB;\nalter table "+t+' '+ddl+';\n'+metadata(operation)+"begin update "+t+" set TXT='"+new+"' where ID=0 and TXT='"+old+"';if sql%rowcount!=1 then raise_application_error(-20004,'row count');end if;end;\n/\nselect 'XID|'||xidusn||'.'||xidslot||'.'||xidsqn from v$transaction where ses_addr=(select saddr from v$session where sid=sys_context('USERENV','SID'));\ncommit;\nselect 'TRUTH|"+operation+"|'||ID||'|'||TXT||'|'||NOTE"+("||'|'||nvl(EXTRA,'<NULL>')" if operation=='ADD' else '')+' from '+t+" order by ID;\nselect 'TRUTH_SCN|'||dbms_flashback.get_system_change_number from dual;\nalter session set container=CDB$ROOT;\nalter system archive log current;\nselect 'ARCHIVE|'||sequence#||'|'||name from v$archived_log where thread#=1 and sequence#="+str(seq)+' and name is not null order by recid;'
  truth=sql(operation+'-once',body);wait('captured_sequence',seq);sourcepaths=[x.strip().split('|')[2] for x in truth.splitlines() if x.strip().startswith('ARCHIVE|')];assert len(sourcepaths)==1
  archive=O/('archive-'+str(seq)+'.dbf');run('copy-archive-'+str(seq),['docker','cp',S+':'+sourcepaths[0],archive]);archives.append(archive);paths.append(sourcepaths[0])
 rc=process.wait(timeout=55);(O/'collector.rc.txt').write_text(str(rc));assert rc==0
run('copy-capture',['docker','cp',S+':'+remote,O/'capture'])
for seq,archive in zip(range(start,start+2),archives):run('capture-archive-'+str(seq),[sys.executable,R/'lab/compare_observation_archive.py',O/'capture'/('sequence-'+str(seq)+'.oracap'),archive])
trace=sql('independent-dump',"alter session set tracefile_identifier='ORK_V2_HIST';\n"+''.join("alter system dump logfile '"+p+"';\n" for p in paths)+"select value from v$diag_info where name='Default Trace File';")
traces=[x.strip() for x in trace.splitlines() if x.strip().startswith('/opt/oracle/') and x.strip().endswith('.trc')];assert len(traces)==1;run('copy-independent-dump',['docker','cp',S+':'+traces[0],O/'dump.trc'])
sql('capture-post-source',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\n"+metadata('FINAL'))
(O/'capture-reference.json').write_text(json.dumps(dict(sequences=list(range(start,start+2)),baseline=b,capture_archive_differences=0,source_actions_replayed=False),indent=2))
print('DICTIONARY_ANCHOR_CAPTURE_COMPLETE')
