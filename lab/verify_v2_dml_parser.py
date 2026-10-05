"""Frozen binary parsing and independent native graph/row comparison of the new acquired-baseline experiment."""
import hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-dml-capture-20261006';A=R/'lab/out/oracle-snapshot-acquisition-20261005';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();java='D:/work/Java/jdk-17.0.7/bin/java.exe';javac='D:/work/Java/jdk-17.0.7/bin/javac.exe'
def run(name,args,rc=0):
 assert not (O/(name+'.rc.txt')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))));p=subprocess.run(list(map(str,args)),capture_output=True,timeout=600)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,(name,p.stderr[-1500:]);return p
repair=sys.argv[1:]==['--resume-leaf'];lib=O/'parser-libs';classes=O/'parser-classes'
if repair:assert (O/'raw-index-leaf.rc.txt').read_text()=='1'
else:
 lib.mkdir(exist_ok=False);classes.mkdir()
 for n in ['kernel','adapter','spi']:
  origin=R/'lab/out/dictionary-end-profiles-20261006/kernel.jar' if n=='kernel' else R/'lab/out/dictionary-acquisition-20261005/adapter.jar' if n=='adapter' else A/'spi.jar'
  shutil.copyfile(origin,lib/(n+'.jar'))
assert sha(lib/'kernel.jar')=='444630a440379742fba3b0586b08413d68251bddb727d1751c40eaaab79899c5';assert sha(lib/'adapter.jar')=='9516d4f9029b88bcd7fd416137188c96d613b51b21286c969474cd40135ddc2f'
with zipfile.ZipFile(O/'application.jar') as z:
 for name in z.namelist():
  if name.startswith('BOOT-INF/lib/jackson-') and any(x in name for x in ['jackson-databind-','jackson-core-','jackson-annotations-']):(lib/Path(name).name).write_bytes(z.read(name))
cp=str(lib/'*')+';'+str(classes);probes=['RawDmlLeafProbe','PhysicalTransactionProbe','VerifiedHeapRowsProbe','CapturedRowsProbe','CapturedBatchProbe','AcquiredDmlProjectionProbe']
(O/('parser-repair-pin.json' if repair else 'parser-execution-pin.json')).write_text(json.dumps(dict(binaries={p.name:sha(p) for p in lib.glob('*.jar')},sources={n:sha(R/'lab'/(n+'.java')) for n in probes}),indent=2))
run('compile-new-parser-probes-r2' if repair else 'compile-new-parser-probes',[javac,'-encoding','UTF-8','-cp',cp,'-d',classes]+[R/'lab'/(n+'.java') for n in probes])
b=json.loads((O/'baseline-reference.json').read_text());base=[b['path'],b['sha256']];inputs=[]
sequences=json.loads((O/'capture-reference.json').read_text())['sequences']
for seq in sequences:p=O/'capture'/('sequence-'+str(seq)+'.oracap');inputs += [str(p),sha(p)]
command=[java,'-Xmx6g','-cp',cp];leaf=run('raw-index-leaf-r2' if repair else 'raw-index-leaf',command+['RawDmlLeafProbe']+base+inputs).stdout.decode().strip().split('|')[1]
run('new-complete-batch',command+['CapturedBatchProbe']+base+[leaf]+inputs)
run('new-physical',command+['CapturedRowsProbe','physical']+base+[leaf]+inputs)
run('new-typed-projection',command+['AcquiredDmlProjectionProbe']+base+[leaf]+inputs)
run('new-native-physical',[sys.executable,R/'lab/compare_typed_physical.py',b['path'],O/'new-physical.stdout.txt',O/'dump.trc'])
# Same independent native row algorithm, changed only input location and truthful final-row accounting.
reference=(R/'lab/compare_mixed_capture_rows.py').read_text();reference=reference.replace("(O/'baseline/snapshot.stdout.txt').read_text()","Path(json.loads((O/'baseline-reference.json').read_text())['path']).read_text()")
reference=reference.replace("if 'TRUTH|'+name+'|0|mixed-seed|anchor' not in t or 'TRUTH_COUNT|'+name+'|1' not in t:errors.append(name+'_final')","if [l for l in t.splitlines() if l.startswith('TRUTH|'+name+'|')] != ['TRUTH|'+name+'|0|v2-seed|anchor']:errors.append(name+'_final')")
reference=reference.replace("bytes.fromhex(image[1].split(':')[1]).decode(),bytes.fromhex(image[2].split(':')[1]).decode()","('<NULL>' if image[1]=='NULL:' else bytes.fromhex(image[1].split(':')[1]).decode()),('<NULL>' if image[2]=='NULL:' else bytes.fromhex(image[2].split(':')[1]).decode())")
reference=reference.replace("v=values(c);assert set(v)=={0,1,2}","v=fullvalues(c);assert set(v)=={0,1,2}").replace("values(inverse)==dict(enumerate(current))","fullvalues(inverse)==dict(enumerate(current))")
helper=r'''
def fullvalues(text):
    v=values(text);n=int(re.search(r'ncol:\s*(\d+)',text)[1]);assert 1<=n<=3 and set(v)==set(range(n))
    for i in range(n,3):
        assert re.search(r'^COLUMN\|BEFORE\|'+str(i+1)+r'\|'+str(i+1)+r'\|[^|]+\|VARCHAR2\|Y\|',baseline,re.M)
        v[i]='NULL:'
    return v
'''
reference=reference.replace('for record in re.split',helper+'\nfor record in re.split',1)
comparator=O/'independent-new-rows.py';comparator.write_text(reference);run('new-native-rows',[sys.executable,comparator,O,O/'new-complete-batch.stdout.txt'])
projection=json.loads((O/'new-typed-projection.stdout.txt').read_text());assert [x['operation'] for x in projection['envelopes']]==['INSERT','UPDATE','DELETE'];assert projection['evidence']['baselineScn']==b['scn'] and projection['evidence']['baselineSha256']==b['sha256']
for suffix,bad in [('wrong-before',lambda p:p.replace('VALUE:76322d6166746572','VALUE:6261642d76616c7565')),('missing-event',lambda p:'\n'.join(l for l in p.splitlines() if '|DELETE|' not in l))]:
 p=O/(suffix+'.rows.txt');original=(O/'new-complete-batch.stdout.txt').read_text();changed=bad(original);assert changed!=original;p.write_text(changed);run('new-compare-'+suffix,[sys.executable,comparator,O,p],1)
(O/'parser-proof.json').write_text(json.dumps(dict(events=3,captures=sequences,baseline=b,raw_leaf=int(leaf),native_graph_and_all_intermediate_rows_zero_differences=True,source_actions_replayed=False,publication_accepted=False),indent=2));print('ACQUIRED_BASELINE_NEW_RAW_COMPLETE_DML_VERIFIED|3')

