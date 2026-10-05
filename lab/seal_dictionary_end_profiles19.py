"""Seal candidate/profile evidence and verify the prior closed evidence stays unchanged."""
import hashlib,json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-end-profiles-20261006';OLD=R/'lab/out/dictionary-anchor-real-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
prior=json.loads((R/'docs/workorders/evidence/DELIVERY-dictionary-anchor-real-20261006.json').read_text())
for name,digest in prior['files'].items():assert sha(OLD/name)==digest,name
proof=json.loads((O/'proof.json').read_text());rows=json.loads((O/'row-counterexample-proof.json').read_text());negative=json.loads((O/'negative-end-proof.json').read_text())
native=json.loads((O/'native-full-physical.stdout.txt').read_text());assert native['mismatches']==0
assert proof['results'][2]['rc']==0 and proof['results'][2]['events']==2 and rows['first_event_columns']==3
assert all(p['rc']==1 and p['stdout_bytes']==0 for p in negative['results'])
for p in [R/'lab/verify_dictionary_end_profiles19.py',R/'lab/compare_dictionary_end_rows19.py',R/'lab/negative_dictionary_end19.py',Path(__file__),R/'lab/CapturedRowsProbe.java',R/'lab/PhysicalTransactionProbe.java',R/'lab/compare_typed_physical.py',R/'lab/compare_physical_transactions.py']:
    (O/('sealed-source-'+p.name)).write_bytes(p.read_bytes())
files=[p for p in O.rglob('*') if p.is_file() and 'secret' not in p.name.lower()]
first={p.relative_to(O).as_posix():sha(p) for p in files};assert first=={p.relative_to(O).as_posix():sha(p) for p in files}
manifest={'profile_proof':proof,'row_counterexample':rows,'native_full_physical':native,'negative_proof':negative,'prior_closed_manifest_files_verified':len(prior['files']),'timing':'kernel r1 prebuild pins; successful r2 revised sources pinned after build/before replay; physical helper/comparator sealed after execution; negative r3 executed source retained before cleanup of unused assignment','failures_preserved':['kernel r1 media marker flag byte offset mistake; corrected raw 0x8000 is at bytes4/12, not6/14; source/tests/reports/log preserved','replay r1 native comparator Match slice error; source/log preserved, resumes only readonly comparison','negative r1 selected foreign end tail, rc0 stable target events; wrong selection preserved','negative r2 assumed 24-byte record header, actual target header68; source/log preserved; r3 selects actual target vector3'],'goal_complete':False,'historical_P0_fully_closed':False,'product_HTTP_verified':False,'source_actions':0,'target_actions':0,'hash_scope':'all nonsecret evidence; manifest itself excluded','files':first}
(R/'docs/workorders/evidence/DELIVERY-dictionary-end-profiles-20261006.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print('DICTIONARY_END_PROFILES_SEALED|'+str(len(files))+'|PRIOR_FILES_UNCHANGED|'+str(len(prior['files'])))
