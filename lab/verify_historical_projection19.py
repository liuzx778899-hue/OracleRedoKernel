"""Actual frozen historical events project with full fields and per-event schema bindings."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-product-candidate-20261006';O=R/'lab/out/v3-historical-raw-20261006';K=R/'lab/out/historical-row-schema-candidate-20261006'
assert not (N/'historical-projection-proof.json').exists()
(N/'executed-projection-driver.py').write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
classes=N/'classes';classes.mkdir(exist_ok=True)
cp=';'.join(map(str,[K/'kernel.jar',N/'adapter.jar',R/'lab/out/dictionary-baseline-candidate-20261006/spi.jar',classes]))
def run(name,args,expected=0):
    args=list(map(str,args))
    if (N/(name+'.command.json')).exists():
        assert json.loads((N/(name+'.command.json')).read_text())==args
        assert int((N/(name+'.rc.txt')).read_text())==expected
        return (N/(name+'.stdout.txt')).read_text().splitlines()
    (N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=240)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==expected,(name,p.stderr.decode())
    if expected:assert not p.stdout
    return p.stdout.decode().splitlines()
j='D:/work/Java/jdk-17.0.7/bin/'
run('compile-historical-projection',[j+'javac.exe','-cp',cp,'-d',classes,R/'lab/OracleHistoricalProjectionProbe.java'])
old=json.loads((K/'actual-historical-batch-r1.command.json').read_text());args=[j+'java.exe','-Xmx1536m','-cp',cp,'OracleHistoricalProjectionProbe',*old[5:]]
actual=run('actual-historical-projection',args);repeat=run('repeat-historical-projection',args);assert actual==repeat
erased=run('erased-historical-projection',args[:8]+[O/'erased-68.oracap',sha(O/'erased-68.oracap'),O/'erased-69.oracap',sha(O/'erased-69.oracap')])
assert actual[1]==erased[1] and actual[0]!=erased[0]
raw=(K/'actual-historical-batch-r2.stdout.txt').read_text().splitlines()
def verify(lines,factory):
    assert len(lines)==10 and lines[0].split('|')[1:]==factory[0].split('|')[1:]
    for i in range(2):
        schema,event,row=factory[1+3*i:4+3*i];meta=event.split('|');fields=lines[2+4*i].split('|')
        xid=['10.25.2305','5.27.602'][i];version=schema.split('|')[2]
        assert fields==['EVENT','oracle1:'+meta[1],meta[2],meta[3],xid,meta[5],meta[6],version,version,'[TXT]','1']
        for label,cells in zip(['BEFORE','AFTER'],row.split('|')[-2:]):
            expected=[]
            for name,cell in zip(['ID','TXT','NOTE','EXTRA_V3'],cells.split(',')):
                state,hexvalue=cell.split(':');value='NULL' if state=='NULL' else 'NUMBER:0' if name=='ID' else 'VARCHAR2:'+hexvalue
                expected.append(name+'='+value)
            assert lines[3+4*i+(label=='AFTER')]==label+'|'+','.join(expected)
        assert lines[5+4*i]=='BINDING|oracle-batch-sha256:'+factory[0].split('|')[1]+'|true|true|null|null|null'
verify(actual,raw);verify(erased,(K/'historical-erased-markers.stdout.txt').read_text().splitlines())
for name,at,oldvalue,newvalue in [('lost-column',3,',EXTRA_V3=NULL',''),('wrong-schema',2,'c88266ce','c88266cf'),('wrong-before',3,'64696374','64696375')]:
    bad=list(actual);assert oldvalue in bad[at];bad[at]=bad[at].replace(oldvalue,newvalue)
    try:verify(bad,raw)
    except AssertionError:pass
    else:raise AssertionError('comparator missed '+name)
refusals=[]
for stem in ['business-wrong-business-before','DERIVED-history-wrong-generation-complete-events']:
    command=json.loads((K/(stem+'.command.json')).read_text());command[3]=cp;command[4]='OracleHistoricalProjectionProbe'
    run('projection-refusal-'+stem,command,1);refusals.append(stem)
oldfolder=R/'lab/out/v2-dml-capture-20261006';legacy=json.loads((oldfolder/'implicit-tail-typed-projection.command.json').read_text())
jackson=sorted((oldfolder/'implicit-tail-libs').glob('jackson-*.jar'));assert len(jackson)>=3
legacy[3]=cp+';'+str(oldfolder/'parser-classes')+';'+';'.join(map(str,jackson))
assert json.loads('\n'.join(run('static-v2-projection-impact-r2',legacy)))==json.loads((oldfolder/'implicit-tail-typed-projection.stdout.txt').read_text())
out=dict(adapter_sha256=sha(N/'adapter.jar'),kernel_sha256=sha(K/'kernel.jar'),complete_projected_events=2,per_event_schema_columns=[4,3],schema_sequence_sha256=actual[1].split('|')[1],raw_id_payload_position_xid_scn_equal=True,full_before_after_equal=True,hidden_note_and_added_null_preserved=True,repeat_byte_equal=True,erased_marker_events_schema_sequence_equal=True,batch_capture_binding_changes_with_input=True,comparator_counterexamples=3,complete_projection_refusals=refusals,static_v2_projection_json_equal=True,source_actions=0,target_actions=0,product_http=False,checkpoint_ack=False)
(N/'historical-projection-proof.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
