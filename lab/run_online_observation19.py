"""One-shot coordinated observation and new dedicated fixture. Earlier fixture is never replayed."""
import hashlib,json,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/online-observe-20261004-r3';CONTAINER='oraredo-19c-lab-20261004'
OUT.mkdir(parents=True,exist_ok=False);commands=[]
def run(name,args):
    started=time.time();r=subprocess.run(list(map(str,args)),capture_output=True)
    (OUT/(name+'.stdout.txt')).write_bytes(r.stdout);(OUT/(name+'.stderr.txt')).write_bytes(r.stderr)
    commands.append({'name':name,'started':started,'ended':time.time(),'command':list(map(str,args)),'rc':r.returncode})
    (OUT/'commands.json').write_text(json.dumps(commands,indent=2),encoding='utf-8')
    if r.returncode:raise RuntimeError(name+': '+str(r.returncode)+'; preserve; no DML replay')
    return r.stdout.decode('utf-8')
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).strip()
assert binding=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
run('observer-copy',['docker','cp',ROOT/'lab/observe_online_redo.py',CONTAINER+':/tmp/ork-observer-r3.py'])
run('fixture-copy',['docker','cp',ROOT/'lab/sql/fixture19-capture-r3.sql',CONTAINER+':/tmp/ork-capture-r3.sql'])
args=['docker','exec',CONTAINER,'python3','/tmp/ork-observer-r3.py','/opt/oracle/oradata/ORK19/redo02.log','/tmp/ork-online-observe-r3',32768,40,0.5]
with (OUT/'observer.stdout.txt').open('wb') as stdout,(OUT/'observer.stderr.txt').open('wb') as stderr:
    process=subprocess.Popen(list(map(str,args)),stdout=stdout,stderr=stderr);started=time.time()
    while (OUT/'observer.stdout.txt').stat().st_size==0:
        if process.poll() is not None:raise RuntimeError('observer ended before READY; no fixture execution')
        if time.time()-started>30:raise RuntimeError('observer READY timeout; no fixture execution')
        time.sleep(0.1)
    first=json.loads((OUT/'observer.stdout.txt').read_text().splitlines()[0]);assert first['sequence']==17
    if process.poll() is not None:raise RuntimeError('observer terminal before fixture; no fixture execution')
    before={'pid':process.pid,'poll':process.poll(),'time':time.time(),'first_durable_sample':first}
    text=run('fixture',['docker','exec',CONTAINER,'sqlplus','-s','/','as','sysdba','@/tmp/ork-capture-r3.sql'])
    after={'pid':process.pid,'poll':process.poll(),'time':time.time()}
    (OUT/'overlap.json').write_text(json.dumps({'before_fixture':before,'after_fixture':after},indent=2),encoding='utf-8')
    rc=process.wait(timeout=120)
    commands.append({'name':'observer','started':started,'ended':time.time(),'command':list(map(str,args)),'rc':rc})
    (OUT/'commands.json').write_text(json.dumps(commands,indent=2),encoding='utf-8');assert rc==0
run('executed-fixture',['docker','cp',CONTAINER+':/tmp/ork-capture-r3.sql',OUT/'executed-fixture.sql'])
run('journal-export',['docker','cp',CONTAINER+':/tmp/ork-online-observe-r3',OUT/'capture'])
archive=[s.split('|') for s in text.splitlines() if s.startswith('ARCHIVE|')];assert len(archive)==1
run('archive-export',['docker','cp',CONTAINER+':'+archive[0][4],OUT/'archive-1-17.dbf'])
assert before['poll'] is None and after['poll'] is None
assert (OUT/'executed-fixture.sql').read_bytes()==(ROOT/'lab/sql/fixture19-capture-r3.sql').read_bytes()
print(json.dumps({'fixture_rc':0,'observer_rc':0,'live_before_and_after_fixture':True,'capture_complete':False,'cdc_events':0}))
