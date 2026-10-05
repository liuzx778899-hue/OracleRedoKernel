"""Resume read-only evidence after a host observation timeout; never restart collector or replay DML."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/acquired-dml-capture-20261005';S='oraredo-19c-lab-20261004';remote='/tmp/ork-acquired-dml-20261005'
terminal=json.loads((O/'collector.stdout.txt').read_text().splitlines()[-1]);assert terminal['terminal'] and terminal['captured_generations']==3 and terminal['next_sequence']==55
p=subprocess.run(['docker','exec',S,'pgrep','-af','/tmp/follow_closed_members.py '+remote],capture_output=True);assert p.returncode==1 and not p.stdout
def run(name,args,input=None):
 assert not (O/(name+'.rc.txt')).exists();p=subprocess.run(list(map(str,args)),input=input,capture_output=True,timeout=240)
 (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout,name;return p.stdout.decode()
run('copy-capture',['docker','cp',S+':'+remote,O/'capture'])
paths=[]
for seq,name in zip(range(52,55),['MIXED_COMMIT','INSERT_ROLLBACK','DELETE_ROLLBACK']):
 archive=O/('archive-'+str(seq)+'.dbf');assert archive.is_file();rows=[x.strip().split('|') for x in (O/(name+'-once.stdout.txt')).read_text().splitlines() if x.strip().startswith('ARCHIVE|')];assert len(rows)==1 and int(rows[0][1])==seq;paths.append(rows[0][2])
 run('capture-archive-'+str(seq),[sys.executable,R/'lab/compare_observation_archive.py',O/'capture'/('sequence-'+str(seq)+'.oracap'),archive])
body="whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off\nalter session set tracefile_identifier='ORK_ACQUIRED_DML';\n"+''.join("alter system dump logfile '"+p+"';\n" for p in paths)+"select value from v$diag_info where name='Default Trace File';\nexit success\n";(O/'independent-dump.sql').write_text(body)
trace=run('independent-dump',['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode());traces=[x.strip() for x in trace.splitlines() if x.strip().startswith('/opt/oracle/') and x.strip().endswith('.trc')];assert len(traces)==1
run('copy-independent-dump',['docker','cp',S+':'+traces[0],O/'dump.trc'])
(O/'capture-reference.json').write_text(json.dumps(dict(sequences=[52,53,54],baseline=json.loads((O/'baseline-reference.json').read_text()),capture_archive_differences=0,collector_terminal_verified=True,collector_host_rc_unavailable=True,source_actions_replayed=False),indent=2))
print('ACQUIRED_DML_READONLY_EVIDENCE_RESUMED|52|53|54')
