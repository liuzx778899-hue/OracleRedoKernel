"""Marker-erased complete raw events and the existing adapter projection, without product/target writes."""
import copy,hashlib,json,struct,subprocess,sys
from types import SimpleNamespace
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006';N=R/'lab/out/split-history-candidate-20261007';A=N/sys.argv[1]
V=N/'projection-r2';V.mkdir(exist_ok=True);frozen=V/'executed-projection-driver-r3.py';assert not frozen.exists();frozen.write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args):
    args=list(map(str,args))
    if (V/(name+'.command.json')).exists():
        assert json.loads((V/(name+'.command.json')).read_text())==args and (V/(name+'.rc.txt')).read_text()=='0'
        return SimpleNamespace(stdout=(V/(name+'.stdout.txt')).read_bytes(),stderr=(V/(name+'.stderr.txt')).read_bytes())
    (V/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=900)
    for ext,b in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(V/(name+'.'+ext)).write_bytes(b)
    assert p.returncode==0,(name,p.stderr[-800:]);print(name,0,flush=True);return p
j='D:/work/Java/jdk-17.0.7/bin/';cp=str(A/'kernel.jar')+';'+str(A/'classes');receipt='323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'
base=[j+'java.exe','-Xmx1536m','-cp',cp];baseline=[H/'actual-product-v3.snapshot',receipt];captured=[];erased=[];changes=[]
for seq in [70,71]:
    path=H/f'capture/sequence-{seq}.oracap';captured.extend([path,sha(path)])
    rows=(N/('negative/structure-'+str(seq)+'.stdout.txt')).read_text().splitlines();markers=[]
    for line in rows:
        q=line.split('|')
        if int(q[4])>2566695 and int(q[6])==677737149 and q[7].startswith('24.'):
            assert ',' not in q[7];markers.append((int(q[0]),int(q[1]),int(q[7].split('.')[1])))
    original=path.read_bytes();frames=[];blocks=[];at=0
    while at<len(original):
        m,n=struct.unpack_from('<II',original,at+8);assert n==512
        frames.append(json.loads(original[at+16:at+16+m]));blocks.append(bytearray(original[at+16+m:at+16+m+n]));at+=16+m+n+32
    virtual=b''.join(bytes(b[16:]) for b in blocks[2:]);mutations=[]
    for bi,offset,minor in markers:
        s=(bi-2)*496+offset-16;header=68 if virtual[s+4]&4 else 24;assert virtual[s+header:s+header+2]==bytes([24,minor])
        for step in range(2):
            p=s+header+step;b=2+p//496;o=16+p%496;mutations.append(dict(block=b,offset=o,old=blocks[b][o],new=0));blocks[b][o]=0
    encoded=bytearray()
    for meta,b in zip(frames,blocks):
        struct.pack_into('<H',b,14,0);checksum=0
        for word in struct.unpack('<256H',b):checksum^=word
        struct.pack_into('<H',b,14,checksum);meta=dict(meta);meta['sha256']=hashlib.sha256(b).hexdigest()
        m=json.dumps(meta,sort_keys=True,separators=(',',':')).encode();encoded+=b'ORACAP01'+struct.pack('<II',len(m),512)+m+b+hashlib.sha256(m+b).digest()
    target=N/f'projection/DERIVED-erased-{seq}.oracap';assert target.read_bytes()==encoded;erased.extend([target,sha(target)])
    assert (N/f'projection/erased-structure-{seq}.rc.txt').read_text()=='0'
    changes.append(dict(sequence=seq,markers=len(markers),mutations=mutations,sha256=sha(target)))
assert sum(c['markers'] for c in changes)>=2
original=(A/'capture-business.stdout.txt').read_bytes();value=run('erased-business',base+['CapturedHistoricalBatchProbe',*baseline,'50342539',*erased]).stdout
assert value.splitlines()[1:]==original.splitlines()[1:] and value.splitlines()[0]!=original.splitlines()[0]
history=run('erased-history',base+['ArchivedDictionaryHistoryEvidenceProbe',*baseline,'captures',*erased]).stdout
assert history==(A/'capture-history.stdout.txt').read_bytes()
jackson=list((H/'jackson').glob('jackson-*.jar'));assert len(jackson)==3
cp=';'.join(map(str,[A/'kernel.jar',H/'adapter.jar',H/'spi.jar',A/'classes',*jackson]))
run('compile-projection',[j+'javac.exe','-cp',cp,'-d',A/'classes',R/'lab/AcquiredHistoricalProjectionProbe.java'])
projection=[j+'java.exe','-Xmx1536m','-cp',cp,'AcquiredHistoricalProjectionProbe',*baseline,'50342539']
actual=json.loads(run('actual-projection',projection+captured).stdout);alternate=json.loads(run('erased-projection',projection+erased).stdout)
assert len(actual['envelopes'])==len(actual['evidence']['events'])==2 and actual['evidence']['baselineSha256']==receipt
for value in [actual,alternate]:
    assert hashlib.sha256(json.dumps(value['envelopes'],sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()==value['envelopesSha256']
    for event in value['envelopes']:assert event['metadata']['captureInput']=='oracle-batch-sha256:'+value['evidence']['batchSha256']
left=copy.deepcopy(actual['envelopes']);right=copy.deepcopy(alternate['envelopes'])
for event in left+right:del event['metadata']['captureInput']
assert left==right and actual['evidence']['schemaSequenceSha256']==alternate['evidence']['schemaSequenceSha256']
assert actual['evidence']['batchSha256']!=alternate['evidence']['batchSha256']
for i,(before,after,columns) in enumerate([('history-v3-restored','history-product-added',4),('history-product-added','history-product-restored',3)]):
    event=left[i];assert event['operation']=='UPDATE' and event['changedColumns']==['TXT']
    assert event['before']==dict(ID=0,TXT=before,NOTE='anchor',**({'EXTRA_HP':None} if columns==4 else {}))
    assert event['after']==dict(ID=0,TXT=after,NOTE='anchor',**({'EXTRA_HP':None} if columns==4 else {}))
(V/'actual-projection.json').write_text(json.dumps(actual,indent=2))
(V/'projection-proof.json').write_text(json.dumps(dict(kernel_sha256=sha(A/'kernel.jar'),adapter_sha256=sha(H/'adapter.jar'),spi_sha256=sha(H/'spi.jar'),markers=changes,history_equal=True,business_event_bytes_equal=True,envelope_semantics_equal=True,full_before_after_equal=True,capture_input_changes_with_batch=True,schema_sequence_sha256=actual['evidence']['schemaSequenceSha256'],envelopes_sha256=actual['envelopesSha256'],events=2,source_actions=0,target_actions=0,product_http=False,checkpoint_ack=False),indent=2))
print('COMPLETE_HISTORY_ADAPTER_PROJECTION|2',flush=True)
