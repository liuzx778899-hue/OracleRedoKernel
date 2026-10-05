"""Safe receipt-verifier seal; target connection secret stays in its prior ignored directory."""
import hashlib,json,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-readonly-receipt-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=json.loads((O/'proof.json').read_text());assert proof['result_corruptions']==44 and proof['target_snapshot_unchanged']
for n in ['SNON','SNUL','SMIX','EMPT']:assert (O/(n+'-r2.rc.txt')).read_text()=='0'
assert (O/'SNON.rc.txt').read_text()=='1'
password=(R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines()[1].encode()
snapshot=O/'delivered-scripts';snapshot.mkdir(exist_ok=True)
for n in ['OracleReadOnlyReceiptProbe.java','build_oracle_readonly_receipt.py','verify_oracle_readonly_receipt.py','seal_oracle_readonly_receipt.py']:shutil.copyfile(R/'lab'/n,snapshot/n)
for n in ['OracleTargetBatchWriter.java','OracleConsumerCheckpoint.java']:shutil.copyfile(R/'integration/streamhouse-engine-oracle/src/main/java/com/streamhouse/engines/oracle'/n,snapshot/n)
entries=[]
for p in sorted(O.rglob('*')):
    if not p.is_file() or 'm2' in p.relative_to(O).parts or 'secret' in p.name.lower() or p.name=='seal.json':continue
    assert password not in p.read_bytes(),p.name
    entries.append(dict(path=p.relative_to(O).as_posix(),bytes=p.stat().st_size,sha256=sha(p)))
for x in entries:assert sha(O/x['path'])==x['sha256']
(O/'seal.json').write_text(json.dumps({'proof':proof,'build':json.loads((O/'build-proof.json').read_text()),'files':entries},indent=2))
summary={'local_seal_path':'lab/out/oracle-readonly-receipt-20261005/seal.json','local_seal_sha256':sha(O/'seal.json'),'files':len(entries),'proof':proof,'build':json.loads((O/'build-proof.json').read_text()),'independent_review':'PENDING'}
(R/'docs/workorders/evidence/DELIVERY-oracle-readonly-receipt-20261005.json').write_text(json.dumps(summary,indent=2));print('READ_ONLY_RECEIPT_SEALED|'+str(len(entries)))
