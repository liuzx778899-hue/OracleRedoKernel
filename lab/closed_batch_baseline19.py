"""Acquire a fresh read-only baseline and freeze continuous archives covering already closed fixture transactions. No DML."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;CONTAINER='oraredo-19c-lab-20261004'
if len(sys.argv)!=3 or sys.argv[1]!='--execute':raise RuntimeError('usage: --execute fresh-directory')
out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=False)
def run(name,args):
 p=subprocess.run(args,capture_output=True)
 for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]:(out/(name+'.'+ext+'.txt')).write_bytes(data)
 (out/(name+'.rc.txt')).write_text(str(p.returncode));(out/(name+'-command.json')).write_text(json.dumps(args))
 if p.returncode:raise RuntimeError(name+': preserve failure, no automatic repeat')
 return p.stdout.decode('utf-8')
run('baseline',[sys.executable,str(ROOT/'lab/capture_heap_baseline19.py'),'--execute',str(out/'baseline')])
baseline=(out/'baseline/snapshot.stdout.txt').read_text();receipt=json.loads((out/'baseline/receipt.json').read_text());scn=int(re.search(r'^SNAPSHOT_SCN\|(\d+)',baseline,re.M)[1])
expected='ROW|73076|12|403|0|C10C|636F6E63757272656E742D636C6F736564|6E65772D6E6F7465'
if expected not in baseline or 'ROW_COUNT|1' not in baseline:raise RuntimeError('FIXTURE_STATE_MISMATCH')
def sql(name,body):
 text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 trimspool on feedback off heading off\n'+body+'\nexit success\n';path=out/(name+'.sql');path.write_text(text);remote='/tmp/ork-closed-batch-'+out.name+'-'+name+'.sql'
 run(name+'-copy',['docker','cp',str(path),CONTAINER+':'+remote]);return run(name,['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote])
truth=sql('truth-archive',"alter session set container=ORKPDB;\n"+''.join("select '"+label+"|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||rawtohex(utl_raw.cast_from_number(ID))||'|'||rawtohex(utl_raw.cast_to_raw(TXT))||'|'||rawtohex(utl_raw.cast_to_raw(NOTE)) from ORKLAB.T_BASELINE_20261004 "+suffix+" order by rowid;\n" for label,suffix in [('OLD_BASELINE','as of scn '+str(scn)),('FINAL','')])+"alter session set container=CDB$ROOT;\nalter system archive log current;\nselect 'ARCHIVE|'||thread#||'|'||sequence#||'|'||first_change#||'|'||next_change#||'|'||name from v$archived_log where thread#=1 and sequence#>=33 and name is not null order by sequence#;")
files=[]
for line in truth.splitlines():
 p=line.strip().split('|')
 if len(p)!=6 or p[0]!='ARCHIVE' or int(p[3])>scn:continue
 path=out/('archive-'+p[1]+'-'+p[2]+'.dbf');run('archive-'+p[2],['docker','cp',CONTAINER+':'+p[5],str(path)])
 files.append({'sequence':int(p[2]),'source':p[5],'path':path.name,'first_scn':int(p[3]),'next_scn':int(p[4]),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
if not files or files[0]['sequence']!=33 or files[-1]['next_scn']<=scn or any(a['next_scn']!=b['first_scn'] or a['sequence']+1!=b['sequence'] for a,b in zip(files,files[1:])):raise RuntimeError('ARCHIVE_COVERAGE_MISMATCH')
trace=sql('dump',"alter session set tracefile_identifier='ORK_CLOSED_BATCH';\n"+''.join("alter system dump logfile '"+f['source']+"';\n" for f in files)+"select value from v$diag_info where name='Default Trace File';")
paths=[x.strip() for x in trace.splitlines() if x.strip().startswith('/opt/oracle/') and x.strip().endswith('.trc')]
if len(paths)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('trace',['docker','cp',CONTAINER+':'+paths[0],str(out/'dump.trc')])
result={'snapshot_scn':scn,'acquisition_sha256':receipt['snapshot_sha256'],'files':files,'source_dml_executed':False,'scope':'closed physical batch empty changes; complete no-target silence and CDC unaccepted'}
(out/'manifest.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
