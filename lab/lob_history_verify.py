"""Independent acquired-byte comparison for raw117 ADD-column LOB events; no source actions."""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parent.parent;B=R/'lab/out/lob-history-20261011';S=B/'source';V=B/'offline-r1'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=[]
for kind,version in [('B',2),('S',3)]:
    table='T_D4_HL'+kind+'_20261011';label=kind+'-project-r'+str(version)
    assert (V/(label+'.rc')).read_text().strip()=='0'
    projected=json.loads((V/(label+'.stdout')).read_text());events=projected['events'];assert len(events)==1
    event=events[0];e=projected['evidence'];schema=e['eventSchemas'][0]['columns']
    assert [c['name'] for c in schema]==['ID','TXT','B','C','EXTRA']
    after=S/(table+'-after-add.snapshot');baseline=S/(table+'-baseline.snapshot')
    rows=[l.split('|')[5:] for l in after.read_text().splitlines() if l.startswith('ROW|')]
    before=[l.split('|')[5:] for l in baseline.read_text().splitlines() if l.startswith('ROW|')]
    assert len(rows)==len(before)==1 and len(rows[0])==5 and len(before[0])==4
    expected=[v.lower() for v in rows[0]];prior=[v.lower() for v in before[0]]+['null']
    def cells(image):
        return ['c102',image['TXT'].encode().hex(),image['B']['hex'],image['C']['hex'],
                'null' if image['EXTRA'] is None else image['EXTRA'].encode().hex()]
    assert event['after']['ID']==event['before']['ID']==1
    assert event['operation']=='UPDATE' and event['metadata']['beforeComplete'] and event['metadata']['afterComplete']
    assert cells(event['after'])==expected and cells(event['before'])==prior
    assert len(bytes.fromhex(event['after']['B']['hex']))==10000 and len(bytes.fromhex(event['after']['C']['hex']))==5000
    proof.append(dict(storage='BASICFILE' if kind=='B' else 'SECUREFILE',events=1,completeRowDifferences=0,
        baselineSha256=sha(baseline),afterSnapshotSha256=sha(after),rawSha256=sha(S/'inputs/117.raw'),
        envelopeSha256=projected['envelopesSha256'],sourceActionsRepeated=0))
assert (S/'source-stop.rc').read_text().strip()=='0'
out=V/'add-proof-r1.json';assert not out.exists();out.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
