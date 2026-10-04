"""Read-only dedicated Linux source proof; fresh output, no DML or log switch."""
import hashlib,json,shutil,struct,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/online-resume-20261005';CONTAINER='oraredo-19c-lab-20261004'
continuing=sys.argv[1:]==['--continue-after-copy-permission']
if not continuing:OUT.mkdir(parents=True,exist_ok=False)

def run(name,args,expected=0,input_data=None):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args)),ensure_ascii=False))
    result=subprocess.run(list(map(str,args)),input=input_data,capture_output=True,timeout=90)
    for extension,data in [('stdout',result.stdout),('stderr',result.stderr)]: (OUT/(name+'.'+extension+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if (result.returncode==0)!=(expected==0):raise RuntimeError(name+': unexpected rc; preserve evidence')
    if expected and result.stdout:raise RuntimeError(name+': refusal leaked stdout')
    return result.stdout

binding=run('binding-continuation' if continuing else 'binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).decode().strip()
assert binding=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
sql=b"whenever sqlerror exit failure\nset pages 0 lines 32767 feedback off heading off\nselect 'CURRENT|'||l.sequence#||'|'||f.member from v$log l join v$logfile f on l.group#=f.group# where l.status='CURRENT' order by f.member;\nexit success\n"
text=(OUT/'inventory.stdout.txt').read_text() if continuing else run('inventory',['docker','exec','-i',CONTAINER,'sqlplus','-s','/','as','sysdba'],input_data=sql).decode()
members=[line.strip().split('|') for line in text.splitlines() if line.strip().startswith('CURRENT|')];assert len(members)==1
source=members[0][2];sequence=int(members[0][1]);remote='/tmp/ork-online-resume-20261005';script='/tmp/ork-resume-observation.py'
if not continuing:run('script-copy',['docker','cp',ROOT/'lab/resume_online_observation.py',CONTAINER+':'+script])
base=['docker','exec',CONTAINER,'python3',script]
if not continuing:
    run('first',base+[source,remote,4096,2,0.2])
    run('export-first',['docker','cp',CONTAINER+':'+remote,OUT/'first'])
    run('resume',base+[source,remote,4096,2,0.2,'--resume'])
    run('verify',base+['--verify',remote])
    run('export-resume',['docker','cp',CONTAINER+':'+remote,OUT/'resumed'])
original=(OUT/'first/observations.oracap').read_bytes();resumed=(OUT/'resumed/observations.oracap').read_bytes()
assert resumed.startswith(original)
frames=[];offset=0
while offset<len(resumed):
    start=offset;m,n=struct.unpack_from('<II',resumed,offset+8);offset+=16+m+n+32;frames.append((start,offset))
assert len(frames)>2

def clone(name):
    target=OUT/name;shutil.copytree(OUT/'resumed',target);return target

def test(name,target,expected,source_resume=False):
    destination='/tmp/ork-resume-proof-'+name
    run(name+'-copy',['docker','cp',target,CONTAINER+':'+destination])
    run(name+'-copy-owner',['docker','exec','--user','root',CONTAINER,'chown','-R','oracle:oinstall',destination])
    return run(name,base+([source,destination,4096,1,0,'--resume'] if source_resume else ['--verify',destination]),expected)

# Complete durable frames ahead of the last receipt can be recovered without deleting bytes.
tail='complete-tail-r2' if continuing else 'complete-tail'
target=clone(tail);(target/'receipt.json').write_text(json.dumps({'bytes':0,'sha256':hashlib.sha256(b'').hexdigest(),'observations':0}))
test(tail,target,0,True)
run('complete-tail-export',['docker','cp',CONTAINER+':/tmp/ork-resume-proof-'+tail,OUT/'complete-tail-recovered'])
assert (OUT/'complete-tail-recovered/observations.oracap').read_bytes().startswith(resumed)

for name,mutate in [
    ('truncated-byte',lambda b:b[:-1]),
    ('lost-complete-frame',lambda b:b[:frames[-1][0]]),
    ('bad-envelope',lambda b:b[:20]+bytes([b[20]^1])+b[21:]),
    ('duplicate-frame',lambda b:b+b[frames[0][0]:frames[0][1]]),
]:
    target=clone(name);(target/'observations.oracap').write_bytes(mutate(resumed));test(name,target,1)
target=clone('torn-tail');(target/'observations.oracap').write_bytes(resumed+b'ORACAP01');test('torn-tail',target,1)
target=clone('bad-count');receipt=json.loads((target/'receipt.json').read_text());receipt['observations']+=1;(target/'receipt.json').write_text(json.dumps(receipt));test('bad-count',target,1)
target=clone('receipt-inside-frame');receipt=json.loads((target/'receipt.json').read_text());receipt['bytes']=frames[0][1]-1;receipt['sha256']=hashlib.sha256(resumed[:receipt['bytes']]).hexdigest();(target/'receipt.json').write_text(json.dumps(receipt));test('receipt-inside-frame',target,1)
target=clone('wrong-inode');binding=json.loads((target/'source.json').read_text());binding['inode']+=1;(target/'source.json').write_text(json.dumps(binding));test('wrong-inode',target,1,True)
target=clone('wrong-generation');binding=json.loads((target/'source.json').read_text());binding['generation']['sequence']+=1;(target/'source.json').write_text(json.dumps(binding));test('wrong-generation',target,1,True)
summary={'source_sequence':sequence,'initial_frames':json.loads((OUT/'first/receipt.json').read_text())['observations'],'frames_verified':len(frames),'initial_bytes':len(original),'resumed_bytes':len(resumed),'original_prefix_preserved':True,'negative_cases':9,'capture_complete':False,'group_follow':False,'consumer_ack':False,'cdc_events':0,'source_actions':'read-only; no DML/config/log-switch'}
(OUT/'proof.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary))
