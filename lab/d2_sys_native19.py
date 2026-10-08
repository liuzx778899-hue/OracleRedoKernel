"""Independent native dump of frozen new archives only. No source business replay."""
from pathlib import Path
import json, shutil, subprocess
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d2-sys-cross-member-20261008';S='oraredo-19c-lab-20261004'
assert not (N/'executed-native-driver.py').exists()
shutil.copyfile(__file__,N/'executed-native-driver.py')
archives=json.loads((N/'archives.json').read_text());baseline=json.loads((N/'baseline-reference.json').read_text())
body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 feedback off heading off lines 32767\nalter session set tracefile_identifier=ORK_D2_SYS_CROSS_20261008;\n'
body+=''.join("alter system dump logfile '"+a['sourcePath']+"' scn min "+str(baseline['scn'])+";\n" for a in archives)
body+="select value from v$diag_info where name='Default Trace File';\nexit success\n"
(N/'native.sql').write_bytes(body.encode())
p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True,timeout=180)
for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/('native.'+suffix)).write_bytes(value)
assert p.returncode==0 and b'SP2-' not in p.stdout
traces=[s.strip() for s in p.stdout.decode().splitlines() if s.strip().startswith('/opt/oracle/') and s.strip().endswith('.trc')]
assert len(traces)==1
p=subprocess.run(['docker','cp',S+':'+traces[0],str(N/'dump.trc')],capture_output=True,timeout=180)
for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/('native-copy.'+suffix)).write_bytes(value)
assert p.returncode==0
print('NATIVE_FROZEN sourceBusinessActions=0')
