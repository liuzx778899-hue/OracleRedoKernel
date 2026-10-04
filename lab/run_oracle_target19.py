"""One-shot real Oracle target commit/ACK experiment. Never replays source DML.

New owned target schema in the dedicated lab only. Secrets stay in ignored local
storage and are redacted from SQL/output evidence. Helpers kill only Java clients.
"""
import hashlib,json,secrets,shutil,subprocess,sys,time
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/oracle-target-20261005'
SOURCE='oraredo-19c-lab-20261004';USER='ORKTGT_CMT_20261005';IMAGE='flink:1.18-java17'
OLD=ROOT/'lab/out/closed-follow-20261005';BUILD=ROOT/'lab/out/release-oracle-target-20261005'
SPI=ROOT/'lab/out/release-oracle-projection-20261005';KERNEL=ROOT/'lab/out/captured-batch-20261005'
ack_continuing=sys.argv[1:]==['--resume-after-ack-newline']
continuing=ack_continuing or sys.argv[1:]==['--resume-after-password-length']
if not continuing and sys.argv[1:]!=['--execute']:raise RuntimeError('explicit one-shot --execute required')
if not continuing:OUT.mkdir(parents=True,exist_ok=False)
elif not ack_continuing:
    assert (OUT/'target-setup.rc.txt').read_text()=='1' and 'ORA-00972' in (OUT/'target-setup.stdout.txt').read_text()
    shutil.copyfile(OUT/'target.secret',OUT/'initial-unused-target.secret')
else:
    assert (OUT/'ack-without-commit.rc.txt').read_text()=='1' and 'ORACLE_TARGET_ACK_CONFLICT' in (OUT/'ack-without-commit.stderr.txt').read_text()
password=(OUT/'target.secret').read_text().splitlines()[1] if ack_continuing else 'OrkA_'+secrets.token_hex(12)


def run(name,args,input_data=None,expected=0):
    args=list(map(str,args));(OUT/(name+'.command.json')).write_text(json.dumps(args))
    result=subprocess.run(args,input=input_data,capture_output=True,timeout=180)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:
        (OUT/(name+'.'+ext+'.txt')).write_bytes(data.replace(password.encode(),b'<redacted-secret>'))
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode));assert result.returncode==expected,name
    return result


def sql(name,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off echo off\n'+body+'\nexit success\n'
    (OUT/(name+'.sql.redacted')).write_text(text.replace(password,'<target-secret>'))
    return run(name,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],text.encode()).stdout.decode()


