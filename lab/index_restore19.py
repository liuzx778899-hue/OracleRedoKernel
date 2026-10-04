"""One-shot primary-key update/delete rollback capture in the named dedicated lab only."""
import datetime,hashlib,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
CONTAINER='oraredo-19c-lab-20261004'
OUT=ROOT/'lab/out/oracle19-index-restore-20261004'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit --execute required; no automatic DML retry')
OUT.mkdir(parents=True,exist_ok=False)
def run(name,args):
    p=subprocess.run(args,capture_output=True)
    (OUT/(name+'.stdout.txt')).write_bytes(p.stdout);(OUT/(name+'.stderr.txt')).write_bytes(p.stderr)
    with (OUT/'commands.jsonl').open('a',encoding='utf-8') as f:
        f.write(json.dumps({'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':args,'rc':p.returncode})+'\n')
    if p.returncode:raise RuntimeError(name+': failed; preserve outputs, never replay DML')
    return p.stdout.decode('utf-8')
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
script=ROOT/'lab/sql/fixture19-index-restore.sql'
run('copy-fixture',['docker','cp',str(script),CONTAINER+':/tmp/ork-index-restore.sql'])
text=run('fixture',['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@/tmp/ork-index-restore.sql'])
run('executed-fixture',['docker','cp',CONTAINER+':/tmp/ork-index-restore.sql',str(OUT/'executed-fixture.sql')])
start=int(re.search(r'^START_SCN\|(\d+)',text,re.M)[1]);end=int(re.search(r'^END_SCN\|(\d+)',text,re.M)[1])
files=[]
for line in text.splitlines():
    p=line.strip().split('|')
    if len(p)!=8 or p[0]!='ARCHIVE' or int(p[4])<=start or int(p[3])>end:continue
    local=OUT/('archive-'+p[1]+'-'+p[2]+'.dbf')
    run('capture-'+p[2],['docker','cp',CONTAINER+':'+p[7],str(local)])
    files.append({'sequence':int(p[2]),'source':p[7],'path':local.name,'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if not files:raise RuntimeError('ZERO_ARCHIVES')
dump=OUT/'dump.sql'
dump.write_text("whenever sqlerror exit failure\nwhenever oserror exit failure\nset pages 0 lines 32767 feedback off\nalter session set tracefile_identifier='ORK_INDEX_RESTORE';\n"+
                ''.join("alter system dump logfile '"+x['source']+"' scn min "+str(start)+" scn max "+str(end)+";\n" for x in files)+
                "select value from v$diag_info where name='Default Trace File';\nexit success\n",encoding='utf-8')
run('copy-dump',['docker','cp',str(dump),CONTAINER+':/tmp/ork-index-restore-dump.sql'])
trace=run('dump',['docker','exec',CONTAINER,'sqlplus','-s','/','as','sysdba','@/tmp/ork-index-restore-dump.sql'])
paths=[line.strip() for line in trace.splitlines() if line.strip().startswith('/opt/oracle/') and line.strip().endswith('.trc')]
if len(paths)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('copy-trace',['docker','cp',CONTAINER+':'+paths[0],str(OUT/'dump.trc')])
manifest={'source':CONTAINER,'start_scn':start,'end_scn':end,'supplemental_logging':'OFF','files':files,
          'fixture_actions_rc':0,'dump_rc':0,'scope':'source fixture/capture only; parser and zero-intrusion production acceptance pending'}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps(manifest,indent=2))
