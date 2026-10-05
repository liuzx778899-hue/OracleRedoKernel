"""Actual overlapping closed windows: two events, then those two plus one new event."""
import base64,hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-overlap-20261005'
T=R/'lab/out/oracle-target-20261005';K=R/'lab/out/captured-batch-20261005';SOURCE='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute'];O.mkdir(parents=True,exist_ok=False)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None):
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for ext,b in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(b)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
    return p.stdout.decode()
def sql(n,text):
    body='whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off echo off\n'+text+'\nexit success\n'
    (O/(n+'.sql')).write_text(body)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode())
assert run('source-owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|running'
assert not run('no-existing-helper',['docker','ps','--filter','label=scope=oracle-overlap','--format','{{.Names}}']).strip()
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;"
before=sql('source-before',source_sql)
assert 'CONFIG|1276726821|1276717861|NO|NO' in before and 'SOURCE_ROW|11|blocking-closed|new-note' in before
manifest=json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-target-20261005.json').read_text(encoding='utf-8'));pinned={f['path']:f['sha256'] for f in manifest['files']}
for name in ['adapter.jar','spi.jar','kernel.jar','ojdbc8.jar','classes/OracleTargetWorker.class','sequence-40.oracap','sequence-41.oracap','sequence-42.oracap']:
    p=T/name;assert sha(p)==pinned[p.relative_to(R).as_posix()]
args=json.loads((K/'actual.command.json').read_text())[:7]
for seq in [40,41]:p=T/('sequence-'+str(seq)+'.oracap');args += [str(p),sha(p)]
prefix=run('prefix-two-kernel',args);full=(K/'actual.stdout.txt').read_text()
prefix_events=[s for s in prefix.splitlines() if s.startswith('EVENT|')];full_events=[s for s in full.splitlines() if s.startswith('EVENT|')]
assert len(prefix_events)==2 and prefix_events==full_events[:2]
assert [s for s in prefix.splitlines() if s.startswith('ROW|')]==[s for s in full.splitlines() if s.startswith('ROW|')][:2]
batch1=prefix.splitlines()[0].split('|')[1];batch2=full.splitlines()[0].split('|')[1];schema=full.splitlines()[0].split('|')[4];assert batch1!=batch2
check=sql('target-preflight',"alter session set container=ORKPDB;\nselect 'USER_COUNT|'||count(*) from dba_users where username='ORKTGT_CMT_20261005';\nselect 'TABLE_COUNT|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'MIXD\_%' escape '\\';")
assert 'USER_COUNT|1' in check and 'TABLE_COUNT|0' in check
# Reuse only already-audited safe owned table DDL, excluding account/password statements.
ddl=(T/'target-setup-password29.sql.redacted').read_text()
start=ddl.index('create table ORKTGT_CMT_20261005.NORM_IDENT');end=ddl.index('create table ORKTGT_CMT_20261005.PRE_IDENT')
owned=ddl[start:end].replace('NORM','MIXD');assert 'password' not in owned.lower() and '<target-secret>' not in owned
sql('target-five-table-setup','alter session set container=ORKPDB;\n'+owned+'commit;')
worker=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-overlap','--user','0','--network','container:'+SOURCE,
        '--mount','type=bind,source='+str(T)+',target=/old,readonly','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
        '-cp','/old/adapter.jar:/old/spi.jar:/old/kernel.jar:/old/ojdbc8.jar:/old/classes','OracleTargetWorker','/old/target.secret','MIXD','oracle-target-MIXD-20261005',
        '/evidence/prefix.ack','none','/evidence/unused.ready','/old/snapshot.txt','646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf','0x0300019b']
def consume(n,sequences,ack,batch,count,new):
    command=worker.copy();command[command.index('/evidence/prefix.ack')]=ack
    for seq in sequences:
        p=T/('sequence-'+str(seq)+'.oracap');command += ['/old/'+p.name,sha(p)]
    line='COMMITTED|'+str(new)+'|ORK_ORACLE_TARGET_ACK_V1|oracle-target-MIXD-20261005|'+batch+'|'+schema+'|'+str(count)
    assert run(n,command).strip()==line
    assert (O/Path(ack).name).read_bytes()==(line.split('|',2)[2]+'\n').encode()
query=(T/'POST-final.sql.redacted').read_text().replace('POST','MIXD')
def state(n):return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],query.encode())
consume('apply-prefix',[40,41],'/evidence/prefix.ack',batch1,2,2)
first=state('prefix-state');assert 'STATE|MIXD|2|2|1' in first and 'ROW|MIXD|11|capture-follow-b|new-note' in first
prefix_ack=(O/'prefix.ack').read_bytes()
consume('apply-overlap-full',[40,41,42],'/evidence/full.ack',batch2,3,1)
assert (O/'prefix.ack').read_bytes()==prefix_ack
consume('repeat-overlap-full',[40,41,42],'/evidence/full.ack',batch2,3,0)
consume('repeat-prefix-after-full',[40,41],'/evidence/prefix.ack',batch1,2,0)
fresh=state('target-fresh');lines=[s.strip().split('|') for s in fresh.splitlines() if s.strip()]
meta=[s.split('|') for s in full_events]
def image(txt,note):
    enc=lambda s:base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
    return 'ID=NUMBER:11;TXT=VARCHAR2:'+enc(txt)+';NOTE=VARCHAR2:'+enc(note)
expected=[];prev=image('blocking-closed','new-note')
for seq,m in zip([40,41,42],meta):
    truth=[s.strip().split('|') for s in (R/'lab/out/closed-follow-20261005'/('step-'+str(seq)+'.stdout.txt')).read_text().splitlines() if s.strip().startswith('TRUTH|')]
    assert len(truth)==1 and truth[0][1:3]==[str(seq),'11']
    after=image(truth[0][3],truth[0][4]);expected.append(['APPLIED','MIXD','oracle1:'+m[1],m[2],prev,after,'UPDATE']);prev=after
members=[['MEMBER','MIXD',b,str(i),'oracle1:'+m[1]] for b,count in [(batch1,2),(batch2,3)] for i,m in enumerate(meta[:count])]
receipts=[['RECEIPT','MIXD',b,'oracle-target-MIXD-20261005',schema,str(count)] for b,count in [(batch1,2),(batch2,3)]]
assert sorted(s for s in lines if s[0]=='APPLIED')==sorted(expected)
assert sorted(s for s in lines if s[0]=='MEMBER')==sorted(members)
assert sorted(s for s in lines if s[0]=='RECEIPT')==sorted(receipts)
assert [s for s in lines if s[0]=='STATE']==[['STATE','MIXD','3','5','2']]
assert [s for s in lines if s[0]=='ROW']==[['ROW','MIXD','11','blocking-closed','new-note']]
assert sql('source-after',source_sql)==before
assert run('lgwr',['docker','exec',SOURCE,'cat','/proc/5601/comm']).strip()=='ora_lgwr_ork19'
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-overlap','--format','{{.Names}}']).strip()
proof={'raw_journals_unchanged':True,'prefix_events':2,'full_events':3,'new_dml_first':2,'new_dml_overlap':1,'new_dml_repeats':[0,0],
       'target_event_audits':3,'target_members':5,'target_commits':2,'prefix_batch':batch1,'full_batch':batch2,'independent_source_sql_images_mismatches':0,
       'prefix_ack_unchanged_after_full':True,'new_owned_target_tables':5,'source_dml':0,'source_config_changes':0,
       'arbitrary_window_live_state_accepted':False,'checkpoint_accepted':False,'product_pipeline_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
