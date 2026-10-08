"""Seal public evidence separately; private credentials and build dependencies stay out."""
from pathlib import Path
import hashlib, json, shutil

R = Path('D:/work/vibecode/OracleRedoKernel')
N = R / 'lab/out/d2-sys-runtime-20261008'
O = R / 'lab/out/d2-sys-baseline-sealed-20261008'
assert not O.exists()
assert (N / 'stop.rc.txt').read_text() == '0'
assert json.loads((N / 'baseline-truth-proof.json').read_text())['differences'] == 0
known = [json.loads((N / 'control.secret.json').read_text())['password']]
known.append(json.loads((R / 'lab/out/product-active-20261007/admin.secret.json').read_text())['password'])
known.extend(line.split('=', 1)[1] for line in (N / 'runtime.secret.env').read_text().splitlines() if 'PASSWORD=' in line)
known.extend((N / 'source.secret').read_text().splitlines()[1:2])
O.mkdir()
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name or p.suffix not in {'.txt', '.json', '.py', '.sql', '.snapshot'}: continue
    data = p.read_bytes()
    assert not any(s.encode() in data for s in known if s), 'secret found: ' + str(p.relative_to(N))
    dest = O / p.relative_to(N)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
shutil.copyfile(__file__, O / 'executed-seal-driver.py')
items = []
for p in sorted(O.rglob('*')):
    if p.is_file():
        b = p.read_bytes()
        items.append(dict(path=p.relative_to(O).as_posix(), bytes=len(b), sha256=hashlib.sha256(b).hexdigest(), sha512=hashlib.sha512(b).hexdigest()))
manifest = json.dumps(items, sort_keys=True, indent=2).encode()
(O / 'manifest.json').write_bytes(manifest)
for item in items:
    b = (O / item['path']).read_bytes()
    assert len(b) == item['bytes'] and hashlib.sha256(b).hexdigest() == item['sha256'] and hashlib.sha512(b).hexdigest() == item['sha512']
proof = dict(files=len(items), manifestSha256=hashlib.sha256(manifest).hexdigest(), manifestSha512=hashlib.sha512(manifest).hexdigest(), knownSecretMatches=0, sourceBusinessActions=0, targetActions=0, scope='trusted baseline only; actual pending SYS not tested')
(O / 'SEALED.json').write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
