"""Same candidate over frozen real/DERIVED inputs; no source or target actions."""
import hashlib,json,re,shutil,struct,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-end-profiles-20261006';OLD=R/'lab/out/dictionary-anchor-real-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert (O/'kernel-full-r2.rc').read_text().strip()=='0'
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',O/'kernel.jar')
tests={k:sum(int(ET.parse(p).getroot().get(k,0)) for p in (R/'target/surefire-reports').glob('TEST-*.xml')) for k in ['tests','failures','errors','skipped']}
assert tests==dict(tests=269,failures=0,errors=0,skipped=0)
if not (O/'kernel-tests-r2').exists():shutil.copytree(R/'target/surefire-reports',O/'kernel-tests-r2')
(O/'replay-execution-pin-r2.json').write_text(json.dumps({'kernel_sha256':sha(O/'kernel.jar'),'script_sha256':sha(Path(__file__)),'source_pin_timing':'r1 before failed build; revised sources pinned after successful r2 build, before replay; r2 driver resumes only native comparison after recorded replay','revised_sources':{str(p.relative_to(R)):sha(p) for p in [R/'src/main/java/com/streamhouse/oracle/TransactionEnd.java',R/'src/main/java/com/streamhouse/oracle/HistoricalSchemaBarrier.java']},'source_actions':0,'target_actions':0},indent=2))
def run(name,args,expected=None):
    if (O/(name+'.command.json')).exists():
        assert json.loads((O/(name+'.command.json')).read_text())==list(map(str,args))
        rc=int((O/(name+'.rc.txt')).read_text());assert expected is None or rc==expected
        return subprocess.CompletedProcess(args,rc,(O/(name+'.stdout.txt')).read_bytes(),(O/(name+'.stderr.txt')).read_bytes())
    (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode))
    assert expected is None or p.returncode==expected,(name,p.stderr[-1500:]);return p
results=[]
for name in ['actual-v2-batch','erased-v2-batch','erased-no-anchor-control']:
    args=json.loads((OLD/(name+'.command.json')).read_text());i=args.index('-cp')+1;args[i]=str(O/'kernel.jar')+';'+';'.join(args[i].split(';')[1:])
    p=run(name,args);events=sum(l.startswith(b'EVENT|') for l in p.stdout.splitlines())
    results.append({'case':name,'rc':p.returncode,'events':events,'stdout_bytes':len(p.stdout),'stderr_head':p.stderr.decode(errors='replace').splitlines()[:1]})
(O/'replay-current.json').write_text(json.dumps(results,indent=2))
for item in results[:2]:assert item['rc']==1 and item['stdout_bytes']==0 and 'TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY' in item['stderr_head'][0]
# The diagnostic control is evidence to inspect, never historical publication approval.
passed=[]
for name,command in [('mixed','mixed-dml-capture-20261005/raw-complete-batch.command.json'),('empty','empty-dml-capture-20261005/complete-batch-r2.command.json')]:
    old=R/'lab/out'/command;args=json.loads(old.read_text());i=args.index('-cp')+1;args[i]=str(O/'kernel.jar')+';'+';'.join(args[i].split(';')[1:])
    assert run('affected-static-'+name,args,0).stdout==old.with_name(old.name.replace('command.json','stdout.txt')).read_bytes();passed.append(name)
# Match the exact raw identity/status/tail/ext to the independently emitted native text.
raw=(OLD/'raw-unsupported-end-profiles.stdout.txt').read_text().splitlines();entries=[];current=None
for line in raw:
    if line.startswith('UNSUPPORTED_END|'):
        p=line.split('|');current={'sequence':int(p[1]),'block':int(p[2]),'offset':int(p[3]),'fields':[]};entries.append(current)
    elif line.startswith('FIELD|'):current['fields'].append(bytes.fromhex(line.split('|')[2]))
assert len(entries)==2
native_checks=[]
for entry in entries:
    f=entry['fields'];slot=struct.unpack_from('<H',f[0])[0];sequence=struct.unpack_from('<I',f[0],4)[0];flags=struct.unpack_from('<I',f[0],16)[0]
    dba,wrap=struct.unpack_from('<IH',f[1]);record=f[1][6];ext=struct.unpack_from('<H',f[1],8)[0]
    text=(OLD/('unsupported-end-native-'+'-'.join(str(entry[k]) for k in ['sequence','block','offset'])+'.txt')).read_text()
    m=re.search(r'slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) srt: (\d+) sta: (\d+) flg: 0x([0-9a-f]+).*?uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+) ext: (\d+)',text)
    assert m and tuple(int(x,16) for x in [m[1],m[2],m[5],m[6],m[7],m[8]])==(slot,sequence,flags,dba,wrap,record) and int(m[9])==ext and (m[3],m[4])==('0','9')
    native_checks.append({k:entry[k] for k in ['sequence','block','offset']})
proof={'tests':tests,'kernel_sha256':sha(O/'kernel.jar'),'results':results,'native_identity_tail_ext_zero_differences':native_checks,'extra_control_semantics_interpreted':False,'affected_static_byte_equal':passed,'source_actions':0,'target_actions':0,'product_HTTP_verified':False,'general_historical_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
