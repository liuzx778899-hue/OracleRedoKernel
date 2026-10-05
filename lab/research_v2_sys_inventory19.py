"""Full 5.1 coverage of raw-associated SYS XIDs; compare native independently, not history authorization."""
import hashlib,json,re,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';O=R/'lab/out/v2-sys-inventory-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
O.mkdir(exist_ok=False);(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes());classes=O/'classes';classes.mkdir()
shutil.copyfile(H/'kernel.jar',O/'kernel.jar');assert sha(O/'kernel.jar')=='672f01bc0c4a78856ce40af4de778bdca3fe881d3c46eb5de3fb069ff433302e'
def run(name,args):
    assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));return p
cp=str(O/'kernel.jar')+';'+str(classes);p=run('compile-sys-inventory',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/UndoInventoryProbe.java']);assert p.returncode==0
association=json.loads((H/'dictionary-association-proof.json').read_text());owners=sorted({(r['sequence'],r['xid']) for r in association['raw_selected_rows']})
(O/'execution-pin.json').write_text(json.dumps(dict(driver_sha256=sha(Path(__file__)),probe_sha256=sha(R/'lab/UndoInventoryProbe.java'),kernel_sha256=sha(O/'kernel.jar'),association_sha256=sha(H/'dictionary-association-proof.json'),native_dump_sha256=sha(H/'dump.trc'),archives={str(seq):sha(H/('archive-'+str(seq)+'.dbf')) for seq,_ in owners}),indent=2))
native={};native_nodes=[]
for record in re.split(r'(?=REDO RECORD - Thread:)',(H/'dump.trc').read_text()):
    if not record.startswith('REDO RECORD') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not m:continue
    position=tuple(int(x,16) for x in m.groups())
    for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',c)
        if not h:continue
        x=re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
        if not x:continue
        xid='.'.join(str(int(v,16)) for v in x.groups());key=(position[0],xid)
        if key not in owners:continue
        u=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c);meta=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc:\s*(\d+\.\d+)',c)
        assert u and meta,(position,c[:1000])
        node='NODE|'+ '|'.join(map(str,position+(int(h[1]),)))+'|'+':'.join(map(str,(int(h[2],16),int(u[1],16),int(u[2],16))))+'|'+str(int(meta[1],16))+'|'+meta[2]+'|'+meta[3]
        native.setdefault(key,[]).append(node)
        objects=re.search(r'\[objn:\s*(\d+) objd:\s*(\d+) tsn:\s*(\d+)\]',c);assert objects
        native_nodes.append(dict(sequence=position[0],xid=xid,position=position[1:]+(int(h[1]),),logical=int(objects[1]),data=int(objects[2]),opcode=meta[3],begin='Begin trans' in c))
results=[]
for seq,xid in owners:
    name='sys-inventory-'+str(seq)+'-'+xid;p=run(name,['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'UndoInventoryProbe',H/('archive-'+str(seq)+'.dbf'),xid])
    expected=native[(seq,xid)];actual=p.stdout.decode().splitlines()
    if p.returncode==0:assert actual==expected,(xid,len(actual),len(expected))
    else:assert not p.stdout
    results.append(dict(sequence=seq,xid=xid,rc=p.returncode,native_nodes=len(expected),matched=p.returncode==0,reason=p.stderr.decode()[:1200]))
(O/'proof.json').write_text(json.dumps(dict(results=results,native_nodes=native_nodes,full_5_1_coverage_only=True,complete_transaction_controls_graph_verified=False,complete_SYS_row_values_verified=False,historical_dictionary_verified=False,source_actions=0),indent=2))
print(json.dumps(dict(results=results,total_native_nodes=len(native_nodes),complete_dictionary_history=False)))
