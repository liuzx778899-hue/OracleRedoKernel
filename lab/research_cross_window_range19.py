"""Native and raw range inventory only, against the existing once-only transaction."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/cross-window-active-20261007';N=R/'lab/out/cross-window-range-20261007'
N.mkdir(exist_ok=False);(N/'executed-driver.py').write_bytes(Path(__file__).read_bytes())
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
sealed=json.loads((R/'docs/workorders/evidence/RESEARCH-cross-window-active-20261007.json').read_text())
for item in sealed['files']:
    p=R/item['path'];assert sha(p)==item['sha256'] and p.stat().st_size==item['bytes']
def run(name,args,body=None):
    args=list(map(str,args));(N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=body,capture_output=True,timeout=240)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(name,p.returncode)
    return p.stdout.decode()
(N/'classes').mkdir()
shutil.copyfile(R/'lab/CrossWindowRecordProbe.java',N/'executed-probe.java')
run('compile-records',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',O/'kernel.jar','-d',N/'classes',R/'lab/CrossWindowRecordProbe.java'])
run('raw-records',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(N/'classes')+';'+str(O/'kernel.jar'),'CrossWindowRecordProbe','2743256',O/'archive-73.dbf',O/'archive-74.dbf'])
body="whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\nalter session set tracefile_identifier='ORK_CROSS_RANGE';\n"
for seq in [73,74]:body+="alter system dump logfile '/opt/oracle/product/19c/dbhome_1/dbs/arch1_"+str(seq)+"_1245693159.dbf' scn min 2743256 scn max 2743353;\n"
body+="select value from v$diag_info where name='Default Trace File';\nexit success\n"
(N/'native-range.sql').write_text(body)
text=run('native-range',['docker','exec','-i','oraredo-19c-lab-20261004','sqlplus','-s','/','as','sysdba'],body.encode());assert 'SP2-' not in text
paths=[l.strip() for l in text.splitlines() if l.strip().startswith('/opt/oracle/') and l.strip().endswith('.trc')];assert len(paths)==1
run('copy-native-range',['docker','cp','oraredo-19c-lab-20261004:'+paths[0],N/'native-range.trc'])
(N/'input-reference.json').write_text(json.dumps(dict(frozen_input_manifest='docs/workorders/evidence/RESEARCH-cross-window-active-20261007.json',frozen_input_files=len(sealed['files']),frozen_input_unchanged=True,source_business_actions=0,target_actions=0,events=0),indent=2))
print('FROZEN_CROSS_WINDOW_RANGE_INVENTORY_READY')
