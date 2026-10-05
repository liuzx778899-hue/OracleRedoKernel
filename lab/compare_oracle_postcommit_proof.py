"""Independent frozen raw-output versus real SQL images/member/commit/checkpoint/ACK; no JDBC calls."""
import base64,hashlib,json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-postcommit-proof-20261005';F=R/'lab/out/oracle-null-sides-target-20261005';E=R/'lab/out/oracle-empty-target-20261005'
b64=lambda s:base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
source='1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3'
configs=json.loads((O/'config.json').read_text());nonempty={x['prefix']:x for x in json.loads((F/'config.json').read_text())}
def image(text):
    if not text:return ''
    parts=text.split(',');assert len(parts)==3
    out=[]
    for name,part in zip(['ID','TXT','NOTE'],parts):
        state,value=part.split(':',1)
        if state=='NULL':assert value=='';decoded='NULL'
        else:
            assert state=='VALUE'
            if name=='ID':assert value in ['80','c10c'];decoded='NUMBER:'+('0' if value=='80' else '11')
            else:decoded='VARCHAR2:'+b64(bytes.fromhex(value).decode('utf-8'))
        out.append(name+'='+decoded)
    return ';'.join(out)
def verify(c,text=None,cp=None):
    prefix=c['target_prefix'];empty=c['prefix']=='EMPT';path=E if empty else F/c['prefix'];baseline=path/'baseline/snapshot.stdout.txt'
    assert hashlib.sha256(baseline.read_bytes()).hexdigest()==c['baseline_sha256']
    scn=next(s.split('|')[1] for s in baseline.read_text().splitlines() if s.startswith('SNAPSHOT_SCN|'))
    if empty:
        old=json.loads((E/'proof.json').read_text());batch=old['target_batch'];schema=old['target_schema'];events=[];seed=[]
        oldcp=(E/'consumer/checkpoint').read_text().splitlines()[3].split('|');position=oldcp[7:10];expected_count=0
    else:
        ref=nonempty[c['prefix']];w=ref['windows'][-1];batch=w[1];schema=w[2];position=w[4:7];expected_count=ref['expected_events'];seed=[['ROWS','0',ref['seed'],'anchor']]
        raw=(R/'lab/out'/ref['expected_raw_output']).read_text();meta=[s.split('|') for s in raw.splitlines() if s.startswith('EVENT|')];rows=[s.split('|') for s in raw.splitlines() if s.startswith('ROW|')]
        assert len(meta)==len(rows)==expected_count
        events=[['APPLIED','oracle1:'+m[1],m[2],image(r[7]),image(r[8]),r[6]] for m,r in zip(meta,rows)]
    rows=[s.strip().split('|') for s in ((O/(prefix+'-final.stdout.txt')).read_text() if text is None else text).splitlines() if s.strip()]
    assert [s for s in rows if s[0]=='STATE']==[['STATE',str(expected_count),str(expected_count),'1']]
    assert [s for s in rows if s[0]=='IDENT']==[['IDENT','1','oracle-dml-'+prefix+'-20261005',source,schema]]
    assert sorted(s for s in rows if s[0]=='APPLIED')==sorted(events),'complete intermediate images'
    assert [s for s in rows if s[0]=='ROWS']==seed,'final seed'
    assert [s for s in rows if s[0]=='MEMBERS']==[['MEMBERS',batch,str(i),e[1]] for i,e in enumerate(events)],'member order'
    assert [s for s in rows if s[0]=='COMMITS']==[['COMMITS',batch,'oracle-dml-'+prefix+'-20261005',schema,str(expected_count)]],'target receipt'
    checkpoint=(O/prefix/'checkpoint').read_text() if cp is None else cp;parts=checkpoint.splitlines()
    assert parts[:3]==['ORK_ORACLE_CONSUMER_V1','TARGET|'+b64('ORKTGT_CMT_20261005.'+prefix+'|oracle-dml-'+prefix+'-20261005'),'FRONTIER|'+batch]
    assert parts[-1]=='SHA256|'+hashlib.sha256(('\n'.join(parts[:-1])+'\n').encode()).hexdigest()
    captures=[Path(f).stem.split('-')[-1]+':'+h for f,h in zip(c['captures'][::2],c['captures'][1::2])]
    assert [s.split('|') for s in parts[3:-1]]==[['BATCH',batch,schema,c['baseline_sha256'],scn,str(c['leaf']),source]+position+[str(expected_count)]+captures],'all checkpoint fields'
    assert (O/prefix/(batch+'.ack')).read_bytes()==('ORK_ORACLE_TARGET_ACK_V1|oracle-dml-'+prefix+'-20261005|'+batch+'|'+schema+'|'+str(expected_count)+'\n').encode()
    return dict(case=c['prefix'],events=expected_count,complete_images=len(events)*2,checkpoint_and_ack_zero_differences=True)
results=[verify(c) for c in configs];c=next(x for x in configs if x['prefix']=='SMIX');original=(O/(c['target_prefix']+'-final.stdout.txt')).read_text();cp=(O/c['target_prefix']/'checkpoint').read_text();refusals=[]
for name,text,changed_cp in [('missing-member','\n'.join(s for s in original.splitlines() if not s.startswith('MEMBERS|') or '|0|' not in s)+'\n',cp),('wrong-operation',original.replace('|INSERT','|DELETE',1),cp),('checkpoint-source',original,cp.replace(source,'0:'+source.split(':',1)[1]))]:
    assert text!=original or changed_cp!=cp
    if changed_cp!=cp:
        parts=changed_cp.splitlines();parts[-1]='SHA256|'+hashlib.sha256(('\n'.join(parts[:-1])+'\n').encode()).hexdigest();changed_cp='\n'.join(parts)+'\n'
    (O/(name+'.derived-target.txt')).write_text(text);(O/(name+'.derived-checkpoint.txt')).write_text(changed_cp)
    try:verify(c,text,changed_cp);raise RuntimeError('COMPARATOR_ACCEPTED_'+name)
    except AssertionError as e:refusals.append(dict(case=name,reason=str(e)))
assert len(refusals)==3
(O/'independent-proof.json').write_text(json.dumps({'targets':results,'comparator_refusals':refusals,'product_facade_accepted':False,'site_accepted':False},indent=2));print('POST_COMMIT_INDEPENDENT_COMPARISON_VERIFIED|4|3_REFUSALS')
