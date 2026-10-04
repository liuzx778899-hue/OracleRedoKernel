"""Verified ledger corruption and real exclusive-writer tests, no Oracle source changes."""
import hashlib,json,shutil,struct,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/closed-resume-faults-20261005';OLD=ROOT/'lab/out/closed-resume-20261005/after';CONTAINER='oraredo-19c-lab-20261004'
OUT.mkdir(parents=True,exist_ok=False);members=[b['path'] for b in json.loads((OLD/'source.json').read_text())['members']]
expected=hashlib.sha256((OLD/'receipt.json').read_bytes()).hexdigest();base=['docker','exec',CONTAINER,'python3','/tmp/follow_closed_members.py']

def run(name,args,rc=0,reason=None):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if result.returncode!=rc or rc and result.stdout or reason and reason.encode() not in result.stderr:raise RuntimeError(name+': unexpected result; preserve')
    return result

def clone(name):
    target=OUT/name;shutil.copytree(OLD,target);return target

def check(name,target,reason,digest=expected):
    remote='/tmp/ork-closed-resume-fault-'+name+'-20261005'
    run(name+'-copy',['docker','cp',target,CONTAINER+':'+remote]);run(name+'-owner',['docker','exec','--user','root',CONTAINER,'chown','-R','oracle:oinstall',remote])
    run(name,base+[remote,40,1,0]+members+['--resume',digest],1,reason)

target=clone('wrong-receipt-sha');check('wrong-receipt-sha',target,'CAPTURE_RESUME_RECEIPT_DIGEST_MISMATCH','0'*64)
target=clone('truncated-journal');path=target/'sequence-43.oracap';path.write_bytes(path.read_bytes()[:-1]);check('truncated-journal',target,'CAPTURE_RESUME_JOURNAL_DIGEST_MISMATCH')
target=clone('wrong-inode');path=target/'source.json';source=json.loads(path.read_text());source['members'][0]['inode']+=1;path.write_text(json.dumps(source));check('wrong-inode',target,'CAPTURE_RESUME_SOURCE_BINDING_MISMATCH')
target=clone('advanced-next');path=target/'receipt.json';receipt=json.loads(path.read_text());receipt['next_sequence']+=1;path.write_text(json.dumps(receipt));check('advanced-next',target,'CAPTURE_RESUME_LEDGER_INVALID',hashlib.sha256(path.read_bytes()).hexdigest())
target=clone('wrong-scn');path=target/'receipt.json';receipt=json.loads(path.read_text());receipt['captured'][-1]['first_scn']+=1;path.write_text(json.dumps(receipt));check('wrong-scn',target,'CAPTURE_RESUME_CLOSED_HEADER_MISMATCH',hashlib.sha256(path.read_bytes()).hexdigest())
target=clone('sidecar-disagreement');path=target/'sequence-43.receipt.json';receipt=json.loads(path.read_text());receipt['sha256']='0'*64;path.write_text(json.dumps(receipt));check('sidecar-disagreement',target,'CAPTURE_RESUME_SIDECAR_DISAGREEMENT')
target=clone('sidecar-without-journal');shutil.copyfile(target/'sequence-43.receipt.json',target/'sequence-44.receipt.json');check('sidecar-without-journal',target,'CAPTURE_RESUME_SIDECAR_WITHOUT_JOURNAL')
target=clone('orphan-gap');shutil.copyfile(target/'sequence-43.oracap',target/'sequence-45.oracap');check('orphan-gap',target,'CAPTURE_RESUME_ORPHAN_GENERATION_GAP')

def rehash(name,mutation):
    target=clone(name);path=target/'sequence-43.oracap';data=path.read_bytes();m,n=struct.unpack_from('<II',data,8);meta=json.loads(data[16:16+m]);raw=bytearray(data[16+m:16+m+n]);mutation(meta,raw)
    meta['sha256']=hashlib.sha256(raw).hexdigest();encoded=json.dumps(meta,sort_keys=True,separators=(',',':')).encode();frame=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+raw+hashlib.sha256(encoded+raw).digest();path.write_bytes(frame+data[16+m+n+32:]);digest=hashlib.sha256(path.read_bytes()).hexdigest()
    path=target/'receipt.json';receipt=json.loads(path.read_text());receipt['captured'][-1]['sha256']=digest;path.write_text(json.dumps(receipt));(target/'sequence-43.receipt.json').write_text(json.dumps(receipt['captured'][-1]));return target,hashlib.sha256(path.read_bytes()).hexdigest()
target,digest=rehash('rehashed-raw-checksum',lambda meta,raw:raw.__setitem__(40,raw[40]^1));check('rehashed-raw-checksum',target,'CAPTURE_RESUME_RAW_DIGEST_OR_CHECKSUM_MISMATCH',digest)
target,digest=rehash('boolean-source',lambda meta,raw:meta['source'].__setitem__('thread',True));check('boolean-source',target,'CAPTURE_RESUME_FRAME_METADATA_INVALID',digest)

remote='/tmp/ork-closed-follow-20261005';holder=['docker','exec','-i',CONTAINER,'python3','-c',"import fcntl,sys;f=open(sys.argv[1],'a');fcntl.flock(f.fileno(),fcntl.LOCK_EX);print('LOCKED',flush=True);sys.stdin.readline()",remote+'/writer.lock']
(OUT/'holder.command.json').write_text(json.dumps(holder));process=subprocess.Popen(holder,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
try:
    ready=process.stdout.readline();(OUT/'holder-ready.stdout.txt').write_bytes(ready);assert ready.strip()==b'LOCKED'
    run('dual-writer',base+[remote,40,1,0]+members+['--resume',expected],1,'BlockingIOError')
finally:
    stdout,stderr=process.communicate(input=b'\n',timeout=30);(OUT/'holder.stdout.txt').write_bytes(stdout);(OUT/'holder.stderr.txt').write_bytes(stderr);(OUT/'holder.rc.txt').write_text(str(process.returncode))
assert process.returncode==0
run('after-release-resume',base+[remote,40,1,0]+members+['--resume',expected])
run('export-after-release',['docker','cp',CONTAINER+':'+remote,OUT/'after-release'])
assert (OUT/'after-release/receipt.json').read_bytes()==(OLD/'receipt.json').read_bytes()
print(json.dumps({'resume_refusals':11,'real_dual_writer_refused':True,'post_lock_resume':True,'existing_journals_preserved':True,'capture_complete':False,'consumer_ack':False}))
