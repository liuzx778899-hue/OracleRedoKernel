"""Independent DTO assertions against frozen raw factory events and schema definitions."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-schema-protocol-20261006';O=R/'lab/out/v3-historical-raw-20261006';K=R/'lab/out/historical-row-schema-candidate-20261006'
assert not (N/'schema-evidence-proof.json').exists();(N/'executed-schema-verify-driver.py').write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
classes=N/'classes';classes.mkdir(exist_ok=True)
jackson=sorted((R/'lab/out/v2-dml-capture-20261006/implicit-tail-libs').glob('jackson-*.jar'));assert len(jackson)==3
cp=';'.join(map(str,[N/'kernel.jar',N/'adapter.jar',N/'spi.jar',classes,*jackson]));j='D:/work/Java/jdk-17.0.7/bin/'
def run(name,args,expected=0):
    assert not (N/(name+'.command.json')).exists();args=list(map(str,args));(N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=240)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==expected,(name,p.stderr.decode())
    if expected:assert not p.stdout;return p.stderr.decode().splitlines()[0]
    return p.stdout
run('compile-schema-probe',[j+'javac.exe','-cp',cp,'-d',classes,R/'lab/OracleHistoricalSchemaEvidenceProbe.java'])
args=json.loads((K/'actual-historical-batch-r1.command.json').read_text());command=[j+'java.exe','-Xmx1536m','-cp',cp,'OracleHistoricalSchemaEvidenceProbe','normal',*args[5:]]
actual=json.loads(run('actual-schema-evidence',command));again=json.loads(run('repeat-schema-evidence',command));assert actual==again
erased=json.loads(run('erased-schema-evidence',command[:9]+[O/'erased-68.oracap',sha(O/'erased-68.oracap'),O/'erased-69.oracap',sha(O/'erased-69.oracap')]))
raw=(K/'actual-historical-batch-r2.stdout.txt').read_text().splitlines()
def verify(value,factory):
    e=value['evidence'];assert e['batchSha256']==factory[0].split('|')[1] and e['baselineSha256']==factory[0].split('|')[2] and e['baselineScn']==2536302
    assert e['schemaSequenceSha256']=='c23904a9efaed17aaf82a68d8d88478dcf84d89804c14376b6c29aa9f077a2e4'
    base=[dict(segment=i,internal=i,name=name,type='NUMBER' if i==1 else 'VARCHAR2',nullable=i!=1,hidden=i==3) for i,name in enumerate(['ID','TXT','NOTE'],1)]
    assert e['columns']==base and e['schemaSha256']==factory[4].split('|')[2]
    assert len(e['eventSchemas'])==len(e['events'])==len(value['envelopes'])==2
    for i in range(2):
        schema,event,row=factory[1+3*i:4+3*i];f=event.split('|');version=schema.split('|')[2];id='oracle1:'+f[1]
        expected=base+[dict(segment=4,internal=4,name='EXTRA_V3',type='VARCHAR2',nullable=True,hidden=False)] if i==0 else base
        assert e['eventSchemas'][i]==dict(eventId=id,schemaSha256=version,columns=expected)
        item=e['events'][i];assert item['eventId']==id and item['payloadSha256']==f[2] and item['endRecordScn']==int(f[6])
        assert item['rowKey']==dict(dataObjectId=75185,dba=50342532,slot=0)
        env=value['envelopes'][i];assert env['eventId']==id and env['changedColumns']==['TXT'] and env['metadata']['schemaVersion']==version
        assert set(env['before'])==set(env['after'])=={c['name'] for c in expected}
        assert env['before']['ID']==env['after']['ID']==0 and env['before']['NOTE']==env['after']['NOTE']=='anchor'
        assert env['before']['TXT']==['dictionary-restored','history-v3-added'][i] and env['after']['TXT']==['history-v3-added','history-v3-restored'][i]
        if i==0:assert env['before']['EXTRA_V3'] is None and env['after']['EXTRA_V3'] is None
verify(actual,raw);verify(erased,(K/'historical-erased-markers.stdout.txt').read_text().splitlines())
negatives=[]
for mode in ['wrong-commitment','swapped-association','trim-added-column']:
    bad=list(command);bad[5]=mode;negatives.append(dict(mode=mode,reason=run(mode,bad,1),events=0))
out=dict(kernel_sha256=sha(N/'kernel.jar'),adapter_sha256=sha(N/'adapter.jar'),spi_sha256=sha(N/'spi.jar'),complete_events=2,baseline_columns=3,event_columns=[4,3],schema_sequence_sha256=actual['evidence']['schemaSequenceSha256'],factory_schema_and_full_images_equal=True,actual_json_roundtrip_equal=True,repeat_json_equal=True,erased_marker_event_schema_sequence_equal=True,negatives=negatives,source_actions=0,target_actions=0,product_http=False,checkpoint_ack=False)
(N/'schema-evidence-proof.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
