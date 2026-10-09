"""Continue only the uncommitted LOB fixture, with append-only per-step evidence."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent
OLD=R/'lab/out/d4-lob-source-20261009'
retry=sys.argv[1:]==['--resume-verified'];N=OLD/('continue-r2' if retry else 'continue-r1')
S='oraredo-19c-lab-20261004';resume=False
assert retry or sys.argv[1:]==['--execute']
if retry:
 assert (OLD/'continue-r1/stop.rc').read_text().strip()=='0'
 assert not list((OLD/'continue-r1').glob('*.started'))
assert (OLD/'resume-stop.rc').read_text().strip()=='0'
assert (OLD/'resume-T_D4_LOBB_20261009-actions.rc').read_text().strip()=='1'
assert (OLD/'source-actions-started.txt').exists()
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/d4_raw_source.py').read_text()
exec(common[common.index('sha=lambda'):common.index('libs=N/')])
original=(R/'lab/d4_lob_source.py').read_text()
exec(original[original.index('def values('):original.index('try:\n assert run')])
tables=['T_D4_LOBB_20261009','T_D4_LOBS_20261009'];started=False
seed=[dict(id=1,BHEX=dict(length=8,bytes=8,sha256=hashlib.sha256(bytes.fromhex('00ff80c328000102')).hexdigest(),hex='00ff80c328000102'),CHEX=dict(length=6,bytes=8,sha256=hashlib.sha256('seed-中'.encode()).hexdigest(),hex='seed-中'.encode().hex()))]
try:
 assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   ready=sql('ready-'+str(attempt),"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'READY|'||count(*) from dba_tables where owner='ORKLAB' and table_name in ('T_D4_LOBB_20261009','T_D4_LOBS_20261009');")
   if 'READY|2' in ready:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'LOGGING|NO|NO' in ready and 'READY|2' in ready
 for table in tables:assert values(table+'-rollback-check',table)==seed
 length=sql('buffer-length',"alter session set container=ORKPDB;\nselect 'BUFFER|'||length(rpad(unistr('\\4E2D\\6587'),1000,unistr('\\4E2D\\6587')))||'|'||lengthb(rpad(unistr('\\4E2D\\6587'),1000,unistr('\\4E2D\\6587')))||'|'||length(unistr('\\4E2D\\6587'))*500 from dual;")
 buffer=next(x.split('|') for x in length.splitlines() if x.startswith('BUFFER|'))
 assert buffer[1]=='500' and buffer[3]=='1000'
 (N/'rollback-confirmed.json').write_text(json.dumps(dict(tables=tables,fullSeedEqual=True,originalSqlErrorRollback=True,originalRequestedChars=1000,originalActualChars=500,role='diagnostic-only'),indent=2))
 steps=[]
 for table in tables:
  prefix=f'alter session set container=ORKPDB;\n'
  actions=[('large',f"""declare b blob;c clob;chunk raw(2000):=hextoraw(rpad('00FF80C328',4000,'00FF80C328'));chars varchar2(32767):='';begin
 for i in 1..500 loop chars:=chars||unistr('\\4E2D\\6587');end loop;
 if length(chars)<>1000 or utl_raw.length(chunk)<>2000 then raise_application_error(-20001,'BUFFER_LENGTH');end if;
 update ORKLAB.{table} set B=empty_blob(),C=empty_clob() where ID=1 returning B,C into b,c;
 if sql%rowcount<>1 then raise_application_error(-20002,'ROW_COUNT');end if;
 for i in 1..5 loop dbms_lob.writeappend(b,utl_raw.length(chunk),chunk);dbms_lob.writeappend(c,length(chars),chars);end loop;
 if dbms_lob.getlength(b)<>10000 or dbms_lob.getlength(c)<>5000 then raise_application_error(-20003,'LOB_LENGTH');end if;end;
