"""Seal actual post-commit refusal and independent comparison; no credentials/raw inputs uploaded."""
import hashlib,json,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-postcommit-proof-20261005';B=R/'lab/out/oracle-readonly-receipt-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=json.loads((O/'proof.json').read_text());comparison=json.loads((O/'independent-proof.json').read_text())
assert len(proof['cases'])==len(comparison['targets'])==4 and len(comparison['comparator_refusals'])==3
assert [x['committed_events'] for x in proof['cases']]==[2,2,3,0]
for x in proof['cases']:
    p=x['target_prefix'];assert (O/(p+'-proof-unavailable.rc.txt')).read_text()=='1'
    for n in ['resume','repeat']:assert (O/(p+'-'+n+'.rc.txt')).read_text()=='0'
    assert (O/(p+'-committed-unacked.stdout.txt')).read_bytes()==(O/(p+'-final.stdout.txt')).read_bytes()
assert (O/'compare.rc.txt').read_text()=='1' and (O/'compare-r2.rc.txt').read_text()=='0'
password=(R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines()[1].encode()
snapshot=O/'delivered-scripts';snapshot.mkdir(exist_ok=True)
for n in ['OraclePostCommitProofWorker.java','verify_oracle_postcommit_proof.py','compare_oracle_postcommit_proof.py','seal_oracle_postcommit_proof.py']:shutil.copyfile(R/'lab'/n,snapshot/n)
entries=[]
for p in sorted(O.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower() or p.name=='seal.json':continue
    assert password not in p.read_bytes(),p.name
    entries.append(dict(path=p.relative_to(O).as_posix(),bytes=p.stat().st_size,sha256=sha(p)))
for x in entries:assert sha(O/x['path'])==x['sha256']
(O/'seal.json').write_text(json.dumps({'build':json.loads((B/'build-proof.json').read_text()),'proof':proof,'independent_comparison':comparison,'files':entries},indent=2))
(R/'docs/workorders/evidence/DELIVERY-oracle-postcommit-proof-20261005.json').write_text(json.dumps({'local_seal_path':'lab/out/oracle-postcommit-proof-20261005/seal.json','local_seal_sha256':sha(O/'seal.json'),'files':len(entries),'build':json.loads((B/'build-proof.json').read_text()),'proof':proof,'independent_comparison':comparison,'independent_review':'PENDING'},indent=2));print('POST_COMMIT_PROOF_SEALED|'+str(len(entries)))
