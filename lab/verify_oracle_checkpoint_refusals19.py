"""Published checkpoint/ACK target faults and missing retained-input rejection. No kill reruns."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-checkpoint-refusals-20261005';G=R/'lab/out/oracle-checkpoint-crash-20261005';T=R/'lab/out/oracle-target-20261005';SOURCE='oraredo-19c-lab-20261004'
O.mkdir(parents=True,exist_ok=False)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None,rc=0):
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))))
    proc=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for ext,b in [('stdout',proc.stdout),('stderr',proc.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(b)
    (O/(n+'.rc.txt')).write_text(str(proc.returncode));assert proc.returncode==rc,n
    return proc
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|running'
assert not run('no-owned-helper',['docker','ps','--filter','label=scope=oracle-checkpoint-refusals','--format','{{.Names}}']).stdout.strip()
man=json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-checkpoint-crash-20261005.json').read_text(encoding='utf-8'));pinned={f['path']:f['sha256'] for f in man['files']}
for name in ['adapter.jar','kernel.jar','spi.jar']:
    p=G/name;assert sha(p)==pinned[p.relative_to(R).as_posix()];shutil.copyfile(p,O/name)
classes=O/'classes';classes.mkdir();cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi'])+';'+str(T/'ojdbc8.jar')
run('compile-registry-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleCheckpointRegistryProbe.java'])
win=json.loads((R/'lab/out/oracle-overlap-20261005/proof.json').read_text());full=win['full_batch'];prefix=win['prefix_batch'];schema='b38658100a383c1a12ce0f2d560757864458261c71119102622acbdbcf07d3ce';binding='1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3'
folders={}
for p in ['CPBE','CPNO']:
    folder=O/p;shutil.copytree(G/p,folder);folders[p]=folder
for folder in folders.values():
    # Only known checkpoint/ACK/ready evidence copied; no credentials.
    assert not list(folder.glob('*secret*'))
query=(T/'POST-final.sql.redacted').read_text()
def snapshot(n,p):
    body=query.replace('POST',p).replace('exit success',"select 'IDENT|'||TARGET_ID||'|'||SOURCE_BINDING||'|'||SCHEMA_SHA from ORKTGT_CMT_20261005."+p+"_IDENT where ID=1;\nexit success")
    return sorted(line.strip() for line in run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode()).stdout.decode().splitlines() if line.strip())
def worker(n,p='CPBE',mode='both',current=full,missing=None,rc=0):
    args=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-checkpoint-refusals','--user','0','--network','container:'+SOURCE,
          '--mount','type=bind,source='+str(T)+',target=/old,readonly','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
          '-cp','/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/old/ojdbc8.jar:/evidence/classes','OracleCheckpointRegistryProbe','/old/target.secret','/evidence/'+p,p,current,mode,
          '/evidence/unavailable-baseline.txt' if missing=='baseline' else '/old/snapshot.txt','0'*64 if missing=='receipt' else '646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf']
    for seq in ([40,41] if mode=='prefix-only' else [40,41,42]):
        f=T/('sequence-'+str(seq)+'.oracap');args += ['/evidence/unavailable-history/sequence-42.oracap' if missing=='capture' and seq==42 else '/old/'+f.name,sha(f)]
    return run(n,args,rc=rc)
def files(p):return {f.name:sha(f) for f in folders[p].iterdir() if f.is_file() and f.name!='consumer.lock'}
before={p:snapshot(p+'-before',p) for p in folders};old_files={p:files(p) for p in folders}
# The selected history input really does not exist in the mounted fixture directory.
(O/'unavailable-history').mkdir();assert not (O/'unavailable-history/sequence-42.oracap').exists()
input_cases=[('missing-raw','CPBE','both',full,'capture','NoSuchFileException'),('missing-baseline','CPBE','both',full,'baseline','NoSuchFileException'),
             ('wrong-baseline-receipt','CPBE','both',full,'receipt','SNAPSHOT_ACQUISITION_DIGEST_MISMATCH'),
             ('published-full-input-unregistered','CPBE','prefix-only',prefix,None,'ORACLE_CONSUMER_CHECKPOINT_INPUT_MISSING_OR_INVALID'),
             ('published-prefix-input-unregistered','CPNO','full-only',full,None,'ORACLE_CONSUMER_CHECKPOINT_INPUT_MISSING_OR_INVALID')]
for name,p,mode,current,missing,reason in input_cases:
    failure=worker(name,p,mode,current,missing,1);assert failure.stdout==b'' and reason.encode() in failure.stderr
    assert files(p)==old_files[p] and snapshot(name+'-target-after',p)==before[p]
rows=[x.split('|') for x in before['CPBE']];member=next(x for x in rows if x[0]=='MEMBER' and x[2]==full and x[3]=='2');event=next(x for x in rows if x[0]=='APPLIED' and x[2]==member[4]);t='ORKTGT_CMT_20261005.CPBE_'
def mutate(n,statement):
    body="whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off echo off\nalter session set container=ORKPDB;\nbegin "+statement+";if sql%rowcount!=1 then raise_application_error(-20001,'OWNED_ROWCOUNT_NOT_ONE');end if;end;\n/\ncommit;\nexit success\n"
    (O/(n+'.sql')).write_text(body);run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode())
cases=[
 ('commit-missing',"delete from "+t+"COMMITS where BATCH_SHA='"+full+"' and EVENTS_COUNT=3",
  "insert into "+t+"COMMITS(BATCH_SHA,TARGET_ID,SCHEMA_SHA,EVENTS_COUNT) values('"+full+"','oracle-target-CPBE-20261005','"+schema+"',3)",'ORACLE_TARGET_ACK_WITHOUT_COMMIT'),
 ('member-missing',"delete from "+t+"MEMBERS where BATCH_SHA='"+full+"' and ORDINAL=2 and EVENT_ID='"+member[4]+"'",
  "insert into "+t+"MEMBERS(BATCH_SHA,ORDINAL,EVENT_ID) values('"+full+"',2,'"+member[4]+"')",'ORACLE_TARGET_BATCH_MEMBERS_INCOMPLETE'),
 ('payload-conflict',"update "+t+"APPLIED set PAYLOAD_SHA='"+'0'*64+"' where EVENT_ID='"+event[2]+"' and PAYLOAD_SHA='"+event[3]+"'",
  "update "+t+"APPLIED set PAYLOAD_SHA='"+event[3]+"' where EVENT_ID='"+event[2]+"' and PAYLOAD_SHA='"+'0'*64+"'",'ORACLE_TARGET_EVENT_CONFLICT'),
 ('commit-count-conflict',"update "+t+"COMMITS set EVENTS_COUNT=4 where BATCH_SHA='"+full+"' and EVENTS_COUNT=3",
  "update "+t+"COMMITS set EVENTS_COUNT=3 where BATCH_SHA='"+full+"' and EVENTS_COUNT=4",'ORACLE_TARGET_COMMIT_CONFLICT'),
 ('source-conflict',"update "+t+"IDENT set SOURCE_BINDING='different-source' where ID=1 and SOURCE_BINDING='"+binding+"'",
  "update "+t+"IDENT set SOURCE_BINDING='"+binding+"' where ID=1 and SOURCE_BINDING='different-source'",'ORACLE_TARGET_IDENTITY_MISMATCH'),
 ('schema-conflict',"update "+t+"IDENT set SCHEMA_SHA='"+'0'*64+"' where ID=1 and SCHEMA_SHA='"+schema+"'",
  "update "+t+"IDENT set SCHEMA_SHA='"+schema+"' where ID=1 and SCHEMA_SHA='"+'0'*64+"'",'ORACLE_TARGET_IDENTITY_MISMATCH')]
for name,damage,restore,reason in cases:
    mutate(name+'-inject',damage);damaged=snapshot(name+'-damaged-before','CPBE');assert damaged!=before['CPBE']
    failure=worker(name,rc=1);assert failure.stdout==b'' and reason.encode() in failure.stderr
    assert files('CPBE')==old_files['CPBE'] and snapshot(name+'-damaged-after','CPBE')==damaged
    mutate(name+'-restore',restore);assert snapshot(name+'-restored','CPBE')==before['CPBE']
    assert worker(name+'-recover').stdout==b'CONSUMED|42|0|1\n'
    assert files('CPBE')==old_files['CPBE'] and snapshot(name+'-recovered','CPBE')==before['CPBE']
assert snapshot('CPNO-final','CPNO')==before['CPNO']
assert run('lgwr',['docker','exec',SOURCE,'cat','/proc/5601/comm']).stdout.decode().strip()=='ora_lgwr_ork19'
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-checkpoint-refusals','--format','{{.Names}}']).stdout.strip()
proof={'published_state_input_refusals':5,'published_state_actual_target_refusals':6,'success_output_on_refusal':0,'checkpoint_ack_and_evidence_files_preserved':True,
       'consumer_did_not_repair_target_damage':True,'target_control_rows_restored':True,'recovery_new_business_dml':[0,0,0,0,0,0],
       'final_targets_healthy':2,'new_business_source_dml':0,'new_business_target_dml':0,'controlled_target_metadata_faults':6,
       'production_code_changed':False,'kill_rounds_rerun':False,'retention_accepted':False,'product_pipeline_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
