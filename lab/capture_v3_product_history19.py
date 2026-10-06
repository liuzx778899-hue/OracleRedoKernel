"""One-shot history after the actual product V3 acquisition. Never replay earlier source actions."""
import hashlib,json,shutil,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/v3-product-runtime-20261006';H=R/'lab/out/v3-product-history-20261006'
S='oraredo-19c-lab-20261004';TABLE='T_V2_HIST_20261006';INDEX='PK_V2_HIST_20261006'
assert sys.argv[1:]==['--execute'];H.mkdir(exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(H/'executed-capture-driver.py').write_bytes(Path(__file__).read_bytes())
b=json.loads((N/'baseline-reference.json').read_text());assert sha(Path(b['path']))==b['sha256']
assert json.loads((N/'baseline-truth-proof.json').read_text())['differences']==0
assert json.loads((N/'restart-proof.json').read_text())['persisted_get_all_fields_equal']
shutil.copyfile(Path(b['path']),H/'actual-product-v3.snapshot')
for artifact,output in [('oracle-redo-kernel','kernel'),('streamhouse-engine-oracle','adapter'),('streamhouse-spi','spi')]:
    shutil.copyfile(N/(artifact+'.jar'),H/(output+'.jar'))
shutil.copyfile(N/'ojdbc8.jar',H/'ojdbc8.jar')

def run(name,args,body=None):
    args=list(map(str,args));assert not (H/(name+'.command.json')).exists();(H/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=body,capture_output=True,timeout=240)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(H/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==0 and b'SP2-' not in p.stdout,(name,p.returncode)
    return p.stdout.decode()

def sql(name,body):
    script='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\n'+body+'\nexit success\n'
    (H/(name+'.sql')).write_text(script)
    return run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],script.encode())

