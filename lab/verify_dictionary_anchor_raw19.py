"""New actual V2 raw refusal and checksum-valid DERIVED marker erasure; no source actions."""
import hashlib,json,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-anchor-real-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,expected=0):
    if (O/(name+'.command.json')).exists():
        assert json.loads((O/(name+'.command.json')).read_text())==list(map(str,args))
        rc=int((O/(name+'.rc.txt')).read_text());assert rc==expected
        return subprocess.CompletedProcess(args,rc,(O/(name+'.stdout.txt')).read_bytes(),(O/(name+'.stderr.txt')).read_bytes())
    (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr)
    (O/(name+'.rc.txt')).write_text(str(p.returncode))
    assert p.returncode==expected,(name,p.stderr[-1200:]);return p
ref=json.loads((O/'capture-reference.json').read_text());b=ref['baseline']
assert sha(Path(b['path']))==b['sha256']
classes=O/'parser-classes';classes.mkdir(exist_ok=True)
leafsource=(R/'lab/RawDmlLeafProbe.java').read_text().replace('public class RawDmlLeafProbe','public class RawBootstrapLeafProbe').replace(' || record.scn()<=snapshot.scn()','')
leafpath=O/'RawBootstrapLeafProbe.java';leafpath.write_text(leafsource)
probes=[leafpath,*[R/'lab'/(n+'.java') for n in ['CapturedBatchProbe','VerifiedHeapRowsProbe','RawSchemaMarkerProbe','CapturedRecordsProbe']]]
cp=str(O/'kernel.jar')+';'+str(classes)
run('compile-raw-probes',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,*probes])
(O/'raw-execution-pins-r3.json').write_text(json.dumps({str(p.relative_to(R)):sha(p) for p in [*probes,*classes.glob('*'),O/'kernel.jar',Path(__file__)]},indent=2))
inputs=[]
for seq in ref['sequences']:
    p=O/'capture'/('sequence-'+str(seq)+'.oracap');inputs.extend([str(p),sha(p)])
java=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp]
leaf=run('raw-bootstrap-leaf',java+['RawBootstrapLeafProbe',b['path'],b['sha256'],*inputs]).stdout.decode().strip().split('|')[1]
(O/'raw-leaf.txt').write_text(leaf)
batch=java+['CapturedBatchProbe',b['path'],b['sha256'],leaf]
actual=run('actual-v2-batch',batch+inputs,1);assert not actual.stdout
assert b'UNSUPPORTED_HISTORICAL_SCHEMA_MARKER_OPCODE' in actual.stderr
markers=run('raw-markers',java+['RawSchemaMarkerProbe',*inputs]).stdout.decode().splitlines()
positions={}
for seq in ref['sequences']:
    p=O/'capture'/('sequence-'+str(seq)+'.oracap')
    records=run('actual-structure-'+str(seq),java+['CapturedRecordsProbe',p,sha(p),'records']).stdout.decode().splitlines()
    for line in records:
        block,offset,length,validity,scn,subscn,uid,opcodes=line.split('|')
        if int(scn)>b['scn'] and int(uid)==677737149 and opcodes.startswith('24.'):
            assert ',' not in opcodes
            positions.setdefault(seq,[]).append((int(block),int(offset),int(scn),int(opcodes.split('.')[1])))
assert sum(map(len,positions.values()))==3,positions
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
erased=run('erased-v2-batch',batch+derived,1);assert not erased.stdout
assert b'TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:' in erased.stderr
# Diagnostic V1 projection is DERIVED and is never granted acquisition authority.
v2=Path(b['path']).read_text();v1='\n'.join(l for l in v2.splitlines() if not l.startswith(('DICT_ROLE|','DICT_ANCHOR|','DICT_COLUMN|')))+'\n'
v1=v1.replace('ORK_HEAP_SNAPSHOT_V2','ORK_HEAP_SNAPSHOT_V1');p=O/'derived-no-anchor-v1.snapshot.txt';p.write_text(v1)
control=run('erased-no-anchor-control',java+['CapturedBatchProbe',p,sha(p),leaf,*derived],1)
events=sum(l.startswith(b'EVENT|') for l in control.stdout.splitlines());assert events==0 and not control.stdout
assert b'UNSUPPORTED_TRANSACTION_END_EXTENSION' in control.stderr
passed=[]
for name,command in [('mixed','mixed-dml-capture-20261005/raw-complete-batch.command.json'),('empty','empty-dml-capture-20261005/complete-batch-r2.command.json')]:
    old=R/'lab/out'/command;args=json.loads(old.read_text());i=args.index('-cp')+1;args[i]=str(O/'kernel.jar')+';'+';'.join(args[i].split(';')[1:])
    assert run('affected-static-'+name,args).stdout==old.with_name(old.name.replace('command.json','stdout.txt')).read_bytes();passed.append(name)
proof={'actual_v2_rc':1,'actual_refusal':'UNSUPPORTED_HISTORICAL_SCHEMA_MARKER_OPCODE (24.4)','actual_stdout_bytes':0,'derived_scope':'all three post-baseline PDB schema-family vectors: one 24.4 and two 24.1; all SYS/DML fields remain unchanged','derived_erasure_structure_passed':True,'erased_v2_rc':1,'erased_v2_stdout_bytes':0,'erased_no_anchor_diagnostic_rc':1,'erased_no_anchor_diagnostic_reason':'UNSUPPORTED_TRANSACTION_END_EXTENSION','erased_no_anchor_diagnostic_events':events,'same_new_capture_missing_column_reproduction_proven':False,'affected_static_byte_equal':passed,'kernel_sha256':sha(O/'kernel.jar'),'source_actions_replayed':False,'target_actions':0,'product_HTTP_verified':False,'general_historical_dictionary_accepted':False}
(O/'raw-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
