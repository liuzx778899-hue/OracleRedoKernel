"""Exercise atomic research output on incomplete/replayed frozen archive chains."""
import json
import subprocess
import sys
from pathlib import Path

classpath=sys.argv[1]
sample=Path(sys.argv[2]).resolve()
output=Path(sys.argv[3]).resolve()
output.mkdir(parents=True,exist_ok=False)
eight=str(sample/'archive-1-8.dbf')
nine=str(sample/'archive-1-9.dbf')
cases=[('missing_begin_archive',[nine],'UNDO_WITHOUT_VERIFIED_TRANSACTION_BEGIN'),
       ('missing_end_archive',[eight],'INCOMPLETE_TRANSACTION_IDENTITIES'),
       ('duplicate_archive',[eight,eight,nine],'ARCHIVE_SEQUENCE_OR_SCN_GAP')]
verdict=[]
for name,archives,reason in cases:
    result=subprocess.run(['java','-cp',classpath,'TransactionLifecycleProbe',*archives],capture_output=True)
    (output/(name+'.stdout')).write_bytes(result.stdout)
    (output/(name+'.stderr')).write_bytes(result.stderr)
    verdict.append({'case':name,'rc':result.returncode,'stdout_bytes':len(result.stdout),
                    'reason':reason,'passed':result.returncode!=0 and not result.stdout and reason.encode() in result.stderr})
report={'cases':verdict,'passed':sum(r['passed'] for r in verdict),
        'scope':'selected identity lifecycle and atomic research output; CDC events remain disabled'}
(output/'verdict.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
sys.exit(not all(r['passed'] for r in verdict))
