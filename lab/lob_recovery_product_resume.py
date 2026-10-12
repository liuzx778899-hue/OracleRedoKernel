"""Resume the refused owned fixture after metadata serialization correction; no source DML."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/lob-unified-20261012/product-runtime-r1'
S='oraredo-19c-lab-20261004';H='ork-product-runtime';SOURCE='oracle-lob-recovery-20261012';U='ORKLRCTL_20261012'
assert sys.argv[1:]==['--execute'] and (N/'publish-0.rc').read_text().strip()=='1' and not (N/'cut-0.json').exists()
shutil.copyfile(__file__,N/'executed-resume.py')
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines());passwords=[v.encode() for k,v in props.items() if 'PASSWORD' in k]
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
common=(R/'lab/common_fields_product.py').read_text();exec(common[common.index('def run('):common.index("run('compile'")])
originalRun=run
def run(label,args,data=None):return originalRun('resume-'+label,args,data)
cp=os.pathsep.join(map(str,[N/'classes',N/'appclasses',N/'libs/*']))
def prepare(label,baseline,leaf,names):
    return json.loads(run(label,['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'LobRecoveryProductProbe',baseline,sha(baseline),leaf,*[N/'inputs'/n for n in names]]))
def product(label,window,removed,resume=False):
    args=[SOURCE,'/evidence/inputs','/evidence/'+label+'.json',str(removed),window]
    if resume:args.append('--resume')
    run(label,['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--label','scope=lob-recovery-normal','--user','0','--network','container:'+S,
        '--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
        '-Xmx1536m','-cp','/evidence/classes:/evidence/appclasses:/evidence/libs/*','LobRecoveryProductMain',*args])
    return json.loads((N/(label+'.json')).read_text())
started=False;T=props['SH_ORACLE_BOUNDED_TARGET_OWNER']+'.LR20261012';proofs=[]
try:
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    state=run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()
    assert state in ['OracleRedoKernel|exited','OracleRedoKernel|running']
    if state.endswith('|exited'):run('source-start',['docker','start',S])
    started=True
    for attempt in range(30):
        try:
            if 'READY|READ WRITE' in sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';"):break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    baseline=N/'snapshots/29801.snapshot';original=R/'lab/out/lob-migration-20261011/source/inputs/120.raw';retained=sha(original)+'.raw'
    prepared=prepare('corrected-preparation',baseline,50380683,[retained]);old=json.loads((N/'initial-preparation.stdout').read_text())
    assert old['evidence']==prepared['evidence'] and old['envelopesSha256']==prepared['envelopesSha256']
    before=sql('before-correction',f"alter session set container=ORKPDB;\nselect 'TARGET_EVENTS|'||count(*) from {T}_APPLIED;\nselect 'CHECKPOINTS|'||count(*) from {U}.SH_TASK where CHECKPOINT_JSON is not null;")
    assert 'TARGET_EVENTS|0' in before and 'CHECKPOINTS|0' in before
    sql('metadata-correction',f"alter session set container=ORKPDB;\nupdate {U}.SH_ORACLE_ACQUISITION set METADATA_JSON="+clob(prepared['metadata'])+f" where ID=29801 and SOURCE_ID='{SOURCE}' and STATE='ACQUIRED_ONLY' and SNAPSHOT_SHA256='{sha(baseline)}';\ncommit;")
    previous=product('publish-0','lob-recovery-0',0,True);proofs.append(previous)
    for step in [1,2]:
        raw=R/f'lab/out/lob-unified-20261012/source/inputs/{120+step}.raw';name=sha(raw)+'.raw';shutil.copyfile(raw,N/'inputs'/name);names=[retained,name]
        cut=previous['cut'];local=N/Path(cut['baselinePath']).relative_to('/evidence');prepared=prepare('prepare-'+str(step),local,cut['leaf'],names);e=prepared['evidence'];window='lob-recovery-'+str(step);seq=step*2
        binding=dict(baselinePath=None,capturePaths=['/evidence/inputs/'+n for n in names],evidence=e,envelopesSha256=prepared['envelopesSha256'],acquisitionId=29801,inputFormat='RAW_MEMBER',readCutId=cut['id'])
        hashes='\n'.join(n+' '+sha(N/'inputs'/n) for n in names)
        sql('enroll-'+str(step),f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values({29810+step*2},'{SOURCE}','{window}',{seq},{seq+1},'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+f",systimestamp,'owned-frozen-normal');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values({29811+step*2},'{SOURCE}','oracle','{window}',{seq},{seq+1},'/evidence/inputs/{names[0]}',1,'{hashes}',{sum((N/'inputs'/n).stat().st_size for n in names)},0,'PENDING',systimestamp);\ncommit;")
        previous=product('publish-'+str(step),window,1);proofs.append(previous);retained=name
        assert not (N/'inputs'/names[0]).exists() and (N/'inputs'/retained).is_file()
        actual=sql('actual-'+str(step),f"alter session set container=ORKPDB;\nselect 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;\nselect 'VALUES|'||ID||'|'||TXT||'|'||rawtohex(dbms_lob.substr(B,32767,1))||'|'||dbms_lob.substr(C,32767,1) from {T}_ROWS;")
        assert f'COUNTS|{step+1}|{step+1}|{step+1}' in actual and f'VALUES|1|recovery-window-{step}|00AAFF800{step}|recovery-text-{step}' in actual
    final=product('restore-final','lob-recovery-2',0);assert final['cut']==previous['cut'] and final['reclaimed']['removed']==0
    (N/'proof.json').write_text(json.dumps(dict(developmentRuntimeOnly=True,sourceDml=0,frozenAcquisitionReused=True,scenarios=proofs,finalIndependentRestart=final),indent=2))
    print('LOB_NORMAL_PRODUCT_CHAIN_PASS',flush=True)
finally:
    try:
        if H in run('remaining-helper',['docker','ps','-a','--format','{{.Names}}']).splitlines():
            run('failure-logs',['docker','logs',H]);run('failure-stop',['docker','stop','-t','20',H]);run('failure-remove',['docker','rm',H])
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
