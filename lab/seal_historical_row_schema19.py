"""Seal frozen historical business-row delivery without replaying source experiments."""
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-row-schema-candidate-20261006'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
dest=R/'docs/workorders/evidence/DELIVERY-historical-row-schema-20261006.json';assert not dest.exists()
for name in ['CapturedHistoricalBatchProbe.java','verify_historical_row_schema19.py','verify_historical_business_negatives19.py','seal_historical_row_schema19.py']:
    shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
for name in ['ArchivedDictionaryHistory.java','ArchivedHeapRows.java','ArchivedPhysicalTransactions.java','CapturedHeapBatch.java','CapturedHistoricalHeapBatch.java','TransactionEnd.java']:
    shutil.copyfile(R/'src/main/java/com/streamhouse/oracle'/name,N/('sealed-'+name))
for name in ['HistoricalRowSchemaTest.java','TransactionMarkersTest.java']:
    shutil.copyfile(R/'src/test/java/com/streamhouse/oracle'/name,N/('sealed-'+name))
reports=N/'kernel-reports';reports.mkdir();counts=[0,0,0,0]
for p in (R/'target/surefire-reports').glob('TEST-*.xml'):
    shutil.copyfile(p,reports/p.name);t=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):counts[i]+=int(t.attrib[k])
assert counts==[299,0,0,0],counts
assert (N/'build-r2.rc.txt').read_text(encoding='utf-8-sig').strip()=='0'
old=json.loads((R/'docs/workorders/evidence/DELIVERY-v3-history-raw-20261006.json').read_text())
for f in old['files']:
    p=R/f['path'];assert sha(p)==f['sha256'] and p.stat().st_size==f['bytes']
proof=json.loads((N/'historical-row-proof.json').read_text());negative=json.loads((N/'business-negative-proof.json').read_text())
assert proof['kernel_sha256']==negative['kernel_sha256']==sha(N/'kernel.jar')
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower() or p.name.startswith('seal-driver.'):continue
    a=sha(p);assert a==sha(p);files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
out=dict(kernel_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),kernel_sha256=sha(N/'kernel.jar'),kernel_tests=counts,historical_business_rows=proof,business_negatives=negative,impact=json.loads((N/'impact-proof.json').read_text()),previous_374_unchanged=True,double_sha256_verified=True,source_actions=0,target_actions=0,product_rebuilt=False,site_accepted=False,release_accepted=False,files_count=len(files),files=files)
dest.write_text(json.dumps(out,indent=2));print(json.dumps(dict(kernel_code_commit=out['kernel_code_commit'],kernel_sha256=out['kernel_sha256'],kernel_tests=counts,files=len(files),previous_374_unchanged=True)))
