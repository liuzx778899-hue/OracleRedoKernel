"""Read-only actual reuse refusal plus independently checksum-derived closed-prefix fixtures."""
import hashlib,json,struct,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OLD=ROOT/'lab/out/closed-follow-20261005';CONTAINER='oraredo-19c-lab-20261004'
final_only=sys.argv[1:]==['--final-source-only'];OUT=ROOT/('lab/out/closed-follow-final-20261005' if final_only else 'lab/out/closed-follow-faults-20261005')
OUT.mkdir(parents=True,exist_ok=False)

def run(name,args,expected=0):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),capture_output=True,timeout=120)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if (result.returncode==0)!=(expected==0):raise RuntimeError(name+': unexpected result; preserve')
    return result

for script in ('resume_online_observation.py','follow_closed_members.py'):run('copy-'+script,['docker','cp',ROOT/'lab'/script,CONTAINER+':/tmp/'+script])
base=['docker','exec',CONTAINER,'python3','/tmp/follow_closed_members.py']
members=[item['path'] for item in json.loads((OLD/'capture/source.json').read_text())['members']]
if final_only:
    run('actual-closed-41-42',base+['/tmp/ork-follow-final-20261005',41,1,0]+members)
    run('export-final',['docker','cp',CONTAINER+':/tmp/ork-follow-final-20261005',OUT/'capture'])
    for sequence in (41,42):run('coverage-'+str(sequence),[sys.executable,ROOT/'lab/compare_observation_archive.py',OUT/'capture'/('sequence-'+str(sequence)+'.oracap'),OLD/('archive-'+str(sequence)+'.dbf')])
    print(json.dumps({'final_actual_closed_generations':[41,42],'coverage_differences':0,'capture_complete':False,'consumer_ack':False}));sys.exit(0)
result=run('actual-reused-40',base+['/tmp/ork-follow-real-gap-20261005',40,1,0]+members,1)
assert b'CAPTURE_MISSING_GENERATION' in result.stderr and b'captured_sequence' not in result.stdout

def unpack(sequence):
    data=(OLD/'capture'/('sequence-'+str(sequence)+'.oracap')).read_bytes();offset=0;blocks=[]
    while offset<len(data):
        m,n=struct.unpack_from('<II',data,offset+8);blocks.append(data[offset+16+m:offset+16+m+n]);offset+=16+m+n+32
    return bytearray(b''.join(blocks))

def rechecksum(data,index):
    # XOR checksum word at block header+14, independently of collector implementation.
    offset=index*512;struct.pack_into('<H',data,offset+14,0);value=0
    for word in struct.unpack_from('<256H',data,offset):value^=word
    struct.pack_into('<H',data,offset+14,value)

prefixes=OUT/'prefixes';prefixes.mkdir();original=unpack(41);second=unpack(42)
(prefixes/'good41.raw').write_bytes(original);(prefixes/'good42.raw').write_bytes(second)
bad=bytearray(original);bad[2*512+40]^=1;(prefixes/'checksum.raw').write_bytes(bad)
bad=bytearray(original);struct.pack_into('<I',bad,512+156,0xfffffffe);rechecksum(bad,1);(prefixes/'nab.raw').write_bytes(bad)
bad=bytearray(original);struct.pack_into('<I',bad,2*512+4,3);rechecksum(bad,2);(prefixes/'block-id.raw').write_bytes(bad)
bad=bytearray(original);struct.pack_into('<I',bad,512+24,1276726822);rechecksum(bad,1);(prefixes/'wrong-source.raw').write_bytes(bad)
bad=bytearray(second);struct.pack_into('<I',bad,512+180,struct.unpack_from('<I',bad,512+180)[0]+1);rechecksum(bad,1);(prefixes/'scn-gap.raw').write_bytes(bad)
destination='/tmp/ork-closed-prefix-faults-20261005';run('copy-prefixes',['docker','cp',prefixes,CONTAINER+':'+destination])
prepare="import glob,hashlib,json,os,struct;\nfor p in glob.glob('"+destination+"/*.raw'):\n b=open(p,'rb').read();n=(struct.unpack_from('<I',b,24)[0]+1)*512\n with open(p,'r+b') as f:f.truncate(n)\n print(json.dumps({'path':p,'closed_prefix_bytes':len(b),'prefix_sha256':hashlib.sha256(b).hexdigest(),'fixture_bytes':n,'fixture_sha256':hashlib.sha256(open(p,'rb').read()).hexdigest(),'tail':'synthetic zero sparse tail outside closed NAB'}))"
run('prepare',['docker','exec','--user','root',CONTAINER,'python3','-c',prepare])
run('fixture-owner',['docker','exec','--user','root',CONTAINER,'chown','-R','oracle:oinstall',destination])
run('positive-frozen',base+['/tmp/ork-follow-frozen-positive-20261005',41,1,0,destination+'/good41.raw',destination+'/good42.raw'])
cases=[('checksum',['checksum.raw'],'CAPTURE_CLOSED_BLOCK_CHECKSUM_MISMATCH'),('nab',['nab.raw'],'CAPTURE_CLOSED_BOUNDARY_INVALID'),('block-id',['block-id.raw'],'CAPTURE_CLOSED_BLOCK_IDENTITY_MISMATCH'),('source',['good41.raw','wrong-source.raw'],'CAPTURE_MEMBER_SOURCE_MISMATCH'),('missing',['good42.raw'],'CAPTURE_MISSING_GENERATION'),('duplicate',['good41.raw','wrong-source.raw'],'CAPTURE_MEMBER_SOURCE_MISMATCH'),('scn-gap',['good41.raw','scn-gap.raw'],'CAPTURE_CLOSED_SCN_GAP')]
# Duplicate the same generation using a different pathname, without changing source identity.
run('duplicate-copy',['docker','exec',CONTAINER,'cp',destination+'/good41.raw',destination+'/duplicate41.raw'])
cases[5]=('duplicate',['good41.raw','duplicate41.raw'],'CAPTURE_DUPLICATE_SEQUENCE_MEMBER')
for name,paths,reason in cases:
    result=run(name,base+['/tmp/ork-follow-fault-'+name+'-20261005',41,1,0]+[destination+'/'+p for p in paths],1)
    assert reason.encode() in result.stderr
run('positive-export',['docker','cp',CONTAINER+':/tmp/ork-follow-frozen-positive-20261005',OUT/'positive'])
for sequence in (41,42):run('coverage-'+str(sequence),[sys.executable,ROOT/'lab/compare_observation_archive.py',OUT/'positive'/('sequence-'+str(sequence)+'.oracap'),OLD/('archive-'+str(sequence)+'.dbf')])
print(json.dumps({'real_overwritten_generation_refused':True,'derived_refusals':len(cases),'final_source_positive_generations':[41,42],'capture_complete':False,'consumer_ack':False,'cdc_events':0}))
