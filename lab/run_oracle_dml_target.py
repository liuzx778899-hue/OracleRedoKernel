"""New owned targets consume retained nonnull/null/mixed windows; source DML never repeated."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-dml-target-20261005';T=R/'lab/out/oracle-target-20261005';SOURCE='oraredo-19c-lab-20261004'
configs=json.loads((O/'config.json').read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None,rc=0):
    assert not (O/(n+'.rc.txt')).exists(),n
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=data,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,(n,p.stderr)
    return p
def sql(n,text):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off echo off\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(n+'.sql')).write_text(body)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode()).stdout.decode()
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|running'
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\n"+'\n'.join("select 'SOURCE|"+table+"|'||ID||'|'||TXT||'|'||NOTE from ORKLAB."+table+' order by ID;' for table in ['T_DML_CAPTURE_20261005','T_DML_NULL_20261005','T_DML_MIX_20261005'])
source_before=sql('source-before',source_sql);assert 'CONFIG|1276726821|1276717861|NO|NO' in source_before and 'OLD|11|blocking-closed|new-note' in source_before
fault=dict(configs[-1]);fault['prefix']='DFAIL';all_configs=configs+[fault]
prefixes=[c['prefix'] for c in all_configs]
assert 'COUNT|0' in sql('target-preflight',"select 'COUNT|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and substr(table_name,1,instr(table_name,'_')-1) in ("+','.join("'"+p+"'" for p in prefixes)+');')
old=(T/'target-setup-password29.sql.redacted').read_text();template=old[old.index('create table ORKTGT_CMT_20261005.NORM_IDENT'):old.index('create table ORKTGT_CMT_20261005.PRE_IDENT')]
ddl=''
for c in all_configs:
    prefix=c['prefix'];schema=c['windows'][0][2]
    part=template.replace('NORM',prefix).replace('oracle-target-'+prefix+'-20261005','oracle-dml-'+prefix+'-20261005').replace('b38658100a383c1a12ce0f2d560757864458261c71119102622acbdbcf07d3ce',schema).replace("values(11,'blocking-closed','new-note')","values(0,'"+c['seed']+"','anchor')")
    if prefix=='DFAIL':part += "alter table ORKTGT_CMT_20261005.DFAIL_ROWS add constraint DFAIL_UPDATE_CHECK check (TXT <> 'mixed-after');\n"
    ddl+=part
sql('target-20-table-setup',ddl+'commit;')
def command(c,count):
    prefix=c['prefix'];folder=prefix if prefix!='DFAIL' else 'DMIX';d=O/prefix
    if not d.exists():d.mkdir()
    a=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-dml-target','--user','0','--network','container:'+SOURCE,'--mount','type=bind,source='+str(T)+',target=/old,readonly','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/evidence/ojdbc8.jar:/evidence/classes','OracleDmlCheckpointWorker','consume','/old/target.secret','/evidence/'+prefix,prefix,'/evidence/'+folder+'/baseline/snapshot.stdout.txt',c['baseline_sha256'],c['leaf'],str(count)]
    for f,h in zip(c['captures'][::2],c['captures'][1::2]):assert sha(Path(f))==h;a += ['/evidence/'+folder+'/'+Path(f).name,h]
    return a
def state(n,p):
    own='ORKTGT_CMT_20261005.'+p+'_'
    return sql(n,"select 'STATE|'||(select count(*) from "+own+'APPLIED)||\'|\'||(select count(*) from '+own+'MEMBERS)||\'|\'||(select count(*) from '+own+'COMMITS) from dual;\n'+"select 'ROW|'||ID||'|'||TXT||'|'||NOTE from "+own+'ROWS order by ID;\n'+"select 'APPLIED|'||EVENT_ID||'|'||PAYLOAD_SHA||'|'||BEFORE_IMAGE||'|'||AFTER_IMAGE||'|'||OPERATION from "+own+'APPLIED order by EVENT_ID;\n'+"select 'MEMBER|'||BATCH_SHA||'|'||ORDINAL||'|'||EVENT_ID from "+own+'MEMBERS order by BATCH_SHA,ORDINAL;\n'+"select 'RECEIPT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from "+own+'COMMITS order by BATCH_SHA;')
results=[]
for c in configs:
    prefix=c['prefix'];previous=0;new=[]
    for i,w in enumerate(c['windows'],1):
        expected=int(w[3])-previous;previous=int(w[3]);p=run(prefix+'-consume-'+str(i),command(c,i))
        assert p.stdout.decode()==f'CONSUMED|{w[4]}|{w[5]}|{w[6]}|{expected}|{i}\n';new.append(expected)
        state(prefix+'-state-'+str(i),prefix)
    checkpoint=(O/prefix/'checkpoint').read_bytes()
    for i,w in enumerate(c['windows'],1):
        p=run(prefix+'-repeat-'+str(i),command(c,i));last=c['windows'][-1]
        assert p.stdout.decode()==f'CONSUMED|{last[4]}|{last[5]}|{last[6]}|0|{len(c["windows"])}\n'
        assert (O/prefix/'checkpoint').read_bytes()==checkpoint
    state(prefix+'-final',prefix);results.append({'prefix':prefix,'new_applications':new,'repeats_all_zero':True})
p=run('DFAIL-update-check-refused',command(fault,3),rc=1);assert not p.stdout and b'ORA-02290' in p.stderr
assert 'STATE|0|0|0' in state('DFAIL-rolled-back','DFAIL')
assert not (O/'DFAIL/checkpoint').exists() and not list((O/'DFAIL').glob('*.ack'))
sql('DFAIL-remove-owned-check','alter table ORKTGT_CMT_20261005.DFAIL_ROWS drop constraint DFAIL_UPDATE_CHECK;')
w=fault['windows'][-1];assert run('DFAIL-resume',command(fault,3)).stdout.decode()==f'CONSUMED|{w[4]}|{w[5]}|{w[6]}|3|1\n'
assert run('DFAIL-repeat',command(fault,3)).stdout.decode()==f'CONSUMED|{w[4]}|{w[5]}|{w[6]}|0|1\n';state('DFAIL-final','DFAIL')
assert sql('source-after',source_sql)==source_before
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-dml-target','--format','{{.Names}}']).stdout.strip()
(O/'target-proof.json').write_text(json.dumps({'results':results,'target_mid_update_failure_atomic_rollback':True,'fault_resume_new_applications':3,'source_dml':0,'source_config_changes':0,'owned_new_target_tables':20,'standalone_49_50_refused':True,'independent_verification_pending':True},indent=2));print(json.dumps(results))
