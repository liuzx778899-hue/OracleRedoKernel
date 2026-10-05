"""Independent retained raw-image/SQL target/receipt/checkpoint comparison. No target writes."""
import base64,hashlib,json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-dml-target-20261005';configs=json.loads((O/'config.json').read_text())
b64=lambda s:base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
def raw_image(text):
    if not text:return ''
    fields=text.split(',');assert len(fields)==3
    out=[]
    for name,field in zip(['ID','TXT','NOTE'],fields):
        state,hexval=field.split(':',1)
        if state=='NULL':encoded='NULL';assert hexval==''
        else:
            assert state=='VALUE'
            if name=='ID':assert hexval in ['80','c10c'];encoded='NUMBER:'+('0' if hexval=='80' else '11')
            else:encoded='VARCHAR2:'+b64(bytes.fromhex(hexval).decode('utf-8'))
        out.append(name+'='+encoded)
    return ';'.join(out)
def expected(c):
    text=(R/'lab/out'/c['expected_raw_output']).read_text();meta=[s.split('|') for s in text.splitlines() if s.startswith('EVENT|')];rows=[s.split('|') for s in text.splitlines() if s.startswith('ROW|')]
    assert len(meta)==len(rows)==c['expected_events']
    return [['APPLIED','oracle1:'+m[1],m[2],raw_image(r[7]),raw_image(r[8]),r[6]] for m,r in zip(meta,rows)]
def verify(c,prefix,fault=False,target_text=None):
    p=prefix;exp=expected(c);target=(O/(p+'-final.stdout.txt')).read_text() if target_text is None else target_text;lines=[s.strip().split('|') for s in target.splitlines() if s.strip()]
    assert sorted(s for s in lines if s[0]=='APPLIED')==sorted(exp),'complete intermediate images / operations'
    assert [s for s in lines if s[0]=='ROW']==[['ROW','0',c['seed'],'anchor']],'final seed'
    windows=c['windows'][-1:] if fault else c['windows']
    members=[['MEMBER',w[1],str(i),e[1]] for w in windows for i,e in enumerate(exp[:int(w[3])])]
    receipts=[['RECEIPT',w[1],'oracle-dml-'+p+'-20261005',w[2],w[3]] for w in windows]
    assert sorted(s for s in lines if s[0]=='MEMBER')==sorted(members),'ordered batch members'
    assert sorted(s for s in lines if s[0]=='RECEIPT')==sorted(receipts),'actual target commit receipts'
    assert [s for s in lines if s[0]=='STATE']==[['STATE',str(len(exp)),str(len(members)),str(len(windows))]]
    cp=(O/p/'checkpoint').read_text();parts=cp.splitlines();last=c['windows'][-1]
    assert parts[0]=='ORK_ORACLE_CONSUMER_V1' and parts[1]=='TARGET|'+b64('ORKTGT_CMT_20261005.'+p+'|oracle-dml-'+p+'-20261005')
    assert parts[2]=='FRONTIER|'+last[1]
    assert parts[-1]=='SHA256|'+hashlib.sha256(('\n'.join(parts[:-1])+'\n').encode()).hexdigest()
    folder=c['prefix'];baseline=(O/folder/'baseline/snapshot.stdout.txt').read_text();scn=next(s.split('|')[1] for s in baseline.splitlines() if s.startswith('SNAPSHOT_SCN|'))
    captures=[Path(f).stem.split('-')[-1]+':'+h for f,h in zip(c['captures'][::2],c['captures'][1::2])]
    batch_lines=[]
    for w in windows:
        batch_lines.append(['BATCH',w[1],w[2],c['baseline_sha256'],scn,c['leaf'],'1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3',w[4],w[5],w[6],w[3]]+captures[:int(w[0])])
        assert (O/p/(w[1]+'.ack')).read_bytes()==('ORK_ORACLE_TARGET_ACK_V1|oracle-dml-'+p+'-20261005|'+w[1]+'|'+w[2]+'|'+w[3]+'\n').encode()
    assert [s.split('|') for s in parts[3:-1]]==batch_lines,'all checkpoint scope/capture/position fields'
    if not fault:
        for i,w in enumerate(c['windows'],1):
            rows=[s.strip().split('|') for s in (O/(p+'-state-'+str(i)+'.stdout.txt')).read_text().splitlines() if s.startswith('ROW|')]
            wanted=[['ROW','0',c['seed'],'anchor']]
            if p in ['DNON','DNUL'] and i==1:wanted.append(['ROW','11','insert-visible','insert-note'] if p=='DNON' else ['ROW','11','',''])
            assert rows==wanted,'actual intermediate SQL state'
    return {'target':p,'complete_images':len(exp)*2,'members':len(members),'receipts':len(receipts),'checkpoint_fields_zero_differences':True}
assert (O/'target-proof.json').exists()
results=[verify(c,c['prefix']) for c in configs];results.append(verify(configs[-1],'DFAIL',True))
# Exercise this independent verifier on derived copies; accepted evidence stays unchanged.
c=configs[-1];path=O/'DMIX-final.stdout.txt';original=path.read_bytes();refused=[]
for name,before,after in [('missing-update',next(s for s in original.decode().splitlines() if s.startswith('APPLIED|') and s.endswith('|UPDATE'))+'\n',''),('wrong-operation','|INSERT','|DELETE'),('member-order','|0|oracle1:','|8|oracle1:')]:
    text=original.decode().replace(before,after,1);assert text!=original.decode();derived=O/(name+'-target-comparison.txt');derived.write_text(text)
    try:verify(c,'DMIX',target_text=text);raise RuntimeError('comparator accepted '+name)
    except AssertionError as error:refused.append({'case':name,'reason':str(error)})
assert path.read_bytes()==original and len(refused)==3
proof={'independent_targets':results,'comparator_refusals':refused,'full_source_dml_replayed':False,'standalone_rollback_empty_batch_accepted':False,'rollback_prefix_new_applications':[3,0,0],'product_pipeline_accepted':False,'site_accepted':False,'release_accepted':False}
(O/'independent-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
