"""Resume the unconsumed RAW lab run after fixing only its imported metadata JSON convention."""
from pathlib import Path
import os,sys,json,hashlib,shutil,time
R=Path('D:/work/vibecode/OracleRedoKernel');label='d4-raw-product-metadata-resume-20261009'
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out/d4-raw-product-20261009';assert not (N/'executed-resume.py').exists();shutil.copyfile(__file__,N/'executed-resume.py')
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines())
secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
original_run=run
def run(name,args,data=None,private=False):return original_run('resume-'+name,args,data,private)
A=R/'lab/out/d4-raw-source-20261009';SOURCE='oracle-d4-raw';U='ORKRAWCTL_20261009';T='ORKTGT_CMT_20261005.RRAW1';TARGET='oracle-raw-RRAW1';ACQ=1239501
prepared=json.loads((N/'preparation.stdout').read_text());e=prepared['evidence'];reference=json.loads((A/'baseline-reference.json').read_text())
application=N/'application.jar';assert sha(application)=='073c95364a9a15305527a139e846bdd6ac754839930af8cb705cfd6cab1f23c9'
metadata=json.loads((N/'product-metadata.json').read_text());old=dict(prepared['metadata']);old['scn']=str(old['scn']);assert old==metadata
assert (N/'source-stop.rc').read_text().strip()=='0' and not (N/'target-create.rc').exists() and not (N/'enroll.rc').exists()
def clob(value):
 text=json.dumps(value,separators=(',',':'))
 return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))
started=False;helper=False
# Use the original unexecuted target/consume/verification tail, preserving its failures and cleanup.
original=(N/'executed-product.py').read_text();tail=original[original.index(" source=':'"):]
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   out=sql(f'ready-{attempt}',f"alter session set container=ORKPDB;\nselect 'SOURCE|'||ID||'|'||rawtohex(BIN) from ORKLAB.T_D4_RAW_20261009;\nselect 'UNCONSUMED|'||(select count(*) from {U}.SH_TASK)||'|'||(select count(*) from {U}.SH_CAP_WINDOW)||'|'||(select count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'RRAW1_%') from dual;")
   if 'SOURCE|' in out:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'SOURCE|1|010080FF' in out and 'UNCONSUMED|0|0|0' in out
 sql('metadata-convention',f"alter session set container=ORKPDB;\nbegin update {U}.SH_ORACLE_ACQUISITION set METADATA_JSON="+clob(metadata)+f" where ID={ACQ} and SNAPSHOT_SHA256='{reference['sha256']}' and SNAPSHOT_SCN={reference['scn']} and STATE='ACQUIRED_ONLY';if sql%rowcount<>1 then raise_application_error(-20001,'actual early receipt missing');end if;end;\n/\ncommit;")
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-raw-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--mount','type=bind,source='+str(N/'snapshots')+',target=/evidence/snapshots,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
 for attempt in range(40):
  try:http(f'health-{attempt}','/system/info');break
  except (AssertionError,ValueError):
   if attempt==39:raise
   time.sleep(3)
 verified=http('acquisition-verified',f'/oracle/acquisitions/{ACQ}');assert verified['snapshotSha256']==reference['sha256'] and int(verified['snapshotScn'])==reference['scn']
except BaseException:
 try:
  if helper:run('helper-logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 finally:
  if started:run('source-stop',['docker','stop','-t','60',S])
 raise
exec('try:\n'+tail)
