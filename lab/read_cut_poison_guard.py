"""One necessary cut corruption refusal; restores the exact snapshot and does not change source data."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(__file__),'cut-poison-r1']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
proof=json.loads((N/'future-windows-proof.json').read_text());candidate=proof['candidate'];applicationFile='application-r2.jar'
assert (N/'future-windows-r2.rc').read_text().strip()=='0'
props.update(SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_GATEWAY_STAGING_DIR='/read-cut/staging')
(N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
snapshot=N/'prefix-state/read-cuts'/Path(proof['cut']['baselinePath']).name
raw=N/'central-raw/raw-members'/SOURCE
manifest=lambda:sorted((p.name,p.stat().st_size) for p in raw.glob('*.raw'))
before=manifest();original=snapshot.read_bytes();assert sha(snapshot)==proof['cut']['baselineSha256']
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
    start_app('guard')
    snapshot.write_bytes(original+b'poison')
    try:
        rejected=request('poison-refused','/transfer/oracle/read-cut/reclaim',{},True,False)
        assert manifest()==before
    finally:snapshot.write_bytes(original)
    assert sha(snapshot)==proof['cut']['baselineSha256']
    floor=request('restored-floor','/transfer/oracle/retention',{},True);assert int(floor['requiredRawFirstSequence'])==103
    actual=sql('actual',f"select 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;")
    assert 'COUNTS|4|4' in actual
    (N/'poison-guard-proof.json').write_text(json.dumps(dict(rejection=rejected,zeroDeletion=True,snapshotRestored=True,sourceDml=0,eventsBeforeAfter=[4,4],retention=floor),indent=2))
finally:
    try:
        if helper:stop_app('cleanup')
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
