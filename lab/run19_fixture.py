"""Dedicated fixture execution; never accepts a user-selected/shared container."""
import datetime
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONTAINER = 'oraredo-19c-lab-20261004'
OUT = ROOT / 'lab/out/oracle19-nosupp-20261004'
OUT.mkdir(parents=True, exist_ok=True)

def run(name, args):
    stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    result = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='strict')
    (OUT / (name + '.stdout.txt')).write_text(result.stdout, encoding='utf-8')
    (OUT / (name + '.stderr.txt')).write_text(result.stderr, encoding='utf-8')
    with (OUT / 'commands.jsonl').open('a', encoding='utf-8') as stream:
        stream.write(json.dumps({'started_utc': stamp, 'command': args, 'rc': result.returncode}) + '\n')
    if result.returncode:
        raise RuntimeError(f'{name}: rc={result.returncode}; see preserved stdout/stderr')
    return result.stdout

def sql(name, script):
    remote = '/tmp/ork-' + script
    run(name + '-copy', ['docker','cp',str(ROOT/'lab/sql'/script),CONTAINER+':'+remote])
    return run(name, ['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote])

identity = run('container-binding', ['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if identity.strip() != 'OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':
    raise RuntimeError('dedicated source binding mismatch')
freezing = sys.argv[1:] == ['--freeze']
preflight = sql('preflight-freeze' if freezing else 'preflight', 'preflight.sql')
if '19.3.0.0.0' not in preflight or 'ORK19' not in preflight:
    raise RuntimeError('version/database mismatch')
if sys.argv[1:] not in (['--execute'], ['--freeze']):
    print('Read-only preflight complete; --execute runs a new dedicated fixture once.')
    sys.exit(0)
if freezing:
    if (OUT/'freeze.stdout.txt').exists():
        raise RuntimeError('freeze already attempted: preserve evidence, use a new named export')
    text = sql('freeze', 'freeze19.sql')
else:
    if (OUT/'fixture.stdout.txt').exists():
        raise RuntimeError('fixture already attempted: preserve it; do not replay committed DML')
    sql('enable-archive', 'lab19-enable-archive.sql')
    text = sql('fixture', 'fixture19.sql')
files = []
for line in text.splitlines():
    pieces = line.strip().split('|')
    if len(pieces) == 7 and pieces[0].isdigit():
        source = pieces[-1]
        target = OUT / ('archive-' + pieces[0] + '-' + pieces[1] + '.dbf')
        run('capture-'+pieces[1], ['docker','cp',CONTAINER+':'+source,str(target)])
        files.append({'thread':pieces[0], 'sequence':pieces[1], 'first_scn':pieces[2], 'next_scn':pieces[3],
                      'oracle_blocks':pieces[4], 'block_size':pieces[5], 'source_path':source,
                      'path':target.name, 'bytes':target.stat().st_size,
                      'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
if not files:
    raise RuntimeError('ZERO_ARCHIVE_CAPTURES')
(OUT/'manifest.json').write_text(json.dumps({'source_id':CONTAINER,'supplemental_logging':'OFF', 'files':files}, indent=2), encoding='utf-8')
print(json.dumps({'captured_archives':len(files), 'manifest':str(OUT/'manifest.json')}))
