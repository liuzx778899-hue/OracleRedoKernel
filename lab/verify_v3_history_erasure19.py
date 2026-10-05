"""New capture marker erasure preserves complete SYS history; no source actions."""
import hashlib,json,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v3-historical-raw-20261006';K=O/'new-kernel.jar'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (O/'history-erasure-proof.json').exists();(O/'executed-erasure-driver.py').write_bytes(Path(__file__).read_bytes())
def run(name,args,expected=0):
    args=list(map(str,args));assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==expected,p.stderr.decode();return p
ref=json.loads((O/'capture-reference.json').read_text());b=ref['baseline'];classes=O/'classes';cp=str(K)+';'+str(classes)
run('compile-erasure-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',K,'-d',classes,R/'lab/CapturedRecordsProbe.java'])
java=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp]
positions={}
for seq in ref['sequences']:
    p=O/'capture'/('sequence-'+str(seq)+'.oracap')
    records=run('actual-structure-'+str(seq),java+['CapturedRecordsProbe',p,sha(p),'records']).stdout.decode().splitlines()
    for line in records:
        block,offset,length,validity,scn,subscn,uid,opcodes=line.split('|')
        if int(scn)>b['scn'] and int(uid)==677737149 and opcodes.startswith('24.'):
            assert ',' not in opcodes
            positions.setdefault(seq,[]).append((int(block),int(offset),int(scn),int(opcodes.split('.')[1])))
assert sum(map(len,positions.values()))>=2,positions
derived=[];mutation=[]
for seq in ref['sequences']:
    source=O/'capture'/('sequence-'+str(seq)+'.oracap');original=source.read_bytes();frames=[];blocks=[];at=0
    while at<len(original):
        m,n=struct.unpack_from('<II',original,at+8);assert n==512
        frames.append(json.loads(original[at+16:at+16+m]));blocks.append(bytearray(original[at+16+m:at+16+m+n]));at+=16+m+n+32
    changed=[bytearray(v) for v in blocks];changes=[]
    virtual=b''.join(bytes(v[16:]) for v in blocks[2:])
    for block,offset,scn,minor in positions.get(seq,[]):
        start=(block-2)*496+offset-16;header=68 if virtual[start+4]&4 else 24
        assert virtual[start+header:start+header+2]==bytes([24,minor])
        for step in range(2):
            p=start+header+step;bi=2+p//496;bo=16+p%496
            changes.append({'block':bi,'offset':bo,'old':changed[bi][bo],'new':0,'record_scn':scn});changed[bi][bo]=0
    for bi,(a,v) in enumerate(zip(blocks,changed)):
        offsets={c['offset'] for c in changes if c['block']==bi}
        assert all(x==y for i,(x,y) in enumerate(zip(a,v)) if i not in offsets)
    encoded=bytearray()
    for meta,payload in zip(frames,changed):
        struct.pack_into('<H',payload,14,0);checksum=0
        for word in struct.unpack('<256H',payload):checksum^=word
        struct.pack_into('<H',payload,14,checksum);meta=dict(meta);meta['sha256']=hashlib.sha256(payload).hexdigest()
        m=json.dumps(meta,sort_keys=True,separators=(',',':')).encode()
        encoded+=b'ORACAP01'+struct.pack('<II',len(m),512)+m+payload+hashlib.sha256(m+payload).digest()
    path=O/('erased-'+str(seq)+'.oracap');path.write_bytes(encoded);derived.extend([str(path),sha(path)])
    mutation.append({'sequence':seq,'original_sha256':sha(source),'derived_sha256':sha(path),'changes':changes,'other_raw_bytes_unchanged_except_block_checksums':True})
    run('erased-structure-'+str(seq),java+['CapturedRecordsProbe',path,sha(path),'records'])
(O/'erasure-mutations.json').write_text(json.dumps(mutation,indent=2))

p=run('erased-complete-history',java+['ArchivedDictionaryHistoryEvidenceProbe',b['path'],b['sha256'],'captures',*derived])
assert p.stdout==(O/'actual-capture-history.stdout.txt').read_bytes()
(O/'history-erasure-proof.json').write_text(json.dumps(dict(kernel_sha256=sha(K),mutated_markers=sum(len(v) for v in positions.values()),complete_history_output_byte_equal=True,events=0,source_actions=0,target_actions=0),indent=2))
print(json.dumps(dict(markers=sum(len(v) for v in positions.values()),complete_history_equal=True,events=0)))
