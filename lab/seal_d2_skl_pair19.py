from pathlib import Path
import hashlib,json,shutil,xml.etree.ElementTree as ET
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d2-sys-skl-20261008';O=R/'lab/out/d2-sys-skl-pair-sealed-20261008'
assert not O.exists() and (N/'committed-pairs.rc.txt').read_text().strip()=='0'
assert 'PAIR_COUNT|2|events=0|closure_authorized=false|scope_exclusion_authorized=false' in (N/'committed-pairs.stdout.txt').read_text()
reports=N/'final-test-reports';reports.mkdir();totals=[0,0,0,0]
for p in sorted((R/'target/surefire-reports').glob('TEST-*.xml')):
 root=ET.parse(p).getroot()
 for i,key in enumerate(['tests','failures','errors','skipped']):totals[i]+=int(root.get(key,'0'))
 shutil.copyfile(p,reports/p.name)
assert totals==[342,0,0,0]
def receipt(p):
 b=p.read_bytes();return dict(path=str(p),bytes=len(b),sha256=hashlib.sha256(b).hexdigest(),sha512=hashlib.sha512(b).hexdigest())
refs=[receipt(R/'lab/out/d2-sys-cross-member-20261008'/name) for name in ['80.raw','81.raw','dump.trc']]
refs.extend(receipt(N/name) for name in ['kernel.jar','kernel-source.zip'])
(N/'artifact-references.json').write_text(json.dumps(refs,indent=2))
private=R/'lab/out/d2-sys-runtime-20261008'
known=[json.loads((private/'control.secret.json').read_text())['password'],json.loads((R/'lab/out/product-active-20261007/admin.secret.json').read_text())['password']]
known.extend(line.split('=',1)[1] for line in (private/'runtime.secret.env').read_text().splitlines() if 'PASSWORD=' in line)
known.extend((private/'source.secret').read_text().splitlines()[1:2])
O.mkdir()
for p in sorted(N.rglob('*')):
 if not p.is_file() or 'committed-build-tree' in p.parts or 'classes' in p.parts or 'secret' in p.name or p.suffix not in {'.txt','.json','.py','.xml'}:continue
 b=p.read_bytes();assert not any(s.encode() in b for s in known if s)
 dest=O/p.relative_to(N);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
shutil.copyfile(__file__,O/'executed-seal-driver.py');items=[]
for p in sorted(O.rglob('*')):
 if p.is_file():
  item=receipt(p);item['path']=p.relative_to(O).as_posix();items.append(item)
manifest=json.dumps(items,sort_keys=True,indent=2).encode();(O/'manifest.json').write_bytes(manifest)
for item in items:
 r=receipt(O/item['path']);assert all(r[k]==item[k] for k in ['bytes','sha256','sha512'])
proof=dict(files=len(items),manifestSha256=hashlib.sha256(manifest).hexdigest(),manifestSha512=hashlib.sha512(manifest).hexdigest(),tests=totals,actualPairs=2,sourceActions=0,targetActions=0,events=0,publicKnownSecretMatches=0,scope='same-record SKL pair only; no maintenance closure or exclusion authority')
(O/'SEALED.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
