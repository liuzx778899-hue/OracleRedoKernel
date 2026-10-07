"""Independent native/value comparison and full-input checksum-valid marker refusals."""
import hashlib,json,re,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/cross-window-active-20261007';N=R/'lab/out/cross-window-range-20261007'
assert not (N/'events-proof.json').exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
native=(N/'native-range.trc').read_text()
records={}
for m in re.finditer(r'REDO RECORD - Thread:1 RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+).*?(?=REDO RECORD -|\Z)',native,re.S):
    text=m.group();at=tuple(int(x,16) for x in m.groups());scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',text)[1],16)
    changes={int(q[1]):q.group() for q in re.finditer(r'CHANGE #(\d+).*?(?=CHANGE #|\Z)',text,re.S)}
    records[at]=(scn,changes)
def column(text):
    m=re.search(r'col\s+1: \[\s*(\d+)\]\s*((?:[0-9a-f]{2}\s+)+)',text)
    assert m
    raw=''.join(m[2].split());assert len(raw)==int(m[1])*2
    return 'VALUE:'+raw
before=column(records[(73,219164,16)][1][3]);middle=column(records[(73,219164,16)][1][1]);after=column(records[(74,19,16)][1][2])
assert before==after and middle==column(records[(74,19,16)][1][1])
assert 'Begin trans' in records[(73,219164,16)][1][3]
assert 'xid:  0x000a.00e.00000b35' in records[(73,219164,16)][1][3] and 'xid:  0x000a.00e.00000b35' in records[(74,19,16)][1][1]
assert 'slt: 0x000e sqn: 0x00000b35' in records[(74,19,372)][1][1]
def verify(text):
    q=[x.split('|') for x in text.splitlines()];events=[x for x in q if x[0]=='EVENT'];rows=[x for x in q if x[0]=='ROW'];schemas=[x for x in q if x[0]=='SCHEMA']
    assert len(events)==len(rows)==len(schemas)==2
    for i,at in enumerate(['73:219164:16:3','74:19:16:1']):
        assert events[i][3:]==[at,'Xid[usn=10, slot=14, sequence=2869]',str(records[(73,219164,16)][0]),str(records[(74,19,372)][0])]
        assert rows[i][1:6]==[at,'75185','50342532','0','UPDATE']
        assert rows[i][6:]==['VALUE:80,'+(before if i==0 else middle)+',VALUE:616e63686f72','VALUE:80,'+(middle if i==0 else after)+',VALUE:616e63686f72']
        assert schemas[i][1:3]==[at,'9a0aac66a1babbb575e9ce68fb60383fecd6006707bc6570af5a065e079dbde0']
        assert 'name=NOTE, type=VARCHAR2, nullable=true, hidden=true' in schemas[i][3]
        assert re.fullmatch('[a-f0-9]{64}',events[i][1]) and re.fullmatch('[a-f0-9]{64}',events[i][2])
    assert len({x[1] for x in events})==2
text=(N/'candidate-closed.stdout.txt').read_text();verify(text)
detected=0
for old,new in [('sequence=2869','sequence=2870'),('|2743344','|2743345'),('VALUE:616e63686f72','VALUE:616e63686f73'),('74:19:16:1','74:19:16:2')]:
    try:verify(text.replace(old,new));raise RuntimeError('comparison failed to detect poison')
    except AssertionError:detected+=1
b=json.loads((O/'baseline-reference.json').read_text());base=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(N/'event-classes')+';'+str(N/'candidate-kernel.jar'),'CapturedHistoricalBatchProbe',b['path'],b['sha256'],'50342539',str(O/'capture/sequence-73.oracap'),sha(O/'capture/sequence-73.oracap')]
def run(name,args):
    name += '-r2'
    (N/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    return p
p=run('repeat-events',base+[str(O/'capture/sequence-74.oracap'),sha(O/'capture/sequence-74.oracap')]);assert p.returncode==0 and p.stdout==(N/'candidate-closed.stdout.txt').read_bytes()
raw=(O/'capture/sequence-74.oracap').read_bytes();negative=[]
for name,index in [('lower-unobserved',0),('upper-not-adjacent',8),('padding',6)]:
    data=bytearray();offset=0;edits=0
    while offset<len(raw):
        assert raw[offset:offset+8]==b'ORACAP01';m,n=struct.unpack_from('<II',raw,offset+8);offset+=16
        meta=json.loads(raw[offset:offset+m]);block=bytearray(raw[offset+m:offset+m+n]);offset+=m+n+32
        if meta['block']==3:
            needle=bytes.fromhex('0ddc29000000000017dc290000800000');at=block.find(needle);assert at>=0 and block.count(needle)==1
            block[at+index]^=2 if name=='lower-unobserved' else 1;struct.pack_into('<H',block,14,0);checksum=0
            for value in struct.unpack('<256H',block):checksum^=value
            struct.pack_into('<H',block,14,checksum);assert __import__('functools').reduce(int.__xor__,struct.unpack('<256H',block))==0
            meta['sha256']=hashlib.sha256(block).hexdigest();edits+=1
        encoded=json.dumps(meta,sort_keys=True,separators=(',',':')).encode();data+=b'ORACAP01'+struct.pack('<II',len(encoded),len(block))+encoded+block+hashlib.sha256(encoded+block).digest()
    assert edits==1
    path=N/('derived-'+name+'-r2.oracap');path.write_bytes(data)
    p=run('reject-'+name,base+[str(path),sha(path)]);assert p.returncode==1 and not p.stdout and b'UNSUPPORTED_MEDIA_RECOVERY_MARKER_RANGE' in p.stderr
    negative.append(dict(name=name,sha256=sha(path),rc=1,stdout_bytes=0,checksum_valid=True,scope='derived input only'))
proof=dict(kernel_sha256=sha(N/'candidate-kernel.jar'),tests=[313,0,0,0],events=2,same_xid=True,begin_record_scn=2743312,commit_record_scn=2743344,native_before_after_changes=4,complete_baseline_cells_preserved=True,differences=0,comparison_poisons_detected=detected,repeat_bytes_equal=True,negatives=negative,pending_rc=1,pending_stdout_bytes=0,cross_publication_recovery='NOT_VERIFIED',target_actions=0)
assert int((N/'candidate-pending.rc.txt').read_text())==1 and not (N/'candidate-pending.stdout.txt').read_bytes()
(N/'events-proof.json').write_text(json.dumps(proof,indent=2));(N/'executed-events-verifier.py').write_bytes(Path(__file__).read_bytes());print(json.dumps(proof))
