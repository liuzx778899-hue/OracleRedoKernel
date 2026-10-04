"""SIGKILL only our own collector, never Oracle; existing closed seq43 reused read-only."""
import hashlib,json,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/closed-resume-crash-20261005';OLD=ROOT/'lab/out/closed-resume-20261005';CONTAINER='oraredo-19c-lab-20261004'
OUT.mkdir(parents=True,exist_ok=False);members=[b['path'] for b in json.loads((OLD/'after/source.json').read_text())['members']]

def run(name,args,expected=0,reason=None,empty=False):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if result.returncode!=expected or empty and result.stdout or reason and reason.encode() not in result.stderr:raise RuntimeError(name+': unexpected result; preserve')
    return result

assert run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
for name in ('follow_closed_members.py','resume_online_observation.py'):
    frozen=OUT/('executed-'+name);frozen.write_bytes((ROOT/'lab'/name).read_bytes());run('copy-'+name,['docker','cp',frozen,CONTAINER+':/tmp/'+name])
    assert hashlib.sha256(frozen.read_bytes()).hexdigest().encode() in run('executed-sha-'+name,['docker','exec',CONTAINER,'sha256sum','/tmp/'+name]).stdout
base=['docker','exec',CONTAINER,'python3','/tmp/follow_closed_members.py']

script="""import json,os,signal,sys
sys.path.insert(0,'/tmp')
import follow_closed_members as collector
original=collector.durable_json
def crash(directory,name,value):
    if name=='receipt.json' and value['captured']:
        print('KILL_POINT_BEFORE_AGGREGATE',flush=True)
        os.kill(os.getpid(),signal.SIGKILL)
    return original(directory,name,value)
collector.durable_json=crash
collector.follow(sys.argv[1],43,1,0,json.loads(sys.argv[2]))
"""
path=OUT/'kill-before-aggregate.py';path.write_text(script);remote='/tmp/ork-closed-real-kill-aggregate-20261005';run('copy-kill-aggregate',['docker','cp',path,CONTAINER+':/tmp/ork-kill-aggregate.py'])
result=run('kill-aggregate',['docker','exec',CONTAINER,'python3','/tmp/ork-kill-aggregate.py',remote,json.dumps(members)],137)
assert b'KILL_POINT_BEFORE_AGGREGATE' in result.stdout
run('export-crashed',['docker','cp',CONTAINER+':'+remote,OUT/'crashed'])
receipt=OUT/'crashed/receipt.json';saved=json.loads(receipt.read_text());assert saved['next_sequence']==43 and not saved['captured']
expected=hashlib.sha256(receipt.read_bytes()).hexdigest();run('recover-aggregate',base+[remote,43,1,0]+members+['--resume',expected])
run('export-recovered',['docker','cp',CONTAINER+':'+remote,OUT/'recovered'])
assert (OUT/'crashed/sequence-43.oracap').read_bytes()==(OUT/'recovered/sequence-43.oracap').read_bytes()
after=json.loads((OUT/'recovered/receipt.json').read_text());assert after['next_sequence']==44 and len(after['captured'])==1
run('coverage-recovered',[sys.executable,ROOT/'lab/compare_observation_archive.py',OUT/'recovered/sequence-43.oracap',OLD/'archive-43.dbf'])

script="""import builtins,json,os,signal,sys
sys.path.insert(0,'/tmp')
import follow_closed_members as collector
original=builtins.open
class Partial:
    def __init__(self,stream):self.stream=stream
    def __enter__(self):return self
    def __exit__(self,*args):self.stream.close()
    def write(self,data):
        self.stream.write(data[:100]);self.stream.flush();os.fsync(self.stream.fileno())
        print('KILL_POINT_PARTIAL_FRAME',flush=True)
        os.kill(os.getpid(),signal.SIGKILL)
def opening(path,mode='r',*args,**kwargs):
    stream=original(path,mode,*args,**kwargs)
    return Partial(stream) if mode=='xb' and path.endswith('.oracap') else stream
builtins.open=opening
collector.follow(sys.argv[1],43,1,0,json.loads(sys.argv[2]))
"""
path=OUT/'kill-partial-frame.py';path.write_text(script);remote='/tmp/ork-closed-real-kill-partial-20261005';run('copy-kill-partial',['docker','cp',path,CONTAINER+':/tmp/ork-kill-partial.py'])
result=run('kill-partial',['docker','exec',CONTAINER,'python3','/tmp/ork-kill-partial.py',remote,json.dumps(members)],137);assert b'KILL_POINT_PARTIAL_FRAME' in result.stdout
run('export-partial',['docker','cp',CONTAINER+':'+remote,OUT/'partial'])
receipt=OUT/'partial/receipt.json';expected=hashlib.sha256(receipt.read_bytes()).hexdigest();assert (OUT/'partial/sequence-43.oracap').stat().st_size==100
run('refuse-partial',base+[remote,43,1,0]+members+['--resume',expected],1,'CAPTURE_UNCOMMITTED_GENERATION',True)
run('export-partial-after',['docker','cp',CONTAINER+':'+remote,OUT/'partial-after'])
assert (OUT/'partial/sequence-43.oracap').read_bytes()==(OUT/'partial-after/sequence-43.oracap').read_bytes()
assert (OUT/'partial/receipt.json').read_bytes()==(OUT/'partial-after/receipt.json').read_bytes()
(OUT/'proof.json').write_text(json.dumps({'actual_self_sigkills':2,'aggregate_lag_recovered_without_rewriting_journal':True,'partial_frame_refused_and_retained':True,'oracle_process_killed':False,'source_dml':False,'source_closures':0,'consumer_ack':False,'capture_complete':False},indent=2));print((OUT/'proof.json').read_text())
