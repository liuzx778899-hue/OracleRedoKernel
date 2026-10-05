"""Seal new adapter and target evidence twice; exclude private Maven caches and all secrets."""
import hashlib,json,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-dml-target-20261005';B=R/'lab/out/release-oracle-dml-20261005'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
assert (O/'independent-verify.rc.txt').read_text().strip()=='0'
(O/'independent-verify.command.json').write_text(json.dumps(['C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','lab/verify_oracle_dml_target.py']))
cmd=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi'])+';'+str(O/'classes'),'OracleProjectionProbe','public',str(O/'DMIX/sequence-48.oracap')]
assert not (O/'public-new-adapter.rc.txt').exists()
(O/'public-new-adapter.command.json').write_text(json.dumps(cmd));p=subprocess.run(cmd,capture_output=True,timeout=60)
for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/('public-new-adapter.'+ext+'.txt')).write_bytes(v)
(O/'public-new-adapter.rc.txt').write_text(str(p.returncode));assert p.returncode!=0 and not p.stdout
with zipfile.ZipFile(O/'adapter.jar') as new,zipfile.ZipFile(R/'lab/out/release-oracle-checkpoint-20261005/adapter.jar') as old:
    classes=[n for n in old.namelist() if n.endswith('.class')];assert all(new.read(n)==old.read(n) for n in classes)
sources=['integration/streamhouse-engine-oracle/pom.xml']+[p.relative_to(R).as_posix() for p in (R/'integration/streamhouse-engine-oracle/src').rglob('*.java')]+['lab/'+n for n in ['OracleDmlCheckpointWorker.java','OracleProjectionProbe.java','build_oracle_dml_adapter.py','prepare_oracle_dml_target.py','configure_oracle_dml_target.py','run_oracle_dml_target.py','verify_oracle_dml_target.py','seal_oracle_dml_target.py']]
seals=[];dest=O/'sealed-current-sources';dest.mkdir()
for name in sources:
    p=R/name;out=dest/name;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out)
    result=subprocess.run(['git','show','HEAD:'+name],cwd=R,capture_output=True)
    seals.append({'path':name,'actual_source_sha256':sha(p),'prior_head_blob_sha256':hashlib.sha256(result.stdout).hexdigest() if result.returncode==0 else None,'equals_prior_head_blob_after_lf_normalization':p.read_bytes().replace(b'\r\n',b'\n')==result.stdout.replace(b'\r\n',b'\n') if result.returncode==0 else None,'stage':'post-execution current source; failed execution-time prepare source separately preserved'})
files=[]
paths=[p for p in O.rglob('*') if p.is_file()]+[p for p in B.rglob('*') if p.is_file() and 'm2' not in p.relative_to(B).parts]
for p in sorted(paths):
    assert 'secret' not in p.name.lower();a=sha(p);assert a==sha(p)
    files.append({'path':p.relative_to(R).as_posix(),'bytes':p.stat().st_size,'sha256':a})
commands=[]
for folder in [O,B]:
    for p in sorted(folder.glob('*.rc.txt')):
        name=p.name[:-7];commands.append({'path':p.relative_to(R).as_posix(),'rc':int(p.read_text().strip()),'stdout_bytes':(folder/(name+'.stdout.txt')).stat().st_size,'stderr_bytes':(folder/(name+'.stderr.txt')).stat().st_size})
proof={'artifact':json.loads((B/'proof.json').read_text()),'target':json.loads((O/'target-proof.json').read_text()),'independent':json.loads((O/'independent-proof.json').read_text()),'all_adapter_runtime_classes_equal_previous_accepted':True,'public_decoder_rc':int((O/'public-new-adapter.rc.txt').read_text()),'sha256_checked_twice':True,'source_seal':seals,'commands':commands,'files':files,'product_site_release_accepted':False}
(R/'docs/workorders/evidence/DELIVERY-oracle-dml-target-20261005.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8');print(json.dumps({'files':len(files),'commands':len(commands),'double_sha_equal':True,'public_refused':True}))
