"""Adopt the verified live r3 app/collector; verify window 102, then execute only the new 103 fixture."""
from pathlib import Path
import sys,base64
R=Path(__file__).resolve().parent.parent
reuseFirst=sys.argv[1:]==['restart-next']
restart=reuseFirst or sys.argv[1:]==['restart']
sys.argv=[str(__file__),'windows-r6' if reuseFirst else 'windows-r5' if restart else 'windows-r4']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
candidate=json.loads((N/'candidate-r3.json').read_text());applicationFile='application-r3.jar'
assert sha(N/applicationFile)==candidate['applicationSha256']
collectorPid=None;collector=None;result=1
def decode_image(text):
    values={}
    for cell in text.split(';'):
        key,value=cell.split('=',1)
        if value=='NULL':values[key]=None
        elif value.startswith('NUMBER:'):values[key]=int(value.split(':',1)[1])
        else:
            assert value.startswith('VARCHAR2:');encoded=value.split(':',1)[1]
            values[key]=base64.urlsafe_b64decode(encoded+'='*(-len(encoded)%4)).decode()
    return values
def actual(name,expected):
    text=sql(name,f"select 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;\nselect 'ROWS|'||count(*) from ORKTGT_CMT_20261005.RONL1_ROWS t join ORKLAB.T_D2_BLOCKS_20261008 s on t.ID=s.ID where t.TXT=s.TXT and t.NOTE=s.NOTE;")
    assert 'COUNTS|'+'|'.join([str(expected)]*4) in text and 'ROWS|20' in text
