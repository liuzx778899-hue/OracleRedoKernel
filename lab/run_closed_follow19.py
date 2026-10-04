"""One-shot dedicated controlled three-generation experiment. Never replay committed source steps."""
import hashlib,json,subprocess,sys,time
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/closed-follow-20261005';CONTAINER='oraredo-19c-lab-20261004'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit one-shot --execute required')
OUT.mkdir(parents=True,exist_ok=False)

def run(name,args,input_data=None):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),input=input_data,capture_output=True,timeout=180)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if result.returncode:raise RuntimeError(name+': preserve; do not replay source steps')
    return result.stdout.decode()

def sql(name,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\n'+body+'\nexit success\n'
    (OUT/(name+'.sql')).write_text(text)
    return run(name,['docker','exec','-i',CONTAINER,'sqlplus','-s','/','as','sysdba'],text.encode())

binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).strip()
assert binding=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
inventory=sql('inventory',"select 'MEMBER|'||member from v$logfile order by group#;\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';\ndeclare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;\n/\nalter session set container=ORKPDB;\ndeclare n number;begin select count(*) into n from ORKLAB.T_BASELINE_20261004 where ID=11 and TXT='blocking-closed' and NOTE='new-note';if n!=1 then raise_application_error(-20002,'fixture mismatch');end if;end;\n/")
members=[line.strip().split('|')[1] for line in inventory.splitlines() if line.strip().startswith('MEMBER|')]
sequence=int([line.strip().split('|')[1] for line in inventory.splitlines() if line.strip().startswith('CURRENT|')][0]);assert len(members)==3 and sequence==40
run('baseline',[sys.executable,ROOT/'lab/capture_heap_baseline19.py','--execute',OUT/'baseline'])
for script in ('resume_online_observation.py','follow_closed_members.py'):
    frozen=OUT/('executed-'+script);frozen.write_bytes((ROOT/'lab'/script).read_bytes())
    run('copy-'+script,['docker','cp',frozen,CONTAINER+':/tmp/'+script])
    run('verify-executed-'+script,['docker','exec',CONTAINER,'sha256sum','/tmp/'+script])
    if hashlib.sha256(frozen.read_bytes()).hexdigest() not in (OUT/('verify-executed-'+script+'.stdout.txt')).read_text():raise RuntimeError('EXECUTED_SOURCE_DIGEST_MISMATCH')
remote='/tmp/ork-closed-follow-20261005';args=['docker','exec',CONTAINER,'python3','/tmp/follow_closed_members.py',remote,sequence,320,0.25]+members
(OUT/'collector.command.json').write_text(json.dumps(list(map(str,args))))
with (OUT/'collector.stdout.txt').open('wb') as stdout,(OUT/'collector.stderr.txt').open('wb') as stderr:
    process=subprocess.Popen(list(map(str,args)),stdout=stdout,stderr=stderr)
    def wait_for(key,value):
        deadline=time.time()+150
        while time.time()<deadline:
            lines=(OUT/'collector.stdout.txt').read_text().splitlines()
            if any(json.loads(line).get(key)==value for line in lines):return
            if process.poll() is not None:raise RuntimeError('collector terminal before '+str(value)+'; no source replay')
            time.sleep(0.1)
        raise RuntimeError('collector observation timeout; inspect same process, no restart')
    wait_for('ready',True)
    previous='blocking-closed';archives=[]
    for offset,value in enumerate(('capture-follow-a','capture-follow-b','blocking-closed')):
        seq=sequence+offset
        body="declare s number;begin select sequence# into s from v$log where status='CURRENT';if s!="+str(seq)+" then raise_application_error(-20003,'sequence changed');end if;end;\n/\nalter session set container=ORKPDB;\nbegin update ORKLAB.T_BASELINE_20261004 set TXT='"+value+"' where ID=11 and TXT='"+previous+"' and NOTE='new-note';if sql%rowcount!=1 then raise_application_error(-20004,'CAS mismatch');end if;commit;end;\n/\nselect 'TRUTH|"+str(seq)+"|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nalter session set container=CDB$ROOT;\nalter system archive log current;\nselect 'ARCHIVE|'||sequence#||'|'||name from v$archived_log where thread#=1 and sequence#="+str(seq)+" and name is not null order by recid;"
        text=sql('step-'+str(seq),body);wait_for('captured_sequence',seq)
        paths=[line.strip().split('|')[2] for line in text.splitlines() if line.strip().startswith('ARCHIVE|')];assert len(paths)==1
        archive=OUT/('archive-'+str(seq)+'.dbf');run('export-'+str(seq),['docker','cp',CONTAINER+':'+paths[0],archive]);archives.append(archive);previous=value
    rc=process.wait(timeout=150);(OUT/'collector.rc.txt').write_text(str(rc));assert rc==0
run('export-capture',['docker','cp',CONTAINER+':'+remote,OUT/'capture'])
for offset,archive in enumerate(archives):
    seq=sequence+offset;run('coverage-'+str(seq),[sys.executable,ROOT/'lab/compare_observation_archive.py',OUT/'capture'/('sequence-'+str(seq)+'.oracap'),archive])
print(json.dumps({'captured_sequences':[40,41,42],'archive_data_byte_differences':0,'controlled_switches':3,'capture_complete':False,'consumer_ack':False,'cdc_events':0}))
