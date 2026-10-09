"""Reconcile retained r90 output using the actual zero-based member/sequence schema contract."""
from pathlib import Path
from decimal import Decimal
import json,hashlib,base64
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d3-stream-runtime-20261009/producer-product-r88'
source=(R/'lab/producer_recovery_product.py').read_text()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=json.loads((N/'product-proof.json').read_text());projection=json.loads((N/'projection.stdout').read_text(),parse_float=Decimal)
e=projection['evidence'];SOURCE='oracle-d2-blocks';TARGET='oracle-recovery-RPROD1'
receipt=json.loads((N/'r89-r89-checkpoint.stdout').read_text())['data']
actual=(N/'r90-r90-actual.stdout').read_text()
exec('def image(values):'+source.split('def image(values):',1)[1].split('def enroll(prefix):',1)[0])
assert not (N/'candidate-normal-published.json').exists()
tail="assert 'COUNTS|46|46|1|46|1'"+source.split(" assert 'COUNTS|46|46|1|46|1'",1)[1].split('\nfinally:',1)[0]
exec(tail.replace('\n ','\n'))
print('EVENTS=46;MISMATCHES=0;CHECKPOINT_ACK_BYTES_EQUAL=true;PRODUCT_RERUN=false')
