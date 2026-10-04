"""Restart actual multi-member capture then one dedicated archive closure, no business DML."""
import hashlib,json,subprocess,sys,time
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/closed-resume-20261005';OLD=ROOT/'lab/out/closed-follow-20261005';CONTAINER='oraredo-19c-lab-20261004';REMOTE='/tmp/ork-closed-follow-20261005'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit --execute required')
OUT.mkdir(parents=True,exist_ok=False)

def run(name,args,input_data=None):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),input=input_data,capture_output=True,timeout=180)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if result.returncode:raise RuntimeError(name+': preserve evidence; no closure replay')
    return result.stdout.decode()

assert run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).strip()=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
sql="whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';\ndeclare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and resetlogs_change#=1920977 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;\n/\nexit success\n"
text=run('inventory',['docker','exec','-i',CONTAINER,'sqlplus','-s','/','as','sysdba'],sql.encode());assert 'CURRENT|43' in text
run('export-before',['docker','cp',CONTAINER+':'+REMOTE,OUT/'before'])
assert (OUT/'before/receipt.json').read_bytes()==(OLD/'capture/receipt.json').read_bytes()
expected=hashlib.sha256((OUT/'before/receipt.json').read_bytes()).hexdigest();members=[m['path'] for m in json.loads((OUT/'before/source.json').read_text())['members']]
for name in ('follow_closed_members.py','resume_online_observation.py'):
    frozen=OUT/('executed-'+name);frozen.write_bytes((ROOT/'lab'/name).read_bytes());run('copy-'+name,['docker','cp',frozen,CONTAINER+':/tmp/'+name])
    assert hashlib.sha256(frozen.read_bytes()).hexdigest() in run('executed-sha-'+name,['docker','exec',CONTAINER,'sha256sum','/tmp/'+name])
args=['docker','exec',CONTAINER,'python3','/tmp/follow_closed_members.py',REMOTE,40,240,0.25]+members+['--resume',expected]
(OUT/'collector.command.json').write_text(json.dumps(list(map(str,args))))
with (OUT/'collector.stdout.txt').open('wb') as stdout,(OUT/'collector.stderr.txt').open('wb') as stderr:
    process=subprocess.Popen(list(map(str,args)),stdout=stdout,stderr=stderr)
    def wait_for(key,value):
        deadline=time.time()+150
        while time.time()<deadline:
            if any(json.loads(line).get(key)==value for line in (OUT/'collector.stdout.txt').read_text().splitlines()):return
            if process.poll() is not None:raise RuntimeError('collector terminal before '+str(value)+'; preserve, no restart')
            time.sleep(0.1)
        raise RuntimeError('observation timeout; inspect same collector handle, no restart')
    wait_for('ready',True)
    ready=json.loads((OUT/'collector.stdout.txt').read_text().splitlines()[0]);assert ready['next_sequence']==43 and ready['resumed'] is True
    sql="whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\ndeclare s number;begin select sequence# into s from v$log where status='CURRENT';if s!=43 then raise_application_error(-20002,'sequence changed');end if;end;\n/\nalter system archive log current;\nselect 'ARCHIVE|'||sequence#||'|'||name from v$archived_log where thread#=1 and sequence#=43 and name is not null order by recid;\nexit success\n"
    (OUT/'closure.sql').write_text(sql);text=run('closure',['docker','exec','-i',CONTAINER,'sqlplus','-s','/','as','sysdba'],sql.encode());wait_for('captured_sequence',43)
    paths=[line.strip().split('|')[2] for line in text.splitlines() if line.strip().startswith('ARCHIVE|')];assert len(paths)==1
    run('export-archive43',['docker','cp',CONTAINER+':'+paths[0],OUT/'archive-43.dbf'])
    rc=process.wait(timeout=180);(OUT/'collector.rc.txt').write_text(str(rc));assert rc==0
run('export-after',['docker','cp',CONTAINER+':'+REMOTE,OUT/'after'])
for n in (40,41,42):assert (OUT/'before'/('sequence-'+str(n)+'.oracap')).read_bytes()==(OUT/'after'/('sequence-'+str(n)+'.oracap')).read_bytes()
run('coverage43',[sys.executable,ROOT/'lab/compare_observation_archive.py',OUT/'after/sequence-43.oracap',OUT/'archive-43.dbf'])
print(json.dumps({'resumed_next_sequence':43,'appended_sequence':43,'original_journals_byte_identical':True,'capture_complete':False,'consumer_ack':False,'cdc_events':0,'source_business_dml':False,'controlled_closures':1}))
