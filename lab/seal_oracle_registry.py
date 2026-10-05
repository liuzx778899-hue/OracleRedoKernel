"""Seal bounded registry receipts; exclude private credentials and raw SQL failure containing them."""
import hashlib,json,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-registry-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert (O/'matrix-r2.rc.txt').read_text()=='0'
proof=json.loads((O/'matrix-proof.json').read_text());assert len(proof)==24
assert {(x['dialect'],x['stage'],x['case']) for x in proof}=={(d,s,c) for d in ['mysql','sqlserver','oracle'] for s in ['NEW','UPG'] for c in ['SNON','SNUL','SMIX','EMPT']}
assert all(x['guard_and_typed_binding_matched'] and x['real_refusal_persisted'] and x['ledger_written']==0 and not x['public_oracle_enabled'] for x in proof)
build=json.loads((O/'build-proof-r2.json').read_text());assert build['tests']==dict(tests=707,errors=0,failures=0,skipped=0)
secrets={x['password'].encode() for x in json.loads((O/'control.secret.json').read_text())}
# Failed original Oracle password is private too, even though it was never installed.
import re
for f in O.glob('*.secret.sql'):
    secrets.update(x.encode() for x in re.findall(r'identified by "([^"]+)"',f.read_text()))
snapshot=O/'delivered-scripts';snapshot.mkdir(exist_ok=True)
for n in ['OracleRegistryDbProbe.java','build_oracle_registry.py','prepare_oracle_registry_databases.py','verify_oracle_registry_databases.py','seal_oracle_registry.py']:shutil.copyfile(R/'lab'/n,snapshot/n)
files=[p for p in O.rglob('*') if p.is_file() and 'secret' not in p.name.lower() and 'm2' not in p.relative_to(O).parts and p.name!='seal.json']
entries=[]
for p in sorted(files):
    assert not any(s in p.read_bytes() for s in secrets),p.name
    entries.append(dict(path=p.relative_to(O).as_posix(),bytes=p.stat().st_size,sha256=sha(p)))
for x in entries:assert sha(O/x['path'])==x['sha256']
result=dict(product_commit=build['product_commit'],tests=build['tests'],matrix_cases=24,public_oracle_enabled=False,independent_review='PENDING',files=entries,excluded='secret filenames and m2; no credentials or raw input uploaded')
(O/'seal.json').write_text(json.dumps(result,indent=2))
summary={k:v for k,v in result.items() if k!='files'};summary.update(local_seal_path='lab/out/oracle-registry-20261005/seal.json',local_seal_sha256=sha(O/'seal.json'),file_count=len(entries),matrix_proof_sha256=sha(O/'matrix-proof.json'))
(R/'docs/workorders/evidence/DELIVERY-oracle-registry-20261005.json').write_text(json.dumps(summary,indent=2))
print('REGISTRY_EVIDENCE_SEALED|'+str(len(entries)))
