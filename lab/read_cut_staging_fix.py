"""Fix the helper's staging mount configuration while the same normal collector retries. No source replay."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(__file__),'windows-staging-fix']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
candidate=json.loads((N/'candidate-r2.json').read_text());applicationFile='application-r2.jar'
assert run('owner',['docker','inspect',HELPER,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip()=='OracleRedoKernel|oracle-read-cut'
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines())
assert props['SH_ORACLE_RAW_DISCOVERY_GRANT_ID']=='21002' and props['SH_GATEWAY_STAGING_DIR']=='/evidence/staging'
props['SH_GATEWAY_STAGING_DIR']='/read-cut/staging'
(N/'staging').mkdir(exist_ok=True)
(N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
helper=True;stop_app('old-helper');start_app('corrected-helper')
print('corrected helper staging directory; same collector and queued source file continue, no DML replay')
