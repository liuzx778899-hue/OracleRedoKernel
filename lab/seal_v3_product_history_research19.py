"""Seal the refused product-baseline window and diagnostic split evidence."""
import hashlib,json,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006';N=R/'lab/out/v3-product-runtime-20261006'
D=R/'docs/workorders/evidence/RESEARCH-v3-product-index-maintenance-20261006.json';assert not D.exists()
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for b in iter(lambda:stream.read(1048576),b''):h.update(b)
    return h.hexdigest()
previous=R/'docs/workorders/evidence/DELIVERY-v3-product-acquisition-20261006.json';prior=json.loads(previous.read_text())
for item in prior['files']:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes'],item['path']
for name in ['history-research-audit','history-research-state']:
    for path in N.glob(name+'.*'):
        if 'secret' not in path.name:shutil.copyfile(path,H/('product-'+path.name))
for path in [R/'lab/capture_v3_product_history19.py',R/'lab/AcquiredHistoricalProjectionProbe.java',R/'lab/DictionaryDiscoveryResearchProbe.java',R/'lab/project_v3_product_history19.py',R/'lab/extract_v3_history_discovery19.py',R/'lab/IndexMaintenanceResearchProbe.java',R/'lab/research_v3_index_maintenance19.py',R/'lab/verify_v3_index_split_rows19.py',R/'lab/IndexMaintenanceGraphResearchProbe.java',Path(__file__),R/'lab/UndoInventoryProbe.java',R/'lab/SysUndoHistoryProbe.java']:
    shutil.copyfile(path,H/('sealed-'+path.name))
assert (H/'collector.rc.txt').read_text().strip()=='0'
assert (H/'first-projection.rc.txt').read_text().strip()=='1'
assert (H/'first-projection.stdout.txt').stat().st_size==0
assert 'DICTIONARY_DISCOVERY_INDEX_COVERAGE' in (H/'first-projection.stderr.txt').read_text()
assert 'UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT' in (H/'maintenance-control-graph.stderr.txt').read_text()
assert (H/'maintenance-control-graph.stdout.txt').stat().st_size==0
rows=json.loads((H/'index-split-rows-research.json').read_text());assert rows['native_byte_differences']==0 and len(rows['negative_detected'])==4
assert (H/'maintenance-raw-graph.rc.txt').read_text().strip()=='0'
audit=(H/'product-history-research-audit.stdout.txt').read_text();assert 'BINDINGS|0' in audit and 'EVENTS|0' in audit and 'LOGGING|NO|NO' in audit and 'OLD|11|blocking-closed|new-note' in audit
files=[]
for path in sorted(H.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert digest==sha(path)
    files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(actual_product_baseline=json.loads((H/'capture-reference.json').read_text())['baseline'],sequences=[70,71],source_actions_once_only=True,source_logging_changes=0,collector_terminal_rc=0,capture_archive_data_block_differences=0,capture_complete_claimed=False,online_headers_normalized=False,events=0,projection='REFUSED',reason='DICTIONARY_DISCOVERY_INDEX_COVERAGE',lifecycle='REFUSED:UNSUPPORTED_TRANSACTION_START_FLAGS_OR_PARENT',diagnostic_rows=rows,diagnostic_original_graph=dict(nodes=5,closed=True,lifecycle_verified=False),product_binding_count=0,product_event_count=0,target_actions=0,historical_http_publication=False,checkpoint_ack=False,site_accepted=False,release_accepted=False,previous_capsule=dict(path=previous.relative_to(R).as_posix(),files=len(prior['files']),unchanged=True),double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for item in files:assert sha(R/item['path'])==item['sha256']
print(json.dumps(dict(files=len(files),previous_unchanged=len(prior['files']),events=0,postcheck=True)))
