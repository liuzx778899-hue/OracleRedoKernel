"""Independent target/control SQL and exact refusal-byte comparison, with falsification cases."""
import hashlib,json,subprocess,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/schema-barrier-20261005'
def compare(before,after,reasons):
 errors=[];initial=[l for l in before.splitlines() if l.startswith('TARGET_')];target=[l for l in after.splitlines() if l.startswith('TARGET_')]
 if initial!=target:errors.append('target_changed')
 expected=['TARGET_ROW|0|schema-seed|anchor','TARGET_APPLIED|0','TARGET_MEMBERS|0','TARGET_COMMITS|0']
 if target!=expected:errors.append('target_not_zero')
 for required in ['TASK|FAILED','CP|<NULL>','WINDOW|PENDING','LEDGER|0','LOGGING|NO|NO','OLD|11|blocking-closed|new-note']:
  if after.splitlines().count(required)!=1:errors.append(required)
 batches=[l for l in after.splitlines() if l.startswith('BATCH|')]
 if batches!=['BATCH|REFUSED|0']*2:errors.append('nonzero_or_published_batch')
 want='ORACLE_APPROVED_INPUT_REFUSED: HISTORICAL_SCHEMA_BARRIER:scn=2501918:object=74695'
 got=[]
 for line in reasons.splitlines():
  if line.startswith('REASON_HEX|'):
   p=line.split('|');got.append((p[1],bytes.fromhex(p[2]).decode()))
 if got!=[('2',want)]*2:errors.append('wrong_refusal_reason')
 return errors
before=(O/'http-before.stdout.txt').read_text();after=(O/'http-repeat-after.stdout.txt').read_text();reasons=(O/'http-refusal-reasons-exact.stdout.txt').read_text();assert not compare(before,after,reasons)
for name,changed,reason in [('target-write',after.replace('TARGET_APPLIED|0','TARGET_APPLIED|1'),reasons),('checkpoint',after.replace('CP|<NULL>','CP|advanced'),reasons),('wrong-reason',after,reasons.replace('484953544F524943414C','484953544F524943414D'))]:
 assert compare(before,changed,reason),name;(O/('comparator-negative-'+name+'.json')).write_text(json.dumps(dict(after=changed,reasons=reason,errors=compare(before,changed,reason)),indent=2))
assert not (O/'acks').exists() or not any((O/'acks').rglob('*'))
assert (O/'http-final-state.stdout.txt').read_text().strip()=='exited|143'
product=json.loads((O/'product-build-proof.json').read_text());fix=json.loads((O/'fix-proof.json').read_text())
with zipfile.ZipFile(O/'application.jar') as z:
 names=[n for n in z.namelist() if n.startswith('BOOT-INF/lib/oracle-redo-kernel-')];assert len(names)==1 and hashlib.sha256(z.read(names[0])).hexdigest()==fix['kernel_sha256']
# Separate a recorded execution from an earlier exploratory console observation.
args=json.loads((O/'old-schema-batch.command.json').read_text());i=args.index('CapturedBatchProbe');inputs=args[i+4:]
command=args[:i]+['RawSchemaMarkerProbe']+inputs;command[command.index('-cp')+1]=args[args.index('-cp')+1]
assert not (O/'raw-markers-recorded.rc.txt').exists();(O/'raw-markers-recorded.command.json').write_text(json.dumps(command))
(O/'raw-marker-probe-execution-pin.json').write_text(json.dumps(dict(source_sha256=hashlib.sha256((R/'lab/RawSchemaMarkerProbe.java').read_bytes()).hexdigest(),class_sha256=hashlib.sha256((O/'parser-classes/RawSchemaMarkerProbe.class').read_bytes()).hexdigest())))
p=subprocess.run(command,capture_output=True,timeout=60);(O/'raw-markers-recorded.stdout.txt').write_bytes(p.stdout);(O/'raw-markers-recorded.stderr.txt').write_bytes(p.stderr);(O/'raw-markers-recorded.rc.txt').write_text(str(p.returncode));assert p.returncode==0
markers=[l for l in p.stdout.decode().splitlines() if l.startswith('MARKER|')];assert len(markers)==5 and markers[-2:]==['MARKER|56|118|96|2501918|677737149|3','MARKER|57|9|208|2501944|677737149|3']
proof=dict(product=product,kernel=fix,task_id=(O/'http-task-id.txt').read_text(),actual_authenticated_HTTP=True,actual_refused_batches=2,decoder_rc=2,target_applied=0,target_members=0,target_commits=0,ledger=0,checkpoint=None,ACK_files=0,window='PENDING',task='FAILED',target_SQL_unchanged=True,reason_bytes_exact=True,comparator_negatives_detected=3,helper='exited|143',independent_review='PENDING',historical_dictionary_or_site_or_release_accepted=False)
(O/'delivery-proof.json').write_text(json.dumps(proof,indent=2));print('ACTUAL_HTTP_SCHEMA_BARRIER_ZERO_ADVANCE_VERIFIED|2|3_NEGATIVES')
