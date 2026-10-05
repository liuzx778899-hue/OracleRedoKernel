"""Frozen actual V3 captures: complete historical business events and independent evidence; no source writes."""
import hashlib,json,re,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent
N=R/'lab/out/historical-row-schema-candidate-20261006';O=R/'lab/out/v3-historical-raw-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (N/'historical-row-proof.json').exists()
(N/'executed-row-driver.py').write_bytes(Path(__file__).read_bytes())
args=json.loads((N/'actual-historical-batch-r1.command.json').read_text())
def run(name,command,expected=0):
    assert not (N/(name+'.command.json')).exists()
    command=list(map(str,command));(N/(name+'.command.json')).write_text(json.dumps(command))
    p=subprocess.run(command,capture_output=True,timeout=240)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:
        (N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==expected,(name,p.stderr.decode())
    if expected:assert not p.stdout,(name,'partial events published')
    return p
positive=(N/'actual-historical-batch-r2.stdout.txt').read_text().splitlines()
assert run('historical-repeat',args).stdout.decode().splitlines()==positive
erased=args[:8]+[str(O/'erased-68.oracap'),sha(O/'erased-68.oracap'),str(O/'erased-69.oracap'),sha(O/'erased-69.oracap')]
e=run('historical-erased-markers',erased).stdout.decode().splitlines()
assert e[1:]==positive[1:] and e[0]!=positive[0]
records={}
dump=(O/'dump.trc').read_text(errors='replace')
for part in dump.split('REDO RECORD - Thread:')[1:]:
    m=re.match(r'1 RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',part)
    if m:records[tuple(int(x,16) for x in m.groups())]='REDO RECORD - Thread:'+part
def compare(lines):
    assert len(lines)==7
    proof=[]
    baseline=json.loads((O/'baseline-truth-proof.json').read_text());assert baseline['differences']==0
    for i,(seq,block,offset,scn,xid,before,after,cols) in enumerate([
        (68,34027,16,2536404,(10,25,2305),'dictionary-restored','history-v3-added',4),
        (69,12,16,2536423,(5,27,602),'history-v3-added','history-v3-restored',3)]):
        schema,event,row=lines[1+i*3:4+i*3];at=f'{seq}:{block}:{offset}:4'
        assert schema.startswith('SCHEMA|'+at+'|')
        assert schema.count('PhysicalColumn[')==cols and 'name=NOTE, type=VARCHAR2, nullable=true, hidden=true' in schema
        assert ('name=EXTRA_V3' in schema)==(cols==4)
        assert event.split('|')[3:]==[at,f'Xid[usn={xid[0]}, slot={xid[1]}, sequence={xid[2]}]',str(scn),str(scn)]
        common='VALUE:80,VALUE:';tail=',VALUE:616e63686f72'+(',NULL:' if cols==4 else '')
        assert row==f'ROW|{at}|75185|50342532|0|UPDATE|{common}{before.encode().hex()}{tail}|{common}{after.encode().hex()}{tail}'
        native=records[(seq,block,offset)]
        (N/f'native-business-{seq}-{block}-{offset}.txt').write_text(native)
        assert int(re.search(r'^SCN: 0x([0-9a-f]+)',native,re.M)[1],16)==scn
        assert f'xid:  0x{xid[0]:04x}.{xid[1]:03x}.{xid[2]:08x}' in native
        changes=re.split(r'CHANGE #\d+ ',native)[1:]
        heap=next(c for c in changes if 'OP:11.5 ' in c.splitlines()[0])
        undo=next(c for c in changes if 'OP:5.1 ' in c.splitlines()[0])
        for c,wanted in [(heap,after),(undo,before)]:
            m=re.search(r'col\s+1:\s+\[(\d+)\]\s+((?:[0-9a-f]{2}\s*)+)',c)
            assert m and int(m[1])==len(wanted) and bytes.fromhex(m[2])==wanted.encode()
            assert 'bdba: 0x03002a84' in c and 'tabn: 0 slot: 0' in c
        phase='ADD' if i==0 else 'DROP';truth=(O/(phase+'-once.stdout.txt')).read_text()
        assert f'XID|{xid[0]}.{xid[1]}.{xid[2]}' in truth
        assert f'TRUTH|{phase}|0|{after}|anchor'+('|<NULL>' if cols==4 else '') in truth
        assert truth.count(phase+'_COL|')==cols
        proof.append(dict(position=at,commit_scn=scn,xid=xid,columns=cols,native_changed_before_after_equal=True,independent_phase_sql_equal=True))
    assert lines[1].split('|')[2]!=lines[4].split('|')[2]
    return proof
proof=compare(positive)
for name,at,old,new in [('before',3,'64696374696f6e617279','64696374696f6e617278'),('schema',1,'name=EXTRA_V3','name=EXTRA_BAD'),('xid',2,'slot=25','slot=24')]:
    bad=list(positive);assert old in bad[at];bad[at]=bad[at].replace(old,new)
    try:compare(bad)
    except AssertionError:pass
    else:raise AssertionError('independent comparator missed '+name)
try:compare([positive[0],*positive[4:],*positive[1:4]])
except AssertionError:pass
else:raise AssertionError('independent comparator missed order')
original=(O/'capture/sequence-68.oracap').read_bytes();frames=[];at=0
while at<len(original):
    m,n=struct.unpack_from('<II',original,at+8);assert n==512
    frames.append((json.loads(original[at+16:at+16+m]),original[at+16+m:at+16+m+n]));at+=16+m+n+32
negative=[]
for rawpath in sorted(O.glob('DERIVED-history-*.dbf')):
    raw=rawpath.read_bytes();encoded=bytearray()
    for meta,old in frames:
        meta=dict(meta);block=meta['block'];payload=raw[block*512:(block+1)*512] if block>=2 else old
        assert len(payload)==512
        meta['sha256']=hashlib.sha256(payload).hexdigest();m=json.dumps(meta,sort_keys=True,separators=(',',':')).encode()
        encoded+=b'ORACAP01'+struct.pack('<II',len(m),512)+m+payload+hashlib.sha256(m+payload).digest()
    path=N/(rawpath.stem+'.oracap');path.write_bytes(encoded)
    p=run(rawpath.stem+'-complete-events',args[:8]+[str(path),sha(path),*args[10:]],1)
    negative.append(dict(name=rawpath.stem,rc=1,events=0,reason=p.stderr.decode().splitlines()[0],input_sha256=sha(path)))
assert len(negative)==7
out=dict(kernel_sha256=sha(N/'kernel.jar'),baseline_sha256=args[6],complete_events=2,events=proof,repeat_byte_equal=True,erased_marker_event_schema_id_payload_byte_equal=True,erased_input_batch_digest_differs=True,comparator_counterexamples=4,complete_factory_refusals=negative,source_actions=0,target_actions=0,product_rebuilt=False,public_cdc_authorized=False,release_accepted=False)
(N/'historical-row-proof.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
