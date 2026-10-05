"""Independently bind typed capacity to the raw file header of each retained capture."""
import json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-typed-evidence-20261005'
configs=json.loads((R/'lab/out/oracle-null-sides-target-20261005/config.json').read_text())
configs.append({'prefix':'EMPT','captures':[str(R/'lab/out/oracle-empty-target-20261005/sequence-51.oracap'),'unused']})
checked=[]
def capacity(path):
    with path.open('rb') as f:
        header=f.read(16);assert header[:8]==b'ORACAP01'
        metadata_bytes=int.from_bytes(header[8:12],'little');assert int.from_bytes(header[12:16],'little')==512
        metadata=json.loads(f.read(metadata_bytes));assert metadata['block']==0
        raw=f.read(512);return int.from_bytes(raw[24:28],'little')+1
for c in configs:
    e=json.loads((O/(c['prefix']+'-typed.stdout.txt')).read_text())['evidence']
    for record,path in zip(e['captures'],c['captures'][::2]):
        actual=capacity(Path(path));assert actual==record['capacityBlocks']
        checked.append({'case':c['prefix'],'sequence':record['sequence'],'capacity_blocks':actual})
    assert len(e['captures'])==len(c['captures'])//2
(O/'capacity-proof.json').write_text(json.dumps({'checked':checked},indent=2))
print('CAPACITY_ZERO_DIFFERENCES|'+str(len(checked)))
