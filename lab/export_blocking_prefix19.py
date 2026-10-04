"""Read-only recovery of the earlier archive covering a prior failed session rollback; no source DML or log switch."""
import hashlib,json,subprocess,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;container='oraredo-19c-lab-20261004'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit --execute required')
out=r/'lab/out/oracle19-blocking-prefix-20261005';out.mkdir(parents=True,exist_ok=False)
def run(name,args):
 p=subprocess.run(args,capture_output=True)
 for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]:(out/(name+'.'+ext+'.txt')).write_bytes(data)
 (out/(name+'.rc.txt')).write_text(str(p.returncode));(out/(name+'-command.json')).write_text(json.dumps(args))
 if p.returncode:raise RuntimeError(name+': preserve failure')
 return p.stdout.decode()
assert run('binding',['docker','inspect',container,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).strip()=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
archive='/opt/oracle/product/19c/dbhome_1/dbs/arch1_37_1245693159.dbf';run('copy-37',['docker','cp',container+':'+archive,str(out/'archive-1-37.dbf')])
sql="whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\nalter session set tracefile_identifier='ORK_BLOCKING_PREFIX';\n"+''.join("alter system dump logfile '/opt/oracle/product/19c/dbhome_1/dbs/arch1_"+str(n)+"_1245693159.dbf';\n" for n in [37,38,39])+"select value from v$diag_info where name='Default Trace File';\nexit success\n"
path=out/'dump.sql';path.write_text(sql);remote='/tmp/ork-blocking-prefix.sql';run('copy-query',['docker','cp',str(path),container+':'+remote]);data=run('dump',['docker','exec',container,'sqlplus','-s','/','as','sysdba','@'+remote]);paths=[x.strip() for x in data.splitlines() if x.strip().startswith('/opt/oracle/') and x.strip().endswith('.trc')];assert len(paths)==1
run('copy-trace',['docker','cp',container+':'+paths[0],str(out/'dump.trc')]);print(json.dumps({'archive37_sha256':hashlib.sha256((out/'archive-1-37.dbf').read_bytes()).hexdigest(),'dump_sha256':hashlib.sha256((out/'dump.trc').read_bytes()).hexdigest(),'source_dml':False}))
