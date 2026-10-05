"""Replay retained raw windows only; never accesses Oracle or target services."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-window-20261005';B=R/'lab/out/release-oracle-window-20261005';K=R/'lab/out/captured-batch-20261005';T=R/'lab/out/oracle-target-20261005'
O.mkdir(parents=True,exist_ok=False)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,rc=0):
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
    for ext,b in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(b)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,n
    return p
proof=json.loads((B/'proof.json').read_text())
for name in ['kernel','adapter','spi']:assert sha(B/(name+'.jar'))==proof[name+'_sha256']
manifest=json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-target-20261005.json').read_text(encoding='utf-8'));pinned={f['path']:f['sha256'] for f in manifest['files']}
inputs=[]
for seq in [40,41,42]:
    p=T/('sequence-'+str(seq)+'.oracap');assert sha(p)==pinned[p.relative_to(R).as_posix()];inputs += [str(p),sha(p)]
snapshot=T/'snapshot.txt';assert sha(snapshot)=='646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf'
drift=O/'derived-snapshot.txt';drift.write_bytes(snapshot.read_bytes()+b'\n');assert sha(drift)!=sha(snapshot)
classes=O/'classes';classes.mkdir();cp=';'.join(str(B/(n+'.jar')) for n in ['kernel','adapter','spi'])
run('compile-window-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleWindowProbe.java'])
actual=run('raw-window',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp+';'+str(classes),'OracleWindowProbe',snapshot,sha(snapshot),drift,sha(drift)]+inputs)
lines=actual.stdout.decode().splitlines();ranges={s.split('|')[0]:s.split('|') for s in lines[:2]}
ref=(K/'actual.stdout.txt').read_text();full=ref.splitlines()[0].split('|')[1]
prefix=json.loads((R/'lab/out/oracle-overlap-20261005/proof.json').read_text())['prefix_batch']
assert ranges['FULL']==['FULL',full,'42','9','2366759',str(0x0300019b)]
# Independent expected blocks come from the original kernel input report, native-verified before this interface.
positions={s.split('|')[1]:s.split('|') for s in ref.splitlines() if s.startswith('INPUT|')}
receipts={str(v['sequence']):v for v in json.loads((R/'lab/out/closed-follow-20261005/capture/receipt.json').read_text())['captured']}
assert ranges['FULL'][3:5]==[str(receipts['42']['blocks']),positions['42'][5]]
assert ranges['PREFIX']==['PREFIX',prefix,'41',str(receipts['41']['blocks']),positions['41'][5],str(0x0300019b)]
args=json.loads((K/'actual.command.json').read_text());args[2]=str(B/'kernel.jar')+';'+str(K/'classes')
replay=run('complete-events-regression',args)
assert replay.stdout==(K/'actual.stdout.txt').read_bytes()
public_args=json.loads((K/'public-events.command.json').read_text());public_args[2]=str(B/'kernel.jar')
public=run('public-events',public_args,1)
assert public.stdout==b'' and b'DML_TRANSACTION_DICTIONARY_GATES_NOT_ACCEPTED' in public.stderr
result={'window_relationships':['EXTENDS_PREFIX','COVERED_PREFIX','SAME_RANGE'],'original_three_event_output_bytes_equal':True,
        'retained_leaf':0x0300019b,'baseline_acquisition_drift_refused':True,'capture_list_immutable':True,
        'range_matches_original_native_verified_inputs':True,'source_actions':0,'target_actions':0,'public_events_stdout':0,
        'checkpoint_implemented':False,'retention_accepted':False}
(O/'proof.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
