"""Finish the live captured second window without source actions or restarting live actors."""
from pathlib import Path
import sys,base64
R=Path(__file__).resolve().parent.parent
mode=sys.argv[2] if len(sys.argv)>2 else 'complete';assert mode in ('complete','window-only')
sys.argv=[str(__file__),sys.argv[1] if len(sys.argv)>1 else 'windows-r7']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
candidate=json.loads((N/'candidate-r3.json').read_text());applicationFile='application-r3.jar'
result=2;cleanup=False
def actual(name):
    out=sql(name,f"select 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;\nselect 'ROWS|'||count(*) from ORKTGT_CMT_20261005.RONL1_ROWS t join ORKLAB.T_D2_BLOCKS_20261008 s on t.ID=s.ID where t.TXT=s.TXT and t.NOTE=s.NOTE;")
    assert 'COUNTS|4|4|4|4' in out and 'ROWS|20' in out
def image(text):
    values={}
    for cell in text.split(';'):
        k,v=cell.split('=',1)
        if v=='NULL':values[k]=None
        elif v.startswith('NUMBER:'):values[k]=int(v.split(':',1)[1])
        else:
            assert v.startswith('VARCHAR2:');v=v.split(':',1)[1];values[k]=base64.urlsafe_b64decode(v+'='*(-len(v)%4)).decode()
    return values
try:
    assert sha(N/applicationFile)==candidate['applicationSha256']
    assert HELPER in run('containers',['docker','ps','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|running'
    started=True;helper=True
    assert (N/'future-2.started').exists() and (N/'windows-r6-source-2.rc').read_text().strip()=='0'
    first=json.loads((N/'future-window-1-proof.json').read_text());key='raw-stream-21002-4'
    for i in range(90):
        out=sql('window-'+str(i),f"select 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW where WINDOW_KEY='{key}';\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='{key}';")
        if 'BATCH|FAILED|' in out:cleanup=True;result=1;raise AssertionError(out)
        if 'WINDOW|CONSUMED' in out and 'BATCH|PUBLISHED|0|1' in out:break
        time.sleep(3)
    else:raise AssertionError('OBSERVATION_PENDING; live actors preserved')
    cleanup=True;result=1;actual('actual')
    ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+key).encode()).hexdigest()+'.ack.json');cp=json.loads(ack.read_text());assert cp['lastWindowKey']==key
    eventId=cp['oracleEvidence']['events'][0]['eventId'];assert "'" not in eventId
    out=sql('images',f"select 'IMAGE|'||BEFORE_IMAGE||'|'||AFTER_IMAGE from ORKTGT_CMT_20261005.RONL1_APPLIED where EVENT_ID='{eventId}';")
    rows=[line.split('|',2)[1:] for line in out.splitlines() if line.startswith('IMAGE|')];assert len(rows)==1
    before,after=map(image,rows[0]);expected=json.loads((N/'next-frozen-proof.json').read_text())['events'][0]['before']
    assert before==dict(expected,ID=2) and after==dict(before,NOTE='readcut-next-2')
    out=sql('checkpoint',f"set serveroutput on size unlimited\nbegin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where json_value(CHECKPOINT_JSON,'$.lastWindowKey')='{key}') loop\ndbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);\nfor i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop dbms_output.put_line('CPHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));end loop;end loop;end;\n/")
    tasks=[line.split('|')[1:] for line in out.splitlines() if line.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE'
    parts=sorted((int(line.split('|')[1]),line.split('|')[2]) for line in out.splitlines() if line.startswith('CPHEX|'))
    assert [i for i,_ in parts]==list(range(len(parts))) and bytes.fromhex(''.join(v for _,v in parts))==ack.read_bytes()
    state=cp['producerState'];assert sha(N/'prefix-state/producer'/Path(state['path']).name)==state['sha256'] and int(state['decodedThroughSequence'])==103 and state['pendingTransactions']==0
    second=dict(windowKey=key,ackSha256=sha(ack),checkpoint=cp);(N/'future-window-2-proof.json').write_text(json.dumps(second,indent=2))
    if mode=='window-only':result=0;raise SystemExit(0)
    raw=N/'central-raw/raw-members'/SOURCE
    storageBefore=dict(files=len(list(raw.glob('*.raw'))),bytes=sum(p.stat().st_size for p in raw.glob('*.raw')))
    cut=request('new-cut','/transfer/oracle/read-cut',{},True);assert int(cut['firstSequence'])==103
    reclaimed=request('new-reclaim','/transfer/oracle/read-cut/reclaim',{},True);assert reclaimed['removed']==2
    storageAfter=dict(files=len(list(raw.glob('*.raw'))),bytes=sum(p.stat().st_size for p in raw.glob('*.raw')))
    actual('after-reclaim');stop_app('windows');start_app('future-restart')
    floor=request('future-floor','/transfer/oracle/retention',{},True);assert int(floor['requiredRawFirstSequence'])==103
    actual('after-restart')
    (N/'future-windows-proof.json').write_text(json.dumps(dict(candidate=candidate,windows=[first,second],newSourceUpdates=2,oldSourceDmlReplayed=0,targetAndLedger=[4,4,4,4],rowsMatched=20,cut=cut,reclaimed=reclaimed,retention=floor,centralStorageBefore=storageBefore,centralStorageAfter=storageAfter),indent=2));result=0
finally:
    if cleanup:
        try:
            if helper:stop_app('cleanup')
        finally:
            if started:run('source-stop',['docker','stop','-t','60',S])
    (N/(label+'-verify.rc')).write_text(str(result))
