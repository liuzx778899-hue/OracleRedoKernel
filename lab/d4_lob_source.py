"""Freeze one owned BasicFile/SecureFile normal LOB sample; diagnostic SQL is never event input."""
from pathlib import Path
import json,hashlib,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d4-lob-source-20261009'
S='oraredo-19c-lab-20261004';resume=sys.argv[1:]==['--resume-early']
if resume:
 assert (N/'stop.rc').read_text().strip()=='0' and (N/'T_D4_LOBS_20261009-early.rc').read_text().strip()=='1'
 assert not (N/'source-actions-started.txt').exists() and not (N/'executed-resume.py').exists()
 shutil.copyfile(__file__,N/'executed-resume.py')
else:
 assert sys.argv[1:]==['--execute'];N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/d4_raw_source.py').read_text();exec(common[common.index('sha=lambda'):common.index('libs=N/')])
tables=[('T_D4_LOBB_20261009','BASICFILE'),('T_D4_LOBS_20261009','SECUREFILE')]
started=False
def values(label,table,scn=None):
 suffix='' if scn is None else ' as of scn '+str(scn)
 # Hex offsets are bytes for BLOB, characters for CLOB; CLOB validation explicitly uses AL32UTF8.
 out=sql(label,f"""alter session set container=ORKPDB;
set serveroutput on size unlimited
declare b blob;c clob;part raw(32767);n number;begin
 for r in (select ID,B,C from ORKLAB.{table}{suffix} order by ID) loop
  dbms_output.put_line('ROW|'||r.ID||'|'||case when r.B is null then 'NULL' else to_char(dbms_lob.getlength(r.B)) end||'|'||case when r.C is null then 'NULL' else to_char(dbms_lob.getlength(r.C)) end);
  if r.B is not null then n:=dbms_lob.getlength(r.B);for i in 0..trunc((n-1)/1000) loop
   dbms_output.put_line('BHEX|'||r.ID||'|'||i||'|'||rawtohex(dbms_lob.substr(r.B,1000,1+i*1000)));end loop;end if;
  if r.C is not null then n:=dbms_lob.getlength(r.C);for i in 0..trunc((n-1)/1000) loop
   dbms_output.put_line('CHEX|'||r.ID||'|'||i||'|'||rawtohex(utl_i18n.string_to_raw(dbms_lob.substr(r.C,1000,1+i*1000),'AL32UTF8')));end loop;end if;
 end loop;end;
/
""")
 result=[]
 for line in out.splitlines():
  if not line.startswith('ROW|'):continue
  _,key,blen,clen=line.split('|');item=dict(id=int(key))
  for tag,length in [('BHEX',blen),('CHEX',clen)]:
   pieces=sorted((int(p.split('|')[2]),p.split('|')[3]) for p in out.splitlines() if p.startswith(tag+'|'+key+'|'))
   assert [i for i,_ in pieces]==list(range(len(pieces)))
   data=None if length=='NULL' else bytes.fromhex(''.join(part for _,part in pieces))
   if data is not None:
    assert len(data)==int(length) if tag=='BHEX' else len(data.decode('utf-8'))==int(length)
   item[tag]=None if data is None else dict(length=int(length),bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),hex=data.hex())
  result.append(item)
 (N/(('resume-' if resume else '')+label+'.json')).write_text(json.dumps(result,indent=2));return result
