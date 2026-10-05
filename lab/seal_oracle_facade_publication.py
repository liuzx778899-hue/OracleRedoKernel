"""Seal Facade wiring tests as unit evidence, without claiming runtime acceptance."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-facade-publication-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
build=json.loads((O/'build-proof.json').read_text());assert build['tests']==dict(tests=715,errors=0,failures=0,skipped=0)
assert (O/'reactor.rc.txt').read_text()=='0'
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()==build['product_commit']
reports=O/'test-reports';reports.mkdir(exist_ok=True)
for entry in build['reports']:
    f=P/entry['path'];assert sha(f)==entry['sha256'];dest=reports/f.parent.parent.parent.name/f.name;dest.parent.mkdir(exist_ok=True);shutil.copyfile(f,dest)
snapshot=O/'delivered-scripts';snapshot.mkdir(exist_ok=True)
for n in ['build_oracle_facade_publication.py','seal_oracle_facade_publication.py']:shutil.copyfile(R/'lab'/n,snapshot/n)
artifacts=[]
for name in ['streamhouse-app','streamhouse-pipeline','streamhouse-spi']:
    f=P/'server'/name/'target'/(name+'-0.1.0-SNAPSHOT.jar');artifacts.append(dict(module=name,sha256=sha(f)))
entries=[dict(path=p.relative_to(O).as_posix(),bytes=p.stat().st_size,sha256=sha(p)) for p in sorted(O.rglob('*')) if p.is_file() and p.name!='seal.json']
for x in entries:assert sha(O/x['path'])==x['sha256']
seal=dict(product_commit=build['product_commit'],tests=build['tests'],artifacts=artifacts,files=entries,real_facade_target_control_ack_accepted=False,independent_review='PENDING')
(O/'seal.json').write_text(json.dumps(seal,indent=2))
summary={k:v for k,v in seal.items() if k!='files'};summary.update(file_count=len(entries),local_seal_path='lab/out/oracle-facade-publication-20261005/seal.json',local_seal_sha256=sha(O/'seal.json'))
(R/'docs/workorders/evidence/DELIVERY-oracle-facade-publication-20261005.json').write_text(json.dumps(summary,indent=2));print('ORACLE_FACADE_WIRING_EVIDENCE_SEALED|'+str(len(entries)))
