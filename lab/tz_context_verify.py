"""Read-only comparison of native JDBC timezone bytes to already verified raw109 values."""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parent.parent;B=R/'lab/out/temporal-variants-20261011';N=B/'context-r1'
for label in ['datum','source-stop']:assert (N/(label+'.rc')).read_text().strip()=='0'
frozen={}
for line in (B/'verify-r3/raw-values.stdout').read_text().splitlines():
    if line.startswith('ROW|'):
        f=line.split('|');frozen[f[1]]=f[7:10]
lines=(N/'datum.stdout').read_text().splitlines();regions={};rows={};contexts=[]
for line in lines:
    f=line.split('|')
    if f[0]=='CONTEXT':contexts.append(f[2:])
    elif f[0]=='REGION':
        id=int(f[1]);assert 1<=id<=8191 and id not in regions;regions[id]=f[2]
    elif f[0]=='ROW':
        key=(f[1],f[2]);assert key not in rows;rows[key]=f[3:]
assert contexts==[['+00:00','timezlrg_32.dat','32']]*2
assert regions[382]=='Europe/Paris' and len(frozen)==3 and len(rows)==6
for (session,id),values in rows.items():assert values==frozen[id],(session,id,values,frozen[id])
assert set(session for session,id in rows)=={'+00:00','+05:30'}
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
proof=dict(nativeByteValues=18,rawByteDifferences=0,sessions=2,regionMappings=len(regions),
    sourceDatabaseZone='+00:00',sourceTimezoneFileVersion=32,driverSha256=sha(N/'libs/ojdbc8.jar'),
    contextOutputSha256=sha(N/'datum.stdout'),rawSha256=json.loads((B/'verify-r3/proof.json').read_text())['rawSha256'],
    sourceDml=0,sourceStopped=True,completeEventChain=False,productIntegrated=False)
out=N/'proof.json';assert not out.exists();out.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
