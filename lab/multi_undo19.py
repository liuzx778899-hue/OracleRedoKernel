"""One-shot new alternating-row transaction experiment on the named dedicated 19c lab only."""
import datetime
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
CONTAINER='oraredo-19c-lab-20261004'
OUT=ROOT/'lab/out/oracle19-multi-undo-20261004'
recover=sys.argv[1:]==['--recover-capture']
if sys.argv[1:] not in (['--execute'],['--recover-capture']):
    raise RuntimeError('--execute or read-only --recover-capture required')
if recover:
    if not (OUT/'fixture.stdout.txt').exists() or (OUT/'manifest.json').exists():
        raise RuntimeError('recovery requires completed source output and no previous capture manifest')
else:
    OUT.mkdir(parents=True,exist_ok=False)

def run(name,args):
    stamp=datetime.datetime.now(datetime.timezone.utc).isoformat()
    result=subprocess.run(args,capture_output=True,text=True,encoding='utf-8')
    (OUT/(name+'.stdout.txt')).write_text(result.stdout,encoding='utf-8')
    (OUT/(name+'.stderr.txt')).write_text(result.stderr,encoding='utf-8')
    with (OUT/'commands.jsonl').open('a',encoding='utf-8') as stream:
        stream.write(json.dumps({'started_utc':stamp,'command':args,'rc':result.returncode})+'\n')
    if result.returncode: raise RuntimeError(name+': failed, preserve evidence; do not replay DML')
    return result.stdout

binding=run('recovery-binding' if recover else 'binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':
    raise RuntimeError('dedicated source binding mismatch')
if recover:
    commands=[json.loads(s) for s in (OUT/'commands.jsonl').read_text(encoding='utf-8').splitlines()]
    if not any(c['rc']==0 and c['command'][-1]=='@/tmp/ork-multi-undo.sql' for c in commands):
        raise RuntimeError('source SQL success not proven; do not replay DML')
    text=(OUT/'fixture.stdout.txt').read_text(encoding='utf-8')
    run('executed-script',['docker','cp',CONTAINER+':/tmp/ork-multi-undo.sql',str(OUT/'executed-fixture.sql')])
    run('recovery-truth-copy',['docker','cp',str(ROOT/'lab/sql/truth19-multi-undo.sql'),CONTAINER+':/tmp/ork-multi-undo-truth.sql'])
    truth_text=run('recovery-truth',['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@/tmp/ork-multi-undo-truth.sql'])
else:
    run('copy-script',['docker','cp',str(ROOT/'lab/sql/fixture19-multi-undo.sql'),CONTAINER+':/tmp/ork-multi-undo.sql'])
    text=run('fixture',['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@/tmp/ork-multi-undo.sql'])
    truth_text=text
start=next(int(s.split('|')[1]) for s in text.splitlines() if s.startswith('START_SCN|'))
end=next(int(s.split('|')[1]) for s in text.splitlines() if s.startswith('END_SCN|'))
truth=[json.loads(s) for s in truth_text.splitlines() if s.startswith('{')]
(OUT/'truth.json').write_text(json.dumps(truth,ensure_ascii=False,indent=2),encoding='utf-8')
files=[]
for line in text.splitlines():
    p=line.strip().split('|')
    if len(p)!=8 or p[0]!='ARCHIVE' or int(p[4])<=start or int(p[3])>end: continue
    local=OUT/f'archive-{p[1]}-{p[2]}.dbf'
    run('capture-'+p[2],['docker','cp',CONTAINER+':'+p[7],str(local)])
    files.append({'thread':int(p[1]),'sequence':int(p[2]),'first_scn':int(p[3]),'next_scn':int(p[4]),
                  'oracle_blocks':int(p[5]),'block_size':int(p[6]),'source_path':p[7],'path':local.name,
                  'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if not files: raise RuntimeError('ZERO_ARCHIVE_CAPTURES')
(OUT/'manifest.json').write_text(json.dumps({'source_id':CONTAINER,'start_scn':start,'end_scn':end,
                                          'supplemental_logging':'OFF','files':files},indent=2),encoding='utf-8')
print(json.dumps({'archives':len(files),'fixture_rc':0,'path':str(OUT),'acceptance':'source actions only; cross-block parser pending'}))
