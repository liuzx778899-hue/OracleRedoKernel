"""Owned real JDBC target application from the already frozen raw input; no source DML."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent
BASE=R/'lab/out/common-fields-20261010';S='oraredo-19c-lab-20261004';H='ork-product-runtime'
N=BASE/'target-r1';SOURCE=BASE/'source'
assert len(sys.argv)==3 and sys.argv[1]=='--execute' and re.fullmatch(r'candidate-r[1-9][0-9]*',sys.argv[2])
C=BASE/sys.argv[2];V=BASE/('verify-r'+C.name.split('-r')[1]);assert (V/'proof.json').exists()
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
secret=R/'lab/out/d4-lob-product-20261010/runtime.secret.env'
props=dict(line.split('=',1) for line in secret.read_text().splitlines() if '=' in line)
owner=props['SPRING_DATASOURCE_USERNAME'];assert owner=='ORKLOBSCTL_20261010'
shutil.copyfile(secret,N/'target.secret.env')
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
candidate=json.loads((C/'development-candidate.json').read_text())
assert sha(C/'kernel.jar')==candidate['kernelSha256'] and sha(C/'adapter.jar')==candidate['adapterSha256']

def run(label,args,data=None):
    assert not (N/(label+'.rc')).exists()
    (N/(label+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=300)
    for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:
        for password in [reader[1],props['SPRING_DATASOURCE_PASSWORD']]:value=value.replace(password.encode(),b'<redacted-secret>')
        (N/(label+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(label,p.returncode)
    return p.stdout.decode()

def sql(label,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'+body+'\nexit success\n'
    (N/(label+'.sql')).write_text(text)
    out=run(label,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode())
    assert 'SP2-' not in out
    return out

classes=N/'classes';classes.mkdir()
cp=os.pathsep.join(map(str,[C/'kernel.jar',C/'adapter.jar',SOURCE/'libs/*',classes]))
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/CommonFieldsTargetProbe.java'])
snapshots=json.loads((SOURCE/'snapshots.json').read_text());raw=SOURCE/'resume-r2/104.raw'
assert sha(raw)=='6658fce9c8f41e836e901c39954469abadf45926f1a756658f46ccd18fdd6290'
proofs=[];started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||count(*) from ORKLAB.T_D4_MIX_S_20261010;")
            if 'READY|1' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'READY|1' in out
    for kind in ['B','S']:
        prefix='CMIX'+kind+'_20261010';t=owner+'.'+prefix;table='T_D4_MIX_'+kind+'_20261010'
        snapshot=snapshots[table];assert sha(snapshot['path'])==snapshot['sha256']
        prep=json.loads((V/(kind+'.stdout')).read_text());e=prep['evidence']
        binding=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
        scn=int(next(line.split('|')[1] for line in Path(snapshot['path']).read_text().splitlines() if line.startswith('SNAPSHOT_SCN|')))
        assert 'FREE|0' in sql(kind+'-free',f"select 'FREE|'||count(*) from dba_tables where owner='{owner}' and table_name like '{prefix}\\_%' escape '\\';")
        sql(kind+'-setup',f"""
create table {t}_ROWS(ID number primary key,TXT varchar2(128),BIN raw(16),DT date,TS timestamp(6),B blob,C clob,J clob,X clob);
insert into {t}_ROWS select ID,TXT,BIN,DT,TS,B,C,J,X from ORKLAB.{table} as of scn {scn};
create table {t}_IDENT(ID number primary key,TARGET_ID varchar2(128),SOURCE_BINDING varchar2(512),SCHEMA_SHA varchar2(64));
insert into {t}_IDENT values(1,'oracle-mixed-{prefix}','{binding}','{e['schemaSha256']}');
create table {t}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64),BEFORE_IMAGE clob,AFTER_IMAGE clob,OPERATION varchar2(16));
create table {t}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {t}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));
create table {t}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128),SCHEMA_SHA varchar2(64),EVENTS_COUNT number);
commit;
""")
        command=['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(BASE)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
            '-cp',f'/evidence/{C.name}/kernel.jar:/evidence/{C.name}/adapter.jar:/evidence/source/libs/*:/evidence/target-r1/classes','CommonFieldsTargetProbe',
            '/evidence/source/'+Path(snapshot['path']).name,snapshot['sha256'],'/evidence/source/resume-r2/104.raw',sha(raw),'/evidence/target-r1/target.secret.env',owner,prefix]
        out=run(kind+'-target',command);assert 'MIXED_TARGET_PASS|1|0|' in out
        actual=sql(kind+'-actual',f"""
select 'COUNTS|'||(select count(*) from {t}_APPLIED)||'|'||(select count(*) from {t}_MEMBERS)||'|'||(select count(*) from {t}_COMMITS) from dual;
select 'MATCH|'||count(*) from {t}_ROWS t join ORKLAB.{table} s on s.ID=t.ID where t.TXT=s.TXT and t.BIN=s.BIN and t.DT=s.DT and t.TS=s.TS
and dbms_lob.compare(t.B,s.B)=0 and dbms_lob.getlength(t.B)=dbms_lob.getlength(s.B)
and dbms_lob.compare(t.C,s.C)=0 and dbms_lob.getlength(t.C)=dbms_lob.getlength(s.C)
and dbms_lob.compare(t.J,s.J)=0 and dbms_lob.getlength(t.J)=dbms_lob.getlength(s.J)
and dbms_lob.compare(t.X,s.X)=0 and dbms_lob.getlength(t.X)=dbms_lob.getlength(s.X);
""")
        assert 'COUNTS|1|1|1' in actual and 'MATCH|1' in actual
        proofs.append(dict(storage=kind,target=[1,1,1],columns=9,completeRowDifferences=0,newApplications=1,retryApplications=0,independentCommitVerified=True))
    (N/'proof.json').write_text(json.dumps(dict(kernelSha256=candidate['kernelSha256'],adapterSha256=candidate['adapterSha256'],sourceDml=0,sqlUsedAsEventInput=False,scenarios=proofs,streamhouseCPAckVerified=False),indent=2))
    print('MIXED_JDBC_TARGET_PASS',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
