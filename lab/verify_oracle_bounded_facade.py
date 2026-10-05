"""First real Facade runtime on owned controls/new targets; never rerun frozen source DML."""
import hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-bounded-runtime-20261005';T=R/'lab/out/oracle-target-20261005';F=R/'lab/out/oracle-null-sides-target-20261005';E=R/'lab/out/oracle-empty-target-20261005';SOURCE='oraredo-19c-lab-20261004'
assert json.loads((O/'build-proof.json').read_text())['tests']==dict(tests=723,errors=0,failures=0,skipped=0)
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
isolated=sys.argv[1:]==['--isolated-control']
resume=sys.argv[1:]==['--resume-runtime'] or isolated;suffix='-r3' if isolated else '-r2' if resume else ''
if resume:assert (O/'runtime-facade.rc.txt').read_text()=='1'
def run(name,args,data=None):
    args=list(map(str,args));assert not (O/(name+'.rc.txt')).exists();(O/(name+'.command.json')).write_text(json.dumps(args))
    with (O/(name+'.stdout.txt')).open('wb') as out,(O/(name+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,input=data,stdout=out,stderr=err,timeout=1800)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name
    return (O/(name+'.stdout.txt')).read_bytes()
def sql(name,text):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(name+'.sql')).write_text(body)
    return run(name,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode())
