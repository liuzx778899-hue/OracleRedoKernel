"""Frozen acquired ADD values and independent DROP SQL truth compare the raw-derived events."""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parent.parent;B=R/'lab/out/lob-history-20261011';S=B/'source';V=B/'offline-r1'
proof=[]
for kind,label in [('B','B-drop-r6'),('S','S-drop-r1')]:
    assert (V/(label+'.rc')).read_text().strip()=='0'
    projected=json.loads((V/(label+'.stdout')).read_text());events=projected['events'];assert len(events)==2
    table='T_D4_HL'+kind+'_20261011'
    truth=(S/(table+'-drop.stdout')).read_text();assert 'ROW|1|after-drop|10000|5000' in truth
    previous=events[0]['after'];drop=events[1]
    assert drop['operation']=='UPDATE' and drop['metadata']['beforeComplete'] and drop['metadata']['afterComplete']
    expected={k:v for k,v in previous.items() if k!='EXTRA'}
    assert drop['before']==expected
    expected['TXT']='after-drop';assert drop['after']==expected
    assert list(drop['after'])==['ID','TXT','B','C']
    proof.append(dict(storage='BASICFILE' if kind=='B' else 'SECUREFILE',events=2,dropEvents=1,completeRowDifferences=0,
        raw118Sha256=hashlib.sha256((S/'inputs/118.raw').read_bytes()).hexdigest(),envelopeSha256=projected['envelopesSha256']))
out=V/'drop-proof-r1.json';assert not out.exists();out.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
