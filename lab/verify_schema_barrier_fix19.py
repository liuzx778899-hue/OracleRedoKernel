"""Real preserved counterexample refusal plus affected static-schema regressions; no source replay."""
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/schema-barrier-20261005';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,expected=0):
 assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 p=subprocess.run(list(map(str,args)),capture_output=True,timeout=240)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==expected,(name,p.stderr[-1500:]);return p
jar=O/'fixed-kernel.jar';shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',jar)
assert (O/'kernel-build-r2.rc.txt').read_text().strip()=='0'
tests={k:sum(int(ET.parse(p).getroot().get(k,0)) for p in (R/'target/surefire-reports').glob('TEST-*.xml')) for k in ['tests','failures','errors','skipped']}
assert tests==dict(tests=259,failures=0,errors=0,skipped=0)
shutil.copytree(R/'target/surefire-reports',O/'kernel-test-reports')
args=json.loads((O/'old-schema-batch.command.json').read_text());i=args.index('-cp')+1;args[i]=str(jar)+';'+args[i]
(O/'fix-execution-pin.json').write_text(json.dumps(dict(kernel_sha256=sha(jar),files={str(p.relative_to(R)):sha(p) for p in [R/'src/main/java/com/streamhouse/oracle/HistoricalSchemaBarrier.java',R/'src/main/java/com/streamhouse/oracle/ArchivedPhysicalTransactions.java',Path(__file__)]}),indent=2))
bad=run('fixed-schema-batch',args,1);assert not bad.stdout and b'HISTORICAL_SCHEMA_BARRIER:scn=2501918:object=74695' in bad.stderr
passed=[]
for name,command in [('mixed','mixed-dml-capture-20261005/raw-complete-batch.command.json'),('empty','empty-dml-capture-20261005/complete-batch-r2.command.json')]:
 old=R/'lab/out'/command;args=json.loads(old.read_text());i=args.index('-cp')+1;parts=args[i].split(';');args[i]=str(jar)+';'+';'.join(parts[1:])
 assert run('affected-static-regression-'+name,args).stdout==old.with_name(old.name.replace('command.json','stdout.txt')).read_bytes();passed.append(name)
# Independent metadata and SQL truth: compare schema rows, not the decoder's schema digest.
def lines(path,prefix):return [line.split('|')[1:] for line in path.read_text().splitlines() if line.startswith(prefix+'|')]
initial=O/'fixture-once.stdout.txt';added=O/'ADD-once.stdout.txt';final=O/'DROP-once.stdout.txt'
assert lines(initial,'INITIAL_OBJECT')==lines(added,'ADD_OBJECT')==lines(final,'DROP_OBJECT')
assert lines(initial,'INITIAL_COL')==lines(final,'DROP_COL') and len(lines(added,'ADD_COL'))==4
rows=[line.split('|') for line in (O/'old-schema-batch.stdout.txt').read_text().splitlines() if line.startswith('ROW|')]
assert len(rows)==2 and all(len(row[-1].split(','))==3 for row in rows)
assert lines(added,'TRUTH')==[['ADD','0','schema-added','anchor','<NULL>']]
proof=dict(kernel_sha256=sha(jar),tests=tests,real_metadata_ABA=True,old_rc=0,old_events=2,first_event_expected_columns=4,first_event_actual_columns=3,fixed_rc=1,fixed_stdout_bytes=0,affected_static_batches_byte_equal=passed,source_transactions_replayed=False,target_actions=0,historical_dictionary_accepted=False,independent_review='PENDING')
(O/'fix-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
