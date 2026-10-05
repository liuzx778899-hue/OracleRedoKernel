"""Read independent SQL export against pre-existing raw/native-checked typed and envelope evidence."""
import hashlib,json,subprocess,copy,base64
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-control-publication-20261005';REF=R/'lab/out/oracle-typed-evidence-20261005'
assert len(json.loads((O/'matrix-proof.json').read_text()))==24
def run(n,args):
    args=list(map(str,args));assert not (O/(n+'.rc.txt')).exists();(O/(n+'.command.json')).write_text(json.dumps(args))
    with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,stdout=out,stderr=err,timeout=300)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
cp=str(O/'libs-r2/*')
run('compile-select-audit-r2',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',O/'classes-r2',R/'lab/OracleControlPublicationAudit.java'])
run('select-audit-r2',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-control-select-audit','--user','0','--network','container:oraredo-19c-lab-20261004','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes-r2:/evidence/libs-r2/*','OracleControlPublicationAudit','/evidence/control.secret.json','/evidence/control-snapshots.json'])
rows=json.loads((O/'control-snapshots.json').read_text())
def envelopes(name,e):
    # Use the already sealed legacy full projection; do not invoke the parser or journal.
    if not e['events']:return []
    lines=[x.split('|') for x in (REF/(name+'-legacy-inspect.stdout.txt')).read_text().splitlines()]
    full=max(int(x[1]) for x in lines if x[0]=='WINDOW')
    entries=[x for x in lines if x[0]=='ENTRY' and int(x[1])==full]
    assert len(entries)==len(e['events'])
    def image(text):
        if not text:return None
        result={}
        for cell in text.split(';'):
            key,value=cell.split('=',1)
            if value=='NULL':result[key]=None
            elif value.startswith('NUMBER:'):result[key]=int(value[7:])
            else:
                assert value.startswith('VARCHAR2:')
                raw=value[9:];result[key]=base64.b64decode(raw+'='*(-len(raw)%4)).decode()
        return result
    events=[];ordinals={}
    for entry,typed in zip(entries,e['events']):
        assert entry[3]==typed['eventId'] and entry[4]==typed['payloadSha256']
        xid=typed['xid'];tx='oracle-xid:'+'.'.join(str(xid[k]) for k in ['usn','slot','sequence'])
        ordinals[tx]=ordinals.get(tx,0)+1
        events.append(dict(eventId=entry[3],source=dict(server=None,database=None,databaseGuid=None,recoveryFork=None),table=dict(schema=e['heap']['owner'],name=e['heap']['name']),operation=entry[5],transaction=dict(transactionId=tx,beginLsn=None,recordLsn=None,commitLsn=None,sequence=ordinals[tx]),before=image(entry[6]),after=image(entry[7]),changedColumns=entry[8].split(',') if entry[8] else [],metadata=dict(captureInput='oracle-batch-sha256:'+e['batchSha256'],schemaVersion=e['schemaSha256'],beforeComplete=True,afterComplete=True)))
    return events

def verify(row):
    dialect,stage,name=row['key'].split(':');ref=json.loads((REF/(name+'-typed.stdout.txt')).read_text());e=ref['evidence'];events=envelopes(name,e)
    assert row['event_id_width']==80 and row['transport']==[1,1] and row['input_sha256']==e['captures'][0]['journalSha256']
    assert row['event_count']==len(events) and row['batch_state']==('EMPTY' if not events else 'PUBLISHED')
    assert row['task_state']=='RUNNING' and row['window_state']=='CONSUMED' and row['frozen']==1 and row['dropped']==0 and row['gates']==['PASS']
    cp=row['checkpoint'];assert cp['lastBatchId']==row['batch_id'] and cp['lastWindowKey']=='journal-'+name and cp['sourceId']=='oracle-journal-'+name and cp['engine']=='oracle'
    assert cp['oracleEvidence']==e,'all Oracle checkpoint fields'
    prefix={'SNON':'PCNON','SNUL':'PCNUL','SMIX':'PCMIX','EMPT':'PCEMP'}[name]
    receipt=cp['targetCommit'];assert receipt['targetId']=='oracle-dml-'+prefix+'-20261005' and receipt['source']==e['source'] and receipt['baselineSha256']==e['baselineSha256'] and receipt['schemaSha256']==e['schemaSha256'] and receipt['batchSha256']==e['batchSha256'] and receipt['events']==len(events)
    # Canonical flat primitives/arrays/maps match Jackson's sorted property/key serialization for these fixtures.
    digest=hashlib.sha256(json.dumps(events,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest();assert receipt['envelopesSha256']==digest,'canonical full event digest'
    expected=[dict(event_id=x['eventId'],source_id='oracle-journal-'+name,table=x['table']['schema']+'.'+x['table']['name'],operation=x['operation']) for x in events]
    assert row['ledger']==sorted(expected,key=lambda x:x['event_id']),'actual ledger identity/source/table/operation'
for r in rows:verify(r)
refusals=[];base=next(r for r in rows if r['key']=='mysql:NEW:SMIX')
for name in ['ledger-source','checkpoint-source','receipt-count']:
    changed=copy.deepcopy(base)
    if name=='ledger-source':changed['ledger'][0]['source_id']='forged'
    elif name=='checkpoint-source':changed['checkpoint']['oracleEvidence']['source']['dbid']+=1
    else:changed['checkpoint']['targetCommit']['events']+=1
    (O/(name+'.derived-control.json')).write_text(json.dumps(changed))
    try:verify(changed);raise RuntimeError('AUDIT_ACCEPTED_'+name)
    except AssertionError:refusals.append(name)
(O/'independent-control-proof.json').write_text(json.dumps({'real_select_exports':len(rows),'all_control_and_checkpoint_fields_zero_differences':True,'comparator_refusals':refusals,'facade_accepted':False,'site_accepted':False},indent=2));print('CONTROL_PUBLICATION_INDEPENDENT_AUDIT_VERIFIED|24|3_REFUSALS')
