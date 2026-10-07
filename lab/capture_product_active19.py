"""Owned, once-only transaction crossing two closed generations. No release or ACK claim."""
import hashlib,json,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/product-active-20261007';S='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute']
(N/'executed-capture_product_active19.py').write_bytes(Path(__file__).read_bytes())
assert not (N/'source-actions-started.txt').exists(),'never replay this transaction'
b=json.loads((N/'baseline-reference.json').read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert b['actual_product_http'] is True and b['acquisition_id']==int((N/'acquisition-id.txt').read_text())
assert sha(Path(b['path']))==b['sha256'] and json.loads((N/'baseline-truth-proof.json').read_text())['differences']==0

def run(name,args,body=None,required=True):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists()
    (N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=body,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
    if required:assert p.returncode==0 and b'SP2-' not in p.stdout,(name,p.returncode)
    return p

def sql(name,body):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\n'+body+'\nexit success\n'
    (N/(name+'.sql')).write_text(body)
    return run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode()).stdout.decode()

assert run('capture-ownership',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Running}}']).stdout.strip()==b'OracleRedoKernel|true'
active=run('capture-process-preflight',['docker','exec','-i',S,'python3','-'],b"from pathlib import Path\nfor p in Path('/proc').glob('[0-9]*/cmdline'):\n try:\n  a=p.read_bytes().split(b'\\0')\n  if any(x.endswith((b'/follow_closed_members.py',b'/resume_online_observation.py')) for x in a):print(p.parent.name)\n except OSError:pass\n")
assert not active.stdout.strip()
pre=sql('capture-source-preflight',"select 'MEMBER|'||member from v$logfile order by group#;\nselect 'CURRENT|'||sequence#||'|'||first_change# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SEED|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;")
q=[l.strip().split('|') for l in pre.splitlines()];members=[v[1] for v in q if v[0]=='MEMBER'];cur=next(v for v in q if v[0]=='CURRENT');start=int(cur[1])
assert len(members)==3 and int(cur[2])<=b['scn'] and ['LOGGING','NO','NO'] in q and ['SEED','0','history-product-restored','anchor'] in q
remote='/tmp/ork-product-active-20261007'
run('remote-directory',['docker','exec',S,'mkdir',remote])
for name in ['follow_closed_members.py','resume_online_observation.py']:
    (N/('executed-'+name)).write_bytes((R/'lab'/name).read_bytes())
    run('copy-'+name,['docker','cp',N/('executed-'+name),S+':'+remote+'/'+name])
    assert sha(N/('executed-'+name)).encode() in run('sha-'+name,['docker','exec',S,'sha256sum',remote+'/'+name]).stdout