assert run('source-ownership',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|running'
assert run('product-ownership',['docker','inspect','ork-oracle-v3-product-20261006','--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip()=='OracleRedoKernel|oracle-v3-product|true'
active=run('collector-preflight',['docker','exec','-i',S,'python3','-'],b"from pathlib import Path\nfor p in Path('/proc').glob('[0-9]*/cmdline'):\n try:\n  a=p.read_bytes().split(b'\\0')\n  if any(x.endswith((b'/follow_closed_members.py',b'/resume_online_observation.py')) for x in a):print(p.parent.name)\n except OSError:pass\n")
assert not active.strip(),'existing source collector must be resolved before actions'
pre=sql('source-preflight',"select 'MEMBER|'||member from v$logfile order by group#;\nselect 'CURRENT|'||sequence#||'|'||first_change# from v$log where status='CURRENT';\nselect 'SOURCE|'||dbid||'|'||activation#||'|'||resetlogs_change#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SEED|'||ID||'|'||TXT||'|'||NOTE from ORKLAB."+TABLE+";\nselect 'COLUMNS|'||count(*) from dba_tab_cols where owner='ORKLAB' and table_name='"+TABLE+"';\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
members=[line.strip().split('|')[1] for line in pre.splitlines() if line.strip().startswith('MEMBER|')]
current=next(line.strip().split('|') for line in pre.splitlines() if line.strip().startswith('CURRENT|'));start=int(current[1])
assert len(members)==3 and int(current[2])<=b['scn'],'baseline generation not present; do not execute fixture'
assert 'SOURCE|1276726821|1276717861|1920977|NO|NO' in pre and 'SEED|0|history-v3-restored|anchor' in pre
assert 'COLUMNS|3' in pre and 'OLD|11|blocking-closed|new-note' in pre
(H/'baseline-reference.json').write_text(json.dumps(dict(b,product_baseline_path=b['path'],path=str(H/'actual-product-v3.snapshot'),start_sequence=start)))
for name in ['resume_online_observation.py','follow_closed_members.py']:
    path=H/('executed-'+name);path.write_bytes((R/'lab'/name).read_bytes());run('copy-'+name,['docker','cp',path,S+':/tmp/'+name])
    assert sha(path) in run('sha-'+name,['docker','exec',S,'sha256sum','/tmp/'+name])
remote='/tmp/ork-v3-product-history-20261006';collector=['docker','exec',S,'python3','/tmp/follow_closed_members.py',remote,str(start),'300','.2',*members]
(H/'collector.command.json').write_text(json.dumps(collector));(H/'source-actions-started.txt').write_text('ONCE_ONLY_NO_SOURCE_REPLAY')
with (H/'collector.stdout.txt').open('wb') as out,(H/'collector.stderr.txt').open('wb') as err:
    process=subprocess.Popen(collector,stdout=out,stderr=err);(H/'collector-host-pid.txt').write_text(str(process.pid))
    def wait(key,value):
        deadline=time.monotonic()+55
        while time.monotonic()<deadline:
            if any(json.loads(line).get(key)==value for line in (H/'collector.stdout.txt').read_text().splitlines()):return
            if process.poll() is not None:raise RuntimeError('collector terminal; preserve state, never replay source')
            time.sleep(.1)
        raise RuntimeError('observation timeout; inspect the same collector handle')
    wait('ready',True);archives=[]
    for offset,phase in enumerate(['ADD','DROP']):
        seq=start+offset;before,after=('history-v3-restored','history-product-added') if phase=='ADD' else ('history-product-added','history-product-restored')
        ddl='add EXTRA_HP varchar2(128)' if phase=='ADD' else 'drop column EXTRA_HP'
        body="declare n number;begin select sequence# into n from v$log where status='CURRENT';if n!="+str(seq)+" then raise_application_error(-20001,'sequence changed');end if;end;\n/\nalter session set container=ORKPDB;\nalter table ORKLAB."+TABLE+' '+ddl+';\n'
        body+="select 'COLUMN|'||column_name||'|'||internal_column_id||'|'||segment_column_id||'|'||hidden_column||'|'||nullable from dba_tab_cols where owner='ORKLAB' and table_name='"+TABLE+"' order by internal_column_id;\nbegin update ORKLAB."+TABLE+" set TXT='"+after+"' where ID=0 and TXT='"+before+"';if sql%rowcount!=1 then raise_application_error(-20002,'row count');end if;end;\n/\n"
        body+="select 'XID|'||xidusn||'.'||xidslot||'.'||xidsqn from v$transaction where ses_addr=(select saddr from v$session where sid=sys_context('USERENV','SID'));\ncommit;\nselect 'TRUTH|"+phase+"|'||ID||'|'||TXT||'|'||NOTE"+("||'|'||nvl(EXTRA_HP,'<NULL>')" if phase=='ADD' else '')+' from ORKLAB.'+TABLE+";\nselect 'TRUTH_SCN|'||dbms_flashback.get_system_change_number from dual;\nalter session set container=CDB$ROOT;\nalter system archive log current;\nselect 'ARCHIVE|'||sequence#||'|'||name from v$archived_log where thread#=1 and sequence#="+str(seq)+' and name is not null order by recid;'
        truth=sql(phase+'-once',body);wait('captured_sequence',seq)
        paths=[line.strip().split('|')[2] for line in truth.splitlines() if line.strip().startswith('ARCHIVE|')];assert len(paths)==1
        run('copy-archive-'+str(seq),['docker','cp',S+':'+paths[0],H/('archive-'+str(seq)+'.dbf')]);archives.append(paths[0])
    rc=process.wait(timeout=70);(H/'collector.rc.txt').write_text(str(rc));assert rc==0
run('copy-capture',['docker','cp',S+':'+remote,H/'capture'])
for seq in range(start,start+2):run('capture-archive-'+str(seq),[sys.executable,R/'lab/compare_observation_archive.py',H/'capture'/('sequence-'+str(seq)+'.oracap'),H/('archive-'+str(seq)+'.dbf')])
trace=sql('independent-dump',"alter session set tracefile_identifier='ORK_HP_V3';\n"+''.join("alter system dump logfile '"+path+"';\n" for path in archives)+"select value from v$diag_info where name='Default Trace File';")
paths=[line.strip() for line in trace.splitlines() if line.strip().startswith('/opt/oracle/') and line.strip().endswith('.trc')];assert len(paths)==1
run('copy-dump',['docker','cp',S+':'+paths[0],H/'dump.trc'])
(H/'capture-reference.json').write_text(json.dumps(dict(sequences=list(range(start,start+2)),baseline=b,capture_archive_differences=0,source_actions_replayed=False)))
print('NEW_PRODUCT_V3_HISTORY_CAPTURE_COMPLETE')
