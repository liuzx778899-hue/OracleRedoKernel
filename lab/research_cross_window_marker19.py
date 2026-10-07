"""Read-only raw/native research for the sealed new transaction; never replays source DML."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-active-20261007';S='oraredo-19c-lab-20261004'
def run(name,args,body=None):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists()
    (N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=body,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,(name,p.returncode)
    return p.stdout.decode()
for name in ['capture_cross_window19.py','research_cross_window_marker19.py','MediaRecoveryMarkerProbe.java']:(N/('executed-'+name)).write_bytes((R/'lab'/name).read_bytes())
run('compile-marker',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',N/'kernel.jar','-d',N/'event-classes',R/'lab/MediaRecoveryMarkerProbe.java'])
run('raw-marker',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(N/'event-classes')+';'+str(N/'kernel.jar'),'MediaRecoveryMarkerProbe',N/'archive-74.dbf'])
body="whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\nalter session set tracefile_identifier='ORK_CROSS_WINDOW';\nalter system dump logfile '/opt/oracle/product/19c/dbhome_1/dbs/arch1_74_1245693159.dbf';\nselect value from v$diag_info where name='Default Trace File';\nexit success\n"
(N/'native-marker.sql').write_text(body)
text=run('native-marker',['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode())
assert 'SP2-' not in text
paths=[l.strip() for l in text.splitlines() if l.strip().startswith('/opt/oracle/') and l.strip().endswith('.trc')];assert len(paths)==1
run('copy-native-marker',['docker','cp',S+':'+paths[0],N/'sequence74-native.trc'])
proof=dict(baseline=json.loads((N/'baseline-reference.json').read_text()),sequences=[73,74],xid='10.14.2869',pending_rc=int((N/'events-pending.rc.txt').read_text()),closed_rc=int((N/'events-closed.rc.txt').read_text()),pending_stdout_bytes=(N/'events-pending.stdout.txt').stat().st_size,closed_stdout_bytes=(N/'events-closed.stdout.txt').stat().st_size,collector_rc=int((N/'collector.rc.txt').read_text()),source_dml_replayed=False,events=0,target_actions=0,cross_window_recovery='NOT_VERIFIED')
assert proof['pending_rc']==proof['closed_rc']==1 and proof['pending_stdout_bytes']==proof['closed_stdout_bytes']==0 and proof['collector_rc']==0
(N/'research-proof.json').write_text(json.dumps(proof,indent=2))
print(json.dumps(proof))