classes=N/'event-classes';classes.mkdir()
run('compile-events',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',N/'kernel.jar','-d',classes,R/'lab/CapturedHistoricalBatchProbe.java'])
collector=['docker','exec',S,'python3',remote+'/follow_closed_members.py',remote+'/capture',str(start),'600','.2',*members]
(N/'collector.command.json').write_text(json.dumps(collector))

def wait_file(path,check,process):
    deadline=time.monotonic()+55
    while time.monotonic()<deadline:
        if check(path.read_text(errors='replace')):return
        if process.poll() is not None:raise RuntimeError('process terminal: preserve source marker, no replay')
        time.sleep(.1)
    raise RuntimeError('observation timeout: inspect the same handle, no replay')

def observed(seq,text):
    return any(json.loads(l).get('captured_sequence')==seq for l in text.splitlines())

with (N/'collector.stdout.txt').open('wb') as co,(N/'collector.stderr.txt').open('wb') as ce,(N/'transaction.stdout.txt').open('wb') as to,(N/'transaction.stderr.txt').open('wb') as te:
    cp=subprocess.Popen(collector,stdout=co,stderr=ce);(N/'collector-host-pid.txt').write_text(str(cp.pid))
    wait_file(N/'collector.stdout.txt',lambda t:any(json.loads(l).get('ready') for l in t.splitlines()),cp)
    first="whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\nalter session set container=ORKPDB;\nbegin update ORKLAB.T_V2_HIST_20261006 set TXT='product-active-stage-one' where ID=0 and TXT='history-product-restored';if sql%rowcount!=1 then raise_application_error(-20001,'rowcount');end if;end;\n/\nselect 'XID|'||xidusn||'.'||xidslot||'.'||xidsqn from v$transaction where ses_addr=(select saddr from v$session where sid=sys_context('USERENV','SID'));\nprompt PENDING_READY\n"
    second="begin update ORKLAB.T_V2_HIST_20261006 set TXT='history-product-restored' where ID=0 and TXT='product-active-stage-one';if sql%rowcount!=1 then raise_application_error(-20002,'rowcount');end if;end;\n/\ncommit;\nselect 'RESTORED|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\nexit success\n"
    (N/'transaction-first.sql').write_text(first);(N/'transaction-second.sql').write_text(second)
    (N/'source-actions-started.txt').write_text('ONCE_ONLY_NO_SOURCE_REPLAY')
    txn=subprocess.Popen(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],stdin=subprocess.PIPE,stdout=to,stderr=te);(N/'transaction-host-pid.txt').write_text(str(txn.pid))
    try:
        txn.stdin.write(first.encode());txn.stdin.flush()
        wait_file(N/'transaction.stdout.txt',lambda t:'PENDING_READY' in t,txn)
        for offset in range(2):
            seq=start+offset
            if offset:
                txn.stdin.write(second.encode());txn.stdin.flush();txn.stdin.close()
                rc=txn.wait(timeout=55);(N/'transaction.rc.txt').write_text(str(rc));assert rc==0
            truth=sql('archive-'+str(seq),"declare n number;begin select sequence# into n from v$log where status='CURRENT';if n!="+str(seq)+" then raise_application_error(-20003,'sequence changed');end if;end;\n/\nalter system archive log current;\nselect 'ARCHIVE|'||name from v$archived_log where thread#=1 and sequence#="+str(seq)+" and name is not null order by recid;")
            wait_file(N/'collector.stdout.txt',lambda t:observed(seq,t),cp)
            paths=[l.strip().split('|')[1] for l in truth.splitlines() if l.strip().startswith('ARCHIVE|')];assert len(paths)==1
            run('copy-archive-'+str(seq),['docker','cp',S+':'+paths[0],N/('archive-'+str(seq)+'.dbf')])
            if not offset:
                assert txn.poll() is None
                (N/'pending-session-observed.json').write_text(json.dumps(dict(host_pid=txn.pid,poll=None,sequence=seq,source_commit_sent=False)))
        rc=cp.wait(timeout=160);(N/'collector.rc.txt').write_text(str(rc));assert rc==0
    finally:
        if txn.poll() is None:
            txn.stdin.write(b'rollback;\nexit failure\n');txn.stdin.flush();txn.stdin.close()
            (N/'owned-session-abort.rc.txt').write_text(str(txn.wait(timeout=55)))
run('copy-captures',['docker','cp',S+':'+remote+'/capture',N/'capture'])
for seq in range(start,start+2):run('compare-'+str(seq),[sys.executable,R/'lab/compare_observation_archive.py',N/'capture'/('sequence-'+str(seq)+'.oracap'),N/('archive-'+str(seq)+'.dbf')])
base=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(classes)+';'+str(N/'kernel.jar'),'CapturedHistoricalBatchProbe',b['path'],b['sha256'],'50342539']
inputs=[]
for seq in range(start,start+2):
    path=N/'capture'/('sequence-'+str(seq)+'.oracap');inputs += [str(path),sha(path)]
    run('events-'+('pending' if seq==start else 'closed'),base+inputs,required=False)
sql('final-source',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SEED|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
(N/'capture-reference.json').write_text(json.dumps(dict(sequences=[start,start+1],baseline=b,source_actions_replayed=False,cross_publication_recovery='NOT_VERIFIED'),indent=2))
print('CROSS_GENERATION_CAPTURE_RETAINED_CHECK_EVENT_RC')
