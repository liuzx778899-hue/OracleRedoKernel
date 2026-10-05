"""Independent SQL exports and durable ACK versus sealed raw/typed/native-checked evidence."""
import ast,base64,copy,hashlib,json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-bounded-runtime-20261005';REF=R/'lab/out/oracle-typed-evidence-20261005';PREV=R/'lab/out/oracle-postcommit-proof-20261005'
# Reuse only the offline legacy-envelope reader; do not run its SQL exporter or accepted matrix.
tree=ast.parse((R/'lab/audit_oracle_control_publication.py').read_text());function=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='envelopes')
exec(compile(ast.Module(body=[function],type_ignores=[]),'sealed-legacy-envelope-reader','exec'))
results=json.loads((O/'runtime-proof.json').read_text());assert len(results)==4
def verify(name,target_text=None,control_text=None,ack_bytes=None):
    prefix={'SNON':'RTNON','SNUL':'RTNUL','SMIX':'RTMIX','EMPT':'RTEMP'}[name];previous={'SNON':'PCNON','SNUL':'PCNUL','SMIX':'PCMIX','EMPT':'PCEMP'}[name]
    e=json.loads((REF/(name+'-typed.stdout.txt')).read_text())['evidence'];events=envelopes(name,e)
    target=(O/(prefix+'-final.stdout.txt')).read_text() if target_text is None else target_text
    expected=(PREV/(previous+'-final.stdout.txt')).read_text().replace('oracle-dml-'+previous+'-20261005','oracle-dml-'+prefix+'-20261005')
    normalize=lambda text:[line.strip().split('|') for line in text.splitlines() if line.strip()]
    assert normalize(target)==normalize(expected),'all target identities, intermediate images, member order, commit and final rows'
    text=(O/(name+'-control-final.stdout.txt')).read_text() if control_text is None else control_text
    lines=normalize(text);rows=[x for x in lines if x[0]=='CONTROL'];assert len(rows)==1
    row=rows[0];assert row[2]==('EMPTY' if not events else 'PUBLISHED') and row[3]==e['captures'][0]['journalSha256'] and row[4:]==['1','1',str(len(events))]
    cps=[x.split('|',1)[1] for x in text.splitlines() if x.startswith('CHECKPOINT|')];assert len(cps)==1;cp=json.loads(cps[0]);batch=int(row[1])
    assert cp['lastBatchId']==batch and cp['lastWindowKey']=='runtime-'+name and cp['sourceId']=='oracle-runtime-'+name and cp['engine']=='oracle'
    assert cp['oracleEvidence']==e,'all typed Oracle checkpoint fields'
    receipt=cp['targetCommit'];assert receipt['targetId']=='oracle-dml-'+prefix+'-20261005' and receipt['source']==e['source'] and receipt['baselineSha256']==e['baselineSha256'] and receipt['schemaSha256']==e['schemaSha256'] and receipt['batchSha256']==e['batchSha256'] and receipt['events']==len(events)
    assert receipt['envelopesSha256']==hashlib.sha256(json.dumps(events,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()
    assert [x for x in lines if x[0]=='TASK']==[['TASK','DONE']] and [x for x in lines if x[0]=='WINDOW']==[['WINDOW','CONSUMED','1','0']] and [x for x in lines if x[0]=='GATE']==[['GATE','PASS']]
    expected_ledger=[['LEDGER',x['eventId'],'oracle-runtime-'+name,x['table']['schema']+'.'+x['table']['name'],x['operation'],str(batch)] for x in events]
    assert [x for x in lines if x[0]=='LEDGER']==sorted(expected_ledger,key=lambda x:x[1])
    files=list((O/'acks'/name).glob('*.ack.json'));assert len(files)==1
    ack=files[0].read_bytes() if ack_bytes is None else ack_bytes;assert json.loads(ack)==cp,'durable ACK all fields'
    runtime=next(x for x in results if x['case']==name);assert runtime['checkpoint']==cp and runtime['first_target_events']==len(events) and runtime['ack_sha256']==hashlib.sha256(files[0].read_bytes()).hexdigest()
    return dict(case=name,events=len(events),all_target_control_checkpoint_ack_fields_zero_differences=True)
proofs=[verify(name) for name in ['SNON','SNUL','SMIX','EMPT']];refusals=[]
target=(O/'RTMIX-final.stdout.txt').read_text();control=(O/'SMIX-control-final.stdout.txt').read_text()
for name in ['wrong-operation','checkpoint-source','ack-conflict']:
    t=target;c=control;a=None
    if name=='wrong-operation':t=t.replace('|INSERT','|DELETE',1)
    elif name=='checkpoint-source':c=c.replace('"dbid":1276726821','"dbid":0',1)
    else:a=b'{}'
    (O/(name+'.derived-target.txt')).write_text(t);(O/(name+'.derived-control.txt')).write_text(c)
    if a is not None:(O/(name+'.derived-ack.json')).write_bytes(a)
    try:verify('SMIX',t,c,a);raise RuntimeError('COMPARATOR_ACCEPTED_'+name)
    except AssertionError:refusals.append(name)
(O/'independent-runtime-proof.json').write_text(json.dumps(dict(cases=proofs,comparator_refusals=refusals,actual_java_facade_bounded_chain=True,full_server_http_accepted=False,site_accepted=False,independent_target_instance=False),indent=2));print('ORACLE_REAL_FACADE_INDEPENDENT_COMPARISON_VERIFIED|4|3_REFUSALS')
