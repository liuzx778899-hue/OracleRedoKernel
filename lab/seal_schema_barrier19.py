"""Preserve exact executed-source reconstruction, reports, and non-secret evidence summary."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/schema-barrier-20261005';P=R/'lab/out/streamhouse-oracle-gate-worktree';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=json.loads((O/'delivery-proof.json').read_text());pin=json.loads((O/'fix-execution-pin.json').read_text())
for path,digest in pin['files'].items():assert sha(R/path)==digest
product=proof['product']
for item in product['reports']:
 path=P/item['path'];assert sha(path)==item['sha256'];dest=O/'product-test-reports'/item['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
# Only two later read-only query branches were added. Recover the original
# startup driver by its already recorded execution SHA, never invent a source pin.
text=(R/'lab/schema_barrier_http19.py').read_bytes();lines=text.splitlines(keepends=True)
initial=b''.join(line for line in lines if not line.startswith(b"elif action=='reason'" ) and not line.startswith(b"elif action=='reason-exact'"))
assert hashlib.sha256(initial).hexdigest()==json.loads((O/'http-execution-pin.json').read_text())['script_sha256'];(O/'executed-http-start-driver.py').write_bytes(initial)
sources=[R/path for path in pin['files']]+[R/'src/test/java/com/streamhouse/oracle/HistoricalSchemaBarrierTest.java',R/'lab/schema_barrier_http19.py',R/'lab/compare_schema_barrier_http19.py',Path(__file__)]
(O/'sealed-current-sources.json').write_text(json.dumps(dict(timing='source snapshot at sealing; original execution pins remain separate',files={str(p.relative_to(R)):sha(p) for p in sources}),indent=2))
secret_values=[(O/'source.secret').read_text().splitlines()[1].encode()]
secret_values += [line.split('=',1)[1].encode() for line in (O/'http-runtime.secret.env').read_text().splitlines() if any(k in line.split('=',1)[0] for k in ['PASSWORD'])]
for path in O.rglob('*'):
 if not path.is_file() or 'secret' in path.name.lower() or path.suffix in ['.jar','.class']:continue
 data=path.read_bytes();assert not any(value and value in data for value in secret_values),str(path)
for path in sources:assert not any(value and value in path.read_bytes() for value in secret_values),str(path)
subprocess.run(['git','diff','--check'],cwd=R,check=True)
subprocess.run(['git','diff','--check'],cwd=P,check=True)
subprocess.run(['C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',R/'lab/seal_oracle_http_evidence.py','schema-barrier-20261005'],check=True)
seal=json.loads((O/'seal.json').read_text());selected={item['path']:item for item in seal['files']}
manifest=dict(root_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),product_code_commit=product['commit'],product_delivery_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip(),pull_request='https://github.com/liuzx778899-hue/StreamHouse/pull/12',kernel_sha256=proof['kernel']['kernel_sha256'],app_sha256=product['app_sha256'],kernel_tests=proof['kernel']['tests'],product_tests=product['tests'],baseline_scn=2501884,baseline_sha256='6498d7223c2e5383facc9e3f848f22dd8cb3272460cf7c50b20a29d34d5379d5',real_schema_ABA=True,old_kernel_events=2,old_first_event_columns=3,SQL_truth_columns=4,fixed_kernel_rc=1,fixed_kernel_stdout_bytes=0,actual_HTTP_refusals=2,target_ledger_CP_ACK_zero_advance=True,source_actions_replayed=False,independent_review='PENDING',site_or_release_accepted=False,nonsecret_files=len(seal['files']),double_SHA_equal=True,seal_sha256=sha(O/'seal.json'),files=[selected[name] for name in ['delivery-proof.json','fix-proof.json','initial.snapshot.txt','capture/sequence-56.oracap','capture/sequence-57.oracap','old-schema-batch.stdout.txt','fixed-schema-batch.stderr.txt','http-refusal-reasons-exact.stdout.txt','http-repeat-after.stdout.txt','kernel-build.stdout.txt','kernel-build-r2.stdout.txt','executed-http-start-driver.py']])
(R/'docs/workorders/evidence/DELIVERY-historical-schema-barrier-20261005.json').write_text(json.dumps(manifest,indent=2));print('SCHEMA_BARRIER_EVIDENCE_SEALED|'+str(len(seal['files'])))
