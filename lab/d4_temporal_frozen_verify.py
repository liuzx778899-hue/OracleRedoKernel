"""Read the already frozen temporal input after the historical-type gate repair; no database actions."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d4-temporal-source-20261009'
multi=sys.argv[1:]==['--repaired-multi'];assert multi or sys.argv[1:]==[]
O=N/('parser-r8' if multi else 'parser-r4');O.mkdir(exist_ok=False)
shutil.copyfile(__file__,O/'executed.py');sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert (N/'stop.rc').read_text().strip()=='0' and (N/'events.rc').read_text().strip()=='1'
assert (R/('lab/out/d4-temporal-20261009/kernel-multi-r7.rc' if multi else 'lab/out/d4-temporal-20261009/kernel-history-r4.rc')).read_text().strip()=='0'
if multi:assert (N/'parser-r4/events.rc').read_text().strip()=='1'
libs=O/'libs';libs.mkdir()
for p in (N/'libs').glob('*.jar'):
 if p.name!='kernel.jar':shutil.copyfile(p,libs/p.name)
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',libs/'kernel.jar')
base=json.loads((N/'baseline-reference.json').read_text());archives=json.loads((N/'archives.json').read_text())
assert sha(base['path'])==base['sha256'];args=[base['path'],base['sha256']]
for a in archives:assert sha(a['path'])==a['sha256'];args.extend([a['path'],a['sha256']])
command=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx3g','-cp',str(libs/'*')+';'+str(N/'classes'),'RawValueEventsProbe',*args]
(O/'command.json').write_text(json.dumps(command,indent=2));p=subprocess.run(command,capture_output=True,timeout=600)
for suffix,data in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:(O/('events.'+suffix)).write_bytes(data)
assert p.returncode==0,p.stderr.decode()
result=json.loads(p.stdout);events=result['events']
def row(i,d,ts):return dict(ID=i,D=None if d is None else dict(type='DATE',iso=d),TS=None if ts is None else dict(type='TIMESTAMP',iso=ts))
seed=row(1,'2024-02-29T23:59:58','2024-02-29T23:59:58.123456789');final=row(1,'2026-10-09T12:13:14','2026-10-09T12:13:14.987654321');insert=row(2,'1900-02-28T00:00:00','1999-12-31T23:59:59.000000000');null=row(2,None,None)
assert [(v['operation'],v['before'],v['after']) for v in events]==[('UPDATE',seed,final),('INSERT',None,insert),('UPDATE',insert,null),('DELETE',null,None)]
assert len({v['eventId'] for v in events})==4
truth=(N/'dml.stdout').read_text()
for expected in ['TRUTH|U1|1|2026-10-09T12:13:14|2026-10-09T12:13:14.987654321','TRUTH|I2|2|1900-02-28T00:00:00|1999-12-31T23:59:59.000000000','TRUTH|U2|2|NULL|NULL','FINAL|1|2026-10-09T12:13:14|2026-10-09T12:13:14.987654321','COUNT|1']:assert expected in truth
(O/'normal-proof.json').write_text(json.dumps(dict(events=4,mismatches=0,rollbackEvents=0,baseline=base,archives=archives,leaf=result['leaf'],kernelSha256=sha(libs/'kernel.jar'),adapterSha256=sha(libs/'adapter.jar'),sourceDmlReplayed=False,sourceActions=0,targetActions=0,productIntegrated=False),indent=2))
