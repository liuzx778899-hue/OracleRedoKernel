"""Preserve preliminary seal failure; exclude the sealer's own still-open output handles."""
import hashlib,json,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-row-schema-candidate-20261006'
dest=R/'docs/workorders/evidence/DELIVERY-historical-row-schema-20261006.json'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
old=json.loads(dest.read_text());bad=[]
for f in old['files']:
    p=R/f['path']
    if sha(p)!=f['sha256'] or p.stat().st_size!=f['bytes']:bad.append(dict(path=f['path'],sealed_sha256=f['sha256'],actual_sha256=sha(p)))
assert len(bad)==1 and bad[0]['path'].endswith('/seal-driver.stdout.txt'),bad
assert not (N/'failed-preliminary-seal.json').exists()
shutil.copyfile(dest,N/'failed-preliminary-seal.json')
(N/'seal-postcheck-failure.json').write_text(json.dumps(dict(failures=bad,reason='sealer stdout was still open during enumeration',action='exclude sealer execution logs; preserve preliminary manifest'),indent=2))
for name in ['seal_historical_row_schema19.py','repair_historical_row_seal19.py']:shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower() or p.name.startswith('seal-driver.'):continue
    a=sha(p);assert a==sha(p);files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
old['files']=files;old['files_count']=len(files);old['sealer_open_log_excluded']=True;old['preliminary_seal_failure_preserved']=True
dest.write_text(json.dumps(old,indent=2))
for f in old['files']:assert sha(R/f['path'])==f['sha256']
print(json.dumps(dict(files=len(files),postcheck_all_hashes_match=True,prior_failure_preserved=True)))
