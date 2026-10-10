"""Continue after the recorded target verifier fix; sequence 100 stays absent. No replay or new DML."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(__file__),'resume-r2']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
candidate=json.loads((N/'candidate-r2.json').read_text());applicationFile='application-r2.jar'
assert sha(N/applicationFile)==candidate['applicationSha256']
assert (N/'runtime-r1.rc').read_text().strip()=='1'
cut=json.loads((N/'cut.json').read_text())
reclaimed=json.loads((N/'reclaim-r1-reclaim.stdout').read_text())['data'];assert reclaimed['removed']==1
assert not (N/'central-raw/raw-members'/SOURCE/'f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820.raw').exists()
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
    start_app('restart')
    floor=request('restart-floor','/transfer/oracle/retention',{},True)
    assert (int(floor['requiredRawFirstSequence']),int(floor['decodedThroughSequence']))==(101,101)
    discovered=request('restart-discover','/oracle/raw-plan/discover',{})
    assert int(discovered['candidate']['firstSequence'])==101 and [int(x['sequence']) for x in discovered['members']]==[101]
    repeated=request('idempotent-reclaim','/transfer/oracle/read-cut/reclaim',{},True);assert repeated['removed']==0
    counts('after-restart')
    assert {p.name:sha(p) for p in (N/'acks').glob('*.ack.json')}=={p.name:sha(p) for p in (O/'acks').glob('*.ack.json')}
    for digest in ['f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820','3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5']:
        assert sha(O/'raw-ready/raw-members'/SOURCE/(digest+'.raw'))==digest
    (N/'reclaim-proof.json').write_text(json.dumps(dict(candidate=candidate,originalCandidate=json.loads((N/'candidate.json').read_text()),cut=cut,reclaimed=reclaimed,retention=floor,discovery=discovered,newSourceDml=0,newEvents=0,targetAndLedger=[2,2,2,2],originalRawAndAckUnchanged=True,restartWithoutSequence100=True),indent=2))
finally:
    try:
        if helper:stop_app('cleanup')
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