try:
 assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 assert 'ork-product-runtime' not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 run('start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   out=sql('ready-'+str(attempt),"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name in ('T_D4_LOBB_20261009','T_D4_LOBS_20261009');")
   if 'FREE|' in out:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'LOGGING|NO|NO' in out and ('FREE|2' if resume else 'FREE|0') in out
 for table,storage in tables:
  if not resume:sql(table+'-seed',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,B blob,C clob,constraint PK_{table[2:]} primary key(ID)) lob(B) store as {storage}(enable storage in row nocache logging) lob(C) store as {storage}(enable storage in row nocache logging);\ninsert into ORKLAB.{table} values(1,hextoraw('00FF80C328000102'),to_clob(unistr('seed-\\4E2D')));\ncommit;")
 metadata=sql('early-metadata',"alter session set container=ORKPDB;\nselect 'SCN|'||to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') from dual;\nselect 'LOB|'||TABLE_NAME||'|'||COLUMN_NAME||'|'||SEGMENT_NAME||'|'||INDEX_NAME||'|'||SECUREFILE||'|'||IN_ROW||'|'||LOGGING||'|'||CHUNK from dba_lobs where OWNER='ORKLAB' and TABLE_NAME in ('T_D4_LOBB_20261009','T_D4_LOBS_20261009') order by TABLE_NAME,COLUMN_NAME;\nselect 'OBJECT|'||OBJECT_NAME||'|'||OBJECT_TYPE||'|'||OBJECT_ID||'|'||DATA_OBJECT_ID from dba_objects where OWNER='ORKLAB' and (OBJECT_NAME in ('T_D4_LOBB_20261009','T_D4_LOBS_20261009','PK_D4_LOBB_20261009','PK_D4_LOBS_20261009') or OBJECT_NAME in(select SEGMENT_NAME from dba_lobs where OWNER='ORKLAB' and TABLE_NAME in ('T_D4_LOBB_20261009','T_D4_LOBS_20261009')) or OBJECT_NAME in(select INDEX_NAME from dba_lobs where OWNER='ORKLAB' and TABLE_NAME in ('T_D4_LOBB_20261009','T_D4_LOBS_20261009'))) order by OBJECT_ID;")
 firstScn=int(next(line.split('|')[1] for line in metadata.splitlines() if line.startswith('SCN|')))
 lob=[line.split('|') for line in metadata.splitlines() if line.startswith('LOB|')];assert len(lob)==4
 for table,storage in tables:assert all(row[5]==('YES' if storage=='SECUREFILE' else 'NO') and row[6:8]==['YES','YES'] for row in lob if row[1]==table)
 for table,_ in tables:
  seed=values(table+'-early',table,firstScn);assert len(seed)==1 and seed[0]['BHEX']['hex']=='00ff80c328000102' and bytes.fromhex(seed[0]['CHEX']['hex']).decode()=='seed-中'
 (N/'early-source-evidence.json').write_text(json.dumps(dict(scn=firstScn,lobs=lob,metadataSha256=sha(N/(('resume-' if resume else '')+'early-metadata.stdout')),role='diagnostic-only-not-approved-kernel-baseline'),indent=2))
 firstOut=sql('before-dml',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';")
 first=int(next(line.split('|')[1] for line in firstOut.splitlines() if line.startswith('CURRENT|')))
 (N/'source-actions-started.txt').write_text('One new LOB fixture per storage only. Never replay.\n')
 for table,_ in tables:
  sql(table+'-actions',f"""alter session set container=ORKPDB;
declare b blob;c clob;chunk raw(2000):=hextoraw(rpad('00FF80C328',4000,'00FF80C328'));chars varchar2(32767):='';begin
 for i in 1..500 loop chars:=chars||unistr('\\4E2D\\6587');end loop;
 update ORKLAB.{table} set B=empty_blob(),C=empty_clob() where ID=1 returning B,C into b,c;
 for i in 1..5 loop dbms_lob.writeappend(b,utl_raw.length(chunk),chunk);dbms_lob.writeappend(c,length(chars),chars);end loop;end;
/
commit;
insert into ORKLAB.{table} values(2,hextoraw('C32800FF'),to_clob(unistr('new-\\6587')));
commit;
update ORKLAB.{table} set B=empty_blob(),C=empty_clob() where ID=2;
commit;
update ORKLAB.{table} set B=null,C=null where ID=2;
commit;
delete from ORKLAB.{table} where ID=2;
commit;
declare b blob;c clob;begin select B,C into b,c from ORKLAB.{table} where ID=1 for update;
 dbms_lob.write(b,4,101,hextoraw('DEADBEEF'));dbms_lob.write(c,2,101,unistr('\\7532\\4E59'));end;
/
commit;
declare b blob;c clob;begin select B,C into b,c from ORKLAB.{table} where ID=1 for update;
 dbms_lob.trim(b,512);dbms_lob.trim(c,128);end;
/
commit;
declare b blob;c clob;begin select B,C into b,c from ORKLAB.{table} where ID=1 for update;
 dbms_lob.write(b,4,1,hextoraw('FFFFFFFF'));dbms_lob.write(c,2,1,'XX');end;
/
rollback;
""")
  actual=values(table+'-final',table);assert len(actual)==1 and actual[0]['id']==1
  binary=bytearray(bytes.fromhex(('00ff80c328'*400))*5);binary[100:104]=bytes.fromhex('deadbeef');binary=bytes(binary[:512])
  text=list('中文'*2500);text[100:102]=list('甲乙');text=''.join(text[:128]).encode('utf-8')
  assert actual[0]['BHEX']['hex']==binary.hex() and actual[0]['CHEX']['hex']==text.hex()
 end=sql('after-dml',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'SCN|'||to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') from dual;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
 assert 'LOGGING|NO|NO' in end;last=int(next(line.split('|')[1] for line in end.splitlines() if line.startswith('CURRENT|')));lastScn=int(next(line.split('|')[1] for line in end.splitlines() if line.startswith('SCN|')))
 sql('archive','alter system archive log current;');archives=[]
 for seq in range(first,last+1):
  out=sql('archive-path-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
  paths={line.split('|',1)[1] for line in out.splitlines() if line.startswith('PATH|')};assert len(paths)==1;path=paths.pop();assert "'" not in path
  local=N/(str(seq)+'.raw');run('copy-'+str(seq),['docker','cp',S+':'+path,local]);assert run('hash-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
  trace=sql('native-'+str(seq),f"alter system dump logfile '{path}' scn min {firstScn} scn max {lastScn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
  trc=next(line.split('|',1)[1] for line in trace.splitlines() if line.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+trc,N/(str(seq)+'.trc')])
  archives.append(dict(sequence=seq,path=str(local),sha256=sha(local),nativeSha256=sha(N/(str(seq)+'.trc'))))
 (N/'source-frozen-proof.json').write_text(json.dumps(dict(firstScn=firstScn,lastScn=lastScn,tables=tables,archives=archives,sourceDmlReplayed=False,diagnosticOnly=True,kernelLobSupported=False,productIntegrated=False),indent=2))
finally:
 if started:run('stop',['docker','stop','-t','60',S])
