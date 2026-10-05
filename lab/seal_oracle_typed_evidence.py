"""Seal typed SPI/adapter, failed rounds and real frozen pipeline transport evidence."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;B=R/'lab/out/release-oracle-typed-evidence-20261005';O=R/'lab/out/oracle-typed-evidence-20261005';P=R/'lab/out/streamhouse-oracle-gate-worktree'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
proof=json.loads((B/'proof.json').read_text());verification=json.loads((O/'proof.json').read_text())
assert [x['events'] for x in verification['cases']]==[2,2,3,0]
files=[];commands=[]
for root in [B,O]:
    for p in sorted(root.rglob('*')):
        if not p.is_file() or 'm2' in p.relative_to(root).parts:continue
        assert 'secret' not in p.name.lower();a=sha(p);assert a==sha(p)
        files.append({'path':p.relative_to(R).as_posix(),'bytes':p.stat().st_size,'sha256':a})
    for p in sorted(root.glob('*.rc.txt')):
        n=p.name[:-7];commands.append({'path':p.relative_to(R).as_posix(),'rc':int(p.read_text()),'stdout_bytes':(root/(n+'.stdout.txt')).stat().st_size,'stderr_bytes':(root/(n+'.stderr.txt')).stat().st_size})
product_sources=['server/streamhouse-spi/src/main/java/com/streamhouse/spi/OracleBatchEvidence.java','server/streamhouse-spi/src/main/java/com/streamhouse/spi/CapDecoder.java']+['server/streamhouse-pipeline/src/main/java/com/streamhouse/pipeline/service/'+n+'.java' for n in ['DecodeBatchService','PipelineFacade','PublishGateService']]
source_names=['integration/streamhouse-engine-oracle/pom.xml','integration/streamhouse-engine-oracle/src/main/java/com/streamhouse/engines/oracle/OracleBatchProjection.java']+['lab/'+n for n in ['OracleTypedEvidenceProbe.java','OracleLegacyDecodeResultProbe.java','build_oracle_typed_evidence.py','verify_oracle_typed_evidence.py','verify_typed_capacity.py','verify_legacy_typed_spi.py','seal_oracle_typed_evidence.py']]
manifest={'release':proof,'verification':verification,'capacity':json.loads((O/'capacity-proof.json').read_text()),'legacy':json.loads((O/'legacy-proof.json').read_text()),'failed_adapter_compile_preserved':True,'failed_outer_legacy_comparison_preserved':True,'files':files,'commands':commands,'double_sha_equal':True,'sources':[{'path':n,'sha256':sha(R/n)} for n in source_names],'product_sources':[{'path':n,'sha256':sha(P/n)} for n in product_sources],'prior_oracle_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),'independent_review':'PENDING','product_trusted_registry_accepted':False,'public_oracle_decoder_enabled':False,'deployment_site_release_accepted':False}
(R/'docs/workorders/evidence/DELIVERY-oracle-typed-evidence-20261005.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8');print(json.dumps({'files':len(files),'commands':len(commands),'double_sha_equal':True}))
