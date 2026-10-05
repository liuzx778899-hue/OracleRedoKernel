"""Seal the actual bounded Java Facade chain; retain failures and all pending production gates."""
import hashlib,json,re,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-bounded-runtime-20261005';P=R/'lab/out/streamhouse-oracle-gate-worktree'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
build=json.loads((O/'build-proof.json').read_text());assert build['tests']==dict(tests=723,errors=0,failures=0,skipped=0)
assert (O/'runtime-facade-r3.rc.txt').read_text()=='0'
proof=json.loads((O/'independent-runtime-proof.json').read_text());assert len(proof['cases'])==4 and len(proof['comparator_refusals'])==3
assert all(x['all_target_control_checkpoint_ack_fields_zero_differences'] for x in proof['cases'])
assert (O/'runtime-source-before-r3.stdout.txt').read_bytes()==(O/'runtime-source-after-r3.stdout.txt').read_bytes()
assert b'CONFIG|1276726821|1276717861|NO|NO' in (O/'runtime-source-after-r3.stdout.txt').read_bytes()
execution=json.loads((O/'runtime-execution-r3.json').read_text());assert execution['product_commit']==build['product_commit']
for x in execution['files']:assert sha(R/x['path'])==x['sha256']
secrets={x['password'].encode() for f in O.glob('*control.secret.json') for x in json.loads(f.read_text())}
secrets.add((R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines()[1].encode())
for f in O.glob('*.secret.sql'):secrets.update(x.encode() for x in re.findall(r'identified by "([^"]+)"',f.read_text()))
reports=O/'accepted-test-reports';reports.mkdir(exist_ok=True)
for x in build['reports']:
    f=P/x['path'];assert sha(f)==x['sha256'];dest=reports/f.parent.parent.parent.name/f.name;dest.parent.mkdir(exist_ok=True);shutil.copyfile(f,dest)
snapshot=O/'delivered-scripts';snapshot.mkdir(exist_ok=True)
for n in ['OracleBoundedFacadeProbe.java','build_oracle_bounded_runtime.py','verify_oracle_bounded_facade.py','compare_oracle_bounded_facade.py','seal_oracle_bounded_runtime.py']:shutil.copyfile(R/'lab'/n,snapshot/n)
entries=[]
for f in sorted(O.rglob('*')):
    if not f.is_file() or 'secret' in f.name.lower() or f.name=='seal.json':continue
    assert not any(s in f.read_bytes() for s in secrets),f.name
    entries.append(dict(path=f.relative_to(O).as_posix(),bytes=f.stat().st_size,sha256=sha(f)))
for x in entries:assert sha(O/x['path'])==x['sha256']
result=dict(product_commit=build['product_commit'],tests=build['tests'],binary_artifacts=build['binary_artifacts'],bounded_java_facade_cases=4,independent_comparator_refusals=3,source_business_dml=0,source_config_changed=False,recovery='recreated Java instances; not process restart or SIGKILL',control_dialect='Oracle new owned schema',target_instance='same owned Oracle instance, separate schema',server_http_accepted=False,site_accepted=False,independent_review='PENDING',files=entries)
(O/'seal.json').write_text(json.dumps(result,indent=2));summary={k:v for k,v in result.items() if k!='files'};summary.update(file_count=len(entries),local_seal_path='lab/out/oracle-bounded-runtime-20261005/seal.json',local_seal_sha256=sha(O/'seal.json'))
(R/'docs/workorders/evidence/DELIVERY-oracle-bounded-runtime-20261005.json').write_text(json.dumps(summary,indent=2));print('ORACLE_BOUNDED_RUNTIME_EVIDENCE_SEALED|'+str(len(entries)))
