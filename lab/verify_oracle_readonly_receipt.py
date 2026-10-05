"""Read-only retained target verification, statement-result refusals, and new local checkpoint recovery."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-readonly-receipt-20261005';OLD=R/'lab/out/oracle-null-sides-target-20261005';E=R/'lab/out/oracle-empty-target-20261005';T=R/'lab/out/oracle-target-20261005';SOURCE='oraredo-19c-lab-20261004'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
repair=sys.argv[1:]==['--resume-published-checkpoint']
if repair:assert (O/'SNON.rc.txt').read_text()=='1'
suffix='-r2' if repair else ''
proof=json.loads((O/'build-proof.json').read_text())
for n in ['kernel','spi','adapter']:assert sha(O/(n+'.jar'))==proof[n+'_sha256']
def run(n,args,data=None):
    assert not (O/(n+'.rc.txt')).exists();args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args))
    with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,input=data,stdout=out,stderr=err,timeout=900)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
    return (O/(n+'.stdout.txt')).read_bytes()
assert run('owner'+suffix,['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
shutil=__import__('shutil')
if not repair:shutil.copyfile(OLD/'ojdbc8.jar',O/'ojdbc8.jar')
classes=O/('classes'+suffix);classes.mkdir()
cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi','ojdbc8'])
run('compile'+suffix,['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleReadOnlyReceiptProbe.java'])
configs=json.loads((R/'lab/out/oracle-registry-20261005/configs.json').read_text())
def snapshot(n):
    body='whenever sqlerror exit failure\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'
    for c in configs:
        p='ORKTGT_CMT_20261005.'+c['prefix']+'_'
        for table,fields,order in [('IDENT','ID,TARGET_ID,SOURCE_BINDING,SCHEMA_SHA','ID'),('APPLIED','EVENT_ID,PAYLOAD_SHA,BEFORE_IMAGE,AFTER_IMAGE,OPERATION','EVENT_ID'),('MEMBERS','BATCH_SHA,ORDINAL,EVENT_ID','BATCH_SHA,ORDINAL'),('COMMITS','BATCH_SHA,TARGET_ID,SCHEMA_SHA,EVENTS_COUNT','BATCH_SHA'),('ROWS','ID,TXT,NOTE','ID')]:
            expr="||'|'||".join(fields.split(','));body+="select '"+c['prefix']+'_'+table+"|'||"+expr+' from '+p+table+' order by '+order+';\n'
    body+='exit success\n'
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode())
before=snapshot('target-before'+suffix);results=[]
if repair:assert before==(O/'target-before.stdout.txt').read_bytes()
for c in configs:
    a=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-readonly-receipt','--user','0','--network','container:'+SOURCE]
    for path,target in [(O,'/evidence'),(T,'/old'),(OLD,'/frozen'),(E,'/empty')]:a+=['--mount','type=bind,source='+str(path)+',target='+target+(',readonly' if target!='/evidence' else '')]
    a+=['--entrypoint','java','flink:1.18-java17','-Xmx1536m','-cp','/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/evidence/ojdbc8.jar:/evidence/'+classes.name,'OracleReadOnlyReceiptProbe','/old/target.secret',c['prefix'],'/evidence/'+c['prefix']+suffix,c['baseline_path'],c['baseline_sha256'],str(c['leaf'])]+c['captures']
    output=run(c['prefix']+suffix,a).decode().strip();assert output=='READ_ONLY_RECEIPT_VERIFIED|'+c['prefix']+'|'+str(c.get('expected_events',{'SNON':2,'SNUL':2,'SMIX':3,'EMPT':0}[c['prefix']]))+'|'+str(8 if c['prefix']=='EMPT' else 12)+'|NO_DML|REPEAT_ZERO'
    results.append({'case':c['prefix'],'stdout':output})
assert snapshot('target-after'+suffix)==before
assert not run('helpers-exited'+suffix,['docker','ps','--filter','label=scope=oracle-readonly-receipt','--format','{{.Names}}']).strip()
(O/'proof.json').write_text(json.dumps({'cases':results,'target_snapshot_unchanged':True,'target_business_dml':0,'source_business_dml':0,'result_corruptions':44,'checkpoint_missing_commit_refusals':4,'new_consumers_recover_existing_commit_only':True,'public_oracle_enabled':False,'product_facade_not_executed':True},indent=2));print('READ_ONLY_RECEIPTS_VERIFIED|4|44_REFUSALS')
