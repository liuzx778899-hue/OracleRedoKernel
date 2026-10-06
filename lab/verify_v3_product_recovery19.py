"""Actual HTTP recovery refuses damaged V3 files and metadata; restore every owned mutation."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/v3-product-runtime-20261006';S='oraredo-19c-lab-20261004'
resume=sys.argv[1:] in [['--resume-after-file'],['--resume-after-preflight']]
sql_repair=sys.argv[1:]==['--resume-after-preflight']
assert not (N/'recovery-proof.json').exists()
if sql_repair:
    assert 'SP2-0341' in (N/'missing-history-poison.stdout.txt').read_text()
    assert json.loads((N/'after-sp2-unmodified.stdout.txt').read_text())[1]['data']==json.loads((N/'initial-read.stdout.txt').read_text())[1]['data']
elif resume:assert (N/'recovery-first.failure-from-tool.txt').exists() and not (N/'missing-history-poison.command.json').exists()
else:assert not (N/'retained-pristine.snapshot').exists()
(N/('executed-recovery-r3-driver.py' if sql_repair else 'executed-recovery-r2-driver.py' if resume else 'executed-recovery-driver.py')).write_bytes(Path(__file__).read_bytes())
row=json.loads((N/'initial-read.stdout.txt').read_text())[1]['data'];identifier=str(row['id']);path=N/'snapshots'/(identifier+'.snapshot')
original=path.read_bytes();assert hashlib.sha256(original).hexdigest()==row['snapshotSha256']
if resume:assert (N/'retained-pristine.snapshot').read_bytes()==original
else:(N/'retained-pristine.snapshot').write_bytes(original)

def http(name,accepted):
    p=subprocess.run([sys.executable,str(R/'lab/v3_product_runtime19.py'),'verify',name],capture_output=True,timeout=180)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.driver.'+suffix)).write_bytes(value)
    assert p.returncode==0,p.stderr.decode();result=json.loads((N/(name+'.stdout.txt')).read_text())
    assert (result[1]['code']==0)==accepted,(name,result[1]['code'])
    if accepted:assert result[1]['data']==row
    return result

if resume:
    assert json.loads((N/'truncated-file-refused.stdout.txt').read_text())[1]['code']!=0
    assert json.loads((N/'file-exact-restore.stdout.txt').read_text())[1]['data']==row
else:
    path.write_bytes(original[:-1]);(N/'retained-truncated.snapshot').write_bytes(path.read_bytes())
    try:http('truncated-file-refused',False)
    finally:path.write_bytes(original)
    http('file-exact-restore',True)

def clob(value):
    return '\n||'.join("to_clob('"+value[i:i+2000].replace("'","''")+"')" for i in range(0,len(value),2000))

def replace(name,before,after):
    body=("whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nalter session set container=ORKPDB;\nbegin\n"
        "update ORKV3CTL_20261006.SH_ORACLE_ACQUISITION set METADATA_JSON=\n"+clob(after)+"\nwhere ID="+identifier+
        "\nand dbms_lob.compare(METADATA_JSON,\n"+clob(before)+")=0;\nif SQL%ROWCOUNT!=1 then raise_application_error(-20001,'METADATA_SCOPE_MISMATCH');end if;\ncommit;\nend;\n/\nexit success\n")
    (N/(name+'.sql')).write_text(body)
    args=['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'];(N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=body.encode(),capture_output=True,timeout=180)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==0 and b'SP2-' not in p.stdout,name

metadata=row['metadataJson'];negatives=['missing-history','missing-layout-column','wrong-row-value','wrong-index-address','wrong-index-role']
for mode in negatives:
    value=json.loads(metadata);history=value['dictionaryBaseline']
    if mode=='missing-history':del value['dictionaryBaseline']
    elif mode=='missing-layout-column':history['layouts']['COL$']['columns'].pop()
    elif mode=='wrong-row-value':history['rows'][0]['values'][0]['hex']='c10d'
    elif mode=='wrong-index-address':history['indexes'][0]['indexDba']+=1
    elif mode=='wrong-index-role':history['indexes'][0]['role']['dataObject']+=1
    changed=json.dumps(value,separators=(',',':'));assert changed!=metadata
    attempt=mode+('-r3' if sql_repair else '')
    replace(attempt+'-poison',metadata,changed)
    try:http(attempt+'-refused',False)
    finally:replace(attempt+'-exact-restore',changed,metadata)
    http(attempt+'-restored-read',True)
assert path.read_bytes()==original
out=dict(file_refusals=1,metadata_refusals=negatives,all_restored_get_fields_equal=True,snapshot_exact_restore=True,
    acquisition_id=identifier,state='ACQUIRED_ONLY',source_dml=0,target_actions=0,historical_publication=False)
(N/'recovery-proof.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
