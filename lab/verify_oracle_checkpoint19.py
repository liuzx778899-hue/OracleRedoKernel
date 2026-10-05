"""Actual Linux checkpoint persistence/recovery against existing healthy Oracle receipts."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-checkpoint-20261005';B=R/'lab/out/release-oracle-checkpoint-20261005';T=R/'lab/out/oracle-target-20261005';GOOD=R/'lab/out/oracle-overlap-20261005';SOURCE='oraredo-19c-lab-20261004'
O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None,rc=0):
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,n
    return p
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|running'
assert not run('no-existing-helper',['docker','ps','--filter','label=scope=oracle-checkpoint','--format','{{.Names}}']).stdout.strip()
proof=json.loads((B/'proof.json').read_text())
for n in ['adapter','kernel','spi']:
    assert sha(B/(n+'.jar'))==proof[n+'_sha256'];shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
manifest=json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-target-20261005.json').read_text(encoding='utf-8'));pins={f['path']:f['sha256'] for f in manifest['files']}
assert sha(T/'ojdbc8.jar')==pins[(T/'ojdbc8.jar').relative_to(R).as_posix()]
for seq in [40,41,42]:p=T/('sequence-'+str(seq)+'.oracap');assert sha(p)==pins[p.relative_to(R).as_posix()]
classes=O/'classes';classes.mkdir();cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi'])+';'+str(T/'ojdbc8.jar')
run('compile-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleCheckpointProbe.java'])
old=json.loads((GOOD/'proof.json').read_text());full=old['full_batch'];prefix=old['prefix_batch']
query=(T/'POST-final.sql.redacted').read_text().replace('POST','MIXD')
def target(n):return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],query.encode()).stdout
before=target('target-before');assert sorted(before.splitlines())==sorted((GOOD/'target-fresh.stdout.txt').read_bytes().splitlines())
def worker(n,folder,current=full,rc=0):
    args=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-checkpoint','--user','0','--network','container:'+SOURCE,
          '--mount','type=bind,source='+str(T)+',target=/old,readonly','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
          '-cp','/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/old/ojdbc8.jar:/evidence/classes','OracleCheckpointProbe','/old/target.secret',
          '/evidence/'+folder.name,current,'/old/snapshot.txt','646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf']
    for seq in [40,41,42]:p=T/('sequence-'+str(seq)+'.oracap');args += ['/old/'+p.name,sha(p)]
    return run(n,args,rc=rc)
normal=O/'normal';normal.mkdir();assert worker('normal',normal).stdout==b'CONSUMED|42|9|2366759|0|2\n'
checkpoint=(normal/'checkpoint').read_bytes();acks={n:(normal/(n+'.ack')).read_bytes() for n in [prefix,full]}
assert worker('old-prefix-no-retreat',normal,prefix).stdout==b'CONSUMED|42|9|2366759|0|2\n' and (normal/'checkpoint').read_bytes()==checkpoint
for n in [prefix,full]:assert (normal/(n+'.ack')).read_bytes()==acks[n]
lines=checkpoint.decode().splitlines();assert lines[0]=='ORK_ORACLE_CONSUMER_V1' and lines[2]=='FRONTIER|'+full
assert lines[-1]=='SHA256|'+hashlib.sha256(('\n'.join(lines[:-1])+'\n').encode()).hexdigest()
for name in ['complete-pending','partial-pending','bad-checkpoint','wrong-ack','unknown-ack','stale-prefix']:
    folder=O/name;folder.mkdir()
    if name in ['bad-checkpoint','wrong-ack','unknown-ack']:shutil.copytree(normal,folder,dirs_exist_ok=True)
    if name=='complete-pending':(folder/'checkpoint.tmp').write_bytes(checkpoint)
    elif name=='partial-pending':(folder/'checkpoint.tmp').write_bytes(checkpoint[:100])
    elif name=='bad-checkpoint':(folder/'checkpoint').write_bytes(checkpoint.replace(b'|2366759|',b'|2366760|'))
    elif name=='wrong-ack':(folder/(full+'.ack')).write_bytes(acks[full].replace(b'|3\n',b'|4\n'))
    elif name=='unknown-ack':(folder/('0'*64+'.ack')).write_bytes(acks[full])
    elif name=='stale-prefix':
        row=next(x for x in lines if x.startswith('BATCH|'+prefix+'|'));body='\n'.join([lines[0],lines[1],'FRONTIER|'+prefix,row])+'\n'
        (folder/'checkpoint').write_bytes((body+'SHA256|'+hashlib.sha256(body.encode()).hexdigest()+'\n').encode())
    prior={p.name:sha(p) for p in folder.iterdir() if p.is_file() and p.name!='consumer.lock'}
    if name in ['complete-pending','stale-prefix']:
        assert worker(name,folder,prefix).stdout==b'CONSUMED|42|9|2366759|0|2\n'
        assert (folder/'checkpoint').read_bytes()==checkpoint
        for n in [prefix,full]:assert (folder/(n+'.ack')).read_bytes()==acks[n]
        if name=='complete-pending':
            saved=list(folder.glob('checkpoint.tmp.retained-*'));assert len(saved)==1 and saved[0].read_bytes()==checkpoint
    else:
        failed=worker(name,folder,rc=1);assert failed.stdout==b'' and b'ORACLE_CONSUMER_' in failed.stderr
        assert all(sha(folder/n)==v for n,v in prior.items())
        assert set(prior)=={p.name for p in folder.iterdir() if p.is_file() and p.name!='consumer.lock'}
assert sorted(target('target-after').splitlines())==sorted(before.splitlines())
assert run('lgwr',['docker','exec',SOURCE,'cat','/proc/5601/comm']).stdout.decode().strip()=='ora_lgwr_ork19'
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-checkpoint','--format','{{.Names}}']).stdout.strip()
result={'actual_linux_checkpoint':True,'actual_oracle_receipt_validation':True,'old_prefix_frontier_remains_sequence':42,
        'complete_pending_restored_and_retained':True,'stale_prefix_recovered_from_all_registered_target_commits':True,
        'file_refusals':4,'refusal_success_output':0,'refusal_owned_files_preserved':True,'target_dml':0,'target_snapshot_unchanged':True,
        'kernel_tests_rerun':False,'source_dml':0,'checkpoint_sigkill_accepted':False,'new_dml_then_checkpoint_accepted':False,'retention_accepted':False}
(O/'proof.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
