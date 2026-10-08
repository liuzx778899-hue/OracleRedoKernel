"""Freeze public research evidence, retain raw/artifact custody by dual-hash references."""
from pathlib import Path
import hashlib,json,shutil,xml.etree.ElementTree as ET
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d2-sys-cross-member-20261008';O=R/'lab/out/d2-sys-cross-sealed-20261008'
assert not O.exists()
assert (N/'held-ddl.rc.txt').read_text().strip()=='0' and (N/'drop-delay.rc.txt').read_text().strip()=='0'
for name in ['committed-prefix','committed-closed']:
 assert (N/(name+'.rc.txt')).read_text().strip()=='1'
 assert not (N/(name+'.stdout.txt')).read_bytes()
 assert b'UNSUPPORTED_DICTIONARY_DISCOVERY_ROW' in (N/(name+'.stderr.txt')).read_bytes()
assert not (N/'committed-prefix.state').exists()
reports=N/'final-test-reports';reports.mkdir()
totals=[0,0,0,0]
for p in sorted((R/'target/surefire-reports').glob('TEST-*.xml')):
 root=ET.parse(p).getroot()
 for i,key in enumerate(['tests','failures','errors','skipped']):totals[i]+=int(root.get(key,'0'))
 shutil.copyfile(p,reports/p.name)
assert totals==[339,0,0,0],totals
def receipt(p):
 b=p.read_bytes();return dict(path=str(p),bytes=len(b),sha256=hashlib.sha256(b).hexdigest(),sha512=hashlib.sha512(b).hexdigest())
artifacts=[receipt(N/name) for name in ['80.raw','81.raw','kernel.jar','kernel-source.zip']]
(N/'artifact-references.json').write_text(json.dumps(artifacts,indent=2))
baseline=R/'lab/out/d2-sys-runtime-20261008'
known=[json.loads((baseline/'control.secret.json').read_text())['password'],json.loads((R/'lab/out/product-active-20261007/admin.secret.json').read_text())['password']]
known.extend(line.split('=',1)[1] for line in (baseline/'runtime.secret.env').read_text().splitlines() if 'PASSWORD=' in line)
known.extend((baseline/'source.secret').read_text().splitlines()[1:2])
O.mkdir()
for p in sorted(N.rglob('*')):
 if not p.is_file() or 'committed-build-tree' in p.parts or 'probe-classes' in p.parts or 'secret' in p.name or p.suffix not in {'.txt','.json','.py','.sql','.xml','.trc'}:continue
 b=p.read_bytes();assert not any(s.encode() in b for s in known if s),p.name
 target=O/p.relative_to(N);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
shutil.copyfile(__file__,O/'executed-seal-driver.py')
items=[]
for p in sorted(O.rglob('*')):
 if p.is_file():
  r=receipt(p);r['path']=p.relative_to(O).as_posix();items.append(r)
manifest=json.dumps(items,sort_keys=True,indent=2).encode();(O/'manifest.json').write_bytes(manifest)
for item in items:
 r=receipt(O/item['path']);assert all(r[k]==item[k] for k in ['bytes','sha256','sha512'])
proof=dict(files=len(items),manifestSha256=hashlib.sha256(manifest).hexdigest(),manifestSha512=hashlib.sha512(manifest).hexdigest(),publicEvidenceKnownSecretMatches=0,rawFilesReferencedNotSanitized=True,tests=totals,actualNativePendingXid='10.26.3522',prefixRc=1,closedRc=1,events=0,reason='UNSUPPORTED_DICTIONARY_DISCOVERY_ROW',sourceRestored=True,triggerRemoved=True,targetActions=0,parserSupported=False)
(O/'SEALED.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