if not continuing:
    assert run('source-binding',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
    inventory=sql('source-before',"declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and resetlogs_change#=1920977 and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source identity/config mismatch');end if;end;\n/\nalter session set container=ORKPDB;\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'TARGET_USER_COUNT|'||count(*) from dba_users where username='"+USER+"';\nselect 'SERVICE|'||name from v$services where upper(name) like 'ORKPDB%';")
    assert 'SOURCE_ROW|11|blocking-closed|new-note' in inventory and 'TARGET_USER_COUNT|0' in inventory
    run('helper-image',['docker','image','inspect',IMAGE,'--format','{{.Id}}'])
    run('helper-java17',['docker','run','--rm','--entrypoint','java',IMAGE,'-version'])
    assert b'version "17.' in (OUT/'helper-java17.stderr.txt').read_bytes()
    proof=json.loads((BUILD/'proof.json').read_text())
    for name,path,digest in [('adapter.jar',BUILD/'streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',proof['adapter_jar_sha256']),
                             ('spi.jar',SPI/'streamhouse-spi-0.1.0-SNAPSHOT.jar',proof['spi_contract_jar_sha256']),
                             ('kernel.jar',KERNEL/'kernel.jar',proof['kernel_sha256'])]:
        assert hashlib.sha256(path.read_bytes()).hexdigest()==digest;shutil.copyfile(path,OUT/name)
    run('oracle-driver-copy',['docker','cp',SOURCE+':/opt/oracle/product/19c/dbhome_1/jdbc/lib/ojdbc8.jar',OUT/'ojdbc8.jar'])
    run('oracle-driver-source-sha',['docker','exec',SOURCE,'sha256sum','/opt/oracle/product/19c/dbhome_1/jdbc/lib/ojdbc8.jar'])
    assert hashlib.sha256((OUT/'ojdbc8.jar').read_bytes()).hexdigest() in (OUT/'oracle-driver-source-sha.stdout.txt').read_text()
    shutil.copyfile(OLD/'baseline/snapshot.stdout.txt',OUT/'snapshot.txt')
    digest='646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf';assert hashlib.sha256((OUT/'snapshot.txt').read_bytes()).hexdigest()==digest
    pairs=[]
    for s in [40,41,42]:
        path=OUT/('sequence-'+str(s)+'.oracap');shutil.copyfile(OLD/'capture'/path.name,path);pairs.extend(['/evidence/'+path.name,hashlib.sha256(path.read_bytes()).hexdigest()])
    classes=OUT/'classes';classes.mkdir();classpath=';'.join(str(OUT/name) for name in ['adapter.jar','spi.jar','kernel.jar','ojdbc8.jar'])
    run('compile-worker',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',classpath,'-d',classes,ROOT/'lab/OracleTargetWorker.java'])
else:
    assert run('resume-source-binding',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|running'
    check=sql('resume-ack-user-preflight' if ack_continuing else 'resume-user-preflight',"alter session set container=ORKPDB;\nselect 'TARGET_USER_COUNT|'||count(*) from dba_users where username='"+USER+"';")
    assert ('TARGET_USER_COUNT|1' if ack_continuing else 'TARGET_USER_COUNT|0') in check
    pairs=[]
    for s in [40,41,42]:
        path=OUT/('sequence-'+str(s)+'.oracap');pairs.extend(['/evidence/'+path.name,hashlib.sha256(path.read_bytes()).hexdigest()])
    digest='646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf'
if not ack_continuing:(OUT/'target.secret').write_text(USER+'\n'+password+'\njdbc:oracle:thin:@//127.0.0.1:1521/orkpdb\n')
batch='b6658e92d88d4a910a5a4b4870189b6ba8fa4d30873ff715f08e35dd3d15aeb8';schema='b38658100a383c1a12ce0f2d560757864458261c71119102622acbdbcf07d3ce'
source_key='1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3'
prefixes=['NORM','PRE','POST','ACKD','LATE','FACK','IDBAD']
ddl='alter session set container=ORKPDB;\ncreate user '+USER+' identified by "'+password+'" default tablespace USERS quota 64M on USERS;\ngrant create session,create table to '+USER+';\n'
for p in prefixes:
    t=USER+'.'+p;target_id='oracle-target-'+p+'-20261005'
    ddl+='create table '+t+"_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n"
    ddl+='insert into '+t+"_IDENT values(1,'"+target_id+"','"+source_key+"','"+schema+"');\n"
    ddl+='create table '+t+"_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\n"
    ddl+='insert into '+t+"_ROWS values(11,'blocking-closed','new-note');\n"
    ddl+='create table '+t+"_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n"
    ddl+='create table '+t+"_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references "+t+"_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n"
    ddl+='create table '+t+"_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\n"
ddl+='commit;'
if not ack_continuing:sql('target-setup-password29' if continuing else 'target-setup',ddl)


def args(name,p,stage='none',foreign=False,detached=False):
    target_id='oracle-target-'+p+'-20261005'+('-foreign' if foreign else '')
    values=['docker','run','--name',name,'--label','owner=OracleRedoKernel','--label','scope=oracle-target-commit',
            '--user','0','--network','container:'+SOURCE,'--mount','type=bind,source='+str(OUT)+',target=/evidence',
            '--entrypoint','java']
    values+=['-d'] if detached else ['--rm']
    return values+[IMAGE,'-cp','/evidence/adapter.jar:/evidence/spi.jar:/evidence/kernel.jar:/evidence/ojdbc8.jar:/evidence/classes',
                   'OracleTargetWorker','/evidence/target.secret',p,target_id,'/evidence/'+p+'.ack',stage,'/evidence/'+p+'.ready',
                   '/evidence/snapshot.txt',digest,'0x0300019b']+pairs


def state(name,p,applications,commits):
    t=USER+'.'+p
    body="alter session set container=ORKPDB;\nselect 'STATE|"+p+"|'||(select count(*) from "+t+"_APPLIED)||'|'||(select count(*) from "+t+"_MEMBERS)||'|'||(select count(*) from "+t+"_COMMITS) from dual;\n"
    body+="select 'ROW|"+p+"|'||ID||'|'||TXT||'|'||NOTE from "+t+"_ROWS order by ID;\n"
    body+="select 'APPLIED|"+p+"|'||EVENT_ID||'|'||PAYLOAD_SHA||'|'||BEFORE_IMAGE||'|'||AFTER_IMAGE||'|'||OPERATION from "+t+"_APPLIED order by EVENT_ID;\n"
    body+="select 'MEMBER|"+p+"|'||BATCH_SHA||'|'||ORDINAL||'|'||EVENT_ID from "+t+"_MEMBERS order by ORDINAL;\n"
    body+="select 'RECEIPT|"+p+"|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from "+t+"_COMMITS order by BATCH_SHA;\n"
    text=sql(name,body);assert 'STATE|'+p+'|'+str(applications)+'|'+str(applications)+'|'+str(commits) in text,name
    assert 'ROW|'+p+'|11|blocking-closed|new-note' in text,name


if not ack_continuing:
    run('normal',args('ork-oratgt-normal-20261005','NORM'));state('normal-state','NORM',3,1)
    for p,stage in [('PRE','before-commit'),('POST','after-commit'),('ACKD','after-ack')]:
        name='ork-oratgt-'+p.lower()+'-20261005';cid=run(p+'-start',args(name,p,stage,detached=True)).stdout.decode().strip()
        (OUT/(p+'.helper.json')).write_text(json.dumps({'id':cid,'name':name,'stage':stage}))
        deadline=time.monotonic()+90
        while not (OUT/(p+'.ready')).exists() and time.monotonic()<deadline:
            if subprocess.check_output(['docker','inspect',cid,'--format','{{.State.Running}}']).decode().strip()!='true':
                run(p+'-early-logs',['docker','logs',cid]);raise RuntimeError('helper terminal before pause: '+p)
            time.sleep(.1)
        assert (OUT/(p+'.ready')).read_text()==stage,p
        assert run(p+'-ownership',['docker','inspect',cid,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).stdout.decode().strip()=='OracleRedoKernel|oracle-target-commit'
        if p=='PRE':
            second=run('second-writer',args('ork-oratgt-second-20261005',p),expected=1)
            assert not second.stdout and b'ORA-00054' in second.stderr
        run(p+'-kill',['docker','kill',cid]);run(p+'-wait',['docker','wait',cid])
        assert (OUT/(p+'-wait.stdout.txt')).read_text().strip()=='137'
        logs=run(p+'-logs',['docker','logs',cid]);assert not logs.stdout
        run(p+'-remove',['docker','rm',cid])
        state(p+'-interrupted',p,0 if p=='PRE' else 3,0 if p=='PRE' else 1)
        assert (OUT/(p+'.ack')).exists()==(p=='ACKD')
        resumed=run(p+'-resume',args('ork-oratgt-'+p.lower()+'-resume-20261005',p))
        assert ('COMMITTED|'+('3' if p=='PRE' else '0')+'|').encode() in resumed.stdout
        state(p+'-final',p,3,1)
    for p in ['NORM','PRE','POST','ACKD']:
        old=(OUT/(p+'.ack')).read_bytes();result=run(p+'-repeat',args('ork-oratgt-'+p.lower()+'-repeat-20261005',p))
        assert b'COMMITTED|0|' in result.stdout and (OUT/(p+'.ack')).read_bytes()==old
        state(p+'-repeat-state',p,3,1)
    late=run('late-before',args('ork-oratgt-late-20261005','LATE','late-before'),expected=1)
    assert not late.stdout and b'ORACLE_TARGET_ROW_MATCH_NOT_ONE' in late.stderr
    state('late-before-state','LATE',0,0);assert not (OUT/'LATE.ack').exists()
else:
    state('FACK-before-lf-repair','FACK',0,0)
    shutil.copyfile(OUT/'FACK.ack',OUT/'FACK.original-crlf-ack.retained')
(OUT/'FACK.ack').write_bytes(('ORK_ORACLE_TARGET_ACK_V1|oracle-target-FACK-20261005|'+batch+'|'+schema+'|3\n').encode())
fake=run('ack-without-commit-lf' if ack_continuing else 'ack-without-commit',args('ork-oratgt-fack-20261005','FACK'),expected=1)
assert not fake.stdout and b'ORACLE_TARGET_ACK_WITHOUT_COMMIT' in fake.stderr;state('ack-without-commit-state','FACK',0,0)
wrong=run('wrong-target',args('ork-oratgt-idbad-20261005','IDBAD',foreign=True),expected=1)
assert not wrong.stdout and b'ORACLE_TARGET_IDENTITY_MISMATCH' in wrong.stderr;state('wrong-target-state','IDBAD',0,0)
original=(OUT/'NORM.ack').read_bytes();(OUT/'NORM.ack').write_bytes(original.replace(b'|3\n',b'|4\n'))
conflict=run('ack-conflict',args('ork-oratgt-ackconflict-20261005','NORM'),expected=1)
assert not conflict.stdout and b'ORACLE_TARGET_ACK_CONFLICT' in conflict.stderr
shutil.copyfile(OUT/'NORM.ack',OUT/'NORM.conflicting-ack.retained');(OUT/'NORM.ack').write_bytes(original)
sql('target-proof-poison',"alter session set container=ORKPDB;\nupdate "+USER+".NORM_APPLIED set AFTER_IMAGE='deliberate-conflict' where EVENT_ID='oracle1:f5e4dd9b7b8b9ffe0064bb7ca6b9612406f0a23ac5830c7ffa49a3451bf2cf50';\ncommit;")
bad=run('target-proof-conflict',args('ork-oratgt-proofconflict-20261005','NORM'),expected=1)
assert not bad.stdout and b'ORACLE_TARGET_EVENT_CONFLICT' in bad.stderr and (OUT/'NORM.ack').read_bytes()==original
state('target-proof-conflict-state','NORM',3,1)
after=sql('source-after',"select 'SOURCE_INSTANCE|'||status from v$instance;\nselect 'SOURCE_CONFIG|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
assert 'SOURCE_INSTANCE|OPEN' in after and 'SOURCE_CONFIG|NO|NO' in after and 'SOURCE_ROW|11|blocking-closed|new-note' in after
proof={'actual_oracle_target':True,'actual_java_client_sigkills':3,'kill_rc':137,'normal_and_three_recovery_targets':4,
       'same_batch_repeat_new_applications':0,'target_refusals':6,'source_business_dml':0,'source_logging_config_changes':0,'source_target_same_dedicated_instance':True,
       'owned_target_schema_created':USER,'target_dml_and_receipt_same_transaction':True,'product_pipeline_accepted':False,
       'public_decoder_enabled':False,'power_loss_accepted':False,'secret_excluded_from_evidence':True}
(OUT/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
