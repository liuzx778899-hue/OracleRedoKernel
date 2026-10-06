"""Freeze raw and native diagnostics for the captured split; never execute source actions."""
import json,re,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006'
assert not (H/'maintenance-research.command.json').exists()
(H/'executed-maintenance-research-driver.py').write_bytes(Path(__file__).read_bytes())
(H/'executed-IndexMaintenanceResearchProbe.java').write_bytes((R/'lab/IndexMaintenanceResearchProbe.java').read_bytes())
j='D:/work/Java/jdk-17.0.7/bin/'
for name,args in [('compile-maintenance-research',[j+'javac.exe','-cp',H/'kernel.jar','-d',H/'projection-classes',R/'lab/IndexMaintenanceResearchProbe.java']),('maintenance-research',[j+'java.exe','-cp',str(H/'kernel.jar')+';'+str(H/'projection-classes'),'IndexMaintenanceResearchProbe',H/'archive-70.dbf'])]:
    args=list(map(str,args));(H/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=900)
    for ext,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(H/(name+'.'+ext)).write_bytes(value)
    assert p.returncode==0
selected=[];current=None;buffer=[]
def save():
    if current and current[0]==70 and 126580<=current[1]<=126630 and any('OBJ:50 ' in s or '[objn: 50 objd: 50 ' in s for s in buffer):
        name='native-maintenance-'+('-'.join(map(str,current)))+'.txt';(H/name).write_text(''.join(buffer));selected.append(dict(position=current,native=name))
with (H/'dump.trc').open(errors='replace') as stream:
    for line in stream:
        m=re.match(r'REDO RECORD - Thread:1 RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
        if m:
            save();current=tuple(int(x,16) for x in m.groups());buffer=[]
        if current and current[0]==70 and 126580<=current[1]<=126630:buffer.append(line)
save();(H/'native-maintenance-records.json').write_text(json.dumps(selected,indent=2))
print('DIAGNOSTIC_ONLY|native_records='+str(len(selected))+'|events=0')
