"""Seal bounded control publication evidence, excluding credentials and Maven cache."""
import hashlib,json,re,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-control-publication-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
build=json.loads((O/'build-proof-r3.json').read_text())
assert build['tests']==dict(tests=711,errors=0,failures=0,skipped=0)
assert (O/'matrix-r2.rc.txt').read_text()=='0'
matrix=json.loads((O/'matrix-proof.json').read_text());assert len(matrix)==24
assert all(x['real_checkpoint_sql_failure_rolled_back_all'] and x['concurrent_publication_single'] and x['replay_ledger_written']==0 and x['target_dml']==0 and not x['facade_executed'] for x in matrix)
audit=json.loads((O/'independent-control-proof.json').read_text())
assert audit['real_select_exports']==24 and audit['all_control_and_checkpoint_fields_zero_differences'] and len(audit['comparator_refusals'])==3
assert (O/'source-target-before-r2.stdout.txt').read_bytes()==(O/'source-target-after-r2.stdout.txt').read_bytes()
secrets={x['password'].encode() for x in json.loads((O/'control.secret.json').read_text())}
secrets.add((R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines()[1].encode())
for f in O.glob('*.secret.sql'):secrets.update(x.encode() for x in re.findall(r'identified by "([^"]+)"',f.read_text()))
snapshot=O/'delivered-scripts';snapshot.mkdir(exist_ok=True)
for n in ['OracleControlPublicationAudit.java','OracleControlPublicationDbProbe.java','audit_oracle_control_publication.py','build_oracle_control_publication.py','prepare_oracle_control_publication.py','verify_oracle_control_publication.py','seal_oracle_control_publication.py']:shutil.copyfile(R/'lab'/n,snapshot/n)
files=[p for p in O.rglob('*') if p.is_file() and 'secret' not in p.name.lower() and 'm2' not in p.relative_to(O).parts and p.name!='seal.json']
entries=[]
for p in sorted(files):
    assert not any(s in p.read_bytes() for s in secrets),p.name
    entries.append(dict(path=p.relative_to(O).as_posix(),bytes=p.stat().st_size,sha256=sha(p)))
for x in entries:assert sha(O/x['path'])==x['sha256']
result=dict(product_commit=build['product_commit'],tests=build['tests'],matrix_cases=24,audit=audit,concurrency='two threads, same JVM, real database source lock',facade_accepted=False,ack_accepted=False,independent_review='PENDING',files=entries,excluded='secret filenames and m2; private evidence remains ignored')
(O/'seal.json').write_text(json.dumps(result,indent=2))
summary={k:v for k,v in result.items() if k!='files'};summary.update(local_seal_path='lab/out/oracle-control-publication-20261005/seal.json',local_seal_sha256=sha(O/'seal.json'),file_count=len(entries),matrix_proof_sha256=sha(O/'matrix-proof.json'))
(R/'docs/workorders/evidence/DELIVERY-oracle-control-publication-20261005.json').write_text(json.dumps(summary,indent=2))
print('CONTROL_PUBLICATION_EVIDENCE_SEALED|'+str(len(entries)))
