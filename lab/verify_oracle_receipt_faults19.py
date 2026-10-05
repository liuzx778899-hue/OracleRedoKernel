"""Actual owned target corruption/refusal/recovery before checkpoint publication."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-receipt-faults-20261005';GOOD=R/'lab/out/oracle-overlap-20261005';T=R/'lab/out/oracle-target-20261005';SOURCE='oraredo-19c-lab-20261004'
resuming=sys.argv[1:]==['--resume-after-row-order']
assert resuming or sys.argv[1:]==['--execute']
BASE=O
if resuming:
    assert (BASE/'missing-member-consume.rc.txt').read_text()=='1' and (BASE/'missing-member-restore.rc.txt').read_text()=='0'
    O=BASE/'continued-after-row-order'
O.mkdir(parents=True,exist_ok=False)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None,rc=0):
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for ext,b in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(b)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,n
    return p
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|running'
assert not run('no-owned-helper',['docker','ps','--filter','label=scope=oracle-overlap','--format','{{.Names}}']).stdout.strip()
manifest=json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-target-20261005.json').read_text(encoding='utf-8'));pinned={f['path']:f['sha256'] for f in manifest['files']}
for name in ['adapter.jar','spi.jar','kernel.jar','ojdbc8.jar','classes/OracleTargetWorker.class']:
    p=T/name;assert sha(p)==pinned[p.relative_to(R).as_posix()]
query=(T/'POST-final.sql.redacted').read_text().replace('POST','MIXD')
query=query.replace('exit success',"select 'IDENT|'||TARGET_ID||'|'||SOURCE_BINDING||'|'||SCHEMA_SHA from ORKTGT_CMT_20261005.MIXD_IDENT where ID=1;\nexit success")
def canonical(b):return sorted(s.strip() for s in b.decode().splitlines() if s.strip())
def snapshot(n):return canonical(run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],query.encode()).stdout)
before=snapshot('target-before');assert [s for s in before if not s.startswith('IDENT|')]==canonical((GOOD/'target-fresh.stdout.txt').read_bytes())
if resuming:assert canonical((BASE/'target-before.stdout.txt').read_bytes())==canonical((BASE/'missing-member-restored.stdout.txt').read_bytes())
ack=GOOD/'full.ack';ack_before=ack.read_bytes()
worker=json.loads((GOOD/'repeat-overlap-full.command.json').read_text())
# Use same dedicated fixture and pinned binary; each command follows a new actual corruption.
worker[worker.index('scope=oracle-overlap')]='scope=oracle-receipt-faults'
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;"
def sql(n,text):
    b='whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off echo off\n'+text+'\nexit success\n'
    (O/(n+'.sql')).write_text(b);return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],b.encode()).stdout
source_before=sql('source-before',source_sql)
assert b'CONFIG|1276726821|1276717861|NO|NO' in source_before and b'SOURCE_ROW|11|blocking-closed|new-note' in source_before
rows=[s.split('|') for s in before]
proof=json.loads((GOOD/'proof.json').read_text());batch=proof['full_batch'];table='ORKTGT_CMT_20261005.MIXD_'
m=next(s for s in rows if s[0]=='MEMBER' and s[2]==batch and s[3]=='2');e=next(s for s in rows if s[0]=='APPLIED' and s[2]==m[4]);binding='1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3'
# Every mutation is conditional and must affect exactly one owned fixture row.
def mutate(n,statement):
    return sql(n,"alter session set container=ORKPDB;\nbegin "+statement+";if sql%rowcount!=1 then raise_application_error(-20001,'OWNED_FIXTURE_ROWCOUNT_NOT_ONE');end if;end;\n/\ncommit;")
cases=[
 ('missing-member',"delete from "+table+"MEMBERS where BATCH_SHA='"+batch+"' and ORDINAL=2 and EVENT_ID='"+m[4]+"'",
  "insert into "+table+"MEMBERS(BATCH_SHA,ORDINAL,EVENT_ID) values('"+batch+"',2,'"+m[4]+"')",'ORACLE_TARGET_BATCH_MEMBERS_INCOMPLETE'),
 ('payload-conflict',"update "+table+"APPLIED set PAYLOAD_SHA='"+'0'*64+"' where EVENT_ID='"+e[2]+"' and PAYLOAD_SHA='"+e[3]+"'",
  "update "+table+"APPLIED set PAYLOAD_SHA='"+e[3]+"' where EVENT_ID='"+e[2]+"' and PAYLOAD_SHA='"+'0'*64+"'",'ORACLE_TARGET_EVENT_CONFLICT'),
 ('commit-count-conflict',"update "+table+"COMMITS set EVENTS_COUNT=4 where BATCH_SHA='"+batch+"' and EVENTS_COUNT=3",
  "update "+table+"COMMITS set EVENTS_COUNT=3 where BATCH_SHA='"+batch+"' and EVENTS_COUNT=4",'ORACLE_TARGET_COMMIT_CONFLICT'),
 ('source-binding-conflict',"update "+table+"IDENT set SOURCE_BINDING='different-source' where ID=1 and SOURCE_BINDING='"+binding+"'",
  "update "+table+"IDENT set SOURCE_BINDING='"+binding+"' where ID=1 and SOURCE_BINDING='different-source'",'ORACLE_TARGET_IDENTITY_MISMATCH')]
for name,damage,restore,reason in cases:
    if not (resuming and name=='missing-member'):
        mutate(name+'-inject',damage)
        damaged=snapshot(name+'-damaged-before');assert damaged!=before
        failure=run(name+'-consume',worker,rc=1)
        assert failure.stdout==b'' and reason.encode() in failure.stderr and ack.read_bytes()==ack_before
        assert snapshot(name+'-damaged-after')==damaged
        mutate(name+'-restore',restore)
        assert snapshot(name+'-restored')==before
    else:assert snapshot(name+'-resume-restored')==before
    recovered=run(name+'-recover-consume',worker)
    assert recovered.stdout.decode().startswith('COMMITTED|0|') and ack.read_bytes()==ack_before
    assert snapshot(name+'-recovered-after')==before
assert sql('source-after',source_sql)==source_before
assert run('lgwr',['docker','exec',SOURCE,'cat','/proc/5601/comm']).stdout.decode().strip()=='ora_lgwr_ork19'
assert not run('no-live-helper',['docker','ps','--filter','label=scope=oracle-receipt-faults','--format','{{.Names}}']).stdout.strip()
(O/'proof.json').write_text(json.dumps({'actual_oracle_receipt_refusals':4,'refusal_stdout_events':0,'ack_bytes_unchanged':True,
 'damaged_target_unchanged_by_consumer':True,'owned_target_restored_relationally':True,'ident_in_fresh_audit':True,'continued_after_comparator_order_failure':resuming,'recovery_new_dml':[0,0,0,0],
 'final_target_state':'3 audits / 5 members / 2 commits','source_dml':0,'source_config_changes':0,'checkpoint_implemented':False},indent=2))
print((O/'proof.json').read_text())
