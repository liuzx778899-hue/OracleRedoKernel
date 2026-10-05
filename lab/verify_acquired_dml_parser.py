"""Frozen binary parsing and independent native graph/row comparison of the new acquired-baseline experiment."""
import hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/acquired-dml-capture-20261005';A=R/'lab/out/oracle-snapshot-acquisition-20261005';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();java='D:/work/Java/jdk-17.0.7/bin/java.exe';javac='D:/work/Java/jdk-17.0.7/bin/javac.exe'
def run(name,args,rc=0):
 assert not (O/(name+'.rc.txt')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))));p=subprocess.run(list(map(str,args)),capture_output=True,timeout=600)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,(name,p.stderr[-1500:]);return p
repair=sys.argv[1:]==['--resume-leaf'];lib=O/'parser-libs';classes=O/'parser-classes'
if repair:assert (O/'raw-index-leaf.rc.txt').read_text()=='1'
else:
 lib.mkdir(exist_ok=False);classes.mkdir()
 for n in ['kernel','adapter','spi']:shutil.copyfile(A/(n+'.jar'),lib/(n+'.jar'))
assert sha(lib/'kernel.jar')=='960954207b626be4d19db547ce2a9e3c5245075e84edb7196935ba3c0489c55e';assert sha(lib/'adapter.jar')=='50feffa8494a9225e48a8886752b071918e9ae50d3373c0952c6add398cb3b35'
with zipfile.ZipFile(O/'application.jar') as z:
 for name in z.namelist():
  if name.startswith('BOOT-INF/lib/jackson-') and any(x in name for x in ['jackson-databind-','jackson-core-','jackson-annotations-']):(lib/Path(name).name).write_bytes(z.read(name))
cp=str(lib/'*')+';'+str(classes);probes=['RawDmlLeafProbe','PhysicalTransactionProbe','VerifiedHeapRowsProbe','CapturedRowsProbe','CapturedBatchProbe','AcquiredDmlProjectionProbe']
(O/('parser-repair-pin.json' if repair else 'parser-execution-pin.json')).write_text(json.dumps(dict(binaries={p.name:sha(p) for p in lib.glob('*.jar')},sources={n:sha(R/'lab'/(n+'.java')) for n in probes}),indent=2))
run('compile-new-parser-probes-r2' if repair else 'compile-new-parser-probes',[javac,'-encoding','UTF-8','-cp',cp,'-d',classes]+[R/'lab'/(n+'.java') for n in probes])
b=json.loads((O/'baseline-reference.json').read_text());base=[b['path'],b['sha256']];inputs=[]
for seq in [52,53,54]:p=O/'capture'/('sequence-'+str(seq)+'.oracap');inputs += [str(p),sha(p)]
command=[java,'-Xmx6g','-cp',cp];leaf=run('raw-index-leaf-r2' if repair else 'raw-index-leaf',command+['RawDmlLeafProbe']+base+inputs).stdout.decode().strip().split('|')[1]
run('new-complete-batch',command+['CapturedBatchProbe']+base+[leaf]+inputs)
run('new-physical',command+['CapturedRowsProbe','physical']+base+[leaf]+inputs)
run('new-typed-projection',command+['AcquiredDmlProjectionProbe']+base+[leaf]+inputs)
run('new-native-physical',[sys.executable,R/'lab/compare_typed_physical.py',b['path'],O/'new-physical.stdout.txt',O/'dump.trc'])
# Same independent native row algorithm, changed only input location and truthful final-row accounting.
reference=(R/'lab/compare_mixed_capture_rows.py').read_text();reference=reference.replace("(O/'baseline/snapshot.stdout.txt').read_text()","Path(json.loads((O/'baseline-reference.json').read_text())['path']).read_text()")
reference=reference.replace("if 'TRUTH|'+name+'|0|mixed-seed|anchor' not in t or 'TRUTH_COUNT|'+name+'|1' not in t:errors.append(name+'_final')","if [l for l in t.splitlines() if l.startswith('TRUTH|'+name+'|')] != ['TRUTH|'+name+'|0|acquired-seed|anchor']:errors.append(name+'_final')")
comparator=O/'independent-new-rows.py';comparator.write_text(reference);run('new-native-rows',[sys.executable,comparator,O,O/'new-complete-batch.stdout.txt'])
projection=json.loads((O/'new-typed-projection.stdout.txt').read_text());assert [x['operation'] for x in projection['envelopes']]==['INSERT','UPDATE','DELETE'];assert projection['evidence']['baselineScn']==b['scn'] and projection['evidence']['baselineSha256']==b['sha256']
for suffix,bad in [('wrong-before',lambda p:p.replace('VALUE:61637175697265642d6166746572','VALUE:61637175697265642d6265666f7265')),('missing-event',lambda p:'\n'.join(l for l in p.splitlines() if '|DELETE|' not in l))]:
 p=O/(suffix+'.rows.txt');original=(O/'new-complete-batch.stdout.txt').read_text();changed=bad(original);assert changed!=original;p.write_text(changed);run('new-compare-'+suffix,[sys.executable,comparator,O,p],1)
(O/'parser-proof.json').write_text(json.dumps(dict(events=3,captures=[52,53,54],baseline=b,raw_leaf=int(leaf),native_graph_and_all_intermediate_rows_zero_differences=True,source_actions_replayed=False,publication_accepted=False),indent=2));print('ACQUIRED_BASELINE_NEW_RAW_COMPLETE_DML_VERIFIED|3')
