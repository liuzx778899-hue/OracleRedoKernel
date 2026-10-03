"""Offline derived-input counterexamples. No source database operations."""
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

root=Path(__file__).resolve().parent.parent
source=root/'lab/out/oracle19-nosupp-20261004/archive-1-9.dbf'
out=root/'lab/out/oracle19-negative-20261004'
out.mkdir(parents=True,exist_ok=True)
jar=Path(sys.argv[1]).resolve()
java=sys.argv[2]
raw=source.read_bytes()
copies={'truncate-byte':raw[:-1], 'missing-final-block':raw[:-512], 'missing-middle-block':raw[:1536]+raw[2048:]}
bad=bytearray(raw); bad[1200]^=1; copies['checksum-poison']=bad
bad=bytearray(raw); struct.pack_into('<I',bad,2*512+8,123)
struct.pack_into('<H',bad,2*512+14,0)
check=0
for word in struct.unpack('<256H',bad[1024:1536]):
    check^=word
struct.pack_into('<H',bad,2*512+14,check)
copies['resealed-sequence-poison']=bad
reports=[]
for name,data in copies.items():
    path=out/(name+'.dbf'); path.write_bytes(data)
    result=subprocess.run([java,'-jar',str(jar),'scan',str(path)],capture_output=True,text=True,encoding='utf-8')
    (out/(name+'.stdout.txt')).write_text(result.stdout,encoding='utf-8')
    (out/(name+'.stderr.txt')).write_text(result.stderr,encoding='utf-8')
    reports.append({'case':name,'input_sha256':hashlib.sha256(data).hexdigest(),'rc':result.returncode,
                    'stdout':result.stdout,'stderr':result.stderr,'pass':result.returncode!=0 and not result.stdout.strip()})
report={'source_sha256':hashlib.sha256(raw).hexdigest(),'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
        'cases':reports,'scope':'derived real archived redo structural refusal, not missing-sequence transaction acceptance'}
(out/'verdict.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({'cases':len(reports),'passed':sum(r['pass'] for r in reports)}))
sys.exit(not all(r['pass'] for r in reports))
