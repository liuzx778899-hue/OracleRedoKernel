"""One final cut/reclaim/restart/corruption check; reuses actual published window proofs."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(__file__),'final-cut-r1']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
candidate=json.loads((N/'candidate-r4.json').read_text());applicationFile='application-r4.jar'
assert (N/'windows-r10-verify.rc').read_text().strip()=='0'
assert (N/'sealed-reclaim-package.rc').read_text().strip()=='0'
assert sha(N/applicationFile)==candidate['applicationSha256']
proofs=[json.loads((N/('future-window-'+str(i)+'-proof.json')).read_text()) for i in (1,2)]
assert proofs[1]['checkpoint']['producerState']['decodedThroughSequence']==103
props.update(SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_GATEWAY_STAGING_DIR='/read-cut/staging')
(N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
raw=N/'central-raw/raw-members'/SOURCE
def manifest():return {p.name:p.stat().st_size for p in raw.glob('*.raw')}
def actual(name):
    out=sql(name,f"select 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;\nselect 'ROWS|'||count(*) from ORKTGT_CMT_20261005.RONL1_ROWS t join ORKLAB.T_D2_BLOCKS_20261008 s on t.ID=s.ID where t.TXT=s.TXT and t.NOTE=s.NOTE;")
    assert 'COUNTS|4|4|4|4' in out and 'ROWS|20' in out
ackBefore={p.name:sha(p) for p in (N/'acks').glob('*.ack.json')};before=manifest();result=1
try:
    assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('source-start',['docker','start',S]);started=True
    for i in range(30):
        try:
            if 'READY|1' in sql('ready-'+str(i),"select 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=2 and NOTE='readcut-next-2';"):break
        except AssertionError:
            if i==29:raise
        time.sleep(3)
    else:raise AssertionError('SOURCE_NOT_READY')
    start_app('candidate');actual('before')
    cut=request('prepare','/transfer/oracle/read-cut',{},True,timeout=900);assert int(cut['firstSequence'])==103
    (N/'final-cut.json').write_text(json.dumps(cut,indent=2))
    reclaimed=request('reclaim','/transfer/oracle/read-cut/reclaim',{},True,timeout=900)
    assert (reclaimed['removed'],int(reclaimed['bytes']),int(reclaimed['requiredRawFirstSequence']))==(2,419431424,103)
    after=manifest();victims={Path(p).name for p in cut['reclaimPaths']}
    assert set(before)-set(after)==victims and len(victims)==2 and {k:v for k,v in before.items() if k not in victims}==after
    actual('after-reclaim');stop_app('candidate');start_app('restart')
    floor=request('restart-floor','/transfer/oracle/retention',{},True,timeout=900)
    assert (int(floor['requiredRawFirstSequence']),int(floor['decodedThroughSequence']))==(103,103)
    actual('after-restart')
    snapshot=N/'prefix-state/read-cuts'/Path(cut['baselinePath']).name;original=snapshot.read_bytes()
    assert sha(snapshot)==cut['baselineSha256']
    snapshot.write_bytes(original+b'poison')
    try:
        rejection=request('poison-refused','/transfer/oracle/read-cut/reclaim',{},True,False)
        assert manifest()==after
    finally:snapshot.write_bytes(original)
    assert sha(snapshot)==cut['baselineSha256']
    restored=request('restored-floor','/transfer/oracle/retention',{},True,timeout=900)
    assert restored==floor and ackBefore=={p.name:sha(p) for p in (N/'acks').glob('*.ack.json')}
    actual('after-guard')
    (N/'final-closeout-proof.json').write_text(json.dumps(dict(candidate=candidate,cut=cut,reclaimed=reclaimed,retention=floor,poisonRejection=rejection,poisonZeroDeletion=True,snapshotRestored=True,ackUnchanged=True,targetAndLedger=[4,4,4,4],rowsMatched=20,newSourceDml=0,newEvents=0,centralStorageBefore=dict(files=len(before),bytes=sum(before.values())),centralStorageAfter=dict(files=len(after),bytes=sum(after.values()))),indent=2));result=0
finally:
    try:
        if helper:stop_app('cleanup')
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
    (N/'final-closeout.rc').write_text(str(result))
