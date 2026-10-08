"""Once-only normal business transaction across multiple heap blocks."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,time
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d2-blocks-source-20261008';B=R/'lab/out/d2-blocks-runtime-20261008';S='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute'];assert not N.exists(),'never replay this source batch';assert (B/'baseline-reference.json').exists()
N.mkdir();shutil.copyfile(__file__,N/'executed-source.py')
def run(label, args, data=None):
    assert not (N / (label + '.rc.txt')).exists()
    args = list(map(str, args))
    (N / (label + '.command.json')).write_text(json.dumps(args))
    p = subprocess.run(args, input=data, capture_output=True, timeout=180)
    for suffix, value in [('stdout.txt', p.stdout), ('stderr.txt', p.stderr), ('rc.txt', str(p.returncode).encode())]: (N / (label + '.' + suffix)).write_bytes(value)
    assert p.returncode == 0, (label, p.returncode)
    return p.stdout.decode('utf-8')

def sql_text(body):
    return 'whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\n' + body + '\nexit success\n'

def sql(label, body):
    text = sql_text(body)
    (N / (label + '.sql')).write_bytes(text.encode())
    out = run(label, ['docker', 'exec', '-i', S, 'sqlplus', '-s', '/', 'as', 'sysdba'], text.encode())
    assert 'SP2-' not in out
    return out


b=json.loads((B/'baseline-reference.json').read_text());assert b['sourceFixture']=='T_D2_BLOCKS_20261008';assert hashlib.sha256(Path(b['path']).read_bytes()).hexdigest()==b['sha256']
(N/'baseline-reference.json').write_text(json.dumps(b,indent=2))
assert run('owner',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Running}}']).strip()=='OracleRedoKernel|true'
pre=sql('preflight',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROWS|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where TXT=rpad('block-seed',100,'x') and NOTE='anchor';")
assert 'LOGGING|NO|NO' in pre and 'ROWS|20' in pre
start=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
body=sql_text("alter session set container=ORKPDB;\nbegin for i in 0..19 loop update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='block1' where ID=i and NOTE='anchor';if sql%rowcount!=1 then raise_application_error(-20001,'rowcount');end if;end loop;dbms_session.set_identifier('ork-d2-blocks-held-20261008');dbms_session.sleep(45);end;\n/\ncommit;\nbegin for i in 0..19 loop update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='anchor' where ID=i and NOTE='block1';if sql%rowcount!=1 then raise_application_error(-20002,'rowcount');end if;end loop;end;\n/\ncommit;")
(N/'source-actions-started.txt').write_text('never replay blocks source experiment\n');(N/'writer.sql').write_text(body)
with (N/'writer.stdout').open('xb') as out,(N/'writer.stderr').open('xb') as err:
 p=subprocess.Popen(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],stdin=subprocess.PIPE,stdout=out,stderr=err);p.stdin.write(body.encode());p.stdin.close()
 try:
  for i in range(20):
   text=sql('held-'+str(i),"select 'HELD|'||t.xidusn||'.'||t.xidslot||'.'||t.xidsqn from v$session s join v$transaction t on s.taddr=t.addr where s.client_identifier='ork-d2-blocks-held-20261008' and s.event='PL/SQL lock timer';")
   if 'HELD|' in text:break
   assert p.poll() is None;time.sleep(1)
  assert 'HELD|' in text;(N/'held.txt').write_text(text);sql('switch-prefix','alter system archive log current;')
 finally:
  rc=p.wait(timeout=150);(N/'writer.rc').write_text(str(rc));assert rc==0
final=sql('source-final',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROWS|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where TXT=rpad('block-seed',100,'x') and NOTE='anchor';")
assert 'LOGGING|NO|NO' in final and 'ROWS|20' in final
end=int(next(l.split('|')[1] for l in final.splitlines() if l.startswith('CURRENT|')));sql('switch-closed','alter system archive log current;');archives=[]
for seq in range(start,end+1):
 text=sql('archive-'+str(seq),f"select 'ARCHIVE|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
 paths=set(l.split('|')[1] for l in text.splitlines() if l.startswith('ARCHIVE|'));assert len(paths)==1;path=next(iter(paths));local=N/(str(seq)+'.raw')
 run('copy-'+str(seq),['docker','cp',S+':'+path,local]);sha=hashlib.sha256(local.read_bytes()).hexdigest();assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha
 archives.append(dict(sequence=seq,path=str(local),sha256=sha,bytes=local.stat().st_size,sourcePath=path))
(N/'archives.json').write_text(json.dumps(archives,indent=2));print('FROZEN_SOURCE_RESTORED',flush=True)