try:
    if restart:
        assert HELPER not in run('containers-before',['docker','ps','-a','--format','{{.Names}}']).splitlines()
        assert run('source-before',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
        run('source-start',['docker','start',S]);started=True
        for i in range(30):
            try:
                if 'READY|1' in sql('ready-'+str(i),"select 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=1 and NOTE='readcut-next-1';"):break
            except AssertionError:
                if i==29:raise
            time.sleep(3)
        props.update(SH_ORACLE_RAW_DISCOVERY_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_ORACLE_RAW_DISCOVERY_GRANT_ID='21002',SH_GATEWAY_STAGING_DIR='/read-cut/staging')
        (N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
        start_app('windows')
        collector=subprocess.Popen(['docker','exec',S,'/tmp/ork-read-cut-20261010/run-future-collector.sh'],stdout=(N/(label+'-collector.stdout')).open('xb'),stderr=(N/(label+'-collector.stderr')).open('xb'))
        time.sleep(2)
    assert HELPER in run('containers',['docker','ps','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|running'
    started=True;helper=True
    collectorPid=run('collector-pid',['docker','exec',S,'cat','/tmp/ork-read-cut-20261010/collector.pid']).strip();assert collectorPid.isdecimal()
    live=run('collector-live',['docker','exec',S,'head','-n','3','/proc/'+collectorPid+'/status']);assert 'sh_agent' in live and 'Z (zombie)' not in live
    assert (N/'future-1.started').exists() and (N/'future-1-archive.started').exists() and not (N/'future-2.started').exists()
    pre=sql('preflight',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'FIRST|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=1 and NOTE='readcut-next-1';\nselect 'SECOND|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=2 and NOTE='anchor';")
    assert 'CURRENT|103' in pre and 'FIRST|1' in pre and 'SECOND|1' in pre
    proofs=[json.loads((N/'future-window-1-proof.json').read_text())] if reuseFirst else []
    if reuseFirst:
        assert proofs[0]['windowKey']=='raw-stream-21002-3';actual('before-second',3)
    for row in ([2] if reuseFirst else [1,2]):
        note='readcut-next-'+str(row);key='raw-stream-21002-'+str(row+2)
        if row==2:
            mark=N/'future-2.started';assert not mark.exists();mark.write_text('once-only second fixture UPDATE and ROOT archive\n')
            sql('source-2',"update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='readcut-next-2' where ID=2 and NOTE='anchor';\ncommit;\nalter session set container=CDB$ROOT;\nalter system archive log current;")
        for i in range(90):
            observed=sql('window-'+str(row)+'-'+str(i),f"select 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW where WINDOW_KEY='{key}';\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='{key}';")
            if 'WINDOW|CONSUMED' in observed and 'BATCH|PUBLISHED|0|1' in observed:break
            time.sleep(3)
        else:raise AssertionError('FUTURE_NORMAL_WINDOW_NOT_CONSUMED '+key)
        actual('actual-'+str(row),row+2)
        ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+key).encode()).hexdigest()+'.ack.json');assert ack.exists()
        cp=json.loads(ack.read_text());assert cp['lastWindowKey']==key
        eventId=cp['oracleEvidence']['events'][0]['eventId'];assert "'" not in eventId
        images=sql('images-'+str(row),f"select 'IMAGE|'||BEFORE_IMAGE||'|'||AFTER_IMAGE from ORKTGT_CMT_20261005.RONL1_APPLIED where EVENT_ID='{eventId}';")
        selected=[line.split('|',2)[1:] for line in images.splitlines() if line.startswith('IMAGE|')];assert len(selected)==1
        before,after=map(decode_image,selected[0]);assert before['ID']==row and before['NOTE']=='anchor' and after==dict(before,NOTE=note)
        expected=json.loads((N/'next-frozen-proof.json').read_text())['events'][0]['before'];assert before==dict(expected,ID=row)
        out=sql('checkpoint-'+str(row),f"set serveroutput on size unlimited\nbegin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where json_value(CHECKPOINT_JSON,'$.lastWindowKey')='{key}') loop\ndbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);\nfor i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop dbms_output.put_line('CPHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));end loop;end loop;end;\n/")
        tasks=[line.split('|')[1:] for line in out.splitlines() if line.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE'
        parts=sorted((int(line.split('|')[1]),line.split('|')[2]) for line in out.splitlines() if line.startswith('CPHEX|'))
        assert [i for i,_ in parts]==list(range(len(parts))) and bytes.fromhex(''.join(v for _,v in parts))==ack.read_bytes()
        state=cp['producerState'];assert sha(N/'prefix-state/producer'/Path(state['path']).name)==state['sha256'] and int(state['decodedThroughSequence'])==row+101
        proofs.append(dict(windowKey=key,ackSha256=sha(ack),checkpoint=cp))
        (N/('future-window-'+str(row)+'-proof.json')).write_text(json.dumps(proofs[-1],indent=2))
    run('collector-stop',['docker','exec',S,'kill','-TERM',collectorPid]);collectorPid=None
    cut=request('new-cut','/transfer/oracle/read-cut',{},True);assert int(cut['firstSequence'])==103
    reclaimed=request('new-reclaim','/transfer/oracle/read-cut/reclaim',{},True);assert reclaimed['removed']==2
    actual('after-new-reclaim',4)
    stop_app('windows');start_app('future-restart')
    floor=request('future-floor','/transfer/oracle/retention',{},True);assert int(floor['requiredRawFirstSequence'])==103
    actual('after-future-restart',4)
    (N/'future-windows-proof.json').write_text(json.dumps(dict(candidate=candidate,windows=proofs,newSourceUpdates=2,oldSourceDmlReplayed=0,targetAndLedger=[4,4,4,4],rowsMatched=20,cut=cut,reclaimed=reclaimed,retention=floor),indent=2));result=0
finally:
    try:
        if collectorPid is not None:run('cleanup-collector',['docker','exec',S,'kill','-TERM',collectorPid])
    finally:
        try:
            if helper:stop_app('cleanup')
        finally:
            try:
                if started:run('source-stop',['docker','stop','-t','60',S])
                if collector is not None:
                    (N/(label+'-collector-final.rc')).write_text(str(collector.wait(timeout=30)))
            finally:(N/('future-'+label+'.rc')).write_text(str(result))