/
commit;"""),
 ('insert',f"insert into ORKLAB.{table} values(2,hextoraw('C32800FF'),to_clob(unistr('new-\\6587')));\ncommit;"),
 ('empty',f"update ORKLAB.{table} set B=empty_blob(),C=empty_clob() where ID=2;\ncommit;"),
 ('null',f"update ORKLAB.{table} set B=null,C=null where ID=2;\ncommit;"),
 ('delete',f"delete from ORKLAB.{table} where ID=2;\ncommit;"),
 ('partial',f"declare b blob;c clob;begin select B,C into b,c from ORKLAB.{table} where ID=1 for update;\ndbms_lob.write(b,4,101,hextoraw('DEADBEEF'));dbms_lob.write(c,2,101,unistr('\\7532\\4E59'));end;\n/\ncommit;"),
 ('trim',f"declare b blob;c clob;begin select B,C into b,c from ORKLAB.{table} where ID=1 for update;\ndbms_lob.trim(b,512);dbms_lob.trim(c,128);end;\n/\ncommit;"),
 ('rollback',f"declare b blob;c clob;begin select B,C into b,c from ORKLAB.{table} where ID=1 for update;\ndbms_lob.write(b,4,1,hextoraw('FFFFFFFF'));dbms_lob.write(c,2,1,'XX');end;\n/\nrollback;")]
  binary=bytearray(bytes.fromhex('00ff80c328'*2000));text=list('中文'*2500)
  for name,body in actions:
   label=table+'-'+name
   # The exclusive directory and started marker prevent accidental replay, including ambiguous commits.
   (N/(label+'.started')).write_text('Never replay this step. Inspect committed state before any recovery.\n')
   sql(label,prefix+body)
   if name=='partial':binary[100:104]=bytes.fromhex('deadbeef');text[100:102]=list('甲乙')
   if name=='trim':binary=binary[:512];text=text[:128]
   actual=values(label+'-truth',table)
   expected=[(1,bytes(binary),''.join(text).encode())]
   if name=='insert':expected.append((2,bytes.fromhex('c32800ff'),'new-文'.encode()))
   if name=='empty':expected.append((2,b'',b''))
   if name=='null':expected.append((2,None,None))
   assert len(actual)==len(expected)
   for row,(key,b,c) in zip(actual,expected):
    assert row['id']==key
    assert row['BHEX'] is None if b is None else row['BHEX']['hex']==b.hex()
    assert row['CHEX'] is None if c is None else row['CHEX']['hex']==c.hex()
   sql(label+'-scn',"select 'SCN|'||to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') from dual;")
   steps.append(dict(table=table,step=name,rc=0,fullValuesEqual=True));(N/'steps.json').write_text(json.dumps(steps,indent=2))
 end=sql('after-dml',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'SCN|'||to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') from dual;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
 assert 'LOGGING|NO|NO' in end
 first=97;last=int(next(x.split('|')[1] for x in end.splitlines() if x.startswith('CURRENT|')))
 firstScn=json.loads((OLD/'early-source-evidence.json').read_text())['scn'];lastScn=int(next(x.split('|')[1] for x in end.splitlines() if x.startswith('SCN|')))
 sql('archive','alter system archive log current;');archives=[]
 for seq in range(first,last+1):
  out=sql('archive-path-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
  paths={x.split('|',1)[1] for x in out.splitlines() if x.startswith('PATH|')};assert len(paths)==1
  path=paths.pop();assert "'" not in path
  local=N/(str(seq)+'.raw');run('copy-'+str(seq),['docker','cp',S+':'+path,local])
  assert run('hash-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
  trace=sql('native-'+str(seq),f"alter system dump logfile '{path}' scn min {firstScn} scn max {lastScn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
  trc=next(x.split('|',1)[1] for x in trace.splitlines() if x.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+trc,N/(str(seq)+'.trc')])
  archives.append(dict(sequence=seq,path=str(local),sha256=sha(local),nativeSha256=sha(N/(str(seq)+'.trc'))))
 (N/'source-frozen-proof.json').write_text(json.dumps(dict(firstScn=firstScn,lastScn=lastScn,tables=tables,steps=steps,archives=archives,sourceDmlReplayed=False,diagnosticOnly=True,kernelLobSupported=False,productIntegrated=False),indent=2))
finally:
 if started:run('stop',['docker','stop','-t','60',S])
