"""Seal the new cross-generation refusal evidence; completion is explicitly unverified."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-active-20261007'
D=R/'docs/workorders/evidence/RESEARCH-cross-window-active-20261007.json'
assert not D.exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
proof=json.loads((N/'research-proof.json').read_text())
assert proof['pending_rc']==proof['closed_rc']==1 and proof['events']==0
assert 'INCOMPLETE_TRANSACTION_IDENTITIES:1' in (N/'events-pending.stderr.txt').read_text()
assert 'UNSUPPORTED_MEDIA_RECOVERY_MARKER_RANGE' in (N/'events-closed.stderr.txt').read_text()
for seq,blocks in [(73,219166),(74,23)]:
    q=json.loads((N/('compare-'+str(seq)+'.stdout.txt')).read_text())
    assert q['observations_verified']==blocks and not q['missing_blocks'] and not q['different_blocks']
source=(N/'final-source.stdout.txt').read_text()
assert all(x in source for x in ['LOGGING|NO|NO','SEED|0|history-product-restored|anchor','OLD|11|blocking-closed|new-note'])
cmd=['docker','exec','-i','oraredo-19c-lab-20261004','python3','-']
body=b"from pathlib import Path\nfor p in Path('/proc').glob('[0-9]*/cmdline'):\n try:\n  a=p.read_bytes().split(b'\\0')\n  if any(x.endswith((b'/follow_closed_members.py',b'/resume_online_observation.py')) for x in a):print(p.parent.name)\n except OSError:pass\n"
p=subprocess.run(cmd,input=body,capture_output=True,timeout=30)
for suffix,value in [('command.json',json.dumps(cmd).encode()),('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/('terminal-process-check.'+suffix)).write_bytes(value)
assert p.returncode==0 and not p.stdout.strip()
old=R/'docs/workorders/evidence/DELIVERY-split-history-late-http-20261007.json'
previous=json.loads(old.read_text())
for item in previous['files']:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
(N/'executed-seal.py').write_bytes(Path(__file__).read_bytes())
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower() or path.suffix=='.lock':continue
    digest=sha(path);assert sha(path)==digest
    files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,collector_live=False,previous_capsule_files=len(previous['files']),previous_capsule_unchanged=True,double_sha256_verified=True,files_count=len(files),files=files,production_deployed=False,site_accepted=False,released=False,next_step='Independently bind mixed-flag 24.4 raw range to complete cross-generation transaction; no source replay; then persistent cross-publication state/recovery')
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),previous_files=len(previous['files']),collector_live=False,postcheck=True)))
