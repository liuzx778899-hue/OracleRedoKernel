"""Two future normal windows after central reclaim; each source action executes once. No old DML replay."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
resume=len(sys.argv)>1 and sys.argv[1]=='resume'
sys.argv=[str(__file__),'windows-r2' if resume else 'windows-r1']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
candidate=json.loads((N/'candidate-r2.json').read_text());applicationFile='application-r2.jar'
assert (N/'reclaim-proof.json').exists() and (N/'resume-r2.rc').read_text().strip()=='0'
assert sha(N/applicationFile)==candidate['applicationSha256']
props.update(SH_ORACLE_RAW_DISCOVERY_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_ORACLE_RAW_DISCOVERY_GRANT_ID='21002',SH_GATEWAY_STAGING_DIR='/read-cut/staging')
(N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
REMOTE='/tmp/ork-read-cut-20261010';collector=None
def actual(name,expected):
    out=sql(name,f"select 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;\nselect 'ROWS|'||count(*) from ORKTGT_CMT_20261005.RONL1_ROWS t join ORKLAB.T_D2_BLOCKS_20261008 s on t.ID=s.ID where t.TXT=s.TXT and t.NOTE=s.NOTE;")
    assert 'COUNTS|'+'|'.join([str(expected)]*4) in out and 'ROWS|20' in out;return out
try:
    assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('source-start',['docker','start',S]);started=True
    for i in range(30):
        try:
            if 'READY|1' in sql('ready-'+str(i),"select 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=0 and NOTE='online-next';"):break
        except AssertionError:
            if i==29:raise
        time.sleep(3)
    else:raise AssertionError('SOURCE_NOT_READY')
    pre=sql('preflight',f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'GRANT|'||count(*) from {U}.SH_ORACLE_INPUT_BINDING where ID=21002;\nselect 'ANCHOR|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID in (1,2) and NOTE='anchor';\nselect 'MEMBER|'||member from v$logfile order by group#;")
    assert 'CURRENT|102' in pre
    if resume:
        assert 'GRANT|1' in pre and 'ANCHOR|1' in pre
        assert 'PENDING|1' in sql('pending-update',"select 'PENDING|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=1 and NOTE='readcut-next-1';")
    else:assert 'GRANT|0' in pre and 'ANCHOR|2' in pre
    # New lab-only stream authorization; the original immutable grant, acquisition and all old windows stay intact.
    if not resume:sql('grant',f"insert into {U}.SH_ORACLE_INPUT_BINDING(ID,SOURCE_ID,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,CONSUMER_ID,SCHEMA_GENERATION,STATE,BINDING_JSON,REGISTERED_AT,REGISTERED_BY) select 21002,SOURCE_ID,'read-cut-future-grant',1,4,CONSUMER_ID,SCHEMA_GENERATION,STATE,BINDING_JSON,systimestamp,'owned-read-cut-normal' from {U}.SH_ORACLE_INPUT_BINDING where ID=21001;\ncommit;")
    if not resume:actual('before',2)
    else:counts('before')
    start_app('windows')
    binary=R/'lab/out/d3-retention-20261010/sh_agent';assert sha(binary)=='033bf2f5d6e8a13ecc996af9fe68f67f65fab74bc4761c09550a938fb87abf78'
    members=[line.split('|',1)[1] for line in pre.splitlines() if line.startswith('MEMBER|')];assert len(members)==3
    config='[agent]\nsource_id='+SOURCE+'\n[oracle]\nmember_count=3\n'+''.join('member_'+str(i+1)+'='+path+'\n' for i,path in enumerate(members))+'spool_dir='+REMOTE+'/spool\npoll_interval_ms=5000\nmax_spool_bytes=536870912\n[transfer]\nbase_url=http://127.0.0.1:'+str(PORT)+'\n'
    (N/'future-collector.ini').write_bytes(config.encode());(N/'machine.secret.env').write_bytes(('SH_AGENT_MACHINE_TOKEN='+token+'\n').encode())
    (N/'run-future-collector.sh').write_bytes(('#!/bin/sh\nset -eu\nset -a\n. '+REMOTE+'/machine.secret.env\nset +a\necho $$ > '+REMOTE+'/collector.pid\nexec '+REMOTE+'/sh_agent --collect-oracle '+REMOTE+'/future-collector.ini\n').encode())
    run('collector-dir',['docker','exec',S,'test','-d',REMOTE] if resume else ['docker','exec',S,'mkdir',REMOTE])
    for src,dst in [(binary,'sh_agent'),(N/'future-collector.ini','future-collector.ini'),(N/'machine.secret.env','machine.secret.env'),(N/'run-future-collector.sh','run-future-collector.sh')]:run('copy-'+dst.replace('.','-'),['docker','cp',src,S+':'+REMOTE+'/'+dst])
    files=[REMOTE+'/'+p for p in ['sh_agent','future-collector.ini','machine.secret.env','run-future-collector.sh']]
    run('collector-owner',['docker','exec','--user','0',S,'chown','oracle:oinstall',*files]);run('collector-mode',['docker','exec',S,'chmod','700',*files])
    out=(N/(label+'-collector.stdout')).open('xb');err=(N/(label+'-collector.stderr')).open('xb')
    collector=subprocess.Popen(['docker','exec',S,REMOTE+'/run-future-collector.sh'],stdout=out,stderr=err)
    proofs=[]
    for ordinal,row in enumerate([1,2]):
        note='readcut-next-'+str(row)
        mark=N/('future-'+str(row)+'.started')
        if resume and row==1:
            assert mark.exists();archive=N/'future-1-archive.started';assert not archive.exists();archive.write_text('archive already committed update only\n')
            sql('archive-existing-1','alter session set container=CDB$ROOT;\nalter system archive log current;')
        else:
            assert not mark.exists();mark.write_text('once-only owned fixture UPDATE and archive current\n')
            sql('source-'+str(row),f"update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='{note}' where ID={row} and NOTE='anchor';\ncommit;\nalter session set container=CDB$ROOT;\nalter system archive log current;")
        key='raw-stream-21002-'+str(3+ordinal)
        for i in range(90):
            assert collector.poll() is None,'normal collector stopped'
            observed=sql('window-'+str(row)+'-'+str(i),f"select 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW where WINDOW_KEY='{key}';\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='{key}';\nselect 'FAIL|'||REASON||'|'||DETAIL from {U}.SH_FAILCLOSED_RECORD order by ID;")
            if 'WINDOW|CONSUMED' in observed and 'BATCH|PUBLISHED|0|1' in observed:break
            time.sleep(3)
        else:raise AssertionError('FUTURE_NORMAL_WINDOW_NOT_CONSUMED '+key)
        actual('actual-'+str(row),3+ordinal)
        images=sql('images-'+str(row),f"select 'IMAGE|'||BEFORE_IMAGE||'|'||AFTER_IMAGE from ORKTGT_CMT_20261005.RONL1_APPLIED where json_value(AFTER_IMAGE,'$.NOTE')='{note}';")
        selected=[line.split('|',2)[1:] for line in images.splitlines() if line.startswith('IMAGE|')];assert len(selected)==1
        before,after=map(json.loads,selected[0]);assert before==dict(ID=row,TXT='seed',NOTE='anchor') and after==dict(ID=row,TXT='seed',NOTE=note)
        ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+key).encode()).hexdigest()+'.ack.json')
        for i in range(30):
            if ack.exists():break
            time.sleep(1)
        checkpoint=json.loads(ack.read_text());assert checkpoint['lastWindowKey']==key
        control=sql('checkpoint-'+str(row),f"set serveroutput on size unlimited\nbegin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where json_value(CHECKPOINT_JSON,'$.lastWindowKey')='{key}') loop\ndbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);\nfor i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop dbms_output.put_line('CPHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));end loop;end loop;end;\n/")
        taskrows=[line.split('|')[1:] for line in control.splitlines() if line.startswith('TASK|')];assert len(taskrows)==1 and taskrows[0][1]=='DONE'
        parts=sorted((int(line.split('|')[1]),line.split('|')[2]) for line in control.splitlines() if line.startswith('CPHEX|'))
        assert [i for i,_ in parts]==list(range(len(parts))) and bytes.fromhex(''.join(value for _,value in parts))==ack.read_bytes()
        state=checkpoint['producerState'];assert sha(N/'prefix-state/producer'/Path(state['path']).name)==state['sha256']
        assert int(state['decodedThroughSequence'])==102+ordinal
        proofs.append(dict(windowKey=key,ackSha256=sha(ack),checkpoint=checkpoint))
    pid=run('collector-pid',['docker','exec',S,'cat',REMOTE+'/collector.pid']).strip();assert pid.isdecimal()
    run('collector-stop',['docker','exec',S,'kill','-TERM',pid]);assert collector.wait(timeout=60)==0;collector=None
    cut=request('new-cut','/transfer/oracle/read-cut',{},True);assert int(cut['firstSequence'])==103
    reclaimed=request('new-reclaim','/transfer/oracle/read-cut/reclaim',{},True);assert reclaimed['removed']==2
    actual('after-new-reclaim',4)
    stop_app('windows');start_app('future-restart')
    floor=request('future-floor','/transfer/oracle/retention',{},True);assert int(floor['requiredRawFirstSequence'])==103
    actual('after-future-restart',4)
    (N/'future-windows-proof.json').write_text(json.dumps(dict(candidate=candidate,windows=proofs,newSourceUpdates=2,oldSourceDmlReplayed=0,targetAndLedger=[4,4,4,4],rowsMatched=20,cut=cut,reclaimed=reclaimed,retention=floor),indent=2))
finally:
    try:
        if collector is not None and collector.poll() is None:
            pid=run('cleanup-pid',['docker','exec',S,'cat',REMOTE+'/collector.pid']).strip();assert pid.isdecimal()
            run('cleanup-collector',['docker','exec',S,'kill','-TERM',pid]);collector.wait(timeout=60)
        if helper:stop_app('cleanup')
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
