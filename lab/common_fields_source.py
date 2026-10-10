"""One new owned mixed-field fixture per storage; never replay source actions."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent
N=R/'lab/out/common-fields-20261010/source'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert sys.argv[1:]==['--execute'] and (N.parent/'development-candidate.json').exists()
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
assert len(reader)==3 and re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*',reader[0])
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
def run(label,args,data=None):
    assert not (N/(label+'.rc')).exists()
    (N/(label+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=300)
    for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:
        (N/(label+'.'+suffix)).write_bytes(value.replace(reader[1].encode(),b'<redacted-secret>'))
    assert p.returncode==0,(label,p.returncode)
    return p.stdout.decode('utf-8')
def sql(label,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 trimspool on\n'+body+'\nexit success\n'
    (N/(label+'.sql')).write_text(text)
    out=run(label,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode())
    assert 'SP2-' not in out
    return out
libs=N/'libs';classes=N/'classes';libs.mkdir();classes.mkdir()
for name in ['kernel','adapter']:shutil.copyfile(N.parent/(name+'.jar'),libs/(name+'.jar'))
old=R/'lab/out/d3-stream-runtime-20261009/producer-product-r109/libs'
for p in list(old.glob('jackson-*.jar'))+list(old.glob('streamhouse-spi-*.jar')):shutil.copyfile(p,libs/p.name)
shutil.copyfile(R/'lab/out/oracle-snapshot-acquisition-20261005/ojdbc8.jar',libs/'ojdbc8.jar')
shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
cp=str(libs/'*')+';'+str(classes)
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/OracleSnapshotAcquisitionProbe.java',R/'lab/LobProductPreparationProbe.java'])
tables=[('T_D4_MIX_B_20261010','PK_D4_MIX_B_20261010','BASICFILE'),('T_D4_MIX_S_20261010','PK_D4_MIX_S_20261010','SECUREFILE')]
started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name in ('T_D4_MIX_B_20261010','T_D4_MIX_S_20261010');")
            if 'FREE|0' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'FREE|0' in out and 'LOGGING|NO|NO' in out
    first=int(next(l.split('|')[1] for l in out.splitlines() if l.startswith('CURRENT|')))
    for table,index,storage in tables:
        clauses=' '.join('lob('+c+') store as '+storage+'(enable storage in row nocache logging)' for c in ['B','C','J','X'])
        sql(table+'-fixture',f"""alter session set container=ORKPDB;
create table ORKLAB.{table}(ID number not null,TXT varchar2(128),BIN raw(16),DT date,TS timestamp(6),B blob,C clob,J clob,X clob,constraint {index} primary key(ID)) {clauses};
insert into ORKLAB.{table} values(1,'seed',hextoraw('00FF'),to_date('2024-02-29 23:59:58','YYYY-MM-DD HH24:MI:SS'),timestamp '2024-02-29 23:59:58.123456',hextoraw('0080FF'),to_clob('text-seed'),to_clob('{{"value":"seed"}}'),to_clob('<row><value>seed</value></row>'));
commit;
grant select,flashback on ORKLAB.{table} to {reader[0]};""")
    snapshots={}
    for table,index,storage in tables:
        run(table+'-acquire',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,
            '--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
            '-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret',table,'ORKLAB',table,index,'history-normal','/evidence/'+table+'.snapshot'])
        snapshots[table]=dict(path=str(N/(table+'.snapshot')),sha256=sha(N/(table+'.snapshot')))
    (N/'snapshots.json').write_text(json.dumps(snapshots,indent=2))
    for table,index,storage in tables:
        (N/(table+'.actions-started')).write_text('one new future transaction; never replay\n')
        sql(table+'-actions',f"""alter session set container=ORKPDB;
declare b blob;c clob;j clob;x clob;begin
 update ORKLAB.{table} set TXT='mixed-text',BIN=hextoraw('FF0080'),DT=to_date('2025-03-01 00:00:01','YYYY-MM-DD HH24:MI:SS'),TS=timestamp '2025-03-01 00:00:01.654321',B=empty_blob(),C=empty_clob(),J=empty_clob(),X=empty_clob() where ID=1 returning B,C,J,X into b,c,j,x;
 if sql%rowcount!=1 then raise_application_error(-20001,'mixed row count');end if;
 dbms_lob.writeappend(b,10000,hextoraw(rpad('00FF80',20000,'00FF80')));
 dbms_lob.writeappend(c,5000,rpad('normal-text-',5000,'normal-text-'));
 dbms_lob.writeappend(j,5012,'{{"value":"'||rpad('j',5000,'j')||'"}}');
 dbms_lob.writeappend(x,5007,'<r>'||rpad('x',5000,'x')||'</r>');
end;
/
commit;
select 'SCALAR|'||ID||'|'||TXT||'|'||rawtohex(BIN)||'|'||to_char(DT,'YYYY-MM-DD HH24:MI:SS')||'|'||to_char(TS,'YYYY-MM-DD HH24:MI:SS.FF6') from ORKLAB.{table};
""")
        for col in ['B','C','J','X']:
            binary=col=='B'
            piece='dbms_lob.substr(v,1000,1+i*1000)' if binary else "utl_i18n.string_to_raw(dbms_lob.substr(v,1000,1+i*1000),'AL32UTF8')"
            sql(table+'-truth-'+col,f"alter session set container=ORKPDB;\nset serveroutput on size unlimited\ndeclare v {'blob' if binary else 'clob'};n number;begin select {col} into v from ORKLAB.{table} where ID=1;n:=dbms_lob.getlength(v);dbms_output.put_line('LENGTH|'||n);for i in 0..trunc((n-1)/1000) loop dbms_output.put_line('HEX|'||i||'|'||rawtohex({piece}));end loop;end;\n/\n")
    last=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    end=int(next(l.split('|')[1] for l in last.splitlines() if l.startswith('CURRENT|')))
    archives=[]
    for seq in range(first,end+1):
        out=sql('archive-'+str(seq),f"select 'ARCHIVE|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('ARCHIVE|'));assert len(paths)==1
        path=next(iter(paths));local=N/(str(seq)+'.raw');run('copy-'+str(seq),['docker','cp',S+':'+path,local])
        assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),bytes=local.stat().st_size,sha256=sha(local)))
    (N/'archives.json').write_text(json.dumps(archives,indent=2))
    print('MIXED_SOURCE_FROZEN_NO_REPLAY',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
