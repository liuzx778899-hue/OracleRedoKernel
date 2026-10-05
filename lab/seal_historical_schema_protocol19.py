"""Seal protocol binaries and product compatibility checks without claiming historical publication."""
import hashlib,json,shutil,subprocess,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-schema-protocol-20261006';P=R/'lab/out/streamhouse-oracle-gate-worktree'
dest=R/'docs/workorders/evidence/DELIVERY-historical-schema-protocol-20261006.json';assert not dest.exists()
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
for manifest in ['DELIVERY-historical-projection-20261006.json','DELIVERY-historical-row-schema-20261006.json']:
    for f in json.loads((R/'docs/workorders/evidence'/manifest).read_text())['files']:
        p=R/f['path'];assert sha(p)==f['sha256'] and p.stat().st_size==f['bytes']
for name in ['build_historical_schema_protocol19.py','OracleHistoricalSchemaEvidenceProbe.java','verify_historical_schema_protocol19.py','verify_schema_protocol_legacy19.py','seal_historical_schema_protocol19.py']:shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
for p in [R/'src/main/java/com/streamhouse/oracle/CapturedHistoricalHeapBatch.java',R/'integration/streamhouse-engine-oracle/src/main/java/com/streamhouse/engines/oracle/OracleHistoricalBatchProjection.java',
    P/'server/streamhouse-spi/src/main/java/com/streamhouse/spi/OracleBatchEvidence.java',P/'server/streamhouse-spi/src/main/java/com/streamhouse/spi/OracleSchemaSequence.java',
    P/'server/streamhouse-spi/src/test/java/com/streamhouse/spi/OracleSchemaSequenceTest.java',P/'server/streamhouse-app/src/main/java/com/streamhouse/app/baseline/OracleInputBindingService.java',
    P/'server/streamhouse-app/src/test/java/com/streamhouse/app/baseline/OracleInputBindingServiceTest.java']:shutil.copyfile(p,N/('sealed-'+p.name))
shutil.copyfile(P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar',N/'product.jar')
reports=N/'product-reports';reports.mkdir();total=[0,0,0,0]
for p in (P/'server').glob('*/target/surefire-reports/TEST-*.xml'):
    shutil.copyfile(p,reports/(p.parents[2].name+'-'+p.name));t=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):total[i]+=int(t.attrib[k])
assert total==[743,0,0,0],total
assert (N/'product-build.rc.txt').read_text(encoding='utf-8-sig').strip()=='0'
with zipfile.ZipFile(N/'product.jar') as z:
    libs={}
    for name in ['oracle-redo-kernel','streamhouse-engine-oracle','streamhouse-spi']:
        paths=[p for p in z.namelist() if p.startswith('BOOT-INF/lib/'+name+'-') and p.endswith('.jar')];assert len(paths)==1
        data=z.read(paths[0]);path=N/('product-embedded-'+name+'.jar');path.write_bytes(data);libs[name]=dict(path=paths[0],sha256=sha(path))
with zipfile.ZipFile(N/'spi.jar') as a,zipfile.ZipFile(N/'product-embedded-streamhouse-spi.jar') as b:
    for name in ['OracleBatchEvidence','OracleSchemaSequence']:
        path='com/streamhouse/spi/'+name+'.class';assert a.read(path)==b.read(path)
for name,args,cwd in [('kernel-build',['--batch-mode','package'],R),('spi-build',['--batch-mode','-pl','streamhouse-spi','-am','package'],P/'server'),('product-build',['--batch-mode','clean','package'],P/'server')]:
    (N/(name+'.command.json')).write_text(json.dumps(dict(executable='D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd',args=args,cwd=str(cwd),JAVA_HOME='D:/work/Java/jdk-17.0.7',recorded_after_execution_from_tool_invocation=True)))
build=json.loads((N/'protocol-build-proof.json').read_text());proof=json.loads((N/'schema-evidence-proof.json').read_text());legacy=json.loads((N/'legacy-protocol-proof.json').read_text())
assert build['sha256']['kernel']==proof['kernel_sha256']==sha(N/'kernel.jar')
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower() or p.name.startswith('protocol-seal-driver.'):continue
    a=sha(p);assert a==sha(p);files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
out=dict(kernel_adapter_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),product_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip(),build=build,schema_evidence=proof,legacy_impact=legacy,product_tests=total,product_sha256=sha(N/'product.jar'),product_embedded_libraries=libs,spi_protocol_class_bytes_match=True,previous_75_and_189_unchanged=True,files_count=len(files),double_sha256_verified=True,product_historical_decoder_enabled=False,source_actions=0,target_actions=0,http_historical_accepted=False,checkpoint_ack_accepted=False,site_accepted=False,release_accepted=False,files=files)
dest.write_text(json.dumps(out,indent=2))
for f in files:assert sha(R/f['path'])==f['sha256']
print(json.dumps(dict(files=len(files),product_code_commit=out['product_code_commit'],product_sha256=out['product_sha256'],product_tests=total,embedded_libraries=libs,postcheck_all_hashes_match=True)))