assert run('runtime-owner'+suffix,['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\n"
before=sql('runtime-source-before'+suffix,source_sql)
prefixes=['RTNON','RTNUL','RTMIX','RTEMP'];old=['PCNON','PCNUL','PCMIX','PCEMP'];owner='ORKTGT_CMT_20261005.'
if not resume:
    assert b'COUNT|0' in sql('runtime-target-preflight',"select 'COUNT|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and substr(table_name,1,instr(table_name,'_')-1) in ("+','.join("'"+p+"'" for p in prefixes)+');')
    ddl=''
    for prefix,previous in zip(prefixes,old):
        t=owner+prefix;o=owner+previous
        ddl+='create table '+t+'_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n'
        ddl+="insert into "+t+"_IDENT select ID,'oracle-dml-"+prefix+"-20261005',SOURCE_BINDING,SCHEMA_SHA from "+o+'_IDENT;\n'
        ddl+='create table '+t+'_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\ninsert into '+t+'_ROWS select ID,TXT,NOTE from '+o+'_ROWS;\n'
        ddl+='create table '+t+'_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n'
        ddl+='create table '+t+'_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references '+t+'_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n'
        ddl+='create table '+t+'_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\n'
    sql('runtime-create-owned-targets',ddl+'commit;')
shutil.copyfile(R/'lab/out/oracle-control-publication-20261005/control.secret.json',O/'control.secret.json')
libs=O/'runtime-libs';libs.mkdir(exist_ok=resume);classes=O/'runtime-classes';classes.mkdir(exist_ok=resume)
with zipfile.ZipFile(P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar') as z:
    for name in z.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar') and not Path(name).name.startswith('streamhouse-'):(libs/Path(name).name).write_bytes(z.read(name))
# The adapter is also named streamhouse-*; copy the pinned binary explicitly.
shutil.copyfile(R/'lab/out/oracle-readonly-receipt-20261005/adapter.jar',libs/'oracle-adapter.jar')
modules=[d.name for d in (P/'server').iterdir() if (d/'target/classes').exists()]
cp=';'.join([str(libs/'*')]+[str(P/'server'/m/'target/classes') for m in modules])
run('runtime-compile'+suffix,['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleRegistryDbProbe.java',R/'lab/OracleBoundedFacadeProbe.java'])
manifest=[]
for root in [classes,libs]+[P/'server'/m/'target/classes' for m in modules]:
    for f in sorted(root.rglob('*')):
        if f.is_file():manifest.append(dict(path=str(f.relative_to(R)),bytes=f.stat().st_size,sha256=hashlib.sha256(f.read_bytes()).hexdigest()))
(O/('runtime-execution'+suffix+'.json')).write_text(json.dumps(dict(product_commit=json.loads((O/'build-proof.json').read_text())['product_commit'],driver_sha256=hashlib.sha256((R/'lab/OracleBoundedFacadeProbe.java').read_bytes()).hexdigest(),files=manifest),indent=2))
a=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-real-facade','--user','0','--network','container:'+SOURCE]
for path,target in [(O,'/evidence'),(P/'server','/product'),(T,'/target'),(F,'/frozen'),(E,'/empty')]:a+=['--mount','type=bind,source='+str(path)+',target='+target+(',readonly' if target!='/evidence' else '')]
linuxcp=':'.join(['/evidence/runtime-classes','/evidence/runtime-libs/*']+['/product/'+m+'/target/classes' for m in modules])
run('runtime-facade'+suffix,a+['--entrypoint','java','flink:1.18-java17','-Xmx2048m','-cp',linuxcp,'OracleBoundedFacadeProbe','/evidence/runtime-control.secret.json' if isolated else '/evidence/control.secret.json','/target/target.secret','/evidence/acks','/evidence/runtime-proof.json','/evidence/control.secret.json']+(['--resume'] if resume else []))
for name,prefix in zip(['SNON','SNUL','SMIX','EMPT'],prefixes):
    t=owner+prefix+'_';body="select 'STATE|'||(select count(*) from "+t+"APPLIED)||'|'||(select count(*) from "+t+"MEMBERS)||'|'||(select count(*) from "+t+'COMMITS) from dual;\n'
    for table,fields,order in [('IDENT','ID,TARGET_ID,SOURCE_BINDING,SCHEMA_SHA','ID'),('APPLIED','EVENT_ID,PAYLOAD_SHA,BEFORE_IMAGE,AFTER_IMAGE,OPERATION','EVENT_ID'),('MEMBERS','BATCH_SHA,ORDINAL,EVENT_ID','BATCH_SHA,ORDINAL'),('COMMITS','BATCH_SHA,TARGET_ID,SCHEMA_SHA,EVENTS_COUNT','BATCH_SHA'),('ROWS','ID,TXT,NOTE','ID')]:
        body+="select '"+table+"|'||"+"||'|'||".join(fields.split(','))+' from '+t+table+' order by '+order+';\n'
    sql(prefix+'-final',body)
    source='oracle-runtime-'+name;schema='ORKRUNTIME_20261005.' if isolated else 'ORKPUB_NEW_20261005.'
    body='set long 200000 longchunksize 200000\n'
    body+="select 'CONTROL|'||b.ID||'|'||b.STATE||'|'||b.INPUT_SHA256||'|'||b.ISSUE_SEQ_START||'|'||b.ISSUE_SEQ_END||'|'||b.EVENT_COUNT from "+schema+"SH_PIPELINE_BATCH b where b.SOURCE_ID='"+source+"' and b.STATE in ('PUBLISHED','EMPTY');\n"
    body+="select 'CHECKPOINT|'||CHECKPOINT_JSON from "+schema+"SH_TASK where REF_TYPE='SOURCE' and REF_ID='"+source+"';\n"
    body+="select 'TASK|'||STATE from "+schema+"SH_TASK where REF_TYPE='SOURCE' and REF_ID='"+source+"';\n"
    body+="select 'WINDOW|'||STATUS||'|'||FROZEN||'|'||DROPPED_SEGMENTS from "+schema+"SH_CAP_WINDOW where SOURCE_ID='"+source+"';\n"
    body+="select 'LEDGER|'||EVENT_ID||'|'||SOURCE_ID||'|'||TABLE_NAME||'|'||OPERATION||'|'||BATCH_ID from "+schema+"SH_EVENT_LEDGER where SOURCE_ID='"+source+"' order by EVENT_ID;\n"
    body+="select 'GATE|'||g.VERDICT from "+schema+"SH_PUBLISH_GATE g join "+schema+"SH_PIPELINE_BATCH b on b.ID=g.BATCH_ID where b.SOURCE_ID='"+source+"';\n"
    sql(name+'-control-final',body)
assert sql('runtime-source-after'+suffix,source_sql)==before
assert not run('runtime-helpers-exited'+suffix,['docker','ps','--filter','label=scope=oracle-real-facade','--format','{{.Names}}']).strip()
print('ORACLE_BOUNDED_REAL_FACADE_VERIFIED|4')
