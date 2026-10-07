"""Seal the final seven-record physical closure, preserving the incomplete and rejected candidates."""
import hashlib,json,shutil,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/dictionary-split-candidate-20261006'
D=R/'docs/workorders/evidence/DELIVERY-autonomous-dictionary-split-20261006.json';assert not D.exists()
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for b in iter(lambda:stream.read(1048576),b''):h.update(b)
    return h.hexdigest()
prior=[]
for name in ['DELIVERY-dictionary-leaf-rows-r2-20261006.json','DELIVERY-dictionary-leaf-rows-20261006.json','DELIVERY-dictionary-maintenance-pairs-20261006.json','DELIVERY-index-maintenance-begin-20261006.json','RESEARCH-v3-product-index-maintenance-20261006.json','DELIVERY-v3-product-acquisition-20261006.json']:
    p=R/'docs/workorders/evidence'/name;m=json.loads(p.read_text())
    for f in m['files']:assert sha(R/f['path'])==f['sha256'] and (R/f['path']).stat().st_size==f['bytes'],f['path']
    prior.append(dict(path=p.relative_to(R).as_posix(),files=len(m['files']),unchanged=True))
tests=[0,0,0,0]
for p in (N/'kernel-r3-reports').glob('TEST-*.xml'):
    root=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):tests[i]+=int(root.attrib[k])
assert tests==[309,0,0,0]
proof=json.loads((N/'r3-split-proof.json').read_text());assert len(proof['negative'])==12 and proof['archive_capture_equal'] and proof['repeat_equal']
for case in proof['negative']:assert case['structure_rc']==0 and case['split_rc']==1 and case['events']==0
positive=(N/'r3-split.stdout.txt').read_bytes();assert positive==(N/'r3-captured-split.stdout.txt').read_bytes()==(N/'r3-repeat.stdout.txt').read_bytes()
text=positive.decode();assert 'xid=Xid[usn=9, slot=9, sequence=660]' in text and 'beginRecordScn=2567426, endRecordScn=2567427' in text
assert '|original=5|allocation=Position[archiveSequence=70, block=126610, offset=188, vector=1]' in text
assert '|end=Position[archiveSequence=70, block=126620, offset=204, vector=2]|before=445|new=220|old=226|oldPrev=null|slot=7|' in text
assert 'rawHex=0101004089a4003304c308345602c105' in text
native=(N/'native-between-before-new.txt').read_text();assert 'OP:13.7' in native and 'OP:13.6' in native and 'Head:  0x0040512f Tail:  0x00405131' in native and 'LCLR (lock clear)' in native
assert (N/'r2-split.rc.txt').read_text()=='1' and (N/'r2-split.stdout.txt').stat().st_size==0 and 'UNCLASSIFIED_ACTIVE_SPLIT_FORWARD' in (N/'r2-split.stderr.txt').read_text()
compat=json.loads((N/'r3-class-compatibility.json').read_text());assert compat['previous_classes']==119 and compat['unchanged'] and len(compat['added'])==3
for p in [R/'src/main/java/com/streamhouse/oracle/ArchivedDictionaryIndexSplit.java',R/'lab/ArchivedDictionarySplitProbe.java',R/'lab/CapturedDictionarySplitProbe.java',R/'lab/build_autonomous_split19.py',R/'lab/verify_dictionary_split19.py',R/'lab/verify_autonomous_split_final19.py',Path(__file__)]:shutil.copyfile(p,N/('sealed-'+p.name))
(N/'review-dispositions.json').write_text(json.dumps(dict(first_candidate='INCOMPLETE: known-page audit missing; positive is not accepted closure',r2_candidate='REFUSED: uncovered 13.7/13.6 freelist record',r3_candidate='BOUNDED_PHYSICAL_CLOSURE_ACCEPTED; business events not authorized',driver_failures=['captured-split: duplicate classpath produced ClassNotFoundException; stderr preserved','opcode locator: 200-byte range too short for 8380-byte before record; frozen driver preserved','manual field table locator: offset28 instead of32; struct.error retained in tool transcript']),indent=2))
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower():continue
    digest=sha(p);assert digest==sha(p);files.append(dict(path=p.relative_to(R).as_posix(),bytes=p.stat().st_size,sha256=digest))
out=dict(tests=tests,kernel_sha256=sha(N/'kernel-r3.jar'),class_compatibility=compat,physical_proof=proof,records=7,original_nodes=5,nontransactional_freelist_control=1,end_rebuild=1,old_previous_link=None,ccnt_interpreted_as_xid=False,business_history_integrated=False,events=0,source_actions=0,product_build=False,target_actions=0,checkpoint_ack=False,site_accepted=False,release_accepted=False,previous=prior,double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for f in files:assert sha(R/f['path'])==f['sha256']
print(json.dumps(dict(files=len(files),tests=tests,kernel=out['kernel_sha256'],physical_records=7,negative=12,events=0)))
