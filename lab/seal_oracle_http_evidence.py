"""Seal non-secret HTTP evidence twice; large binaries stay ignored."""
import hashlib,json,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
for directory in sys.argv[1:]:
 O=R/'lab/out'/directory;items=[]
 def sha(p):
  h=hashlib.sha256()
  with p.open('rb') as f:
   for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
  return h.hexdigest()
 for p in sorted(O.rglob('*')):
  if not p.is_file() or 'secret' in p.name.lower() or p.name in ['seal.json','seal.sha256']:continue
  if any(part in ['data','logs'] for part in p.relative_to(O).parts):continue
  first=sha(p);assert first==sha(p);items.append(dict(path=p.relative_to(O).as_posix(),bytes=p.stat().st_size,sha256=first))
 (O/'seal.json').write_text(json.dumps(dict(files=items,secret_files_excluded=True,two_reads_equal=True),indent=2));(O/'seal.sha256').write_text(sha(O/'seal.json'));print(directory+'|'+str(len(items)))
