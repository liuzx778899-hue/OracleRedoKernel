"""Additional frozen actual-frame corruption and live exclusive-writer tests; no source DML."""
import hashlib,json,shutil,struct,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/online-resume-faults-20261005';CAPTURE=ROOT/'lab/out/online-resume-20261005/resumed';CONTAINER='oraredo-19c-lab-20261004'
OUT.mkdir(parents=True,exist_ok=False)

def run(name,args,expected=0):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),capture_output=True,timeout=90)
    (OUT/(name+'.stdout.txt')).write_bytes(result.stdout);(OUT/(name+'.stderr.txt')).write_bytes(result.stderr);(OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if (result.returncode==0)!=(expected==0) or expected and result.stdout:raise RuntimeError(name+': unexpected result; preserve')
    return result

script='/tmp/ork-resume-observation-final.py'
run('copy-script',['docker','cp',ROOT/'lab/resume_online_observation.py',CONTAINER+':'+script])
base=['docker','exec',CONTAINER,'python3',script];original=(CAPTURE/'observations.oracap').read_bytes();binding=json.loads((CAPTURE/'source.json').read_text())

def clone(name):
    target=OUT/name;shutil.copytree(CAPTURE,target);return target

def upload(name,target):
    destination='/tmp/ork-resume-faults-'+name
    run(name+'-copy',['docker','cp',target,CONTAINER+':'+destination])
    run(name+'-owner',['docker','exec','--user','root',CONTAINER,'chown','-R','oracle:oinstall',destination]);return destination

def reseal(target,data):
    (target/'observations.oracap').write_bytes(data)
    receipt=json.loads((target/'receipt.json').read_text());receipt.update(bytes=len(data),sha256=hashlib.sha256(data).hexdigest());(target/'receipt.json').write_text(json.dumps(receipt))

# Rewrite a real stable data frame and BOTH envelope + prefix hashes: raw checksum must still refuse.
offset=0
while offset<len(original):
    m,n=struct.unpack_from('<II',original,offset+8);end=offset+16+m+n+32;metadata=json.loads(original[offset+16:offset+16+m])
    if metadata['block']>=2 and metadata['state']=='OBSERVED_STABLE':break
    offset=end
payload=bytearray(original[offset+16+m:offset+16+m+n]);payload[40]^=1
metadata['sha256']=hashlib.sha256(payload).hexdigest();encoded=json.dumps(metadata,sort_keys=True,separators=(',',':')).encode()
frame=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+payload+hashlib.sha256(encoded+payload).digest()
target=clone('resealed-checksum');reseal(target,original[:offset]+frame+original[end:]);remote=upload('resealed-checksum',target);run('resealed-checksum',base+['--verify',remote],1)

metadata['block']=-1;encoded=json.dumps(metadata,sort_keys=True,separators=(',',':')).encode();frame=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+payload+hashlib.sha256(encoded+payload).digest()
target=clone('resealed-negative-block');reseal(target,original[:offset]+frame+original[end:]);remote=upload('resealed-negative-block',target);run('resealed-negative-block',base+['--verify',remote],1)
target=clone('missing-receipt');(target/'receipt.json').unlink();remote=upload('missing-receipt',target);run('missing-receipt',base+['--verify',remote],1)

# Hold the same flock from an independent OS process and require a competing collector to refuse.
target=clone('exclusive-writer');remote=upload('exclusive-writer',target)
holder=['docker','exec',CONTAINER,'python3','-c',"import fcntl,sys;f=open(sys.argv[1],'a');fcntl.flock(f.fileno(),fcntl.LOCK_EX);print('LOCKED',flush=True);sys.stdin.readline()",remote+'/writer.lock']
holder.insert(2,'-i')
(OUT/'holder.command.json').write_text(json.dumps(holder))
process=subprocess.Popen(holder,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
try:
    ready=process.stdout.readline();(OUT/'holder-ready.stdout.txt').write_bytes(ready)
    if ready.strip()!=b'LOCKED':raise RuntimeError('writer holder not ready')
    run('exclusive-writer',base+[binding['path'],remote,binding['bounded_blocks'],1,0,'--resume'],1)
finally:
    stdout,stderr=process.communicate(input=b'\n',timeout=30);(OUT/'holder.stdout.txt').write_bytes(stdout);(OUT/'holder.stderr.txt').write_bytes(stderr);(OUT/'holder.rc.txt').write_text(str(process.returncode))
assert process.returncode==0
run('after-lock-release',base+[binding['path'],remote,binding['bounded_blocks'],1,0,'--resume'])
run('final-verify',base+['--verify',remote])
run('export-after-release',['docker','cp',CONTAINER+':'+remote,OUT/'after-release'])
assert (OUT/'after-release/observations.oracap').read_bytes().startswith(original)
print(json.dumps({'additional_refusals':4,'post_lock_resume':True,'capture_complete':False,'consumer_ack':False,'cdc_events':0}))
