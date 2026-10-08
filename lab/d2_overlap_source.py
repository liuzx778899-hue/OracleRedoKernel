"""Once-only two active transactions sharing a row after a real lock wait; lab only."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d2-overlap-source-20261008';B=R/'lab/out/d2-overlap-runtime-20261008';S='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute'];assert not N.exists(),'never replay source actions'
b=json.loads((B/'baseline-reference.json').read_text());assert b['sourceFixture']=='T_D2_OVERLAP_20261008'
assert hashlib.sha256(Path(b['path']).read_bytes()).hexdigest()==b['sha256']
N.mkdir();shutil.copyfile(__file__,N/'executed-source.py');(N/'baseline-reference.json').write_text(json.dumps(b,indent=2))
def run(label,args,data=None):
 p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:
  with (N/(label+'.'+suffix)).open('xb') as f:f.write(value)
 assert p.returncode==0,(label,p.returncode)
 return p.stdout.decode('utf-8')
def sqltext(body):return 'whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\n'+body+'\nexit success\n'
def sql(label,body):
 text=sqltext(body);(N/(label+'.sql')).write_text(text);out=run(label,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode());assert 'SP2-' not in out;return out
assert run('owner',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Running}}']).strip()=='OracleRedoKernel|true'
pre=sql('preflight',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_D2_OVERLAP_20261008 order by ID;")
assert 'LOGGING|NO|NO' in pre and all('ROW|'+str(i)+'|statistics-seed|anchor' in pre for i in [0,1])
start=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
handles=[];files=[]
def writer(label,body):
 text=sqltext('alter session set container=ORKPDB;\n'+body);(N/(label+'.sql')).write_text(text)
 out=(N/(label+'.stdout')).open('xb');err=(N/(label+'.stderr')).open('xb');files.extend([out,err])
 p=subprocess.Popen(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],stdin=subprocess.PIPE,stdout=out,stderr=err);handles.append((label,p));p.stdin.write(text.encode());p.stdin.close();return p
(N/'source-actions-started.txt').write_text('never replay this experiment\n')
try:
 a=writer('writer-a',"update ORKLAB.T_D2_OVERLAP_20261008 set TXT='overlap-a' where ID=0;\nexec dbms_session.set_identifier('ork-d2-overlap-a-20261008');\nexec dbms_session.sleep(70);\ncommit;")
 for i in range(25):
  held=sql('held-'+str(i),"select 'HELD|'||t.xidusn||'.'||t.xidslot||'.'||t.xidsqn||'|'||t.start_scn from v$session s join v$transaction t on s.taddr=t.addr where s.client_identifier='ork-d2-overlap-a-20261008' and s.event='PL/SQL lock timer';")
  if 'HELD|' in held:break
  assert a.poll() is None;time.sleep(1)
 assert 'HELD|' in held
 writer('writer-b',"update ORKLAB.T_D2_OVERLAP_20261008 set NOTE='overlap-b' where ID=1;\nexec dbms_session.set_identifier('ork-d2-overlap-b-20261008');\nupdate ORKLAB.T_D2_OVERLAP_20261008 set NOTE='overlap-b' where ID=0;\ncommit;")
 for i in range(25):
  wait=sql('wait-'+str(i),"select 'WAIT|'||t.xidusn||'.'||t.xidslot||'.'||t.xidsqn||'|'||t.start_scn||'|'||s.blocking_session from v$session s join v$transaction t on s.taddr=t.addr where s.client_identifier='ork-d2-overlap-b-20261008' and s.event='enq: TX - row lock contention' and s.blocking_session is not null;")
  if 'WAIT|' in wait:break
  time.sleep(1)
 assert 'WAIT|' in wait;assert all(p.poll() is None for _,p in handles)
 (N/'transactions.txt').write_text(held+wait);sql('switch-prefix','alter system archive log current;')
finally:
 results=[]
 for label,p in handles:
  rc=p.wait(timeout=180);(N/(label+'.rc')).write_text(str(rc));results.append(rc)
 for f in files:f.close()
 assert all(rc==0 for rc in results)
post=sql('committed',"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_D2_OVERLAP_20261008 order by ID;")
assert 'ROW|0|overlap-a|overlap-b' in post and 'ROW|1|statistics-seed|overlap-b' in post
sql('restore',"alter session set container=ORKPDB;\nupdate ORKLAB.T_D2_OVERLAP_20261008 set TXT='statistics-seed',NOTE='anchor' where ID=0;\nupdate ORKLAB.T_D2_OVERLAP_20261008 set TXT='statistics-seed',NOTE='anchor' where ID=1;\ncommit;")
final=sql('source-final',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_D2_OVERLAP_20261008 order by ID;")
assert 'LOGGING|NO|NO' in final and all('ROW|'+str(i)+'|statistics-seed|anchor' in final for i in [0,1])
end=int(next(l.split('|')[1] for l in final.splitlines() if l.startswith('CURRENT|')));sql('switch-closed','alter system archive log current;');archives=[]
for seq in range(start,end+1):
 text=sql('archive-'+str(seq),f"select 'ARCHIVE|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
 paths=set(l.split('|')[1] for l in text.splitlines() if l.startswith('ARCHIVE|'));assert len(paths)==1;path=next(iter(paths));local=N/(str(seq)+'.raw')
 run('copy-'+str(seq),['docker','cp',S+':'+path,local]);sha=hashlib.sha256(local.read_bytes()).hexdigest();assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha
 archives.append(dict(sequence=seq,path=str(local),sha256=sha,bytes=local.stat().st_size,sourcePath=path))
(N/'archives.json').write_text(json.dumps(archives,indent=2));print('FROZEN_SOURCE_RESTORED')
