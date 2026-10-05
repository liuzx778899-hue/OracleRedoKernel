"""One new metadata-derived batch; raw redo unchanged, existing owned target only."""
import base64,hashlib,json,struct,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent; O=R/'lab/out/oracle-rebatch-20261005'
T=R/'lab/out/oracle-target-20261005'; K=R/'lab/out/captured-batch-20261005'
SOURCE='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute'];O.mkdir(parents=True,exist_ok=False)
def sha(b):return hashlib.sha256(b).hexdigest()
def run(n,args,data=None):
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for ext,b in [('stdout',p.stdout),('stderr',p.stderr)]: (O/(n+'.'+ext+'.txt')).write_bytes(b)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
    return p.stdout.decode()
def sql(n,text):
    body='whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off echo off\n'+text+'\nexit success\n'
    (O/(n+'.sql')).write_text(body)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode())
assert run('source-owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|running'
assert not run('no-existing-helper',['docker','ps','--filter','label=scope=oracle-target-commit','--format','{{.Names}}']).strip()
assert not run('no-existing-rebatch-helper',['docker','ps','--filter','label=scope=oracle-rebatch','--format','{{.Names}}']).strip()
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;"
source_before=sql('source-before',source_sql)
assert 'CONFIG|1276726821|1276717861|NO|NO' in source_before and 'SOURCE_ROW|11|blocking-closed|new-note' in source_before
query=(T/'POST-final.sql.redacted').read_text()
# SQL independent read remains the original safe snapshot query.
before=run('target-before',['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],query.encode())
assert 'STATE|POST|3|3|1' in before
manifest=json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-target-20261005.json').read_text(encoding='utf-8'))
pinned={f['path']:f['sha256'] for f in manifest['files']}
for name in ['adapter.jar','spi.jar','kernel.jar','ojdbc8.jar','classes/OracleTargetWorker.class']:
    p=T/name;assert sha(p.read_bytes())==pinned[p.relative_to(R).as_posix()]
old_ack=(T/'POST.ack').read_bytes()
raw=(T/'sequence-40.oracap').read_bytes();pos=0;output=bytearray();frames=0
while pos<len(raw):
    magic,ml,pl=struct.unpack_from('<8sII',raw,pos);assert magic==b'ORACAP01' and pl==512
    start=pos+16;meta=raw[start:start+ml];payload=raw[start+ml:start+ml+pl]
    assert sha(meta+payload)==raw[start+ml+pl:start+ml+pl+32].hex()
    m=json.loads(meta);m['sample']+=1000000
    modified=json.dumps(m,sort_keys=True,separators=(',',':')).encode()
    assert sha(payload)==m['sha256']
    output+=struct.pack('<8sII',magic,len(modified),pl)+modified+payload+hashlib.sha256(modified+payload).digest()
    pos=start+ml+pl+32;frames+=1
assert pos==len(raw);altered=O/'sequence-40.oracap';altered.write_bytes(output)
assert sha(output)!=sha(raw)
inputs=[altered,T/'sequence-41.oracap',T/'sequence-42.oracap']
args=json.loads((K/'actual.command.json').read_text())[:7]
for p in inputs:args += [str(p),sha(p.read_bytes())]
actual=run('new-batch-kernel',args)
reference=(K/'actual.stdout.txt').read_text()
assert [s for s in actual.splitlines() if s.startswith(('EVENT|','ROW|'))]==[s for s in reference.splitlines() if s.startswith(('EVENT|','ROW|'))]
new=actual.splitlines()[0].split('|')[1];old=reference.splitlines()[0].split('|')[1];schema=reference.splitlines()[0].split('|')[4]
assert new!=old
worker=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-rebatch','--user','0','--network','container:'+SOURCE,
        '--mount','type=bind,source='+str(T)+',target=/old,readonly','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
        '-cp','/old/adapter.jar:/old/spi.jar:/old/kernel.jar:/old/ojdbc8.jar:/old/classes','OracleTargetWorker','/old/target.secret','POST','oracle-target-POST-20261005',
        '/evidence/rebatch.ack','none','/evidence/unused.ready','/old/snapshot.txt','646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf','0x0300019b']
for i,p in enumerate(inputs):worker += [('/evidence/' if i==0 else '/old/')+p.name,sha(p.read_bytes())]
expected='COMMITTED|0|ORK_ORACLE_TARGET_ACK_V1|oracle-target-POST-20261005|'+new+'|'+schema+'|3'
assert run('apply-new-batch',worker).strip()==expected
ack=(O/'rebatch.ack').read_bytes();assert ack==(expected.split('|',2)[2]+'\n').encode()
assert run('repeat-new-batch',worker).strip()==expected and (O/'rebatch.ack').read_bytes()==ack
assert (T/'POST.ack').read_bytes()==old_ack
fresh=run('target-fresh',['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],query.encode())
lines=[s.strip().split('|') for s in fresh.splitlines() if s.strip()]
meta=[s.split('|') for s in reference.splitlines() if s.startswith('EVENT|')]
def image(txt,note):
    enc=lambda s:base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
    return 'ID=NUMBER:11;TXT=VARCHAR2:'+enc(txt)+';NOTE=VARCHAR2:'+enc(note)
expected_apps=[];prev=image('blocking-closed','new-note')
for seq,m in zip([40,41,42],meta):
    truth=[s.strip().split('|') for s in (R/'lab/out/closed-follow-20261005'/('step-'+str(seq)+'.stdout.txt')).read_text().splitlines() if s.strip().startswith('TRUTH|')]
    assert len(truth)==1 and truth[0][1:3]==[str(seq),'11']
    after=image(truth[0][3],truth[0][4]);expected_apps.append(['APPLIED','POST','oracle1:'+m[1],m[2],prev,after,'UPDATE']);prev=after
members=[['MEMBER','POST',b,str(i),'oracle1:'+m[1]] for b in [old,new] for i,m in enumerate(meta)]
receipts=[['RECEIPT','POST',b,'oracle-target-POST-20261005',schema,'3'] for b in [old,new]]
def compare(rows):
    return sum([sorted(s for s in rows if s[0]=='APPLIED')!=sorted(expected_apps),sorted(s for s in rows if s[0]=='MEMBER')!=sorted(members),
                sorted(s for s in rows if s[0]=='RECEIPT')!=sorted(receipts),[s for s in rows if s[0]=='STATE']!=[['STATE','POST','3','6','2']],
                [s for s in rows if s[0]=='ROW']!=[['ROW','POST','11','blocking-closed','new-note']]])
assert compare(lines)==0
for name,tag in [('missing-member','MEMBER'),('missing-receipt','RECEIPT'),('missing-event','APPLIED')]:
    changed=[s.copy() for s in lines];del changed[next(i for i,s in enumerate(changed) if s[0]==tag)]
    assert compare(changed)>0;(O/(name+'.json')).write_text(json.dumps({'mismatches':compare(changed),'derived_comparator_counterexample':True}))
assert sql('source-after',source_sql)==source_before
assert run('lgwr',['docker','exec',SOURCE,'cat','/proc/5601/comm']).strip()=='ora_lgwr_ork19'
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-rebatch','--format','{{.Names}}']).strip()
proof={'derived_metadata_only':True,'raw_payload_frames_unchanged':frames,'event_and_payload_and_row_output_equal':True,'old_batch':old,'new_batch':new,
       'actual_target_applications':3,'actual_target_members':6,'actual_target_commits':2,'new_batch_dml':0,'repeat_new_batch_dml':0,
       'independent_source_sql_audit_mismatches':0,'comparator_counterexamples':3,'old_ack_bytes_unchanged':True,'source_dml':0,
       'source_config_changes':0,'real_partial_overlap_window_accepted':False,'checkpoint_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
