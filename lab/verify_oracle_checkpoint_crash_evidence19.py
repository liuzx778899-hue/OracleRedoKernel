"""Read-only independent checkpoint-field and actual target audit; never repeats kills/DML."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;G=R/'lab/out/oracle-checkpoint-crash-20261005';O=R/'lab/out/oracle-checkpoint-crash-evidence-20261005';O.mkdir(parents=True,exist_ok=False)
assert (G/'proof.json').exists()
SOURCE='oraredo-19c-lab-20261004';T=R/'lab/out/oracle-target-20261005'
windows=json.loads((R/'lab/out/oracle-overlap-20261005/proof.json').read_text());full=windows['full_batch'];prefix=windows['prefix_batch'];schema='b38658100a383c1a12ce0f2d560757864458261c71119102622acbdbcf07d3ce'
receipt=json.loads((R/'lab/out/closed-follow-20261005/capture/receipt.json').read_text());captures=receipt['captured']
baseline=R/'lab/out/closed-follow-20261005/baseline/snapshot.stdout.txt';baseline_sha=hashlib.sha256(baseline.read_bytes()).hexdigest();assert baseline_sha=='646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf'
source='1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3'
def compare(data,batches):
    lines=data.decode().splitlines();errors=[]
    if lines[2]!='FRONTIER|'+full:errors.append('FRONTIER')
    if lines[-1]!='SHA256|'+hashlib.sha256(('\n'.join(lines[:-1])+'\n').encode()).hexdigest():errors.append('CHECKSUM')
    actual=[x.split('|') for x in lines if x.startswith('BATCH|')];expected=[]
    for batch,count in batches:
        cs=captures[:count];last=cs[-1]
        expected.append(['BATCH',batch,schema,baseline_sha,'2366727',str(0x0300019b),source,str(last['sequence']),str(last['blocks']),str(last['next_scn']),str(count)]+[str(c['sequence'])+':'+c['sha256'] for c in cs])
    if actual!=expected:errors.append('TRUSTED_SCOPE_AND_CAPTURE_FIELDS')
    return errors
query=(T/'POST-final.sql.redacted').read_text()
for p in ['CPNO','CPBE','CPAF','CPTM','CPAK']:
    args=['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'];(O/(p+'.command.json')).write_text(json.dumps(args))
    proc=subprocess.run(args,input=query.replace('POST',p).encode(),capture_output=True,timeout=60)
    for ext,b in [('stdout',proc.stdout),('stderr',proc.stderr)]:(O/(p+'.'+ext+'.txt')).write_bytes(b)
    (O/(p+'.rc.txt')).write_text(str(proc.returncode));assert proc.returncode==0
    old=(G/(p+'-independent-read.stdout.txt')).read_bytes();assert sorted(proc.stdout.splitlines())==sorted(old.splitlines())
    batches=[(prefix,2),(full,3)] if p=='CPNO' else [(full,3)]
    errors=compare((G/p/'checkpoint').read_bytes(),batches);assert not errors
    (O/(p+'-checkpoint.json')).write_text(json.dumps({'mismatches':0,'source_scope_fields':True,'original_capture_receipt_fields':True,'target_snapshot_unchanged':True}))
normal=(G/'CPNO/checkpoint').read_text().splitlines()
for name,index in [('wrong-scn',9),('wrong-source',6),('wrong-capture-sha',11)]:
    lines=normal.copy();at=next(i for i,line in enumerate(lines) if line.startswith('BATCH|'+full+'|'));fields=lines[at].split('|');fields[index]='incorrect';lines[at]='|'.join(fields)
    body='\n'.join(lines[:-1])+'\n';altered=(body+'SHA256|'+hashlib.sha256(body.encode()).hexdigest()+'\n').encode();errors=compare(altered,[(prefix,2),(full,3)])
    assert errors==['TRUSTED_SCOPE_AND_CAPTURE_FIELDS'];(O/(name+'.json')).write_text(json.dumps({'recomputed_checksum_valid':True,'mismatches':len(errors),'errors':errors}))
proof={'fresh_real_target_snapshots':5,'checkpoint_all_scope_capture_position_fields_mismatches':0,'independent_comparator_counterexamples':3,'counterexample_checksums_recomputed':True,'target_dml':0,'source_dml':0,'kills_rerun':False,'retention_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
