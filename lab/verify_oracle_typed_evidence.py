"""Compare real typed metadata with retained kernel CLI and acquisition records; no database actions."""
import hashlib,json,re,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree'
B=R/'lab/out/release-oracle-typed-evidence-20261005';O=R/'lab/out/oracle-typed-evidence-20261005'
O.mkdir(parents=True,exist_ok=False);sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=json.loads((B/'proof.json').read_text())
for n in ['kernel','adapter','spi']:assert sha(B/(n+'.jar'))==proof[n+'_sha256']
classes=O/'classes';classes.mkdir();OLD=R/'lab/out/oracle-product-gate-20261005'
cp=';'.join([str(classes)]+[str(B/(n+'.jar')) for n in ['kernel','adapter','spi']]+[str(P/'server'/n/'target/classes') for n in ['streamhouse-pipeline','streamhouse-common']]+[str(P/'server/streamhouse-pipeline/target/test-classes')]+[str(p) for p in sorted((OLD/'product-libs').glob('*.jar'))])
def run(n,args):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(n,p.stderr)
    return p.stdout
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleTypedEvidenceProbe.java',R/'lab/OracleDmlCheckpointWorker.java',R/'lab/OracleProjectionProbe.java'])
configs=json.loads((R/'lab/out/oracle-null-sides-target-20261005/config.json').read_text())
empty=R/'lab/out/oracle-empty-target-20261005';cmd=json.loads((empty/'inspect.command.json').read_text());a=cmd[cmd.index('OracleDmlCheckpointWorker')+1:]
configs.append({'prefix':'EMPT','baseline_path':a[4],'baseline_sha256':a[5],'leaf':a[6],'captures':a[8:],'expected_raw_output':'empty-dml-capture-20261005/complete-batch-r2.stdout.txt'})
def compare(c,data):
    e=data['evidence'];lines=(R/'lab/out'/c['expected_raw_output']).read_text().splitlines()
    head=next(s.split('|') for s in lines if s.startswith('BATCH|'));assert [e['batchSha256'],e['baselineSha256'],str(e['baselineScn']),e['schemaSha256']]==head[1:5]
    source={k:int(v) for k,v in re.findall(r'(\w+)=(\d+)',head[5])};assert e['source']==source
    baseline=Path(c.get('baseline_path',str(R/'lab/out/oracle-null-sides-target-20261005'/c['prefix']/'baseline/snapshot.stdout.txt')))
    assert sha(baseline)==e['baselineSha256'];assert e['leaf']==int(c['leaf'])
    acquisition=baseline.read_text().splitlines()
    for kind in ['heap','index']:
        row=next(s.split('|') for s in acquisition if s.startswith(kind.upper()+'|BEFORE|'))
        assert e[kind]==dict(owner=row[2],name=row[3],logicalObject=int(row[4]),dataObject=int(row[5]))
    columns=[]
    for s in acquisition:
        if s.startswith('COLUMN|BEFORE|'):
            v=s.split('|');columns.append(dict(segment=int(v[2]),internal=int(v[3]),name=v[4],type=v[5],nullable=v[6]=='Y',hidden=v[7]=='YES'))
    assert e['columns']==columns
    raw_events=[s.split('|') for s in lines if s.startswith('EVENT|')];raw_rows=[s.split('|') for s in lines if s.startswith('ROW|')]
    assert len(e['events'])==len(raw_events)==len(raw_rows)
    for item,event,row in zip(e['events'],raw_events,raw_rows):
        pos=list(map(int,event[3].split(':')));xid={k:int(v) for k,v in re.findall(r'(\w+)=(\d+)',event[4])}
        assert item==dict(eventId='oracle1:'+event[1],payloadSha256=event[2],xid=xid,beginRecordScn=int(event[5]),endRecordScn=int(event[6]),rolledBack=False,pairedUndoPosition=dict(zip(['archiveSequence','block','offset','vector'],pos)),rowKey=dict(dataObjectId=int(row[3]),dba=int(row[4]),slot=int(row[5])))
    inputs=[s.split('|') for s in lines if s.startswith('INPUT|')];assert len(e['captures'])==len(inputs)
    for cap,raw,f,h in zip(e['captures'],inputs,c['captures'][::2],c['captures'][1::2]):
        assert cap['journalSha256']==raw[2]==h==sha(Path(f)) and cap['journalBytes']==int(raw[3])==Path(f).stat().st_size
        assert [cap['sequence'],cap['firstScn'],cap['nextScn']]==[int(raw[i]) for i in [1,4,5]]
        # Existing actual checkpoint independently records the closed physical block count.
        if c['prefix']=='EMPT':blocks=3873
        else:blocks=int(next(w for w in c['windows'] if int(w[4])==cap['sequence'])[5])
        assert cap['blocks']==blocks and cap['capacityBlocks']>=cap['blocks']
    assert data['pipeline']==dict(typed_evidence_reached_gate=True,final_state='FAILED',ledger_written=0,target_written=0,checkpoint_advanced=False,source_consumed=False,ordinary_options_authorized_public_decode=False)
results=[]
for c in configs:
    n=c['prefix'];baseline=c.get('baseline_path',str(R/'lab/out/oracle-null-sides-target-20261005'/n/'baseline/snapshot.stdout.txt'))
    args=[baseline,c['baseline_sha256'],c['leaf']]+c['captures']
    data=json.loads(run(n+'-typed',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleTypedEvidenceProbe']+args));compare(c,data)
    if n!='EMPT':
        text=run(n+'-legacy-inspect',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleDmlCheckpointWorker','inspect','unused',O,n,*args[:3],str(len(c['windows'])),*args[3:]])
        assert text==(R/'lab/out/oracle-null-sides-target-20261005'/(n+'-inspect.stdout.txt')).read_bytes()
    results.append(dict(case=n,events=len(data['evidence']['events']),typed_fields_zero_differences=True))
# Independently corrupt metadata copies; no accepted input or product state is edited.
c=configs[2];data=json.loads((O/(c['prefix']+'-typed.stdout.txt')).read_text());refusals=[]
for name in ['source','position','payload','capture']:
    derived=json.loads(json.dumps(data))
    if name=='source':derived['evidence']['source']['dbid']+=1
    elif name=='position':derived['evidence']['events'][0]['pairedUndoPosition']['offset']+=1
    elif name=='payload':derived['evidence']['events'][0]['payloadSha256']='0'*64
    else:derived['evidence']['captures'][0]['journalSha256']='0'*64
    (O/(name+'-derived.json')).write_text(json.dumps(derived))
    try:compare(c,derived);raise RuntimeError('comparator accepted '+name)
    except AssertionError:refusals.append(name)
(O/'proof.json').write_text(json.dumps({'cases':results,'comparator_refusals':refusals,'legacy_projection_byte_equal':True,'source_target_actions':0,'real_product_control_database_used':False,'trusted_registry_accepted':False,'public_oracle_decoder_enabled':False,'full_product_site_release_accepted':False},indent=2));print((O/'proof.json').read_text())
