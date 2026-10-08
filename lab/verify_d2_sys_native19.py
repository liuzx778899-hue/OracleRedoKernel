"""Native-only ruler for the held XID and exact unknown layout locations; no events."""
from pathlib import Path
import json, re, shutil, sys
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d2-sys-cross-member-20261008'
assert not (N/'native-pending-proof.json').exists()
label=sys.argv[1] if len(sys.argv)>1 else 'first'
marker=N/('executed-native-verifier-'+label+'.py')
assert not marker.exists()
shutil.copyfile(__file__,marker)
text=(N/'dump.trc').read_text(encoding='utf-8')
records=re.split(r'(?=REDO RECORD - Thread:)',text)
begin=[];end=[];original=[];skl=[]
for record in records:
    position=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
    scn=re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)
    if not position or not scn:continue
    at=dict(sequence=int(position[1],16),block=int(position[2],16),offset=int(position[3],16),scn=int(scn[1],16))
    if re.search(r'ktudh redo: slt: 0x001a sqn: 0x00000dc2',record):begin.append(at)
    if re.search(r'ktucm redo: slt: 0x001a sqn: 0x00000dc2',record):
        assert re.search(r'CHANGE #\d+ CON_ID:3.*CLS:35.*OP:5\.4 ',record)
        assert 'sta: 9 flg: 0x2' in record
        end.append(at)
    for change in re.split(r'(?=CHANGE #\d+ )',record):
        if not re.search(r'^CHANGE #\d+ CON_ID:3.*OP:5\.1 ',change):continue
        if re.search(r'xid:\s+0x000a\.01a\.00000dc2',change):
            metadata=re.search(r'rci: (\d+) opc: (\d+)\.(\d+) \[objn: (\d+) objd: (\d+)',change)
            assert metadata
            original.append(dict(**at,rci=int(metadata[1]),opcode=metadata[2]+'.'+metadata[3],object=int(metadata[4]),dataObject=int(metadata[5])))
        if at['sequence']==80 and (at['block'],at['offset']) in [(51452,324),(51453,100)]:
            assert 'objn: 21 objd: 2' in change and 'KDO Op code: SKL' in change
            assert 'slot: 4(0x4)' in change
            skl.append(dict(**at,kind='SKL',object=21,dataObject=2,slot=4))
assert len(begin)==1 and len(end)==1 and begin[0]['sequence']==80 and end[0]['sequence']==81
assert begin[0]['scn']<end[0]['scn'] and len(skl)==2
assert {x['sequence'] for x in original}=={80,81}
proof=dict(xid='10.26.3522',begin=begin[0],commit=end[0],originals=original,unknownClusterLinks=skl,events=0,actualCrossMemberNativeEvidence=True,parserSupported=False,sourceReplay=False)
(N/'native-pending-proof.json').write_text(json.dumps(proof,indent=2))
print(json.dumps(dict(xid=proof['xid'],begin=begin[0],commit=end[0],originals=len(original),clusterLinks=len(skl),parserSupported=False)))
